package `in`.voxagent.mobile.pulse

import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
data class PulseDefinition(
    val version: Int = 2,
    val measurement_id: String,
    val bucket: String? = null,
    val dimension: String? = null,
    val chart_type: String = "bar",
    val period_days: Int = 30,
    val timezone: String = ZoneId.systemDefault().id,
    val offset_days: Int = 0,
    val top_n: Int? = null,
) {
    fun withRange(days: Int) =
        copy(
            period_days = days,
            offset_days = 0,
            bucket =
                when {
                    bucket == null -> null
                    days > 365 -> "month"
                    days > 60 && bucket == "day" -> "week"
                    else -> bucket
                },
        )

    fun later() = copy(offset_days = (offset_days - period_days).coerceAtLeast(0))
}

@Serializable data class PulsePoint(val label: String, val value: Double? = null)

@Serializable
data class PulseResult(
    val points: List<PulsePoint> = emptyList(),
    val total: Double? = null,
    val unit: String = "",
    val description: String = "",
    val source: String = "",
    val quality: String = "",
    val error: String? = null,
    val record_count: Long = 0,
    val undated_count: Long = 0,
    val data_as_of: String? = null,
    val computed_at: String = "",
) {
    val canSave: Boolean
        get() = error == null && points.any { it.value?.isFinite() == true }
}

@Serializable
data class SourceProfile(
    val source: String = "",
    val timing: String = "",
    val currency: String = "",
)

@Serializable
data class Measurement(
    val id: String,
    val title: String = "",
    val kind: String = "",
    val unit: String = "",
    val description: String = "",
    val dimensions: List<String> = emptyList(),
    val buckets: List<String> = emptyList(),
    val profile: SourceProfile = SourceProfile(),
)

@Serializable
data class PulseSuggestion(
    val title: String,
    val reason: String = "",
    val definition: PulseDefinition,
    val measurement: Measurement,
    val preview: PulseResult,
)

@Serializable
data class PulseConnection(
    val connector_id: String,
    val authorization_state: String,
    val sync_timeline: Boolean,
    val assistant_read: Boolean,
)

@Serializable
data class DiscoveryResponse(
    val suggestions: List<PulseSuggestion> = emptyList(),
    val record_count: Long = 0,
    val source_count: Int = 0,
    val connections: List<PulseConnection> = emptyList(),
)

@Serializable
data class SavedPulseChart(
    val id: String,
    val title: String,
    val definition: PulseDefinition,
    val result: PulseResult? = null,
)

@Serializable
 data class PulseCanvas(val charts: List<SavedPulseChart> = emptyList(), val next_cursor: String? = null)
