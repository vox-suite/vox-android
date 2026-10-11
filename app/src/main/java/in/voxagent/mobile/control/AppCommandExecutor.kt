package `in`.voxagent.mobile.control

import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import `in`.voxagent.mobile.pulse.PulseApi
import `in`.voxagent.mobile.pulse.PulseDefinition
import `in`.voxagent.mobile.spaces.SpacesApi
import `in`.voxagent.mobile.timeline.TimelineApi
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class AppCommandExecutor(private val state: AppControlState, private val token: () -> String?) {
    suspend fun execute(command: AppCommand): JsonObject {
        val before = state.context.value
        val output =
            try {
                UUID.fromString(command.commandId)
                require(Instant.parse(command.expiresAt).isAfter(Instant.now())) { "expired" }
                require(
                    command.action == "navigate" ||
                        command.expectedRevision == null ||
                        command.expectedRevision == before.revision
                ) {
                    "stale_context"
                }
                val bearer = token() ?: error("Signed out")
                val args = command.arguments
                fun required(key: String) = args.text(key) ?: error("Specify $key")
                fun current() {
                    check(token() == bearer) { "Signed out" }
                    check(state.context.value.revision == before.revision) {
                        "Context changed; read context again"
                    }
                }
                when (command.action) {
                    "navigate" -> {
                        val page = required("page")
                        require(
                            page in
                                setOf(
                                    "agent",
                                    "timeline",
                                    "pulse",
                                    "spaces",
                                    "connections",
                                    "updates",
                                )
                        ) {
                            "Unsupported page"
                        }
                        val space =
                            args.text("spaceId")?.also {
                                UUID.fromString(it)
                                SpacesApi.graph(it, bearer)
                            }
                        val pulse =
                            args.text("pulseId")?.also {
                                UUID.fromString(it)
                                VoxHttp.getJson("/v1/me/pulse/charts/$it", bearer)
                            }
                        current()
                        state.patch {
                            it.copy(
                                page = page,
                                spaceId = space,
                                pulseId = pulse,
                                selectedItemId = null,
                                resourceDetails = null,
                            )
                        }
                        delay(32)
                        buildJsonObject {
                            put("page", page)
                            space?.let { put("spaceId", it) }
                            pulse?.let { put("pulseId", it) }
                        }
                    }
                    "timeline_filter" -> {
                        val filter = resolveTimelineFilter(args)
                        val result = TimelineApi.filtered(bearer, filter, null)
                        current()
                        state.patch {
                            it.copy(page = "timeline", timeline = filter, selectedItemId = null)
                        }
                        VoxJson.encodeToJsonElement(result)
                    }
                    "select_item" -> {
                        val id = required("itemId")
                        val space = before.spaceId ?: error("Open a space first")
                        val graph = SpacesApi.graph(space, bearer)
                        require(graph.nodes.any { it.id == id }) {
                            "Node does not exist in this space"
                        }
                        current()
                        state.patch { it.copy(selectedItemId = id) }
                        buildJsonObject { put("selectedItemId", id) }
                    }
                    "list_spaces" -> VoxJson.encodeToJsonElement(SpacesApi.list(bearer))
                    "list_pulse_measurements" ->
                        VoxJson.encodeToJsonElement(PulseApi.measurements(bearer))
                    "create_space",
                    "space_chat",
                    "add_node",
                    "update_node",
                    "remove_node",
                    "connect_nodes",
                    "arrange_canvas",
                    "stop_work" -> {
                        val create = command.action == "create_space"
                        val space =
                            if (create) null else before.spaceId ?: error("Open a space first")
                        require(
                            create || args.text("spaceId") == null || args.text("spaceId") == space
                        ) {
                            "Open the intended space first"
                        }
                        val body = buildJsonObject {
                            put("command_id", command.commandId)
                            put("action", command.action)
                            put("space_id", space?.let(::JsonPrimitive) ?: JsonNull)
                            put(
                                "arguments",
                                buildJsonObject {
                                    args.forEach { (key, value) -> put(key, value) }
                                    if (
                                        command.action in
                                            setOf("space_chat", "update_node", "remove_node") &&
                                            args.text("nodeId") == null
                                    )
                                        before.selectedItemId?.let { put("nodeId", it) }
                                },
                            )
                        }
                        val result =
                            VoxJson.parseToJsonElement(
                                    VoxHttp.postJson(
                                        "/v1/me/desktop-actions",
                                        body.toString(),
                                        bearer,
                                    )
                                )
                                .jsonObject
                        check(token() == bearer) { "Signed out" }
                        if (state.context.value.revision == before.revision)
                            state.patch {
                                it.copy(
                                    page = "spaces",
                                    spaceId = result.text("space_id"),
                                    selectedItemId = result.text("node_id"),
                                )
                            }
                        state.changed()
                        result
                    }
                    "create_pulse" -> {
                        val definition =
                            VoxJson.decodeFromJsonElement<PulseDefinition>(
                                args["definition"] ?: error("Choose a real Pulse measurement")
                            )
                        val preview = PulseApi.preview(bearer, definition)
                        require(preview.canSave) { "Chart data is unavailable" }
                        current()
                        val result =
                            PulseApi.save(bearer, required("title"), definition, command.commandId)
                        check(token() == bearer) { "Signed out" }
                        if (state.context.value.revision == before.revision)
                            state.patch {
                                it.copy(page = "pulse", pulseId = result.id, selectedItemId = null)
                            }
                        state.changed()
                        VoxJson.encodeToJsonElement(result)
                    }
                    "update_pulse" -> {
                        val id =
                            args.text("pulseId") ?: before.pulseId ?: error("Choose a Pulse chart")
                        UUID.fromString(id)
                        require(
                            args.text("pulseId") == null ||
                                before.pulseId == null ||
                                id == before.pulseId
                        ) {
                            "Choose the intended chart first"
                        }
                        args["definition"]?.let {
                            require(
                                PulseApi.preview(
                                        bearer,
                                        VoxJson.decodeFromJsonElement<PulseDefinition>(it),
                                    )
                                    .canSave
                            ) {
                                "Chart data is unavailable"
                            }
                        }
                        current()
                        val result =
                            VoxJson.parseToJsonElement(
                                VoxHttp.requestJson(
                                    "PATCH",
                                    "/v1/me/pulse/charts/$id",
                                    buildJsonObject {
                                            args["title"]?.let { put("title", it) }
                                            args["definition"]?.let { put("definition", it) }
                                        }
                                        .toString(),
                                    bearer,
                                )
                            )
                        check(token() == bearer) { "Signed out" }
                        if (state.context.value.revision == before.revision)
                            state.patch { it.copy(page = "pulse", pulseId = id) }
                        state.changed()
                        result
                    }
                    else -> error("This action is unavailable on Android")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return buildJsonObject {
                    put("commandId", command.commandId)
                    put("ok", false)
                    put("error", e.message ?: "Action failed")
                    put("context", AppControlJson.encodeToJsonElement(state.context.value))
                }
            }
        return buildJsonObject {
            put("commandId", command.commandId)
            put("ok", true)
            put("output", output)
            put("context", AppControlJson.encodeToJsonElement(state.context.value))
        }
    }
}
