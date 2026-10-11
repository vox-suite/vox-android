package `in`.voxagent.mobile.timeline

import `in`.voxagent.mobile.contracts.*
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.encodeToString

object TimelineApi {
    suspend fun groups(token: String): List<TimelineGroup> =
        VoxJson.decodeFromString(VoxHttp.getJson("/v1/timeline/groups", token))

    suspend fun query(
        token: String,
        from: String,
        to: String,
        group: String?,
        cursor: String?,
    ): TimelinePage =
        VoxJson.decodeFromString(
            VoxHttp.postJson(
                "/v1/timeline/events/query",
                VoxJson.encodeToString(
                    TimelineQuery(
                        start_at = from,
                        end_at = to,
                        group_value = group,
                        cursor = cursor,
                        limit = 30,
                    )
                ),
                token,
            )
        )

    suspend fun filtered(
        token: String,
        filter: `in`.voxagent.mobile.control.TimelineFilter,
        cursor: String?,
    ): TimelinePage =
        VoxJson.decodeFromString(
            VoxHttp.postJson(
                "/v1/timeline/events/query",
                VoxJson.encodeToString(
                    TimelineQuery(
                        start_at = filter.startAt,
                        end_at = filter.endAt,
                        event_type_value = "transaction",
                        spending_only = filter.spendingOnly,
                        merchant = filter.merchant,
                        category = filter.category,
                        cursor = cursor,
                        limit = 30,
                    )
                ),
                token,
            )
        )

    suspend fun days(
        token: String,
        from: String,
        to: String,
        group: String?,
        timezone: String,
    ): TimelineDaysResponse =
        VoxJson.decodeFromString(
            VoxHttp.postJson(
                "/v1/timeline/events/days",
                VoxJson.encodeToString(
                    TimelineCountsQuery(
                        start_at = from,
                        end_at = to,
                        group_value = group,
                        timezone = timezone,
                    )
                ),
                token,
            )
        )
}
