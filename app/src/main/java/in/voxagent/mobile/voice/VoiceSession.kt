package `in`.voxagent.mobile.voice

import android.content.Context
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.logging.RemoteLog
import `in`.voxagent.mobile.net.VoxHttp
import java.util.Timer
import java.util.TimerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import timber.log.Timber

private const val TAG = "VoiceSession"

private const val PING_INTERVAL_MS = 15_000L

private val protocolJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

class VoiceSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioEngine = VoiceAudioEngine(context)

    fun isVoxSpeaking(): Boolean = audioEngine.isPlaying()

    private var webSocket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var currentTurnId: String? = null

    @Volatile private var sessionStartedAt = 0L
    @Volatile private var connectStartedAt = 0L
    @Volatile private var turnSentAt = 0L
    @Volatile private var greetingPending = true
    @Volatile private var awaitingFirstAudio = false
    @Volatile private var awaitingFirstDelta = false
    @Volatile private var lastAudioFrameAt = 0L
    @Volatile private var turnFrames = 0
    @Volatile private var turnBytes = 0
    @Volatile private var maxFrameGapMs = 0L
    @Volatile private var utterances = 0
    @Volatile private var bargeIns = 0

    private val _status = MutableStateFlow(VoiceStatus.IDLE)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    fun start() {
        if (_status.value == VoiceStatus.CONNECTING || _status.value == VoiceStatus.ACTIVE) return
        _status.value = VoiceStatus.CONNECTING
        sessionStartedAt = System.currentTimeMillis()
        greetingPending = true
        awaitingFirstAudio = false
        awaitingFirstDelta = false
        turnSentAt = 0L
        lastAudioFrameAt = 0L
        utterances = 0
        bargeIns = 0
        RemoteLog.i(
            TAG,
            "=== voice session starting === device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT} app=${BuildConfig.VERSION_NAME}",
        )

        scope.launch {
            try {
                connectSocket()
            } catch (e: Exception) {
                RemoteLog.e(TAG, "Failed to connect voice socket", e)
                fail("Voice connection failed: ${e.message}")
            }
        }
    }

    fun stop() {
        if (sessionStartedAt != 0L) {
            RemoteLog.i(
                TAG,
                "=== voice session ended === duration_ms=${System.currentTimeMillis() - sessionStartedAt} utterances=$utterances barge_ins=$bargeIns underruns=${audioEngine.underrunCount()}",
            )
            sessionStartedAt = 0L
        }
        pingTimer?.cancel()
        pingTimer = null
        webSocket?.close(1000, "client stop")
        webSocket = null
        audioEngine.release()
        currentTurnId = null
        _status.value = VoiceStatus.IDLE
    }

    private fun fail(message: String) {
        _status.value = VoiceStatus.ERROR
        _events.tryEmit(VoiceEvent.Error(message))
    }

    private fun connectSocket() {
        val token = AuthManager(context).currentToken()
        if (token == null) {
            fail("Sign in before starting a voice session")
            return
        }

        val wsBase =
            BuildConfig.VOX_API_BASE_URL.trimEnd('/').let {
                when {
                    it.startsWith("https://") -> "wss://" + it.removePrefix("https://")
                    it.startsWith("http://") -> "ws://" + it.removePrefix("http://")
                    else -> "ws://$it"
                }
            }

        val request =
            Request.Builder()
                .url("$wsBase/v1/me/voice/socket?input=stream")
                .header("Authorization", "Bearer $token")
                .build()

        connectStartedAt = System.currentTimeMillis()
        RemoteLog.i(TAG, "connectSocket: connecting to ${request.url}")
        webSocket =
            VoxHttp.client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        RemoteLog.i(
                            TAG,
                            "connected to voice socket: ws_connect_ms=${System.currentTimeMillis() - connectStartedAt} http=${response.code} protocol=${response.protocol}",
                        )
                        startPingTimer(webSocket)

                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Timber.tag(TAG).d("onMessage(text): ${text.length} chars")
                        handleServerMessage(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Timber.tag(TAG).d("onMessage(bytes): ${bytes.size} bytes")
                        onAudioFrame(bytes.size)
                        audioEngine.mp3Decoder.enqueueChunk(bytes.toByteArray())
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        RemoteLog.e(
                            TAG,
                            "Voice socket failure after ${System.currentTimeMillis() - sessionStartedAt}ms http=${response?.code}",
                            t,
                        )
                        fail("Voice connection failed: ${t.message}")
                        stop()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        RemoteLog.i(TAG, "Voice socket closed: $code $reason")
                        if (_status.value != VoiceStatus.IDLE) {
                            _status.value = VoiceStatus.IDLE
                        }
                    }
                },
            )
    }

    private fun startPingTimer(ws: WebSocket) {
        val timer = Timer("voice-ping", true)
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    ws.send(
                        protocolJson.encodeToString(
                            VoiceClientMessage.serializer(),
                            VoiceClientMessage.Ping,
                        )
                    )
                    RemoteLog.i(
                        TAG,
                        "PLAYBACK STATS ~15s: underruns_total=${audioEngine.underrunCount()} queued_chunks=${audioEngine.queuedChunkCount()} playing=${audioEngine.isPlaying()}",
                    )
                }
            },
            PING_INTERVAL_MS,
            PING_INTERVAL_MS,
        )
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (webSocket === ws && _status.value == VoiceStatus.CONNECTING) {
                    stop()
                    fail("Voice server did not acknowledge streaming mode")
                }
            }
        }, 10_000L)
        pingTimer = timer
    }

    private fun startAudio(ws: WebSocket) {
        Timber.tag(TAG).d("startAudio: starting playback + capture")
        audioEngine.startPlayback()

        audioEngine.startCapture { samples ->
            if (webSocket !== ws) return@startCapture
            var offset = 0
            while (offset < samples.size) {
                val end = minOf(offset + 1600, samples.size)
                val chunk = samples.copyOfRange(offset, end)
                if (ws.queueSize() > 64000 || !ws.send(pcm16ToLittleEndianBytes(chunk).toByteString())) {
                    scope.launch {
                        stop()
                        fail("Voice upload stalled")
                    }
                    return@startCapture
                }
                offset = end
            }
        }
    }

    private fun pcm16ToLittleEndianBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun onAudioFrame(size: Int) {
        val now = System.currentTimeMillis()
        if (lastAudioFrameAt != 0L) maxFrameGapMs = maxOf(maxFrameGapMs, now - lastAudioFrameAt)
        lastAudioFrameAt = now
        turnFrames++
        turnBytes += size
        if (greetingPending) {
            greetingPending = false
            RemoteLog.i(
                TAG,
                "LATENCY greeting_first_audio_ms=${now - sessionStartedAt} (since session start)",
            )
        } else if (awaitingFirstAudio) {
            awaitingFirstAudio = false
            if (turnSentAt != 0L)
                RemoteLog.i(
                    TAG,
                    "LATENCY time_to_first_audio_ms=${now - turnSentAt} (utterance sent -> first audio frame)",
                )
        }
    }

    private fun handleServerMessage(text: String) {
        val message =
            try {
                protocolJson.decodeFromString(VoiceServerMessage.serializer(), text)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Unrecognized voice server message: $text")
                return
            }

        when (message) {
            is VoiceServerMessage.Connected -> {
                if (message.input_mode != "stream") {
                    stop()
                    fail("Voice server does not support continuous audio yet")
                    return
                }
                if (_status.value == VoiceStatus.CONNECTING) {
                    webSocket?.let { startAudio(it) }
                    _status.value = VoiceStatus.ACTIVE
                }
                RemoteLog.i(
                    TAG,
                    "voice session connected: format=${message.format} sample_rate=${message.sample_rate} since_session_start_ms=${System.currentTimeMillis() - sessionStartedAt}",
                )
            }
            is VoiceServerMessage.AudioTurnSubmitted -> {
                utterances++
                turnSentAt = System.currentTimeMillis()
                awaitingFirstAudio = true
                awaitingFirstDelta = true
                turnFrames = 0
                turnBytes = 0
                maxFrameGapMs = 0L
                RemoteLog.i(TAG, "server endpoint: audio_ms=${message.audio_ms} detector=earshot")
            }
            is VoiceServerMessage.UserTranscript -> {
                if (turnSentAt != 0L)
                    RemoteLog.i(
                        TAG,
                        "LATENCY transcript_ms=${System.currentTimeMillis() - turnSentAt} (server endpoint -> transcript)",
                    )
                RemoteLog.i(TAG, "user transcript: ${message.text.length} chars")
                _events.tryEmit(VoiceEvent.UserTranscript(message.text))
            }
            is VoiceServerMessage.Thinking -> {
                currentTurnId = message.turn_id
                _events.tryEmit(VoiceEvent.Thinking)
            }
            is VoiceServerMessage.TextDelta -> {
                if (message.turn_id == currentTurnId) {
                    if (awaitingFirstDelta) {
                        awaitingFirstDelta = false
                        if (turnSentAt != 0L)
                            RemoteLog.i(
                                TAG,
                                "LATENCY first_text_delta_ms=${System.currentTimeMillis() - turnSentAt} (server endpoint -> first text)",
                            )
                    }
                    _events.tryEmit(VoiceEvent.Delta(message.delta))
                }
            }
            is VoiceServerMessage.Done -> {
                RemoteLog.i(
                    TAG,
                    "turn done: ${message.turn_id} frames=$turnFrames bytes=$turnBytes max_frame_gap_ms=$maxFrameGapMs total_ms=${if (turnSentAt != 0L) System.currentTimeMillis() - turnSentAt else -1} underruns_total=${audioEngine.underrunCount()}",
                )
                _events.tryEmit(VoiceEvent.Done)
            }
            is VoiceServerMessage.Interrupted -> {
                RemoteLog.i(TAG, "playback interrupted")
                currentTurnId = null
                audioEngine.clearPlayback()
                _events.tryEmit(VoiceEvent.Interrupted)
            }
            is VoiceServerMessage.Error -> {
                RemoteLog.w(TAG, "Voice server error: ${message.message}")
                _events.tryEmit(VoiceEvent.Error(message.message))
            }
            is VoiceServerMessage.Pong -> {}
        }
    }
}
