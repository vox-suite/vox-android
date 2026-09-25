package `in`.voxagent.mobile.sms

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SubmitBatchRequest(val messages: List<SmsMessage>)

object SmsBatchApi {
    suspend fun submitBatch(messages: List<SmsMessage>, bearerToken: String) {
        val requestJson = Json.encodeToString(
            SubmitBatchRequest.serializer(),
            SubmitBatchRequest(messages = messages),
        )
        VoxHttp.postJson("/v1/sms/batches", requestJson, bearerToken)
    }
}
