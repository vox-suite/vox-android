package `in`.voxagent.mobile.projects

import kotlinx.serialization.Serializable

@Serializable
data class Citation(
    val source_id: String,
    val path: String,
    val start_line: Int,
    val end_line: Int,
    val hash: String = "",
)

@Serializable
data class ContextVersion(
    val title: String,
    val body: String,
    val citations: List<Citation> = emptyList(),
    val updated_at: String = "",
    val origin: String = "user",
)

@Serializable
data class ContextNode(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val origin: String,
    val stale: Boolean = false,
    val citations: List<Citation> = emptyList(),
    val history: List<ContextVersion> = emptyList(),
    val updated_at: String = "",
)

@Serializable
data class ProjectSource(
    val id: String,
    val label: String,
    val kind: String,
    val files: Int,
    val symbols: Int,
    val fingerprint: String,
    val indexed_at: String,
)

@Serializable
data class ProjectMessage(
    val id: String,
    val role: String,
    val body: String,
    val at: String,
    val proposals: List<ContextNode> = emptyList(),
)

@Serializable data class ProjectActivity(val id: String, val text: String, val at: String)

@Serializable
data class ProjectData(
    val template: String = "general",
    val notes: String = "",
    val nodes: List<ContextNode> = emptyList(),
    val sources: List<ProjectSource> = emptyList(),
    val messages: List<ProjectMessage> = emptyList(),
    val activity: List<ProjectActivity> = emptyList(),
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val data: ProjectData,
    val revision: Long,
    val created_at: String,
    val updated_at: String,
)

@Serializable data class ProjectPage(val items: List<Project>, val next_cursor: String? = null)

@Serializable data class ProjectCount(val count: Int, val rev: String)

@Serializable data class ProjectCache(val rev: String, val page: ProjectPage)

fun Project.withNode(node: ContextNode): Project {
    val previous = data.nodes.find { it.id == node.id }
    val at = java.time.Instant.now().toString()
    val next =
        node.copy(
            updated_at = at,
            history =
                previous?.let {
                    (it.history +
                            ContextVersion(
                                it.title,
                                it.body,
                                it.citations,
                                it.updated_at,
                                it.origin,
                            ))
                        .takeLast(20)
                } ?: node.history,
        )
    return copy(
        data =
            data.copy(
                nodes =
                    if (previous == null) data.nodes + next
                    else data.nodes.map { if (it.id == node.id) next else it },
                activity =
                    (data.activity +
                            ProjectActivity(
                                java.util.UUID.randomUUID().toString(),
                                "Updated context: ${next.title}",
                                at,
                            ))
                        .takeLast(200),
            )
    )
}
