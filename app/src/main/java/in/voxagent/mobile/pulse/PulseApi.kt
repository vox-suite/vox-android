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
                        put("refresh", false)
                        put("more", more)
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

    suspend fun compose(
        token: String,
        messages: List<ComposeMessage>,
        current: PulseDefinition?,
        title: String?,
    ): ComposeResponse =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/pulse/compose",
                token,
                buildJsonObject {
                        put("timezone", timezone)
                        put("messages", PulseJson.encodeToJsonElement(messages))
                        put(
                            "current",
                            current?.let { PulseJson.encodeToJsonElement(it) } ?: JsonNull,
                        )
                        put("current_title", title?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                    .toString(),
                150_000,
            )
        )

    suspend fun goals(token: String): List<GoalView> =
        VoxJson.decodeFromString(
            request(
                "GET",
                "/v1/me/pulse/goals" + query("timezone" to timezone),
                token,
                timeout = 60_000,
            )
        )

    suspend fun composeGoal(
        token: String,
        messages: List<ComposeMessage>,
        current: GoalDraft?,
    ): GoalComposeResponse =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/pulse/goals/compose",
                token,
                buildJsonObject {
                        put("timezone", timezone)
                        put("messages", PulseJson.encodeToJsonElement(messages))
                        put(
                            "current",
                            current?.let { PulseJson.encodeToJsonElement(it) } ?: JsonNull,
                        )
                    }
                    .toString(),
                150_000,
            )
        )

    suspend fun createGoal(token: String, draft: GoalDraft): GoalView =
        VoxJson.decodeFromString(
            request("POST", "/v1/me/pulse/goals", token, PulseJson.encodeToString(draft), 60_000)
        )

    suspend fun removeGoal(token: String, id: String) {
        request("DELETE", "/v1/me/pulse/goals/$id", token)
    }

    suspend fun entry(token: String, id: String, amount: Double): GoalView =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/pulse/goals/$id/entries",
                token,
                buildJsonObject {
                        put("timezone", timezone)
                        put("amount", amount)
                        put("note", JsonNull)
                    }
                    .toString(),
            )
        )

    suspend fun board(token: String, id: String): BoardDetails =
        VoxJson.decodeFromString(request("GET", "/v1/me/charts/boards/$id", token))

    suspend fun boardData(token: String, id: String): List<BoardResult> =
        VoxJson.decodeFromString(
            request("GET", "/v1/me/charts/boards/$id/data", token, timeout = 60_000)
        )

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
