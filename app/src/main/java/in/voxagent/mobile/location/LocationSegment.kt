package `in`.voxagent.mobile.location

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityKind {
    driving,
    walking,
    cycling,
    still,
    unknown,
}

@Serializable
data class LocationSegment(
    val activity: ActivityKind,
    val started_at: String,
    val ended_at: String,
)
