package `in`.voxagent.mobile.voice

import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.stt.downloadSttModel
import `in`.voxagent.mobile.stt.getSttEngine
import `in`.voxagent.mobile.stt.isSttModelDownloaded
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
import java.util.Timer
import java.util.TimerTask

private const val TAG = "VoiceSession"
private const val SPEECH_THRESHOLD = 0.015f
private const val SILENCE_TIMEOUT_MS = 650L
private const val MIN_UTTERANCE_SAMPLES = 5600 // 350ms at 16kHz
private const val PING_INTERVAL_MS = 15_000L

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
                if (!isSttModelDownloaded(context)) {
                    downloadSttModel(context)
                }
                getSttEngine(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare local Whisper model", e)
                fail("Speech recognition setup failed: ${e.message}")
                return@launch
            }
            connectSocket()
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

        webSocket = VoxHttp.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Voice socket connected")
                startPingTimer(webSocket)
                startAudio(webSocket)
                _status.value = VoiceStatus.ACTIVE
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
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
        audioEngine.startPlayback()

        val speechBuffer = ArrayList<Float>(16000 * 5)
        var isInSpeech = false
        var lastSpeechTime = System.currentTimeMillis()

        audioEngine.startCapture { samples ->
            val rms = kotlin.math.sqrt(samples.sumOf { (it * it).toDouble() } / samples.size.coerceAtLeast(1)).toFloat()

            if (rms >= SPEECH_THRESHOLD) {
                if (audioEngine.isPlaying()) {
                    audioEngine.clearPlayback()
                    ws.send(protocolJson.encodeToString(VoiceClientMessage.serializer(), VoiceClientMessage.Interrupt))
                    _events.tryEmit(VoiceEvent.Interrupted)
                }
                speechBuffer.addAll(samples.asList())
                lastSpeechTime = System.currentTimeMillis()
                isInSpeech = true
            } else if (isInSpeech) {
                speechBuffer.addAll(samples.asList())
                if (System.currentTimeMillis() - lastSpeechTime >= SILENCE_TIMEOUT_MS) {
                    isInSpeech = false
                    if (speechBuffer.size >= MIN_UTTERANCE_SAMPLES) {
                        val utterance = speechBuffer.toFloatArray()
                        speechBuffer.clear()
                        transcribeAndSend(ws, utterance)
                    } else {
                        speechBuffer.clear()
                    }
                }
            }
        }
    }

    private fun transcribeAndSend(ws: WebSocket, utterance: FloatArray) {
        scope.launch {
            val text = try {
                getSttEngine(context).transcribeData(utterance, printTimestamp = false)
            } catch (e: Exception) {
                Log.e(TAG, "Whisper transcription failed", e)
                _events.tryEmit(VoiceEvent.Error("Speech recognition failed unexpectedly"))
                return@launch
            }
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@launch
            _events.tryEmit(VoiceEvent.UserTranscript(trimmed))
            ws.send(
                protocolJson.encodeToString(
                    VoiceClientMessage.serializer(),
                    VoiceClientMessage.Turn(text = trimmed, conversation_id = null, interrupted = false),
                ),
            )
        }
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
