package `in`.voxagent.mobile.spans

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true }

object SpanApi {
    private val listSerializer = kotlinx.serialization.builtins.ListSerializer(Span.serializer())

    /** Mirrors POST /v1/spans/list */
    suspend fun listSpans(
        from: String? = null,
        to: String? = null,
        bearerToken: String,
    ): List<Span> = withContext(Dispatchers.IO) {
        val requestJson = buildJsonObject {
            if (from != null) put("from", from)
            if (to != null) put("to", to)
        }.toString()
        val responseJson = VoxHttp.postJson("/v1/spans/list", requestJson, bearerToken)
        json.decodeFromString(listSerializer, responseJson)
    }

    /** Mirrors POST /v1/spans */
    suspend fun createSpan(payload: NewSpanPayload, bearerToken: String): Span =
        withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(NewSpanPayload.serializer(), payload)
            val responseJson = VoxHttp.postJson("/v1/spans", requestJson, bearerToken)
            json.decodeFromString(Span.serializer(), responseJson)
        }

    /** Mirrors POST /v1/spans/{id}/update */
    suspend fun updateSpan(id: String, patch: SpanPatch, bearerToken: String): Span =
        withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(SpanPatch.serializer(), patch)
            val responseJson = VoxHttp.postJson("/v1/spans/$id/update", requestJson, bearerToken)
            json.decodeFromString(Span.serializer(), responseJson)
        }

    /** Mirrors POST /v1/spans/{id}/delete */
    suspend fun deleteSpan(id: String, bearerToken: String): Unit =
        withContext(Dispatchers.IO) {
            VoxHttp.postJson("/v1/spans/$id/delete", bearerToken = bearerToken)
        }
}
