package `in`.voxagent.mobile.web

import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class WebTokenResponse(val token: String, val expires_at: String)

data class WebSession(val token: String, val expiresAt: Instant)

object WebSessionApi {
    suspend fun mint(bearerToken: String): WebSession = withContext(Dispatchers.IO) {
        val body = VoxHttp.postJson("/v1/auth/web-token", bearerToken = bearerToken)
        val response = json.decodeFromString(WebTokenResponse.serializer(), body)
        WebSession(response.token, Instant.parse(response.expires_at))
    }
}
