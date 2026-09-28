package `in`.voxagent.mobile.net

import `in`.voxagent.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object VoxHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun getJson(path: String, bearerToken: String? = null): String =
        execute(Request.Builder().url(url(path)).get(), bearerToken)

    suspend fun postJson(path: String, body: String, bearerToken: String? = null): String =
        execute(
            Request.Builder().url(url(path)).post(body.toRequestBody(jsonMediaType)),
            bearerToken,
        )

    suspend fun deleteJson(path: String, bearerToken: String? = null): String =
        execute(Request.Builder().url(url(path)).delete(), bearerToken)

    suspend fun patchJson(path: String, body: String, bearerToken: String? = null): String =
        execute(
            Request.Builder().url(url(path)).patch(body.toRequestBody(jsonMediaType)),
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
        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw VoxHttpException(response.code, responseBody)
            }
            responseBody
        }
    }
}

class VoxHttpException(val statusCode: Int, val responseBody: String) :
    Exception("Vox API request failed with status $statusCode")
