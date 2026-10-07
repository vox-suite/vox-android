package `in`.voxagent.mobile.spans

import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
enum class SpanStatus {
    @SerialName("planned") Planned,
    @SerialName("active") Active,
    @SerialName("waiting_user") WaitingUser,
    @SerialName("done") Done,
    @SerialName("failed") Failed,
    @SerialName("cancelled") Cancelled;

    val wire: String
        get() =
            when (this) {
                Planned -> "planned"
                Active -> "active"
                WaitingUser -> "waiting_user"
                Done -> "done"
                Failed -> "failed"
                Cancelled -> "cancelled"
            }

    val label: String
        get() = wire.replace('_', ' ')
}

@Serializable
enum class ExecutionType {
    @SerialName("autonomous") Autonomous,
    @SerialName("interactive") Interactive,
    @SerialName("manual_human") ManualHuman;

    val wire: String
        get() =
            when (this) {
                Autonomous -> "autonomous"
                Interactive -> "interactive"
                ManualHuman -> "manual_human"
            }
}

@Serializable
data class Span(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val category: String = "",
    val source: String = "",
    val status: SpanStatus = SpanStatus.Planned,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("execution_type") val executionType: ExecutionType? = null,
    val data: JsonElement? = null,
    @SerialName("schema_color_token") val schemaColorToken: Int? = null,
    @SerialName("schema_icon_token") val schemaIconToken: Int? = null,
    @SerialName("collection_ids") val collectionIds: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    val version: Int = 0,
) {
    val startMs: Long?
        get() =
            startAt?.let {
                runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
            }

    val endMs: Long?
        get() =
            endAt?.let {
                runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
            }

    val createdMs: Long?
        get() =
            createdAt?.let {
                runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
            }

    val amount: Double?
        get() =
            ((data as? JsonObject)?.get("amount") as? JsonPrimitive)
                ?.takeIf { !it.isString }
                ?.doubleOrNull

    val currency: String
        get() = ((data as? JsonObject)?.get("currency") as? JsonPrimitive)?.contentOrNull ?: "INR"
}

@Serializable
data class SpanCollection(
    val id: String,
    val name: String,
    val kind: String = "custom",
    val status: String = "",
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("span_count") val spanCount: Int = 0,
    val version: Int = 0,
)

@Serializable data class CategoryCount(val category: String = "", val count: Int = 0)

@Serializable
data class DaySummary(
    val day: String,
    val count: Int = 0,
    val categories: List<CategoryCount> = emptyList(),
)

@Serializable
data class DayCounts(
    val revision: Long = 0,
    val unchanged: Boolean = false,
    val days: List<DaySummary> = emptyList(),
)

@Serializable
data class DayPage(
    val items: List<Span> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)
