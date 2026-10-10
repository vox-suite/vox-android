package `in`.voxagent.mobile.pulse

import `in`.voxagent.mobile.net.ApiException
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxHttpException
import `in`.voxagent.mobile.net.VoxJson
import java.net.URLEncoder
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal val PulseJson = Json(VoxJson) { encodeDefaults = true }

object PulseApi {
    private val timezone
        get() = ZoneId.systemDefault().id

    private fun query(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&", prefix = "?") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    suspend fun canvas(
        token: String,
        refresh: Boolean = false,
        cursor: String? = null,
    ): PulseCanvas =
        VoxJson.decodeFromString(
            request(
                "GET",
                "/v1/me/pulse/canvas" +
                    query(
                        *buildList {
                                add("timezone" to timezone)
                                add("refresh" to refresh.toString())
                                cursor?.let { add("cursor" to it) }
                            }
                            .toTypedArray()
                    ),
                token,
                timeout = 60_000,
            )
        )

    suspend fun discover(token: String, more: Boolean = false): DiscoveryResponse =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/pulse/suggestions",
                token,
                buildJsonObject {
                        put("timezone", timezone)
                        put("refresh", more)
                    }
                    .toString(),
                200_000,
            )
        )

    suspend fun preview(token: String, definition: PulseDefinition): PulseResult =
        VoxJson.decodeFromString(
            request("POST", "/v1/me/pulse/preview", token, PulseJson.encodeToString(definition))
        )

    suspend fun save(
        token: String,
        title: String,
        definition: PulseDefinition,
        key: String,
    ): SavedPulseChart =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/pulse/charts",
                token,
                buildJsonObject {
                        put("title", title)
                        put("definition", PulseJson.encodeToJsonElement(definition))
                        put("idempotency_key", key)
                    }
                    .toString(),
            )
        )

    suspend fun deleteChart(token: String, id: String) {
        request("DELETE", "/v1/me/pulse/charts/$id", token)
    }

    suspend fun dismiss(token: String, definition: PulseDefinition) {
        request("POST", "/v1/me/pulse/dismissals", token, PulseJson.encodeToString(definition))
    }

    suspend fun measurements(token: String): List<Measurement> =
        VoxJson.decodeFromString(request("GET", "/v1/me/pulse/measurements" + query("timezone" to timezone), token))

    private suspend fun request(
        method: String,
        path: String,
        token: String,
        body: String = "{}",
        timeout: Long = 20_000,
    ): String =
        try {
            VoxHttp.requestJson(method, path, body, token, timeout)
        } catch (e: VoxHttpException) {
            val msg =
                runCatching {
                        val obj = VoxJson.parseToJsonElement(e.responseBody).jsonObject
                        (obj["error"] ?: obj["message"])?.jsonPrimitive?.content
                    }
                    .getOrNull() ?: "Pulse request failed (${e.statusCode}). Please try again."
            throw ApiException(e.statusCode, msg)
        }
}
