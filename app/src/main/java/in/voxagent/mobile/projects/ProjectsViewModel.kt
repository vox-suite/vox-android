package `in`.voxagent.mobile.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.cache.DeviceCache
import `in`.voxagent.mobile.cache.DeviceCaches
import `in`.voxagent.mobile.net.LiveHub
import `in`.voxagent.mobile.spaces.Space
import `in`.voxagent.mobile.spaces.SpacesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProjectsUi(
    val items: List<Project> = emptyList(),
    val cursor: String? = null,
    val project: Project? = null,
    val selectedId: String? = null,
    val plans: List<Space> = emptyList(),
    val candidates: List<Space> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class ProjectsViewModel(private val token: () -> String?, private val cache: DeviceCache) :
    ViewModel() {
    private val state = MutableStateFlow(ProjectsUi())
    val ui = state.asStateFlow()
    private var selected: String? = null
    private var revision: String? = null
    private var hydrated = false
    private var mutation: Job? = null
    private var refresh: Job? = null
    private val reset: () -> Unit = {
        mutation?.cancel()
        refresh?.cancel()
        selected = null
        state.value = ProjectsUi(loading = false)
    }

    init {
        DeviceCaches.register(reset)
    }

    override fun onCleared() {
        DeviceCaches.unregister(reset)
        super.onCleared()
    }

    private fun bearer() = token() ?: error("Sign in to use Projects")

    suspend fun observe() {
        val live = LiveHub.get(token)
        live.start()
        reload()
        live.events
            .filter {
                it.type == "project_updated" ||
                    it.type == "live_reconnected" ||
                    it.type.startsWith("space_")
            }
            .conflate()
            .collect { reload() }
    }

    fun reload() {
        if (state.value.busy) return
        refresh?.cancel()
        refresh =
            viewModelScope.launch {
                state.update { it.copy(loading = true, error = null) }
                try {
                    if (!hydrated) {
                        cache.read<ProjectCache>()?.let { saved ->
                            revision = saved.rev
                            state.update {
                                it.copy(items = saved.page.items, cursor = saved.page.next_cursor)
                            }
                        }
                        hydrated = true
                    }
                    val count = ProjectsApi.count(bearer())
                    if (count.rev != revision) {
                        val page = ProjectsApi.list(bearer())
                        revision = count.rev
                        state.update { it.copy(items = page.items, cursor = page.next_cursor) }
                        cache.write(ProjectCache(count.rev, page))
                    }
                    selected?.let { id ->
                        val project = ProjectsApi.get(id, bearer())
                        val plans =
                            SpacesApi.list(bearer()).filter {
                                it.project_id == id && it.state != "dropped"
                            }
                        state.update { it.copy(project = project, plans = plans) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.update { it.copy(error = e.message ?: "Unable to load Projects") }
                } finally {
                    state.update { it.copy(loading = false) }
                }
            }
    }

    fun open(id: String?) {
        if (state.value.busy) return
        selected = id
        state.update { it.copy(project = null, selectedId = id, plans = emptyList()) }
        reload()
    }

    fun more() = mutate {
        val cursor = state.value.cursor ?: return@mutate
        val page = ProjectsApi.list(bearer(), cursor)
        state.update {
            it.copy(
                items = (it.items + page.items).distinctBy { p -> p.id },
                cursor = page.next_cursor,
            )
        }
    }

    private fun mutate(work: suspend () -> Unit) {
        if (state.value.busy) return
        refresh?.cancel()
        mutation =
            viewModelScope.launch {
                state.update { it.copy(busy = true, error = null, loading = false) }
                try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.update { it.copy(error = e.message ?: "Unable to update project") }
                } finally {
                    state.update { it.copy(busy = false) }
                }
            }
    }

    fun create(name: String, notes: String, template: String) = mutate {
        val at = java.time.Instant.now().toString()
        val nodes =
            if (notes.isBlank()) emptyList()
            else
                listOf(
                    ContextNode(
                        java.util.UUID.randomUUID().toString(),
                        "goal",
                        "Project vision",
                        notes,
                        "user",
                        updated_at = at,
                    )
                )
        val p =
            ProjectsApi.create(
                name,
                ProjectData(
                    template,
                    notes,
                    nodes,
                    activity =
                        listOf(
                            ProjectActivity(
                                java.util.UUID.randomUUID().toString(),
                                "Project created",
                                at,
                            )
                        ),
                ),
                bearer(),
            )
        selected = p.id
        revision = null
        state.update { it.copy(project = p, selectedId = p.id, items = listOf(p) + it.items) }
    }

    fun save(node: ContextNode) = mutate {
        val p = state.value.project ?: return@mutate
        val saved = ProjectsApi.save(p.withNode(node), bearer())
        state.update { it.copy(project = saved) }
        revision = null
    }

    fun accept(message: String, node: ContextNode) = mutate {
        val p = state.value.project ?: return@mutate
        val candidate =
            node.copy(
                stale =
                    node.stale ||
                        node.citations.any { c ->
                            p.data.sources.any { source ->
                                source.id == c.source_id && source.indexed_at > node.updated_at
                            }
                        }
            )
        val next = p.withNode(candidate)
        val saved =
            ProjectsApi.save(
                next.copy(
                    data =
                        next.data.copy(
                            messages =
                                next.data.messages.map {
                                    if (it.id == message)
                                        it.copy(
                                            proposals = it.proposals.filter { n -> n.id != node.id }
                                        )
                                    else it
                                }
                        )
                ),
                bearer(),
            )
        state.update { it.copy(project = saved) }
        revision = null
    }

    fun dismiss(message: String, node: String) = mutate {
        val p = state.value.project ?: return@mutate
        val saved =
            ProjectsApi.save(
                p.copy(
                    data =
                        p.data.copy(
                            messages =
                                p.data.messages.map {
                                    if (it.id == message)
                                        it.copy(
                                            proposals = it.proposals.filter { n -> n.id != node }
                                        )
                                    else it
                                }
                        )
                ),
                bearer(),
            )
        state.update { it.copy(project = saved) }
        revision = null
    }

    fun candidates() = mutate {
        val plans =
            SpacesApi.list(bearer()).filter {
                it.project_id == null && it.state != "dropped" && it.run_state != "running"
            }
        state.update { it.copy(candidates = plans) }
    }

    fun attach(plan: Space) = mutate {
        val p = state.value.project ?: return@mutate
        ProjectsApi.attach(p.id, plan.id, bearer())
        state.update {
            it.copy(
                plans = it.plans + plan.copy(project_id = p.id),
                candidates = it.candidates.filter { s -> s.id != plan.id },
            )
        }
    }

    fun findings(plan: Space) = mutate {
        val p = state.value.project ?: return@mutate
        val graph = SpacesApi.graph(plan.id, bearer())
        val final =
            graph.nodes.find { it.is_final && it.state == "done" }
                ?: error("This Plan has no completed final output yet")
        val next =
            ProjectsApi.chat(
                p,
                "Review this completed Plan and propose useful updates to project context. Keep them as proposals until I accept them. Plan: ${plan.title}\n${final.body}"
                    .take(12000),
                bearer(),
            )
        state.update { it.copy(project = next) }
        revision = null
    }

    fun settings(name: String, notes: String, template: String) = mutate {
        val p = state.value.project ?: return@mutate
        val saved =
            ProjectsApi.save(
                p.copy(name = name, data = p.data.copy(notes = notes, template = template)),
                bearer(),
            )
        state.update { it.copy(project = saved) }
        revision = null
    }

    fun remove(id: String) = mutate {
        val p = state.value.project ?: return@mutate
        val saved =
            ProjectsApi.save(
                p.copy(data = p.data.copy(nodes = p.data.nodes.filter { it.id != id })),
                bearer(),
            )
        state.update { it.copy(project = saved) }
        revision = null
    }

    fun chat(text: String) = mutate {
        val p = state.value.project ?: return@mutate
        val next = ProjectsApi.chat(p, text, bearer())
        state.update { it.copy(project = next) }
        revision = null
    }

    fun archive() = mutate {
        val p = state.value.project ?: return@mutate
        ProjectsApi.archive(p.id, bearer())
        selected = null
        revision = null
        state.update {
            it.copy(
                project = null,
                selectedId = null,
                items = it.items.filter { old -> old.id != p.id },
            )
        }
    }

    fun plan(intent: String, open: (Space) -> Unit) = mutate {
        val p = state.value.project ?: return@mutate
        val plan = SpacesApi.create(intent.take(160), intent, bearer(), p.id)
        open(plan)
    }
}
