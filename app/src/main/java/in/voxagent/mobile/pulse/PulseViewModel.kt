package `in`.voxagent.mobile.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.net.LiveHub
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PulseViewModel(private val token: () -> String?) : ViewModel() {
    private val state = MutableStateFlow(PulseUiState())
    val ui = state.asStateFlow()
    private var workJob: Job? = null
    private val chartJobs = mutableMapOf<String, Job>()
    private val suggestionKeys = mutableMapOf<PulseSuggestion, String>()
    private var boardId: String? = null
    private var generation = 0
    private var askDraft: PulseUiState? = null

    suspend fun observe() {
        val hub = LiveHub.get(token)
        hub.start()
        if (state.value.mode == PulseMode.Library) reload()
        try {
            hub.events
                .filter {
                    it.type == "live_reconnected" ||
                        it.type.startsWith("pulse_") ||
                        it.type in
                            setOf(
                                "span_created",
                                "span_updated",
                                "span_deleted",
                                "connection_synced",
                            )
                }
                .conflate()
                .collect {
                    if (state.value.mode == PulseMode.Library && !state.value.busy) reload()
                }
        } finally {
            if (!state.value.saving) workJob?.cancel()
            chartJobs.values.forEach { it.cancel() }
        }
    }

    private fun work(saving: Boolean = false, action: suspend (String) -> Unit) {
        if (state.value.saving) return
        workJob?.cancel()
        val mine = ++generation
        state.update { it.copy(busy = true, saving = saving, error = null) }
        workJob =
            viewModelScope.launch {
                try {
                    action(token() ?: error("Sign in to use Pulse."))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (mine == generation)
                        state.update {
                            it.copy(error = e.message ?: "Could not update Pulse.", loading = false)
                        }
                } finally {
                    if (mine == generation)
                        state.update { it.copy(busy = false, saving = false, loading = false) }
                }
            }
    }

    fun go(mode: PulseMode) {
        if (state.value.saving) return
        workJob?.cancel()
        generation++
        if (state.value.mode == PulseMode.Editor && mode != PulseMode.Editor) {
            val draft = askDraft
            state.update {
                it.copy(
                    title = draft?.title.orEmpty(),
                    definition = draft?.definition,
                    measurement = draft?.measurement,
                    preview = draft?.preview,
                    saveKey = draft?.saveKey ?: UUID.randomUUID().toString(),
                )
            }
        }
        state.update {
            it.copy(mode = mode, busy = false, saving = false, error = null, loading = false)
        }
        when (mode) {
            PulseMode.Library -> reload()
            PulseMode.Suggestions -> if (state.value.discovery == null) discover()
            else -> Unit
        }
    }

    fun back() =
        go(if (state.value.mode == PulseMode.Editor) PulseMode.Suggestions else PulseMode.Library)

    fun reload(refresh: Boolean = false, more: Boolean = false) {
        if (state.value.mode == PulseMode.Board) {
            boardId?.let { openBoard(it) }
            return
        }
        if (refresh) {
            chartJobs.values.forEach { it.cancel() }
            chartJobs.clear()
        }
        val cursor = if (more) state.value.canvas?.next_cursor ?: return else null
        work { bearer ->
            coroutineScope {
                val canvas = async { PulseApi.canvas(bearer, refresh, cursor) }
                val goals = async {
                    try {
                        Result.success(PulseApi.goals(bearer))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Result.failure(e)
                    }
                }
                val next = canvas.await()
                val nextGoals = goals.await()
                state.update { current ->
                    val charts =
                        if (more)
                            (current.canvas?.charts.orEmpty() + next.charts).distinctBy { it.id }
                        else next.charts
                    current.copy(
                        canvas = next.copy(charts = charts),
                        goals = nextGoals.getOrDefault(current.goals),
                        error = nextGoals.exceptionOrNull()?.message,
                        chartPreviews = if (refresh) emptyMap() else current.chartPreviews,
                    )
                }
            }
        }
    }

    fun discover(more: Boolean = false) = work { bearer ->
        val response = PulseApi.discover(bearer, more)
        state.update { it.copy(discovery = response) }
    }

    fun retry() {
        when (state.value.mode) {
            PulseMode.Suggestions -> discover()
            PulseMode.Editor,
            PulseMode.Ask -> state.value.definition?.let { change(it) }
            PulseMode.Goal -> state.update { it.copy(error = null) }
            else -> reload()
        }
    }

    fun edit(suggestion: PulseSuggestion) {
        if (state.value.saving) return
        workJob?.cancel()
        generation++
        askDraft = state.value
        state.update {
            it.copy(
                mode = PulseMode.Editor,
                editor = suggestion,
                title = suggestion.title,
                definition = suggestion.definition,
                measurement = suggestion.measurement,
                preview = suggestion.preview,
                busy = false,
                error = null,
                saveKey = UUID.randomUUID().toString(),
            )
        }
    }

    fun title(value: String) {
        state.update { it.copy(title = value.take(120), saveKey = UUID.randomUUID().toString()) }
    }

    fun change(definition: PulseDefinition) {
        state.update {
            it.copy(definition = definition, preview = null, saveKey = UUID.randomUUID().toString())
        }
        work { bearer ->
            val result = PulseApi.preview(bearer, definition)
            state.update { it.copy(preview = result) }
        }
    }

    fun range(chart: SavedPulseChart, definition: PulseDefinition) {
        chartJobs[chart.id]?.cancel()
        state.update {
            it.copy(
                chartPreviews =
                    it.chartPreviews + (chart.id to ChartPreview(definition, loading = true))
            )
        }
        chartJobs[chart.id] =
            viewModelScope.launch {
                try {
                    delay(300)
                    val result =
                        PulseApi.preview(token() ?: error("Sign in to use Pulse."), definition)
                    state.update {
                        it.copy(
                            chartPreviews =
                                it.chartPreviews + (chart.id to ChartPreview(definition, result))
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    state.update {
                        it.copy(
                            chartPreviews =
                                it.chartPreviews +
                                    (chart.id to ChartPreview(definition, error = e.message))
                        )
                    }
                }
            }
    }

    fun dismiss(suggestion: PulseSuggestion) = work { bearer ->
        PulseApi.dismiss(bearer, suggestion.definition)
        state.update {
            it.copy(
                discovery =
                    it.discovery?.let { d ->
                        d.copy(suggestions = d.suggestions.filter { s -> s != suggestion })
                    }
            )
        }
    }

    fun save(suggestion: PulseSuggestion? = null) {
        val current = state.value
        val definition = suggestion?.definition ?: current.definition ?: return
        val preview = suggestion?.preview ?: current.preview ?: return
        val title = suggestion?.title ?: current.title
        if (current.busy || !preview.canSave || title.isBlank()) return
        val key =
            suggestion?.let { suggestionKeys.getOrPut(it) { UUID.randomUUID().toString() } }
                ?: current.saveKey
        work(saving = true) { bearer ->
            val created = PulseApi.save(bearer, title.trim(), definition, key)
            state.update {
                it.copy(
                    mode = PulseMode.Library,
                    canvas =
                        (it.canvas ?: PulseCanvas()).copy(
                            charts =
                                (it.canvas?.charts.orEmpty() + created).distinctBy { chart ->
                                    chart.id
                                }
                        ),
                    discovery = null,
                    editor = null,
                )
            }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val current = state.value
        if (text.isBlank() || current.busy) return
        val goal = current.mode == PulseMode.Goal
        val messages =
            (if (goal) current.goalMessages else current.messages) +
                ComposeMessage("user", text.trim().take(500))
        work { bearer ->
            if (goal) {
                val response = PulseApi.composeGoal(bearer, messages, current.goalDraft)
                state.update {
                    it.copy(
                        goalMessages = messages + ComposeMessage("assistant", response.reply),
                        goalDraft = response.draft ?: it.goalDraft,
                        goalPreview =
                            if (response.draft != null) response.preview else it.goalPreview,
                    )
                }
            } else {
                val response =
                    PulseApi.compose(
                        bearer,
                        messages,
                        current.definition,
                        current.title.ifBlank { null },
                    )
                state.update {
                    it.copy(
                        messages = messages + ComposeMessage("assistant", response.reply),
                        title = response.title ?: it.title,
                        definition = response.definition ?: it.definition,
                        measurement = response.measurement ?: it.measurement,
                        preview = if (response.definition != null) response.preview else it.preview,
                        saveKey = UUID.randomUUID().toString(),
                    )
                }
            }
            onSent()
        }
    }

    fun createGoal() {
        val draft = state.value.goalDraft ?: return
        if (state.value.busy) return
        work(saving = true) { bearer ->
            val created = PulseApi.createGoal(bearer, draft)
            state.update {
                it.copy(
                    mode = PulseMode.Library,
                    goals = it.goals + created,
                    goalDraft = null,
                    goalPreview = null,
                    goalMessages = emptyList(),
                )
            }
        }
    }

    fun entry(goal: GoalView, text: String, onSaved: () -> Unit) {
        val amount = validEntry(text) ?: return
        work(saving = true) { bearer ->
            val updated = PulseApi.entry(bearer, goal.id, amount)
            state.update {
                it.copy(goals = it.goals.map { g -> if (g.id == updated.id) updated else g })
            }
            onSaved()
        }
    }

    fun removeChart(chart: SavedPulseChart) =
        work(saving = true) { bearer ->
            PulseApi.deleteChart(bearer, chart.id)
            state.update {
                it.copy(
                    canvas =
                        it.canvas?.copy(charts = it.canvas.charts.filter { c -> c.id != chart.id })
                )
            }
        }

    fun removeGoal(goal: GoalView) =
        work(saving = true) { bearer ->
            PulseApi.removeGoal(bearer, goal.id)
            state.update { it.copy(goals = it.goals.filter { g -> g.id != goal.id }) }
        }

    fun openBoard(id: String) {
        boardId = id
        state.update { it.copy(mode = PulseMode.Board, board = null, boardResults = emptyList()) }
        work { bearer ->
            coroutineScope {
                val board = async { PulseApi.board(bearer, id) }
                val data = async { PulseApi.boardData(bearer, id) }
                val details = board.await()
                val results = data.await()
                state.update { it.copy(board = details, boardResults = results) }
            }
        }
    }
}
