package `in`.voxagent.mobile.web

import android.webkit.JavascriptInterface
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import `in`.voxagent.mobile.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

private const val REFRESH_BEFORE_EXPIRY_SECONDS = 300L
private const val MAX_ERROR_LENGTH = 200

class VoxHostBridge(private val context: Context, private val tokenProvider: () -> String?) {
    @JavascriptInterface
    fun openExternal(value: String) {
        val uri = Uri.parse(value)
        require(uri.scheme == "https" && uri.host == "accounts.google.com" && uri.path == "/o/oauth2/v2/auth" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) { "Unsupported authorization destination" }
        Handler(Looper.getMainLooper()).post {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private var cached: WebSession? = null

    @JavascriptInterface
    @Synchronized
    fun getSession(): String = try {
        currentSession()
    } catch (e: Exception) {
        buildJsonObject {
            put("error", (e.message ?: "Session unavailable").take(MAX_ERROR_LENGTH))
        }.toString()
    }

    private fun currentSession(): String {
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
