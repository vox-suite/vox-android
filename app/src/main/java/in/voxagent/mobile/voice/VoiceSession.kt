package `in`.voxagent.mobile.voice

import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.Timer
import java.util.TimerTask

private const val TAG = "VoiceSession"
private const val SPEECH_THRESHOLD = 0.015f
private const val SILENCE_TIMEOUT_MS = 650L
private const val MIN_UTTERANCE_SAMPLES = 5600 // 350ms at 16kHz
private const val PING_INTERVAL_MS = 15_000L

// Debounce for barge-in: without echo cancellation, Vox's own speaker output
// leaking into the mic reads as speech above SPEECH_THRESHOLD, which would
// otherwise self-interrupt playback the instant it starts. Require a short
// run of consecutive loud mic chunks (genuine speech is sustained; leaked
// echo of a single word usually isn't) before treating it as a real interrupt.
private const val INTERRUPT_DEBOUNCE_CHUNKS = 4

enum class VoiceStatus { IDLE, CONNECTING, ACTIVE, ERROR }

sealed class VoiceEvent {
    data class UserTranscript(val text: String) : VoiceEvent()
    data class Delta(val text: String) : VoiceEvent()
    data object Thinking : VoiceEvent()
    data object Done : VoiceEvent()
    data object Interrupted : VoiceEvent()
    data class Error(val message: String) : VoiceEvent()
}

private val protocolJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

