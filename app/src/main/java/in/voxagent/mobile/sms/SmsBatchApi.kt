package `in`.voxagent.mobile.sms

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class SubmitBatchRequest(val messages: List<SmsMessage>)

@Serializable data class SmsBatchResponse(val synced_until: String? = null)

private val json = Json { ignoreUnknownKeys = true }

object SmsBatchApi {

    suspend fun submitBatch(messages: List<SmsMessage>, bearerToken: String): SmsBatchResponse {
        val requestJson =
            json.encodeToString(
                SubmitBatchRequest.serializer(),
                SubmitBatchRequest(messages = messages),
            )
        val responseJson = VoxHttp.postJson("/v1/sms/batches", requestJson, bearerToken)
        return json.decodeFromString(SmsBatchResponse.serializer(), responseJson)
    }
}
