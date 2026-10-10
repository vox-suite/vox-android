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
            PulseMode.Measurements -> loadMeasurements()
            else -> Unit
        }
    }

    fun back() =
        go(if (state.value.mode == PulseMode.Editor) PulseMode.Suggestions else PulseMode.Library)

    fun reload(refresh: Boolean = false, more: Boolean = false) {
        if (refresh) {
            chartJobs.values.forEach { it.cancel() }
            chartJobs.clear()
        }
        val cursor = if (more) state.value.canvas?.next_cursor ?: return else null
        work { bearer ->
            val next = PulseApi.canvas(bearer, refresh, cursor)
            state.update { current -> current.copy(canvas = next.copy(charts = if (more) (current.canvas?.charts.orEmpty() + next.charts).distinctBy { it.id } else next.charts), chartPreviews = if (refresh) emptyMap() else current.chartPreviews) }
        }
    }

    fun discover(more: Boolean = false) = work { bearer ->
        val response = PulseApi.discover(bearer, more)
        state.update { it.copy(discovery = response) }
    }

    fun retry() {
        when (state.value.mode) {
            PulseMode.Suggestions -> discover()
            PulseMode.Editor -> state.value.definition?.let { change(it) }
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

    private fun loadMeasurements() = work { bearer ->
        val measurements = PulseApi.measurements(bearer)
        state.update { it.copy(measurements = measurements) }
    }
    fun choose(measurement: Measurement) {
        val definition = PulseDefinition(measurement_id = measurement.id, bucket = measurement.buckets.firstOrNull() ?: "day")
        state.update { it.copy(mode = PulseMode.Editor, measurement = measurement, definition = definition, title = measurement.title, preview = null) }
        change(definition)
    }
}
