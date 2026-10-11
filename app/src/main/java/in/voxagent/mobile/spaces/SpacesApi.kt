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
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces?summary=true", token))

    suspend fun count(token: String): SpacesCount =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces/count", token))

    suspend fun graph(id: String, token: String): SpaceGraph =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces/$id", token))

    suspend fun messages(id: String, token: String): List<SpaceMessage> =
        VoxJson.decodeFromString(request("GET", "/v1/me/spaces/$id/messages", token))

    suspend fun create(
        title: String,
        intent: String,
        token: String,
        projectId: String? = null,
    ): Space =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/spaces",
                token,
                buildJsonObject {
                        put("title", title)
                        put("intent", intent)
                        projectId?.let { put("project_id", it) }
                    }
                    .toString(),
                30_000,
            )
        )

    suspend fun drop(id: String, token: String) {
        request("DELETE", "/v1/me/spaces/$id", token)
    }

    suspend fun chat(id: String, text: String, token: String, nodeId: String? = null) {
        request(
            "POST",
            "/v1/me/spaces/$id/chat",
            token,
            buildJsonObject {
                    put("message", text)
                    nodeId?.let { put("node_id", it) }
                }
                .toString(),
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

    suspend fun stop(id: String, token: String) {
        request("POST", "/v1/me/spaces/$id/stop", token)
    }

    suspend fun retry(id: String, node: String, token: String) {
        request("POST", "/v1/me/spaces/$id/nodes/$node/retry", token)
    }

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
                        409 -> "This Plan changed or is already committed. Refresh and try again."
                        404 -> "This Plan is no longer available."
                        else -> "Plans request failed (${e.statusCode}). Please try again."
                    }
            throw ApiException(e.statusCode, message)
        }
}
