package `in`.voxagent.mobile.net

import `in`.voxagent.mobile.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

data class LiveEvent(val type: String, val payload: JsonObject)

object LiveHub {
    private var socket: LiveSocket? = null

    @Synchronized
    fun get(token: () -> String?): LiveSocket = socket ?: LiveSocket(token).also { socket = it }
}

class LiveSocket(private val token: () -> String?) {
    private val client =
        VoxHttp.client
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job != null) return
        job =
            scope.launch {
                var everOpened = false
                while (isActive) {
                    val bearer = token()
                    if (bearer != null) {
                        val opened = connect(bearer, announceReconnect = everOpened)
                        everOpened = everOpened || opened
                    }
                    delay(RETRY_MS)
                }
            }
    }

    private suspend fun connect(bearer: String, announceReconnect: Boolean): Boolean {
        val closed = CompletableDeferred<Unit>()
        var opened = false
        val base =
            BuildConfig.VOX_API_BASE_URL.trimEnd('/').let {
                when {
                    it.startsWith("https://") -> "wss://" + it.removePrefix("https://")
                    it.startsWith("http://") -> "ws://" + it.removePrefix("http://")
                    else -> "ws://$it"
                }
            }
        val request =
            Request.Builder()
                .url("$base/v1/me/events/socket?platform=android")
                .header("Authorization", "Bearer $bearer")
                .header("Sec-WebSocket-Protocol", "vox.v1")
                .build()
        val ws =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened = true
                        if (announceReconnect)
                            _events.tryEmit(LiveEvent("live_reconnected", JsonObject(emptyMap())))
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching {
                            val frame = VoxJson.parseToJsonElement(text).jsonObject
                            val type = frame["type"]?.jsonPrimitive?.content
                            if (type != null) _events.tryEmit(LiveEvent(type, frame))
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        closed.complete(Unit)
                    }
                },
            )
        try {
            closed.await()
        } finally {
            ws.cancel()
        }
        return opened
    }

    private companion object {
        const val RETRY_MS = 5_000L
    }
}
