package `in`.voxagent.mobile.net

import timber.log.Timber
import android.os.SystemClock
import `in`.voxagent.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    suspend fun postJson(path: String, body: String = "{}", bearerToken: String? = null): String =
        execute(
            Request.Builder().url(url(path)).post(body.toRequestBody(jsonMediaType)),
            bearerToken,
        )

    suspend fun getJson(path: String, bearerToken: String? = null): String =
        execute(Request.Builder().url(url(path)).get(), bearerToken)

    internal fun url(path: String) = BuildConfig.VOX_API_BASE_URL.trimEnd('/') + path

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
        Timber.tag(TAG).d("-> $target $tokenInfo")
        val startedAt = SystemClock.elapsedRealtime()
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("<- ${response.code} $target ${elapsed}ms $tokenInfo body=${responseBody.take(300)}")
                    if (response.code == 401 && bearerToken != null) {
                        _unauthorized.tryEmit(Unit)
                    }
                    throw VoxHttpException(response.code, responseBody)
                }
                Timber.tag(TAG).d("<- ${response.code} $target ${elapsed}ms")
                responseBody
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e("!! $target failed before a response: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    private const val TAG = "VoxHttp"
}

class VoxHttpException(val statusCode: Int, val responseBody: String) :
    Exception("Vox API request failed with status $statusCode")
