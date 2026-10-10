package `in`.voxagent.mobile.connections

import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal data class HistoryPage(val candidates: List<JsonObject>, val excluded: Int, val next: String?)

internal object GmailHistory {
    suspend fun scan(token: String, startDate: String, endDate: String, pageToken: String?): HistoryPage = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("start_date", startDate)
            put("end_date", endDate)
            put("page_token", pageToken?.let(::JsonPrimitive) ?: JsonNull)
        }
        val response = VoxJson.parseToJsonElement(VoxHttp.requestJson("POST", "/v1/connectors/gmail/history/page", payload.toString(), token, 200000)).jsonObject
        HistoryPage(response["candidates"]!!.jsonArray.map { it.jsonObject }, response["excluded"]!!.jsonPrimitive.int, response["next_page_token"]?.jsonPrimitive?.contentOrNull)
    }

    suspend fun importSelected(token: String, candidate: JsonObject) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("message_ids", JsonArray(listOf(candidate["message_id"]!!)))
        }
        VoxJson.parseToJsonElement(VoxHttp.requestJson("POST", "/v1/connectors/gmail/history/import", payload.toString(), token, 120000)).jsonObject["imported_records"]!!.jsonPrimitive.int
    }
}
