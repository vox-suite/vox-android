package `in`.voxagent.mobile.spans

import `in`.voxagent.mobile.net.VoxApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SpansApi {
    suspend fun getSpans(
        token: String,
        from: Instant,
        to: Instant,
        collectionId: String?,
    ): List<Span> =
        VoxApi.post(
            "/v1/spans/list",
            buildJsonObject {
                put("from", from.toString())
                put("to", to.toString())
                if (collectionId != null) put("collection_id", collectionId)
            },
            ListSerializer(Span.serializer()),
            token,
        )

    suspend fun getDays(token: String, from: LocalDate, to: LocalDate): List<DaySummary> =
        VoxApi.post(
            "/v1/spans/days",
            buildJsonObject {
                put("from_day", from.toString())
                put("to_day", to.toString())
                put("timezone", ZoneId.systemDefault().id)
            },
            DayCounts.serializer(),
            token,
        ).days

    suspend fun getDayPage(token: String, day: LocalDate, cursor: String?, limit: Int = 40): DayPage =
        VoxApi.post(
            "/v1/spans/day",
            buildJsonObject {
                put("day", day.toString())
                put("timezone", ZoneId.systemDefault().id)
                put("cursor", cursor)
                put("limit", limit)
            },
            DayPage.serializer(),
            token,
        )

    suspend fun createSpan(token: String, body: JsonObject): Span =
        VoxApi.post("/v1/spans", body, Span.serializer(), token)

    suspend fun updateSpan(token: String, id: String, body: JsonObject): Span =
        VoxApi.post("/v1/spans/$id/update", body, Span.serializer(), token)

    suspend fun deleteSpan(token: String, id: String) =
        VoxApi.postUnit("/v1/spans/$id/delete", token = token)

    suspend fun getCollections(token: String): List<SpanCollection> =
        VoxApi.post(
            "/v1/collections/list",
            JsonObject(emptyMap()),
            ListSerializer(SpanCollection.serializer()),
            token,
        )

    suspend fun setSpanCollection(
        token: String,
        collectionId: String,
        spanId: String,
        member: Boolean,
    ) =
        VoxApi.postUnit(
            "/v1/collections/$collectionId/spans/$spanId/${if (member) "add" else "remove"}",
            token = token,
        )
}
