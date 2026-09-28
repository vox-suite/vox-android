package `in`.voxagent.mobile.net

import android.os.SystemClock
import android.util.Log
import `in`.voxagent.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object VoxHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun postJson(path: String, body: String = "{}", bearerToken: String? = null): String =
        execute(
            Request.Builder().url(url(path)).post(body.toRequestBody(jsonMediaType)),
            bearerToken,
        )

    private fun url(path: String) = BuildConfig.VOX_API_BASE_URL.trimEnd('/') + path

    private suspend fun execute(
        requestBuilder: Request.Builder,
        bearerToken: String?,
    ): String = withContext(Dispatchers.IO) {
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }
        val request = requestBuilder.build()
        val target = "${request.method} ${request.url}"
        val tokenInfo = when {
            bearerToken == null -> "token=none"
            bearerToken.isBlank() -> "token=blank"
            else -> "token=len${bearerToken.length},parts${bearerToken.split('.').size}"
        }
        Log.d(TAG, "-> $target $tokenInfo")
        val startedAt = SystemClock.elapsedRealtime()
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (!response.isSuccessful) {
                    Log.w(
                        TAG,
                        "<- ${response.code} $target ${elapsed}ms $tokenInfo body=${responseBody.take(300)}",
                    )
                    throw VoxHttpException(response.code, responseBody)
                }
                Log.d(TAG, "<- ${response.code} $target ${elapsed}ms")
                responseBody
            }
        } catch (e: IOException) {
            Log.e(TAG, "!! $target failed before a response: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    private const val TAG = "VoxHttp"
}

class VoxHttpException(val statusCode: Int, val responseBody: String) :
    Exception("Vox API request failed with status $statusCode")
