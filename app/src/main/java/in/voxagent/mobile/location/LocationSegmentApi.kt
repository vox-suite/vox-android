package `in`.voxagent.mobile.location

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SubmitSegmentsRequest(val segments: List<LocationSegment>)

private val json = Json { ignoreUnknownKeys = true }

object LocationSegmentApi {
    suspend fun submitSegments(segments: List<LocationSegment>, bearerToken: String) {
        if (segments.isEmpty()) return
        val requestJson = json.encodeToString(
            SubmitSegmentsRequest.serializer(),
            SubmitSegmentsRequest(segments = segments),
        )
        VoxHttp.postJson("/v1/location/segments", requestJson, bearerToken)
    }
}
