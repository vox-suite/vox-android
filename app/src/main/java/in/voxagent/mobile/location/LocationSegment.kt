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
    // Only set for a `still` segment long enough to count as a visit — a single
    // point sampled once, never a continuous trail.
    val lat: Double? = null,
    val lng: Double? = null,
)
