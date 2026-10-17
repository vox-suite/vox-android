package `in`.voxagent.mobile.projects

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Preserve fields introduced by other clients when an Android edit replaces project data. */
internal object ProjectDataSerializer : KSerializer<ProjectData> {
    override val descriptor: SerialDescriptor = ProjectDataWire.serializer().descriptor

    override fun deserialize(decoder: Decoder): ProjectData {
        val input = decoder as JsonDecoder
        val raw = input.decodeJsonElement().jsonObject
        val wire = input.json.decodeFromJsonElement(ProjectDataWire.serializer(), raw)
        return ProjectData(wire.template, wire.notes, wire.nodes, wire.sources, wire.messages, wire.activity, raw)
    }

    override fun serialize(encoder: Encoder, value: ProjectData) {
        val output = encoder as JsonEncoder
        // Explicit defaults overwrite the original value when a user clears a known field.
        val json = Json(output.json) { encodeDefaults = true }
        val wire = ProjectDataWire(value.template, value.notes, value.nodes, value.sources, value.messages, value.activity)
        val updated = json.encodeToJsonElement(ProjectDataWire.serializer(), wire)
        val originalKnown = json.encodeToJsonElement(
            ProjectDataWire.serializer(),
            json.decodeFromJsonElement(ProjectDataWire.serializer(), value.preservedFields),
        )
        output.encodeJsonElement(preserveUnknownFields(value.preservedFields, originalKnown, updated))
    }
}

@Serializable
private data class ProjectDataWire(
    val template: String = "general",
    val notes: String = "",
    val nodes: List<ContextNode> = emptyList(),
    val sources: List<ProjectSource> = emptyList(),
    val messages: List<ProjectMessage> = emptyList(),
    val activity: List<ProjectActivity> = emptyList(),
)

private fun preserveUnknownFields(
    original: JsonElement?,
    originalKnown: JsonElement?,
    updated: JsonElement,
): JsonElement = when (updated) {
    is JsonObject -> {
        val old = original as? JsonObject
        val known = originalKnown as? JsonObject
        JsonObject((old?.toMap() ?: emptyMap()) + updated.mapValues { (key, value) ->
            preserveUnknownFields(old?.get(key), known?.get(key), value)
        })
    }
    is JsonArray -> {
        val old = original as? JsonArray
        val known = originalKnown as? JsonArray
        val unused = known.orEmpty().indices.toMutableSet()
        JsonArray(updated.map { item ->
            val id = (item as? JsonObject)?.get("id")
            // Idless citations and history follow the same known value, never their old position.
            // Consume matches once so duplicate values cannot reuse another entry's metadata.
            val index = unused.firstOrNull { index ->
                val candidate = known!![index]
                if (id != null) (candidate as? JsonObject)?.get("id") == id
                else candidate == item
            }
            if (index != null) unused.remove(index)
            preserveUnknownFields(index?.let { old?.getOrNull(it) }, index?.let { known?.get(it) }, item)
        })
    }
    else -> updated
}
