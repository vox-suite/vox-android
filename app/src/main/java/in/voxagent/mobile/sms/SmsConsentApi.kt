package `in`.voxagent.mobile.sms

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SmsConsentStatus(
    val granted: Boolean,
    val retention_days: Int,
    val granted_at: String? = null,
    val synced_until: String? = null,
)

@Serializable
private data class GrantConsentRequest(val retention_days: Int)

const val SMS_RETENTION_DAYS = 256

private val json = Json { ignoreUnknownKeys = true }

object SmsConsentApi {
    suspend fun getStatus(bearerToken: String): SmsConsentStatus {
        val responseJson = VoxHttp.postJson("/v1/sms/consent/get", bearerToken = bearerToken)
        return json.decodeFromString(SmsConsentStatus.serializer(), responseJson)
    }

    suspend fun grant(bearerToken: String, retentionDays: Int = SMS_RETENTION_DAYS): SmsConsentStatus {
        val requestJson = json.encodeToString(
            GrantConsentRequest.serializer(),
            GrantConsentRequest(retention_days = retentionDays),
        )
        val responseJson = VoxHttp.postJson("/v1/sms/consent/grant", requestJson, bearerToken)
        return json.decodeFromString(SmsConsentStatus.serializer(), responseJson)
    }

    suspend fun revoke(bearerToken: String) {
        VoxHttp.postJson("/v1/sms/consent/revoke", bearerToken = bearerToken)
    }
}
