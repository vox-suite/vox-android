package `in`.voxagent.mobile.control

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class AppDeviceLink(
    context: Context,
    private val state: AppControlState,
    private val token: () -> String?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val executor = AppCommandExecutor(state, token)
    private val prefs =
        context.applicationContext.getSharedPreferences("vox_device", Context.MODE_PRIVATE)
    private val identifier =
        prefs.getString("identifier", null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString("identifier", it) } }
    private val client =
        VoxHttp.client
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    private var job: Job? = null
    private var socket: WebSocket? = null
    private var ready = CompletableDeferred<String>()
    private val commands = linkedMapOf<String, Pair<AppCommand, Deferred<JsonObject>>>()
    private var epoch = 0L

    fun start() {
        if (job?.isActive == true) return
        val generation = ++epoch
        ready = CompletableDeferred()
        job =
            scope.launch {
                while (isActive && generation == epoch) {
                    val bearer = token() ?: break
                    try {
                        val device =
                            VoxJson.parseToJsonElement(
                                    VoxHttp.postJson(
                                        "/v1/devices",
                                        buildJsonObject {
                                                put("device_identifier", identifier)
                                                put("platform", "android")
                                                put("label", "${Build.MANUFACTURER} ${Build.MODEL}")
                                                put("execution_consent", true)
                                                put(
                                                    "capabilities",
                                                    buildJsonObject {
                                                        put("vox_app_control", true)
                                                        put("app_control_version", 1)
                                                    },
                                                )
                                            }
                                            .toString(),
                                        bearer,
                                    )
                                )
                                .jsonObject
                                .text("id") ?: error("Invalid device registration")
                        UUID.fromString(device)
                        if (generation != epoch || token() != bearer) break
                        connect(device, bearer, generation)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {}
                    delay(5000)
                }
            }
    }

    suspend fun awaitDevice(): String {
        withContext(Dispatchers.Main.immediate) { start() }
        return withTimeout(10_000) { ready.await() }
    }

    private suspend fun connect(device: String, bearer: String, generation: Long) = coroutineScope {
        val closed = CompletableDeferred<Unit>()
        val base =
            BuildConfig.VOX_API_BASE_URL.trimEnd('/')
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
        val request =
            Request.Builder()
                .url("$base/v1/devices/$device/socket")
                .header("Authorization", "Bearer $bearer")
                .header("Sec-WebSocket-Protocol", "vox.v1")
                .build()
        val ws =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        scope.launch {
                            if (generation == epoch && token() == bearer) {
                                socket = webSocket
                                ready.complete(device)
                            } else webSocket.cancel()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        scope.launch {
                            if (generation != epoch || token() != bearer || text.length > 131072)
                                return@launch
                            val frame =
                                runCatching { VoxJson.parseToJsonElement(text).jsonObject }
                                    .getOrNull() ?: return@launch
                            val id = frame.text("id") ?: return@launch
                            val result =
                                when (frame.text("type")) {
                                    "app_context" ->
                                        buildJsonObject {
                                            put("ok", true)
                                            put(
                                                "context",
                                                AppControlJson.encodeToJsonElement(
                                                    AppContext.serializer(),
                                                    state.context.value,
                                                ),
                                            )
                                        }
                                    "app_command" -> {
                                        val command =
                                            runCatching {
                                                    VoxJson.decodeFromJsonElement<AppCommand>(
                                                        frame["command"] ?: JsonNull
                                                    )
                                                }
                                                .getOrNull()
                                        if (command == null)
                                            buildJsonObject {
                                                put("ok", false)
                                                put("error", "invalid_command")
                                            }
                                        else {
                                            val prior = commands[command.commandId]
                                            if (prior != null && prior.first != command)
                                                buildJsonObject {
                                                    put("ok", false)
                                                    put("error", "command_conflict")
                                                }
                                            else if (
                                                prior == null &&
                                                    commands.size >= 200 &&
                                                    commands.values.none { it.second.isCompleted }
                                            )
                                                buildJsonObject {
                                                    put("ok", false)
                                                    put("error", "busy")
                                                }
                                            else {
                                                if (prior == null && commands.size >= 200)
                                                    commands.entries
                                                        .firstOrNull { it.value.second.isCompleted }
                                                        ?.key
                                                        ?.let(commands::remove)
                                                val task =
                                                    prior?.second
                                                        ?: scope
                                                            .async { executor.execute(command) }
                                                            .also {
                                                                commands[command.commandId] =
                                                                    command to it
                                                            }
                                                try {
                                                    task.await()
                                                } catch (e: CancellationException) {
                                                    return@launch
                                                }
                                            }
                                        }
                                    }
                                    else ->
                                        buildJsonObject {
                                            put("ok", false)
                                            put("error", "unsupported")
                                        }
                                }
                            if (generation == epoch && token() == bearer)
                                webSocket.send(
                                    JsonObject(result + ("id" to JsonPrimitive(id))).toString()
                                )
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        closed.complete(Unit)
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
        val health = launch {
            var ticks = 0
            while (isActive) {
                delay(1000)
                if (token() != bearer) {
                    ws.cancel()
                    closed.complete(Unit)
                    break
                }
                if (++ticks % 30 == 0)
                    runCatching { VoxHttp.postJson("/v1/devices/$device/heartbeat", "{}", bearer) }
            }
        }
        try {
            closed.await()
        } finally {
            health.cancel()
            ws.cancel()
            if (socket === ws) socket = null
            if (generation == epoch) ready = CompletableDeferred()
        }
    }

    fun stop() {
        epoch++
        job?.cancel()
        job = null
        socket?.cancel()
        socket = null
        ready.cancel()
        commands.values.forEach { it.second.cancel() }
        commands.clear()
    }

    fun dispose() {
        stop()
        scope.cancel()
        state.reset()
    }
}
