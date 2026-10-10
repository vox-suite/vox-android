package `in`.voxagent.mobile.contracts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TimelineGroup(
    val id: String,
    val label: String,
    val sort_order: Int,
    val ui_hint: JsonElement,
    val value: String,
)

@Serializable
data class TimelineEventType(
    val analytics_definition: JsonElement,
    val content_schema: JsonElement,
    val created_at: String,
    val description: String,
    val group_id: String,
    val id: String,
    val label: String,
    val owner_user_id: String? = null,
    val state: String,
    val ui_hint: JsonElement,
    val value: String,
    val version: Int,
)

@Serializable
data class TimelineEvent(
    val confidence: Double,
    val content: JsonElement,
    val created_at: String,
    val dedupe_key: String? = null,
    val ended_at: String? = null,
    val event_type_id: String,
    val group_id: String,
    val id: String,
    val occurred_at: String,
    val record_state: String,
    val revision: Long,
    val source_timezone: String? = null,
    val summary: String? = null,
    val time_precision: String,
    val title: String,
    val updated_at: String,
    val user_id: String,
)

@Serializable
data class TimelineEvidence(
    val created_at: String,
    val id: String,
    val observation_metadata: JsonElement,
    val raw_reference: String? = null,
    val source_attachment_id: String? = null,
    val source_id: String? = null,
    val source_record_id: String? = null,
    val source_type: String,
    val timeline_event_id: String,
    val user_id: String,
)

@Serializable
data class TimelineEventWithEvidence(
    val event: TimelineEvent,
    val evidence: List<TimelineEvidence>,
)

@Serializable
data class TimelineQuery(
    val cursor: String? = null,
    val end_at: String? = null,
    val event_type_id: String? = null,
    val event_type_value: String? = null,
    val group_id: String? = null,
    val group_value: String? = null,
    val limit: Long? = null,
    val record_state: String? = null,
    val start_at: String? = null,
)

@Serializable
data class TimelinePage(
    val events: List<TimelineEventWithEvidence>,
    val next_cursor: String? = null,
)

@Serializable
data class TimelineCountsQuery(
    val end_at: String,
    val group_value: String? = null,
    val start_at: String,
    val timezone: String,
)

@Serializable
data class TimelineDayCount(
    val category: String,
    val count: Long,
    val day: String,
)

@Serializable
data class UpdateItem(
    val available_actions: List<String>,
    val category: String,
    val content: JsonElement,
    val content_version: Int,
    val created_at: String,
    val dedupe_key: String? = null,
    val expires_at: String? = null,
    val id: String,
    val kind: String,
    val priority: String,
    val published_at: String,
    val read_at: String? = null,
    val resolved_at: String? = null,
    val source_job_id: String? = null,
    val status: String,
    val summary: String? = null,
    val title: String,
    val ui_hint: JsonElement,
    val updated_at: String,
    val user_id: String,
)

@Serializable
data class UpdatesQuery(
    val before: String? = null,
    val before_id: String? = null,
    val category: String? = null,
    val kind: String? = null,
    val limit: Long? = null,
    val status: String? = null,
)

@Serializable
data class JobInputRequest(
    val data: JsonElement,
    val input_type: String,
)

@Serializable
data class JobRetryRequest(
    val idempotency_key: String? = null,
)

@Serializable
data class JobActionResponse(
    val job_id: String,
    val message: String,
    val status: String,
)
