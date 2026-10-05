package `in`.voxagent.mobile.spans

import `in`.voxagent.mobile.net.VoxApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

object SpansApi {
    suspend fun getSpans(token: String, from: Instant, to: Instant, collectionId: String?): List<Span> =
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

    suspend fun createSpan(token: String, body: JsonObject): Span =
        VoxApi.post("/v1/spans", body, Span.serializer(), token)

    suspend fun updateSpan(token: String, id: String, body: JsonObject): Span =
        VoxApi.post("/v1/spans/$id/update", body, Span.serializer(), token)

    suspend fun deleteSpan(token: String, id: String) =
        VoxApi.postUnit("/v1/spans/$id/delete", token = token)

    suspend fun getCollections(token: String): List<SpanCollection> =
        VoxApi.post("/v1/collections/list", JsonObject(emptyMap()), ListSerializer(SpanCollection.serializer()), token)

    suspend fun setSpanCollection(token: String, collectionId: String, spanId: String, member: Boolean) =
        VoxApi.postUnit("/v1/collections/$collectionId/spans/$spanId/${if (member) "add" else "remove"}", token = token)
}
