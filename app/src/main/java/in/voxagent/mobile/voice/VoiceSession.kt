package `in`.voxagent.mobile.voice

import android.content.Context
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.auth.SessionStore
import `in`.voxagent.mobile.net.VoxHttp
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

private val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

@Serializable private data class VoiceBootstrap(val session_id: String, val ticket: String)

class VoiceSession(context: Context, private val deviceId: suspend () -> String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = SessionStore(context.applicationContext)
    private val audioEngine = VoiceAudioEngine(context.applicationContext)
    private val socketClient =
        VoxHttp.client.newBuilder().pingInterval(15, TimeUnit.SECONDS).build()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var generation = 0L
    @Volatile private var muted = false
    private var timer: Timer? = null
    private var sessionStarted = 0L
    private var bootstrapReceivedMs = 0L
    private var socketConnectedMs = 0L
    private var audioGeneration = 0L
    private var receivedAudio = false
    private var reportedPlayback = false
    private var playbackPositionBeforeAudio = 0L
    private val _status = MutableStateFlow(VoiceStatus.IDLE)
    val status = _status.asStateFlow()
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun isVoxSpeaking() = audioEngine.isPlaying()

    fun setMuted(value: Boolean) {
        muted = value
    }

    @Synchronized
    fun start() {
        if (_status.value == VoiceStatus.CONNECTING || _status.value == VoiceStatus.ACTIVE) return
        val token = sessionStore.currentToken()
        if (token == null) {
            _status.value = VoiceStatus.ERROR
            _events.tryEmit(VoiceEvent.Error("Sign in before talking to Vox"))
            return
        }
        val id = ++generation
        sessionStarted = System.nanoTime()
        audioGeneration = 0
        receivedAudio = false
        reportedPlayback = false
        muted = false
        _status.value = VoiceStatus.CONNECTING
        startTimer(id, token)
        scope.launch {
            try {
                val device = deviceId()
                if (id != generation || sessionStore.currentToken() != token) return@launch
                val bootstrap =
                    protocolJson.decodeFromString<VoiceBootstrap>(
                        VoxHttp.postJson(
                            "/v1/me/desktop-voice/sessions",
                            "{\"device_id\":\"$device\"}",
                            token,
                        )
                    )
                synchronized(this@VoiceSession) {
                    if (id != generation) return@synchronized
                    check(sessionStore.currentToken() == token) { "Signed out" }
                    val request =
                        Request.Builder()
                            .url(bridgeVoiceUrl(BuildConfig.VOX_BRIDGE_BASE_URL))
                            .build()
                    bootstrapReceivedMs =
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sessionStarted)
                    webSocket =
                        socketClient.newWebSocket(
                            request,
                            listener(id, token, bootstrap.ticket, bootstrap.session_id),
                        )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                terminate(
                    id,
                    "Could not connect to Vox voice. Check your connection and try again.",
                )
            }
        }
    }

    @Synchronized
    fun stop() {
        terminate(generation, null)
    }

    @Synchronized
    private fun terminate(id: Long, error: String?) {
        if (id != generation) return
        generation++
        timer?.cancel()
        timer = null
        val socket = webSocket
        webSocket = null
        socket?.send(
            protocolJson.encodeToString(VoiceClientMessage.serializer(), VoiceClientMessage.Stop)
        )
        socket?.cancel()
        audioEngine.release()
        _status.value = if (error == null) VoiceStatus.IDLE else VoiceStatus.ERROR
        if (error != null) _events.tryEmit(VoiceEvent.Error(error))
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    fun sendText(text: String): Boolean {
        if (text.isBlank() || text.length > 8192 || _status.value != VoiceStatus.ACTIVE)
            return false
        val socket = webSocket ?: return false
        val message =
            protocolJson.encodeToString(
                VoiceClientMessage.serializer(),
                VoiceClientMessage.Text(text),
            )
        if (message.toByteArray(Charsets.UTF_8).size > 8192) return false
        return socket.queueSize() < 16000 && socket.send(message)
    }

    private fun listener(id: Long, token: String, ticket: String, sessionId: String) =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@VoiceSession) {
                    if (id != generation) {
                        webSocket.cancel()
                        return
                    }
                    socketConnectedMs =
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sessionStarted)
                    if (
                        sessionStore.currentToken() != token ||
                            !webSocket.send(
                                protocolJson.encodeToString(
                                    VoiceClientMessage.serializer(),
                                    VoiceClientMessage.Start(ticket),
                                )
                            )
                    )
                        terminate(id, "Voice session could not start")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                synchronized(this@VoiceSession) {
                    if (id != generation) return
                    try {
                        check(text.length <= 8192) { "Oversized voice control" }
                        when (
                            val message =
                                protocolJson.decodeFromString(VoiceServerMessage.serializer(), text)
                        ) {
                            is VoiceServerMessage.Connected -> {
                                check(
                                    _status.value == VoiceStatus.CONNECTING &&
                                        message.isSupported() &&
                                        message.session_id == sessionId
                                ) {
                                    "Unsupported voice audio"
                                }
                                startAudio(id, webSocket)
                                _status.value = VoiceStatus.ACTIVE
                                sendMetric(webSocket, "bootstrap_received", bootstrapReceivedMs)
                                sendMetric(webSocket, "socket_connected", socketConnectedMs)
                                sendMetric(webSocket, "microphone_ready")
                                sendMetric(webSocket, "session_ready")
                            }
                            is VoiceServerMessage.AudioStart -> {
                                if (audioGeneration != message.generation) {
                                    reportedPlayback = false
                                    playbackPositionBeforeAudio = audioEngine.playbackPosition()
                                }
                                audioGeneration = message.generation
                                receivedAudio = false
                            }
                            is VoiceServerMessage.Mark -> {
                                check(
                                    _status.value == VoiceStatus.ACTIVE &&
                                        message.name.isNotBlank() &&
                                        message.name.length <= 128
                                )
                                audioEngine.mark(message.name)
                            }
                            is VoiceServerMessage.Clear -> {
                                audioGeneration = message.generation
                                audioEngine.clearPlayback()
                                receivedAudio = false
                                reportedPlayback = false
                                playbackPositionBeforeAudio = audioEngine.playbackPosition()
                            }
                            is VoiceServerMessage.Error -> terminate(id, "Vox voice session failed")
                        }
                    } catch (_: Exception) {
                        terminate(id, "Vox voice session ended unexpectedly")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                synchronized(this@VoiceSession) {
                    if (id != generation) return
                    try {
                        check(_status.value == VoiceStatus.ACTIVE)
                        if (!receivedAudio) {
                            receivedAudio = true
                            playbackPositionBeforeAudio = audioEngine.playbackPosition()
                            sendMetric(webSocket, "first_audio_received")
                        }
                        audioEngine.enqueueMuLaw(bytes.toByteArray())
                    } catch (_: Exception) {
                        terminate(id, "Voice playback stalled. Try reconnecting.")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                terminate(id, "Voice connection lost. Try reconnecting.")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                terminate(id, "Vox ended the call. Tap Retry to reconnect.")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                terminate(id, "Vox ended the call. Tap Retry to reconnect.")
            }
        }

    private fun startAudio(id: Long, socket: WebSocket) {
        val packetizer = MuLawPacketizer()
        var captureMuted = false
        audioEngine.startPlayback {
            scope.launch { terminate(id, "Another call took over audio. Reconnect when it ends.") }
        }
        audioEngine.startCapture(
            onError = { scope.launch { terminate(id, "Microphone stopped. Try reconnecting.") } }
        ) { samples ->
            if (id != generation || webSocket !== socket) return@startCapture
            val currentlyMuted = muted
            if (captureMuted != currentlyMuted) {
                packetizer.clear()
                captureMuted = currentlyMuted
            }
            if (currentlyMuted) return@startCapture
            for (frame in packetizer.accept(samples)) {
                if (socket.queueSize() > 16000 || !socket.send(frame.toByteString())) {
                    scope.launch { terminate(id, "Voice upload stalled. Try reconnecting.") }
                    break
                }
            }
        }
    }

    private fun sendMetric(
        socket: WebSocket,
        stage: String,
        elapsedMs: Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sessionStarted),
    ) {
        check(
            socket.send(
                protocolJson.encodeToString(
                    VoiceClientMessage.serializer(),
                    VoiceClientMessage.ClientMetric(
                        stage,
                        elapsedMs,
                        audioGeneration,
                        audioEngine.bufferedMilliseconds(),
                    ),
                )
            )
        )
    }

    private fun startTimer(id: Long, token: String) {
        val started = System.nanoTime()
        var lastAuthCheck = 0L
        timer =
            Timer("vox-voice-session", true).also { timer ->
                timer.scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            synchronized(this@VoiceSession) {
                                if (id != generation) return
                                if (
                                    System.nanoTime() - lastAuthCheck > TimeUnit.SECONDS.toNanos(1)
                                ) {
                                    lastAuthCheck = System.nanoTime()
                                    if (sessionStore.currentToken() != token) {
                                        terminate(id, null)
                                        return
                                    }
                                }
                                if (
                                    _status.value == VoiceStatus.CONNECTING &&
                                        System.nanoTime() - started > TimeUnit.SECONDS.toNanos(30)
                                ) {
                                    terminate(id, "Vox voice connection timed out")
                                    return
                                }
                                if (_status.value == VoiceStatus.ACTIVE) {
                                    try {
                                        val socket = webSocket ?: return
                                        if (
                                            receivedAudio &&
                                                !reportedPlayback &&
                                                audioEngine.playbackPosition() >
                                                    playbackPositionBeforeAudio
                                        ) {
                                            reportedPlayback = true
                                            sendMetric(socket, "first_playback_started")
                                        }
                                        for (name in audioEngine.completedMarks()) {
                                            check(
                                                socket.queueSize() < 16000 &&
                                                    socket.send(
                                                        protocolJson.encodeToString(
                                                            VoiceClientMessage.serializer(),
                                                            VoiceClientMessage.PlaybackFinished(
                                                                name
                                                            ),
                                                        )
                                                    )
                                            )
                                        }
                                    } catch (_: Exception) {
                                        terminate(id, "Voice playback stopped. Try reconnecting.")
                                    }
                                }
                            }
                        }
                    },
                    25,
                    25,
                )
            }
    }
}

internal fun bridgeVoiceUrl(base: String): String {
    val url = base.trimEnd('/')
    require(url.startsWith("https://") || url.startsWith("http://")) { "Invalid Bridge URL" }
    return url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
        "/bridge/desktop/voice"
}
