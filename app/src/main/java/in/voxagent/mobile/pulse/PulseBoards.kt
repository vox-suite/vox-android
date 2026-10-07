package `in`.voxagent.mobile.pulse

import kotlinx.serialization.Serializable

@Serializable
data class BoardDetails(
    val id: String,
    val name: String,
    val charts: List<LegacyChart> = emptyList(),
)

@Serializable
data class BoardResult(
    val chart_id: String,
    val data_points: List<PulsePoint> = emptyList(),
    val error: String? = null,
)

@Serializable data class ComposeMessage(val role: String, val content: String)
