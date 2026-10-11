package `in`.voxagent.mobile.pulse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.net.LiveHub
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PulseViewModel(
    private val token: () -> String?,
    private val cache: `in`.voxagent.mobile.cache.DeviceCache?,
) : ViewModel() {
    constructor(token: () -> String?) : this(token, null)

    private val state = MutableStateFlow(PulseUiState())
    val ui = state.asStateFlow()
    private var workJob: Job? = null
    private val chartJobs = mutableMapOf<String, Job>()
    private val suggestionKeys = mutableMapOf<PulseSuggestion, String>()
    private var generation = 0
    private var editorDraft: PulseUiState? = null
    private var dataRevision: String? = null
    private var chartCount: Int? = null
    private var loadedPages = 1
    private var hydrated = false
    private var persistJob: Job? = null
    private val cacheEpoch = cache?.epoch ?: `in`.voxagent.mobile.cache.DeviceCaches.epoch()
    private val reset: () -> Unit = {
        generation++
        workJob?.cancel()
        persistJob?.cancel()
        chartJobs.values.forEach { it.cancel() }
        chartJobs.clear()
        dataRevision = null
        chartCount = null
        state.value = PulseUiState(loading = false)
    }

    init {
        `in`.voxagent.mobile.cache.DeviceCaches.register(reset)
    }

    private fun persist() {
        val canvas = state.value.canvas ?: return
        persistJob?.cancel()
        val snapshot =
            PulseSnapshot(dataRevision, chartCount, java.time.ZoneId.systemDefault().id, canvas)
        persistJob =
            viewModelScope.launch {
                delay(1500)
                cache?.write(snapshot)
            }
    }

    override fun onCleared() {
        `in`.voxagent.mobile.cache.DeviceCaches.unregister(reset)
        super.onCleared()
    }

    suspend fun observe() {
        val hub = LiveHub.get(token)
        hub.start()
        if (state.value.mode == PulseMode.Library) reload()
        coroutineScope {
            val fallback = launch {
                while (isActive) {
                    delay(60_000)
                    if (state.value.mode == PulseMode.Library && !state.value.busy) reload()
                }
            }
            try {
                hub.events
                    .filter {
                        it.type == "timeline_updated" ||
                            it.type == "live_reconnected" ||
                            it.type.startsWith("pulse_") ||
                            it.type.startsWith("span_") ||
                            it.type.contains("connection")
                    }
                    .collectLatest {
                        delay(350)
                        if (state.value.mode == PulseMode.Library) reload()
                    }
            } finally {
                fallback.cancel()
            }
        }
    }

    private fun work(saving: Boolean = false, action: suspend (String) -> Unit) {
        if (state.value.saving || !`in`.voxagent.mobile.cache.DeviceCaches.active(cacheEpoch))
            return
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
            val draft = editorDraft
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
        if (more && state.value.busy) return
        val offset = if (more) state.value.canvas?.next_cursor?.toIntOrNull() ?: return else 0
        if (refresh) {
            chartJobs.values.forEach { it.cancel() }
            chartJobs.clear()
        }
        work { bearer ->
            val request = generation
            fun current() =
                request == generation && `in`.voxagent.mobile.cache.DeviceCaches.active(cacheEpoch)
            if (!hydrated) {
                val saved = cache?.read<PulseSnapshot>()
                if (!current()) return@work
                if (saved?.timezone == java.time.ZoneId.systemDefault().id) {
                    dataRevision = saved.revision
                    chartCount = saved.count
                    loadedPages = maxOf(1, (saved.canvas.charts.size + 11) / 12)
                    state.update { it.copy(canvas = saved.canvas, loading = false) }
                }
                hydrated = true
            }
            val meta = if (more) null else PulseApi.count(bearer)
            if (!current()) return@work
            if (meta?.count == 0) {
                dataRevision = meta.revision
                chartCount = 0
                loadedPages = 1
                state.update { it.copy(canvas = PulseCanvas(), chartPreviews = emptyMap()) }
                persist()
                return@work
            }
            if (
                !refresh &&
                    !more &&
                    meta?.revision == dataRevision &&
                    meta?.count == chartCount &&
                    state.value.canvas != null &&
                    state.value.canvas!!.charts.all { it.result != null && it.result.error == null }
            )
                return@work
            val old = state.value.canvas?.charts.orEmpty().associateBy { it.id }
            val pages = if (more) listOf(offset) else List(loadedPages) { it * 12 }
            val lists = coroutineScope {
                pages.map { start -> async { PulseApi.list(bearer, start) } }.awaitAll()
            }
            if (!current()) return@work
            val fresh =
                lists
                    .flatMap { it.take(12) }
                    .distinctBy { it.id }
                    .map { it.copy(result = old[it.id]?.result) }
            val nextCursor = if (lists.last().size > 12) (pages.last() + 12).toString() else null
            val merged = if (more) (old.values + fresh).distinctBy { it.id } else fresh
            state.update {
                it.copy(
                    canvas = PulseCanvas(merged, nextCursor),
                    loading = false,
                    chartPreviews =
                        if (refresh || (meta != null && meta.revision != dataRevision)) emptyMap()
                        else it.chartPreviews,
                )
            }
            if (more) loadedPages = offset / 12 + 1
            persist()
            val stale =
                fresh.filter {
                    refresh ||
                        (meta != null && meta.revision != dataRevision) ||
                        it.result == null ||
                        it.result.error != null
                }
            val semaphore = kotlinx.coroutines.sync.Semaphore(3)
            coroutineScope {
                stale
                    .map { chart ->
                        launch {
                            semaphore.acquire()
                            try {
                                val result =
                                    try {
                                        PulseApi.result(bearer, chart.id, refresh)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        PulseResult(
                                            error = e.message ?: "Could not load chart",
                                            quality = "unavailable",
                                        )
                                    }
                                if (current()) {
                                    state.update {
                                        it.copy(
                                            canvas =
                                                it.canvas?.copy(
                                                    charts =
                                                        it.canvas.charts.map { item ->
                                                            if (item.id == chart.id)
                                                                item.copy(result = result)
                                                            else item
                                                        }
                                                )
                                        )
                                    }
                                    persist()
                                }
                            } finally {
                                semaphore.release()
                            }
                        }
                    }
                    .joinAll()
            }
            if (current() && meta != null) {
                dataRevision = meta.revision
                chartCount = meta.count
                persist()
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
            PulseMode.Editor -> state.value.definition?.let { change(it) }
            else -> reload()
        }
    }

    fun edit(suggestion: PulseSuggestion) {
        if (state.value.saving) return
        workJob?.cancel()
        generation++
        editorDraft = state.value
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
            dataRevision = null
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
            persist()
            viewModelScope.launch {
                workJob?.join()
                reload()
            }
        }
    }

    fun removeChart(chart: SavedPulseChart) =
        work(saving = true) { bearer ->
            PulseApi.deleteChart(bearer, chart.id)
            dataRevision = null
            state.update {
                it.copy(
                    canvas =
                        it.canvas?.copy(charts = it.canvas.charts.filter { c -> c.id != chart.id })
                )
            }
            persist()
        }

    fun ensureMeasurements() {
        if (
            state.value.measurements.isNotEmpty() ||
                !`in`.voxagent.mobile.cache.DeviceCaches.active(cacheEpoch)
        )
            return
        viewModelScope.launch {
            val bearer = token() ?: return@launch
            runCatching { PulseApi.measurements(bearer) }
                .onSuccess { list ->
                    if (
                        `in`.voxagent.mobile.cache.DeviceCaches.active(cacheEpoch) &&
                            token() == bearer
                    )
                        state.update { it.copy(measurements = list) }
                }
        }
    }

    private fun loadMeasurements() = work { bearer ->
        val measurements = PulseApi.measurements(bearer)
        state.update { it.copy(measurements = measurements) }
    }

    fun choose(measurement: Measurement) {
        val definition =
            PulseDefinition(
                measurement_id = measurement.id,
                bucket = measurement.buckets.firstOrNull() ?: "day",
            )
        state.update {
            it.copy(
                mode = PulseMode.Editor,
                measurement = measurement,
                definition = definition,
                title = measurement.title,
                preview = null,
            )
        }
        change(definition)
    }
}
