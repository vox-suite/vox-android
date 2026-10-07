package `in`.voxagent.mobile.pulse

import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
data class GoalDraft(
    val timezone: String = ZoneId.systemDefault().id,
    val title: String,
    val kind: String,
    val direction: String,
    val target: Double,
    val unit: String,
    val period: String? = null,
    val definition: PulseDefinition? = null,
    val deadline: String? = null,
)

@Serializable
data class GoalView(
    val id: String,
    val title: String,
    val kind: String,
    val direction: String,
    val target: Double,
    val unit: String,
    val current: Double = 0.0,
    val percent: Double = 0.0,
    val status: String = "unavailable",
    val remaining: Double = 0.0,
    val period: String? = null,
    val deadline: String? = null,
    val period_ends_on: String? = null,
    val days_left: Int? = null,
    val per_week_needed: Double? = null,
    val projected_on: String? = null,
    val error: String? = null,
)

@Serializable
data class GoalComposeResponse(
    val reply: String,
    val draft: GoalDraft? = null,
    val preview: GoalView? = null,
)

fun validEntry(text: String): Double? = text.toDoubleOrNull()?.takeIf { it.isFinite() && it != 0.0 }
