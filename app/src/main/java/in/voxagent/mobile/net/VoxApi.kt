package `in`.voxagent.mobile.net

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val VoxJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

class ApiException(val status: Int, message: String) : Exception(message)

object VoxApi {
    suspend fun <T> post(
        path: String,
        body: JsonObject,
        result: DeserializationStrategy<T>,
        token: String,
    ): T = VoxJson.decodeFromString(result, send(path, body, token))

    suspend fun postUnit(path: String, body: JsonObject = JsonObject(emptyMap()), token: String) {
        send(path, body, token)
    }

    private suspend fun send(path: String, body: JsonObject, token: String): String =
        try {
            VoxHttp.postJson(path, body.toString(), token)
        } catch (e: VoxHttpException) {
            throw ApiException(e.statusCode, errorMessage(e))
        }

    private fun errorMessage(e: VoxHttpException): String =
        runCatching {
            val obj = VoxJson.parseToJsonElement(e.responseBody).jsonObject
            (obj["error"] ?: obj["message"])?.jsonPrimitive?.content
        }.getOrNull() ?: "Request failed (${e.statusCode})"
}
