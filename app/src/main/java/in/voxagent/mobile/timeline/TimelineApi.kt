package `in`.voxagent.mobile.timeline

import `in`.voxagent.mobile.contracts.*
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import java.time.ZoneId
import kotlinx.serialization.encodeToString

object TimelineApi {
    suspend fun groups(token: String): List<TimelineGroup> = VoxJson.decodeFromString(VoxHttp.getJson("/v1/timeline/groups", token))
    suspend fun query(token: String, from: String, to: String, group: String?, cursor: String?): TimelinePage =
        VoxJson.decodeFromString(VoxHttp.postJson("/v1/timeline/events/query", VoxJson.encodeToString(TimelineQuery(start_at=from, end_at=to, group_value=group, cursor=cursor, limit=100)), token))
    suspend fun counts(token: String, from: String, to: String, group: String?): List<TimelineDayCount> =
        VoxJson.decodeFromString(VoxHttp.postJson("/v1/timeline/events/counts", VoxJson.encodeToString(TimelineCountsQuery(start_at=from,end_at=to,group_value=group,timezone=ZoneId.systemDefault().id)), token))
}
