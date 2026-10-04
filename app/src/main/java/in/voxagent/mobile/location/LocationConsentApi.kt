package `in`.voxagent.mobile.location

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocationConsentStatus(
    val granted: Boolean,
    val retention_days: Int,
    val granted_at: String? = null,
    val synced_until: String? = null,
)

const val LOCATION_RETENTION_DAYS = 90

@Serializable
private data class GrantConsentRequest(val retention_days: Int)

private val json = Json { ignoreUnknownKeys = true }

object LocationConsentApi {
    suspend fun getStatus(bearerToken: String): LocationConsentStatus {
        val responseJson = VoxHttp.postJson("/v1/location/consent/get", bearerToken = bearerToken)
        return json.decodeFromString(LocationConsentStatus.serializer(), responseJson)
    }

    suspend fun grant(bearerToken: String, retentionDays: Int = LOCATION_RETENTION_DAYS): LocationConsentStatus {
        val requestJson = json.encodeToString(
            GrantConsentRequest.serializer(),
            GrantConsentRequest(retention_days = retentionDays),
        )
        val responseJson = VoxHttp.postJson("/v1/location/consent/grant", requestJson, bearerToken)
        return json.decodeFromString(LocationConsentStatus.serializer(), responseJson)
    }

    suspend fun revoke(bearerToken: String) {
        VoxHttp.postJson("/v1/location/consent/revoke", bearerToken = bearerToken)
    }
}
