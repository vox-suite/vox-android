package `in`.voxagent.mobile.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.net.LiveHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

class SpacesViewModel(private val token: () -> String?) : ViewModel() {
    private val mutableUi = MutableStateFlow(SpacesUiState())
    val ui = mutableUi.asStateFlow()
    private var refreshJob: Job? = null
    private var revision = 0L
    private var loadGeneration = 0L
    private val streamed = HashMap<String, String>()

    private fun withStreams(graph: SpaceGraph): SpaceGraph =
        if (streamed.isEmpty()) graph
        else
            graph.copy(
                nodes =
                    graph.nodes.map { node ->
                        val text = streamed[node.id] ?: return@map node
                        if (node.state == "done" || node.state == "rejected") {
                            streamed.remove(node.id)
                            node
                        } else node.copy(body = text)
                    }
            )

    suspend fun observe() {
        val live = LiveHub.get(token)
        live.start()
        load()
        try {
            coroutineScope {
                launch {
                    live.events
                        .filter { event ->
                            event.type == "space_node_stream" &&
                                event.payload["space_id"]?.jsonPrimitive?.content ==
                                    mutableUi.value.selectedId
                        }
                        .collect { event ->
                            val nodeId = event.payload["node_id"]?.jsonPrimitive?.content
                            val text = event.payload["text"]?.jsonPrimitive?.content
                            if (nodeId != null && text != null) {
                                streamed[nodeId] = text
                                mutableUi.update { state ->
                                    state.copy(
                                        graph =
                                            state.graph?.let { g ->
                                                g.copy(
                                                    nodes =
                                                        g.nodes.map {
                                                            if (it.id == nodeId) it.copy(body = text)
                                                            else it
                                                        }
                                                )
                                            }
                                    )
                                }
                            }
                        }
                }
            live.events
                .filter { event ->
                    event.type != "space_node_stream" &&
                    (event.type == "live_reconnected" ||
                        (event.type.startsWith("space_") &&
                            (mutableUi.value.selectedId == null ||
                                event.payload["space_id"]?.jsonPrimitive?.content ==
                                    mutableUi.value.selectedId ||
                                event.payload["space_id"] == null)))
                }
                .conflate()
                .collect { load() }
            }
        } finally {
            refreshJob?.cancel()
        }
    }

    fun open(id: String?) {
        if (mutableUi.value.busy) return
        revision++
        refreshJob?.cancel()
        mutableUi.update {
            it.copy(
                selectedId = id,
                graph = null,
                messages = emptyList(),
                loading = true,
                error = null,
                commitResult = null,
            )
        }
        reload()
    }

    fun reload() {
        mutableUi.update {
            it.copy(loading = if (it.selectedId == null) it.spaces.isEmpty() else it.graph == null)
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = mutableUi.value.selectedId
        val expectedRevision = revision
        val generation = ++loadGeneration
        fun current() =
            mutableUi.value.selectedId == id &&
                revision == expectedRevision &&
                loadGeneration == generation
        try {
            val bearer = token() ?: error("Sign in to view your spaces.")
            if (id == null) {
                val spaces = SpacesApi.list(bearer)
                if (current())
                    mutableUi.update { it.copy(spaces = spaces, loading = false, error = null) }
            } else {
                val (graph, messages) =
                    coroutineScope {
                        val graph = async { SpacesApi.graph(id, bearer) }
                        val messages = async { SpacesApi.messages(id, bearer) }
                        graph.await() to messages.await()
                    }
                if (current())
                    mutableUi.update {
                        it.copy(graph = withStreams(graph), messages = messages, loading = false, error = null)
                    }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (current())
                mutableUi.update {
                    it.copy(loading = false, error = e.message ?: "Could not load spaces.")
                }
        }
    }

    private fun mutate(action: suspend (String) -> Unit) {
        if (mutableUi.value.busy) return
        revision++
        refreshJob?.cancel()
        mutableUi.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action(token() ?: error("Sign in to update your space."))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableUi.update { it.copy(error = e.message ?: "Could not update space.") }
            } finally {
                mutableUi.update { it.copy(busy = false) }
            }
        }
    }

    fun create(title: String, intent: String, onCreated: () -> Unit) {
        if (title.isBlank() || intent.isBlank()) return
        mutate { bearer ->
            val space = SpacesApi.create(title.trim(), intent.trim(), bearer)
            refreshJob?.cancel()
            mutableUi.update {
                it.copy(selectedId = space.id, graph = null, messages = emptyList(), loading = true)
            }
            revision++
            onCreated()
            load()
        }
    }

    fun drop(id: String, onDropped: () -> Unit) = mutate { bearer ->
        SpacesApi.drop(id, bearer)
        revision++
        onDropped()
        load()
    }

    fun send(text: String, onSent: () -> Unit = {}) {
        val graph = mutableUi.value.graph ?: return
        if (!graph.space.editable || text.isBlank()) return
        mutate { bearer ->
            SpacesApi.chat(graph.space.id, text.trim(), bearer)
            revision++
            onSent()
            load()
        }
    }

    fun update(node: SpaceNode, patch: NodePatch, onSaved: () -> Unit = {}) {
        val graph = mutableUi.value.graph ?: return
        if (!graph.space.editable) return
        mutate { bearer ->
            val updated = SpacesApi.update(graph.space.id, node.id, patch, bearer)
            revision++
            mutableUi.update { current ->
                current.copy(
                    graph =
                        current.graph?.let {
                            it.copy(
                                nodes = it.nodes.map { n -> if (n.id == updated.id) updated else n }
                            )
                        }
                )
            }
            onSaved()
        }
    }

    fun commit() {
        val graph = mutableUi.value.graph ?: return
        if (!graph.canCommit) return
        mutate { bearer ->
            val result = SpacesApi.commit(graph.space.id, bearer)
            revision++
            mutableUi.update {
                it.copy(
                    commitResult = result,
                    graph = it.graph?.let { g -> g.copy(space = g.space.copy(state = "committed")) },
                )
            }
            load()
        }
    }

    fun dismissResult() {
        mutableUi.update { it.copy(commitResult = null) }
    }
}
