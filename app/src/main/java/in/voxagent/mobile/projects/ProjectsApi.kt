package `in`.voxagent.mobile.projects

import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxHttpException
import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.json.*

object ProjectsApi {
    suspend fun count(token: String): ProjectCount =
        VoxJson.decodeFromString(request("GET", "/v1/me/projects/count", token))

    suspend fun list(token: String, before: String? = null): ProjectPage =
        VoxJson.decodeFromString(
            request("GET", "/v1/me/projects" + (before?.let { "?before=$it" } ?: ""), token)
        )

    suspend fun get(id: String, token: String): Project =
        VoxJson.decodeFromString(request("GET", "/v1/me/projects/$id", token))

    suspend fun create(name: String, data: ProjectData, token: String): Project =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/projects",
                token,
                buildJsonObject {
                        put("name", name)
                        put("data", VoxJson.encodeToJsonElement(data))
                    }
                    .toString(),
            )
        )

    suspend fun save(project: Project, token: String): Project =
        VoxJson.decodeFromString(
            request(
                "PUT",
                "/v1/me/projects/${project.id}",
                token,
                buildJsonObject {
                        put("name", project.name)
                        put("data", VoxJson.encodeToJsonElement(project.data))
                        put("revision", project.revision)
                    }
                    .toString(),
            )
        )

    suspend fun archive(id: String, token: String) {
        request("DELETE", "/v1/me/projects/$id", token)
    }

    suspend fun chat(project: Project, message: String, token: String): Project =
        VoxJson.decodeFromString(
            request(
                "POST",
                "/v1/me/projects/${project.id}/chat",
                token,
                buildJsonObject {
                        put("message", message)
                        put("revision", project.revision)
                        put("excerpts", JsonArray(emptyList()))
                    }
                    .toString(),
                100_000,
            )
        )

    suspend fun attach(project: String, plan: String, token: String) {
        request("POST", "/v1/me/projects/$project/plans/$plan/attach", token)
    }

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
            error(
                when (e.statusCode) {
                    409 -> "This project changed. Refresh before saving."
                    404 -> "This project is unavailable."
                    502 -> "Project ideation is unavailable. Please try again."
                    else -> "Projects request failed (${e.statusCode})."
                }
            )
        }
}