class VoiceSession(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioEngine = VoiceAudioEngine()

    private var webSocket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var currentTurnId: String? = null

    private val _status = MutableStateFlow(VoiceStatus.IDLE)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    fun start() {
        if (_status.value == VoiceStatus.CONNECTING || _status.value == VoiceStatus.ACTIVE) return
        _status.value = VoiceStatus.CONNECTING

        scope.launch {
            try {
                connectSocket()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect voice socket", e)
                fail("Voice connection failed: ${e.message}")
            }
        }
    }

    fun stop() {
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

        val wsBase = BuildConfig.VOX_API_BASE_URL.trimEnd('/').let {
            when {
                it.startsWith("https://") -> "wss://" + it.removePrefix("https://")
                it.startsWith("http://") -> "ws://" + it.removePrefix("http://")
                else -> "ws://$it"
            }
        }

        val request = Request.Builder()
            .url("$wsBase/v1/me/voice/socket")
            .header("Authorization", "Bearer $token")
            .build()

        Log.d(TAG, "connectSocket: connecting to ${request.url}")
        webSocket = VoxHttp.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Voice socket connected")
                startPingTimer(webSocket)
                startAudio(webSocket)
                _status.value = VoiceStatus.ACTIVE
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "onMessage(text): $text")
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "onMessage(bytes): ${bytes.size} bytes")
                audioEngine.mp3Decoder.enqueueChunk(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Voice socket failure", t)
                fail("Voice connection failed: ${t.message}")
                stop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Voice socket closed: $code $reason")
                if (_status.value != VoiceStatus.IDLE) {
                    _status.value = VoiceStatus.IDLE
                }
            }
        })
    }

    private fun startPingTimer(ws: WebSocket) {
        val timer = Timer("voice-ping", true)
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                ws.send(protocolJson.encodeToString(VoiceClientMessage.serializer(), VoiceClientMessage.Ping))
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS)
        pingTimer = timer
    }

    private fun startAudio(ws: WebSocket) {
        Log.d(TAG, "startAudio: starting playback + capture")
        audioEngine.startPlayback()

        val speechBuffer = ArrayList<Short>(16000 * 5)
        var isInSpeech = false
        var lastSpeechTime = System.currentTimeMillis()
        var lastLevelLog = 0L
        var consecutiveLoudChunks = 0

        Log.d(TAG, "startAudio: SPEECH_THRESHOLD=$SPEECH_THRESHOLD MIN_UTTERANCE_SAMPLES=$MIN_UTTERANCE_SAMPLES SILENCE_TIMEOUT_MS=$SILENCE_TIMEOUT_MS")

        audioEngine.startCapture { samples ->
            val rms = kotlin.math.sqrt(
                samples.sumOf { val f = it / 32768.0; f * f } / samples.size.coerceAtLeast(1),
            ).toFloat()

            val now = System.currentTimeMillis()
            if (now - lastLevelLog >= 1000) {
                Log.d(TAG, "mic level: rms=$rms threshold=$SPEECH_THRESHOLD isInSpeech=$isInSpeech")
                lastLevelLog = now
            }

            if (rms >= SPEECH_THRESHOLD) {
                consecutiveLoudChunks++

                // If user speaks while agent is playing speech, interrupt --
                // but only once the mic has picked up sustained sound, not a
                // single chunk (which is usually Vox's own output leaking
                // back through the speakers rather than real speech).
                if (consecutiveLoudChunks >= INTERRUPT_DEBOUNCE_CHUNKS && audioEngine.isPlaying()) {
                    Log.d(TAG, "startAudio: sustained speech (rms=$rms) while playing back -> clearing playback + sending Interrupt")
                    audioEngine.clearPlayback()
                    ws.send(protocolJson.encodeToString(VoiceClientMessage.serializer(), VoiceClientMessage.Interrupt))
                    _events.tryEmit(VoiceEvent.Interrupted)
                }
                if (!isInSpeech) Log.d(TAG, "startAudio: speech started (rms=$rms)")
                speechBuffer.addAll(samples.asList())
                lastSpeechTime = now
                isInSpeech = true
            } else if (isInSpeech) {
                consecutiveLoudChunks = 0
                speechBuffer.addAll(samples.asList())
                if (now - lastSpeechTime >= SILENCE_TIMEOUT_MS) {
                    isInSpeech = false
                    Log.d(TAG, "startAudio: speech ended, buffered ${speechBuffer.size} samples")
                    if (speechBuffer.size >= MIN_UTTERANCE_SAMPLES) {
                        val utterance = speechBuffer.toShortArray()
                        speechBuffer.clear()
                        sendUtterance(ws, utterance)
                    } else {
                        Log.d(TAG, "startAudio: utterance too short (${speechBuffer.size} < $MIN_UTTERANCE_SAMPLES), dropping")
                        speechBuffer.clear()
                    }
                }
            }
        }
    }

    /** Little-endian 16-bit PCM bytes, matching vox-core's `i16::from_le_bytes` decode. */
    private fun pcm16ToLittleEndianBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = samples[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun sendUtterance(ws: WebSocket, utterance: ShortArray) {
        val bytes = pcm16ToLittleEndianBytes(utterance)
        Log.d(TAG, "sendUtterance: ${utterance.size} samples (${utterance.size * 1000 / 16000}ms), ${bytes.size} bytes")
        ws.send(bytes.toByteString())
        ws.send(
            protocolJson.encodeToString(
                VoiceClientMessage.serializer(),
                VoiceClientMessage.Turn(conversation_id = null),
            ),
        )
    }

    private fun handleServerMessage(text: String) {
        val message = try {
            protocolJson.decodeFromString(VoiceServerMessage.serializer(), text)
        } catch (e: Exception) {
            Log.w(TAG, "Unrecognized voice server message: $text", e)
            return
        }

        when (message) {
            is VoiceServerMessage.Connected -> {
                Log.i(TAG, "Voice connected: format=${message.format} rate=${message.sample_rate}")
            }
            is VoiceServerMessage.UserTranscript -> {
                Log.d(TAG, "user transcript: \"${message.text}\"")
                _events.tryEmit(VoiceEvent.UserTranscript(message.text))
            }
            is VoiceServerMessage.Thinking -> {
                currentTurnId = message.turn_id
                _events.tryEmit(VoiceEvent.Thinking)
            }
            is VoiceServerMessage.TextDelta -> {
                if (message.turn_id == currentTurnId) {
                    _events.tryEmit(VoiceEvent.Delta(message.delta))
                }
            }
            is VoiceServerMessage.Done -> {
                _events.tryEmit(VoiceEvent.Done)
            }
            is VoiceServerMessage.Interrupted -> {
                currentTurnId = null
                audioEngine.clearPlayback()
                _events.tryEmit(VoiceEvent.Interrupted)
            }
            is VoiceServerMessage.Error -> {
                Log.w(TAG, "Voice server error: ${message.message}")
                _events.tryEmit(VoiceEvent.Error(message.message))
            }
            is VoiceServerMessage.Pong -> {}
        }
    }
}
