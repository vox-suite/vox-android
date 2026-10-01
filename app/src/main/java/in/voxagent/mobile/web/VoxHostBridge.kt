package `in`.voxagent.mobile.web

import android.webkit.JavascriptInterface
import `in`.voxagent.mobile.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

private const val REFRESH_BEFORE_EXPIRY_SECONDS = 300L

class VoxHostBridge(private val tokenProvider: () -> String?) {
    private var cached: WebSession? = null

    @JavascriptInterface
    @Synchronized
    fun getSession(): String {
        val deadline = Instant.now().plusSeconds(REFRESH_BEFORE_EXPIRY_SECONDS)
        val session = cached?.takeIf { it.expiresAt.isAfter(deadline) } ?: run {
            val token = tokenProvider() ?: throw IllegalStateException("Not signed in")
            runBlocking { WebSessionApi.mint(token) }.also { cached = it }
        }
        return buildJsonObject {
            put("apiUrl", BuildConfig.VOX_API_BASE_URL)
            put("token", session.token)
            put("expiresAt", session.expiresAt.toString())
        }.toString()
    }
}
