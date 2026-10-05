package `in`.voxagent.mobile.map.scene

import `in`.voxagent.mobile.net.LiveSocket
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
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
import kotlinx.serialization.json.decodeFromJsonElement

class SceneSource(private val token: () -> String?, private val live: LiveSocket) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scenes = MutableSharedFlow<MapScene>(replay = 1, extraBufferCapacity = 8)
    val scenes: SharedFlow<MapScene> = _scenes.asSharedFlow()
    private var job: Job? = null

    fun start() {
        if (job != null) return
        live.start()
        job = scope.launch {
            launch {
                live.events.collect { event ->
                    when (event.type) {
                        "map_scene" -> runCatching {
                            _scenes.tryEmit(VoxJson.decodeFromJsonElement<MapScene>(event.payload.getValue("scene")))
                        }
                        "live_reconnected" -> refresh()
                    }
                }
            }
            while (isActive && !refresh()) delay(RETRY_MS)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun refresh(): Boolean {
        val bearer = token() ?: return false
        return runCatching {
            val body = VoxHttp.getJson("/v1/me/map/scene", bearer)
            _scenes.emit(VoxJson.decodeFromString<MapScene>(body))
        }.isSuccess
    }

    private companion object {
        const val RETRY_MS = 5_000L
    }
}
