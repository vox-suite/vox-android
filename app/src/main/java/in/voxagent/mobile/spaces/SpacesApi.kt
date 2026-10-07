package `in`.voxagent.mobile.spaces

import `in`.voxagent.mobile.net.ApiException
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxHttpException
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object SpacesApi {
    suspend fun list(token: String): List<Space> =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces", token))

    suspend fun graph(id: String, token: String): SpaceGraph =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces/$id", token))

    suspend fun messages(id: String, token: String): List<SpaceMessage> =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces/$id/messages", token))

    suspend fun create(title: String, intent: String, token: String): Space =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/spaces",
                token,
                buildJsonObject {
                        put("title", title)
                        put("intent", intent)
                    }
                    .toString(),
                30_000,
            )
        )

    suspend fun drop(id: String, token: String) {
        request("DELETE", "/v1/me/spaces/$id", token)
    }

    suspend fun chat(id: String, text: String, token: String) {
        request(
            "POST",
            "/v1/me/spaces/$id/chat",
            token,
            buildJsonObject { put("message", text) }.toString(),
        )
    }

    suspend fun update(id: String, nodeId: String, patch: NodePatch, token: String): SpaceNode =
        VoxJson.decodeFromString(
            request(
                "PATCH",
                "/v1/me/spaces/$id/nodes/$nodeId",
                token,
                VoxJson.encodeToString(patch),
            )
        )

    suspend fun commit(id: String, token: String): CommitSpaceResult =
        VoxJson.decodeFromString(request("POST", "/v1/me/spaces/$id/commit", token))

    private suspend fun request(
        method: String,
        path: String,
        token: String,
        body: String = "{}",
        timeoutMs: Long = 20_000,
    ): String =
        try {
            VoxHttp.requestJson(method, path, body, token, timeoutMs)
        } catch (e: VoxHttpException) {
            val message =
                runCatching {
                        val obj = VoxJson.parseToJsonElement(e.responseBody).jsonObject
                        (obj["error"] ?: obj["message"])?.jsonPrimitive?.content
                    }
                    .getOrNull()
                    ?: when (e.statusCode) {
                        409 -> "This space changed or is already committed. Refresh and try again."
                        404 -> "This space is no longer available."
                        else -> "Spaces request failed (${e.statusCode}). Please try again."
                    }
            throw ApiException(e.statusCode, message)
        }
}
