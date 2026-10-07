package `in`.voxagent.mobile.spaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Space(
    val id: String,
    val title: String = "",
    val intent: String = "",
    val state: String = "ideating",
    val agent_spec: JsonObject = JsonObject(emptyMap()),
    val run_state: String = "idle",
    val run_error: String? = null,
    val committed_collection_id: String? = null,
    val created_at: String = "",
) {
    val editable: Boolean
        get() = state != "committed" && state != "dropped"

    val mission: String?
        get() = agent_spec["mission"]?.jsonPrimitive?.content
}

@Serializable
data class SpaceNode(
    val id: String,
    val kind: String = "research",
    val title: String = "",
    val body: String = "",
    val state: String = "done",
)

@Serializable data class SpaceEdge(val id: String, val from_node: String, val to_node: String)

@Serializable
data class SpaceGraph(
    val space: Space,
    val nodes: List<SpaceNode> = emptyList(),
    val edges: List<SpaceEdge> = emptyList(),
) {
    val canCommit: Boolean
        get() =
            space.editable &&
                space.run_state != "running" &&
                nodes.any { it.state == "done" && it.kind in setOf("plan", "step") }
}

@Serializable
data class SpaceMessage(
    val id: String,
    val role: String,
    val text: String,
    val created_at: String = "",
)

@Serializable
data class CommitSpaceResult(val collection_id: String, val committed_spans_count: Int)

@Serializable
data class NodePatch(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
)
