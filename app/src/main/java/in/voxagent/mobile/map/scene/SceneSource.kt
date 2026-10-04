package `in`.voxagent.mobile.map.scene

import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.net.VoxHttp
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class SceneSource(private val token: () -> String?) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = VoxHttp.client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scenes = MutableSharedFlow<MapScene>(replay = 1, extraBufferCapacity = 8)
    val scenes: SharedFlow<MapScene> = _scenes.asSharedFlow()
    private var job: Job? = null
    private var socket: WebSocket? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                val bearer = token()
                if (bearer != null) {
                    runCatching {
                        val body = VoxHttp.getJson("/v1/me/map/scene", bearer)
                        _scenes.emit(json.decodeFromString<MapScene>(body))
                    }
                    connect(bearer)
                }
                delay(RETRY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close(1000, "stop")
        socket = null
    }

    private suspend fun connect(bearer: String) {
        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val base = BuildConfig.VOX_API_BASE_URL.trimEnd('/').let {
            when {
                it.startsWith("https://") -> "wss://" + it.removePrefix("https://")
                it.startsWith("http://") -> "ws://" + it.removePrefix("http://")
                else -> "ws://$it"
            }
        }
        val request = Request.Builder()
            .url("$base/v1/me/events/socket?platform=android")
            .header("Authorization", "Bearer $bearer")
            .header("Sec-WebSocket-Protocol", "vox.v1")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val frame: JsonObject = json.parseToJsonElement(text).jsonObject
                    if (frame["type"]?.jsonPrimitive?.content == "map_scene") {
                        val scene = json.decodeFromJsonElement(MapScene.serializer(), frame.getValue("scene"))
                        _scenes.tryEmit(scene)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete(Unit)
            }
        })
        closed.await()
    }

    private companion object {
        const val RETRY_MS = 5_000L
    }
}
