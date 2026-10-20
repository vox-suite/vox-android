package `in`.voxagent.mobile.connections

import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal data class Backfill(val state: String?, val processed: Int, val imported: Int, val skipped: Int)

internal object GmailHistory {
    suspend fun backfill(token: String, start: Boolean): Backfill = withContext(Dispatchers.IO) {
        val response = VoxJson.parseToJsonElement(VoxHttp.requestJson(if (start) "POST" else "GET", "/v1/connectors/gmail/backfill", bearerToken = token)).jsonObject
        fun count(key: String) = response[key]?.jsonPrimitive?.intOrNull ?: 0
        Backfill(response["state"]?.jsonPrimitive?.contentOrNull, count("processed"), count("imported"), count("skipped"))
    }
}
