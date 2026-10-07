package `in`.voxagent.mobile.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.spans.DaySummary
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanDayCache
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.spans.SpansApi
import `in`.voxagent.mobile.spans.dayStartMs
import `in`.voxagent.mobile.spans.monthGridDays
import `in`.voxagent.mobile.spans.weekStart
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode {
    Day,
    Week,
    Month,
}

data class TimelineUi(
    val mode: ViewMode = ViewMode.Day,
    val anchor: LocalDate = LocalDate.now(),
    val selectedDay: LocalDate = LocalDate.now(),
    val spans: List<Span> = emptyList(),
    val dayCounts: Map<LocalDate, DaySummary> = emptyMap(),
    val hasMore: Boolean = false,
    val frontierMs: Long? = null,
    val collections: List<SpanCollection> = emptyList(),
    val collectionId: String? = null,
    val loading: Boolean = true,
    val error: String = "",
) {
    val days: List<LocalDate>
        get() =
            when (mode) {
                ViewMode.Day -> listOf(anchor)
                ViewMode.Week -> List(7) { anchor.plusDays(it.toLong()) }
                ViewMode.Month -> List(anchor.lengthOfMonth()) { anchor.withDayOfMonth(1).plusDays(it.toLong()) }
            }

    val collection: SpanCollection?
        get() = collections.firstOrNull { it.id == collectionId }
}

class TimelineViewModel(private val token: () -> String?) : ViewModel() {
    private val _ui = MutableStateFlow(TimelineUi())
    val ui: StateFlow<TimelineUi> = _ui.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadCollections()
        reload()
    }

    fun setMode(mode: ViewMode) {
        _ui.update { s ->
            val anchor =
                when (mode) {
                    ViewMode.Day -> s.anchor
                    ViewMode.Week -> weekStart(s.anchor)
                    ViewMode.Month -> s.anchor.withDayOfMonth(1)
                }
            val week = List(7) { anchor.plusDays(it.toLong()) }
            val selected =
                if (mode == ViewMode.Week && s.selectedDay !in week) anchor else s.selectedDay
            s.copy(mode = mode, anchor = anchor, selectedDay = selected)
        }
        reload()
    }

    fun previous() = shift(-1)

    fun next() = shift(1)

    private fun shift(direction: Long) {
        _ui.update { s ->
            val anchor =
                when (s.mode) {
                    ViewMode.Day -> s.anchor.plusDays(direction)
                    ViewMode.Week -> s.anchor.plusDays(7 * direction)
                    ViewMode.Month -> s.anchor.plusMonths(direction)
                }
            s.copy(anchor = anchor, selectedDay = anchor)
        }
        reload()
    }

    fun today() {
        val today = LocalDate.now()
        _ui.update { s ->
            val anchor =
                when (s.mode) {
                    ViewMode.Day -> today
                    ViewMode.Week -> weekStart(today)
                    ViewMode.Month -> today.withDayOfMonth(1)
                }
            s.copy(anchor = anchor, selectedDay = today)
        }
        reload()
    }

    fun selectDay(day: LocalDate) = _ui.update { it.copy(selectedDay = day) }

    fun openDay(day: LocalDate) {
        _ui.update { it.copy(mode = ViewMode.Day, anchor = day, selectedDay = day) }
        reload()
    }

    fun selectCollection(id: String?) {
        _ui.update { it.copy(collectionId = id) }
        reload()
    }

    fun reload() {
        val bearer = token() ?: return
        val s = _ui.value
        val days = s.days
        loadJob?.cancel()
        _ui.update { it.copy(loading = true) }
        loadJob =
            viewModelScope.launch {
                try {
                    when {
                        s.collectionId != null -> {
                            val from = Instant.ofEpochMilli(dayStartMs(days.first()))
                            val to = Instant.ofEpochMilli(dayStartMs(days.last().plusDays(1)))
                            val spans = SpansApi.getSpans(bearer, from, to, s.collectionId)
                            _ui.update { it.copy(spans = spans, loading = false, error = "") }
                        }
                        s.mode == ViewMode.Month -> {
                            val spans = SpansApi.getSpans(bearer, Instant.ofEpochMilli(dayStartMs(days.first())), Instant.ofEpochMilli(dayStartMs(days.last().plusDays(1))), null)
                            _ui.update { it.copy(spans = spans, hasMore = false, loading = false, error = "") }
                        }
                        else -> {
                            coroutineScope {
                                days.map { day -> async { SpanDayCache.loadFirst(bearer, day) } }
                                    .awaitAll()
                            }
                            publish(days)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(loading = false, error = e.message ?: "Network error") }
                }
            }
    }

    fun loadMore(day: LocalDate = _ui.value.selectedDay) {
        val bearer = token() ?: return
        val s = _ui.value
        if (s.collectionId != null || s.mode == ViewMode.Month) return
        val target = if (s.mode == ViewMode.Day) s.anchor else day
        viewModelScope.launch {
            try {
                SpanDayCache.loadMore(bearer, target)
                publish(s.days)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "Network error") }
            }
        }
    }

    private fun publish(days: List<LocalDate>) {
        val entries = SpanDayCache.entries.value
        val seen = HashSet<String>()
        val spans = days.flatMap { entries[it]?.items.orEmpty() }.filter { seen.add(it.id) }
        val target = _ui.value.let { if (it.mode == ViewMode.Day) it.anchor else it.selectedDay }
        val entry = entries[target]
        _ui.update {
            it.copy(
                spans = spans,
                dayCounts = SpanDayCache.counts.value,
                hasMore = entry?.hasMore == true,
                frontierMs = entry?.items?.lastOrNull()?.startMs,
                loading = false,
                error = "",
            )
        }
    }

    private fun loadCollections() {
        val bearer = token() ?: return
        viewModelScope.launch {
            runCatching { SpansApi.getCollections(bearer) }
                .onSuccess { list -> _ui.update { it.copy(collections = list) } }
        }
    }
}
