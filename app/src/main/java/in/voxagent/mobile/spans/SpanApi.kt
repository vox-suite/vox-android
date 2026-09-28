package `in`.voxagent.mobile.spans

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLEncoder

private val json = Json { ignoreUnknownKeys = true }

object SpanApi {
    private val listSerializer = kotlinx.serialization.builtins.ListSerializer(Span.serializer())

    /** Mirrors GET /v1/spans?from=&to= */
    suspend fun listSpans(
        from: String? = null,
        to: String? = null,
        bearerToken: String,
    ): List<Span> = withContext(Dispatchers.IO) {
        val params = buildList {
            if (from != null) add("from=${URLEncoder.encode(from, "UTF-8")}")
            if (to != null) add("to=${URLEncoder.encode(to, "UTF-8")}")
        }
        val path = "/v1/spans" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        val responseJson = VoxHttp.getJson(path, bearerToken)
        json.decodeFromString(listSerializer, responseJson)
    }

    /** Mirrors POST /v1/spans */
    suspend fun createSpan(payload: NewSpanPayload, bearerToken: String): Span =
        withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(NewSpanPayload.serializer(), payload)
            val responseJson = VoxHttp.postJson("/v1/spans", requestJson, bearerToken)
            json.decodeFromString(Span.serializer(), responseJson)
        }

    /** Mirrors PATCH /v1/spans/{id} */
    suspend fun updateSpan(id: String, patch: SpanPatch, bearerToken: String): Span =
        withContext(Dispatchers.IO) {
            val requestJson = json.encodeToString(SpanPatch.serializer(), patch)
            val responseJson = VoxHttp.patchJson("/v1/spans/$id", requestJson, bearerToken)
            json.decodeFromString(Span.serializer(), responseJson)
        }

    /** Mirrors DELETE /v1/spans/{id} */
    suspend fun deleteSpan(id: String, bearerToken: String): Unit =
        withContext(Dispatchers.IO) {
            VoxHttp.deleteJson("/v1/spans/$id", bearerToken)
        }
}
