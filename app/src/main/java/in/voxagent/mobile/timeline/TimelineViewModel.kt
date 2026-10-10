package `in`.voxagent.mobile.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.spans.DayKey
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
    val groups: List<`in`.voxagent.mobile.contracts.TimelineGroup> = emptyList(),
    val groupValue: String? = null,
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
        loadGroups()
        reload()
    }

    fun revalidate() { if (!_ui.value.loading) reload() }

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

    fun selectGroup(value: String?) {
        _ui.update { it.copy(groupValue = value) }
        reload()
    }
    private var cursor: String? = null
    fun reload() = load(false)
    fun loadMore(day: LocalDate = _ui.value.selectedDay) = load(true)
    private fun load(more: Boolean) {
        val bearer = token() ?: return
        if (more && (_ui.value.loading || cursor == null)) return
        val state = _ui.value
        val days = if (state.mode == ViewMode.Month) monthGridDays(state.anchor) else state.days
        loadJob?.cancel()
        _ui.update { it.copy(loading = true, error = "") }
        loadJob = viewModelScope.launch {
            try {
                val from = Instant.ofEpochMilli(dayStartMs(days.first())).toString()
                val to = Instant.ofEpochMilli(dayStartMs(days.last().plusDays(1))).toString()
                if (state.mode == ViewMode.Month) {
                    val rows = TimelineApi.counts(bearer, from, to, state.groupValue)
                    val counts = rows.groupBy { LocalDate.parse(it.day) }.mapValues { (date, list) ->
                        DaySummary(date.toString(), list.sumOf { it.count }.toInt(), list.map { `in`.voxagent.mobile.spans.CategoryCount(it.category, it.count.toInt()) })
                    }
                    _ui.update { it.copy(dayCounts = counts, loading = false) }
                } else {
                    val page = TimelineApi.query(bearer, from, to, state.groupValue, if (more) cursor else null)
                    val spans = page.events.map { item ->
                        val event = item.event
                        Span(id = event.id, title = event.title, notes = event.summary.orEmpty(),
                            category = _ui.value.groups.firstOrNull { it.id == event.group_id }?.value ?: "personal",
                            source = item.evidence.firstOrNull()?.source_type ?: "timeline", status = `in`.voxagent.mobile.spans.SpanStatus.Done,
                            startAt = event.occurred_at, endAt = event.ended_at, data = event.content, createdAt = event.created_at, version = event.revision.toInt())
                    }
                    cursor = page.next_cursor
                    _ui.update { it.copy(spans = if (more) (it.spans + spans).distinctBy { s -> s.id } else spans, hasMore = cursor != null, loading = false) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it.copy(loading = false, error = e.message ?: "Network error") } }
        }
    }
    private fun loadGroups() {
        val bearer = token() ?: return
        viewModelScope.launch {
            try { val groups = TimelineApi.groups(bearer); _ui.update { it.copy(groups = groups) }; reload() }
            catch (e: Exception) { if (e is CancellationException) throw e; _ui.update { it.copy(error = e.message ?: "Could not load groups") } }
        }
    }
}
