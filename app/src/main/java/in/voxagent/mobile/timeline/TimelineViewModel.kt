package `in`.voxagent.mobile.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.voxagent.mobile.spans.DaySummary
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.monthGridDays
import `in`.voxagent.mobile.spans.weekStart
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
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
    val filter: `in`.voxagent.mobile.control.TimelineFilter? = null,
    val frontiers: Map<LocalDate, Long> = emptyMap(),
    val moreDays: List<LocalDate> = emptyList(),
    val before: Int = 2,
    val after: Int = 2,
    val groups: List<`in`.voxagent.mobile.contracts.TimelineGroup> = emptyList(),
    val groupValue: String? = null,
    val loading: Boolean = true,
    val error: String = "",
) {
    val days: List<LocalDate>
        get() =
            filter?.let { f ->
                val zone = java.time.ZoneId.systemDefault()
                val start = Instant.parse(f.startAt).atZone(zone).toLocalDate()
                val end = Instant.parse(f.endAt).minusMillis(1).atZone(zone).toLocalDate()
                List(
                    (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1)
                        .toInt()
                        .coerceIn(1, 366)
                ) {
                    start.plusDays(it.toLong())
                }
            }
                ?: when (mode) {
                    ViewMode.Day ->
                        List(before + after + 1) {
                            anchor.minusDays(before.toLong()).plusDays(it.toLong())
                        }
                    ViewMode.Week -> List(7) { anchor.plusDays(it.toLong()) }
                    ViewMode.Month ->
                        List(anchor.lengthOfMonth()) {
                            anchor.withDayOfMonth(1).plusDays(it.toLong())
                        }
                }
}

class TimelineViewModel(
    private val token: () -> String?,
    private val cacheRoot: java.io.File,
    userId: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(TimelineUi())
    val ui: StateFlow<TimelineUi> = _ui.asStateFlow()
    private var loadJob: Job? = null
    private var filteredCursor: String? = null
    private var filteredItems =
        emptyList<`in`.voxagent.mobile.contracts.TimelineEventWithEvidence>()

    fun applyFilter(filter: `in`.voxagent.mobile.control.TimelineFilter?) {
        if (_ui.value.filter == filter) return
        filteredCursor = null
        filteredItems = emptyList()
        _ui.update {
            it.copy(
                filter = filter,
                mode = ViewMode.Day,
                before = 2,
                after = 2,
                anchor =
                    filter?.let { f ->
                        Instant.parse(f.startAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    } ?: it.anchor,
            )
        }
        reload()
    }

    private val user = userId?.let(TimelinePersistence::userKey)
    private val store =
        TimelineDayStore(
            java.time.ZoneId.systemDefault(),
            { scope, days, timezone ->
                TimelineApi.days(
                    token() ?: error("Signed out"),
                    days.first().atStartOfDay(java.time.ZoneId.of(timezone)).toInstant().toString(),
                    days
                        .last()
                        .plusDays(1)
                        .atStartOfDay(java.time.ZoneId.of(timezone))
                        .toInstant()
                        .toString(),
                    scope.ifEmpty { null },
                    timezone,
                )
            },
            { scope, day, cursor ->
                TimelineApi.query(
                    token() ?: error("Signed out"),
                    day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString(),
                    day.plusDays(1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toString(),
                    scope.ifEmpty { null },
                    cursor,
                )
            },
        )
    private val generation = TimelinePersistence.register(store)
    private var hydrated = false
    private var persistJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            store.entries.collect {
                publish()
                persist()
            }
        }
        viewModelScope.launch {
            store.counts.collect {
                publish()
                persist()
            }
        }
        reload()
    }

    private fun persist() {
        persistJob?.cancel()
        persistJob =
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                val snapshot = store.snapshot().copy(groups = _ui.value.groups)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    user?.let { TimelinePersistence.write(cacheRoot, it, generation, snapshot) }
                }
            }
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
            s.copy(anchor = anchor, selectedDay = anchor, before = 2, after = 2, filter = null)
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
            s.copy(anchor = anchor, selectedDay = today, before = 2, after = 2, filter = null)
        }
        reload()
    }

    fun openDay(day: LocalDate) {
        _ui.update {
            it.copy(mode = ViewMode.Day, anchor = day, selectedDay = day, before = 2, after = 2)
        }
        reload()
    }

    fun selectGroup(value: String?) {
        _ui.update { it.copy(groupValue = value, filter = null) }
        reload()
    }

    private fun publish() {
        val state = _ui.value
        val days = visibleDays(state)
        val scope = state.groupValue.orEmpty()
        if (state.filter != null) return
        val entries = days.map { store.entry(scope, it) }
        val spans =
            entries
                .flatMap { it.items }
                .distinctBy { it.event.id }
                .map { item ->
                    val event = item.event
                    Span(
                        id = event.id,
                        title = event.title,
                        notes = event.summary.orEmpty(),
                        category =
                            state.groups.firstOrNull { it.id == event.group_id }?.value
                                ?: "personal",
                        source = item.evidence.firstOrNull()?.source_type ?: "timeline",
                        status = `in`.voxagent.mobile.spans.SpanStatus.Done,
                        startAt = event.occurred_at,
                        endAt = event.ended_at,
                        data = event.content,
                        createdAt = event.created_at,
                        version = event.revision.toInt(),
                    )
                }
        val counts =
            days
                .mapNotNull { day ->
                    store.count(scope, day)?.let { row ->
                        day to
                            DaySummary(
                                row.day,
                                row.count.toInt(),
                                row.categories.map {
                                    `in`.voxagent.mobile.spans.CategoryCount(
                                        it.category,
                                        it.count.toInt(),
                                    )
                                },
                            )
                    }
                }
                .toMap()
        val frontiers =
            days
                .mapNotNull { day ->
                    store.entry(scope, day).let { entry ->
                        if (entry.cursor == null) null
                        else
                            entry.items.lastOrNull()?.event?.occurred_at?.let {
                                day to Instant.parse(it).toEpochMilli()
                            }
                    }
                }
                .toMap()
        _ui.update {
            it.copy(
                spans = spans,
                dayCounts = counts,
                frontiers = frontiers,
                moreDays = days.filter { store.entry(scope, it).cursor != null },
                hasMore = entries.any { it.cursor != null },
                error = entries.firstOrNull { it.error.isNotEmpty() }?.error.orEmpty(),
            )
        }
    }

    private fun visibleDays(state: TimelineUi) =
        if (state.mode == ViewMode.Month) monthGridDays(state.anchor) else state.days

    fun extend(left: Boolean) {
        val s = _ui.value
        if (s.filter != null || s.mode != ViewMode.Day || s.days.size >= 30 || s.loading) return
        val amount = minOf(3, 30 - s.days.size)
        _ui.update {
            if (left) it.copy(before = it.before + amount) else it.copy(after = it.after + amount)
        }
        reload()
    }

    fun reload() {
        if (!TimelinePersistence.active(generation)) return
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _ui.update { it.copy(loading = true, error = "") }
                try {
                    if (!hydrated) {
                        val cached =
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                user?.let { TimelinePersistence.read(cacheRoot, it, generation) }
                            }
                        if (!TimelinePersistence.active(generation)) return@launch
                        cached?.let {
                            store.restore(it)
                            if (it.timezone == store.zone.id)
                                _ui.update { state -> state.copy(groups = it.groups) }
                        }
                        hydrated = true
                        publish()
                    }
                    if (_ui.value.groups.isEmpty()) {
                        val groups = TimelineApi.groups(token() ?: return@launch)
                        if (!TimelinePersistence.active(generation)) return@launch
                        _ui.update { it.copy(groups = groups) }
                    }
                    publish()
                    val state = _ui.value
                    if (state.filter != null) {
                        filteredItems = emptyList()
                        filteredCursor = null
                        fetchFiltered(false)
                    } else
                        store.refresh(
                            state.groupValue.orEmpty(),
                            visibleDays(state),
                            state.mode == ViewMode.Month,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(error = e.message ?: "Network error") }
                } finally {
                    _ui.update { it.copy(loading = false) }
                }
            }
    }

    fun loadMore(day: LocalDate = _ui.value.selectedDay) {
        if (!TimelinePersistence.active(generation)) return
        if (_ui.value.filter != null) {
            if (_ui.value.loading || filteredCursor == null) return
            _ui.update { it.copy(loading = true) }
            loadJob =
                viewModelScope.launch {
                    try {
                        fetchFiltered(true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _ui.update { it.copy(error = e.message ?: "Could not load entries") }
                    } finally {
                        _ui.update { it.copy(loading = false) }
                    }
                }
        } else {
            val scope = _ui.value.groupValue.orEmpty()
            viewModelScope.launch { store.more(scope, day) }
        }
    }

    private suspend fun fetchFiltered(more: Boolean) {
        val state = _ui.value
        val filter = state.filter ?: return
        val page =
            TimelineApi.filtered(
                token() ?: error("Signed out"),
                filter,
                if (more) filteredCursor else null,
            )
        if (_ui.value.filter != filter || !TimelinePersistence.active(generation)) return
        filteredItems =
            (if (more) filteredItems + page.events else page.events).distinctBy { it.event.id }
        filteredCursor = page.next_cursor
        val spans =
            filteredItems.map { item ->
                val event = item.event
                Span(
                    id = event.id,
                    title = event.title,
                    notes = event.summary.orEmpty(),
                    category =
                        state.groups.firstOrNull { it.id == event.group_id }?.value ?: "finance",
                    source = item.evidence.firstOrNull()?.source_type ?: "timeline",
                    status = `in`.voxagent.mobile.spans.SpanStatus.Done,
                    startAt = event.occurred_at,
                    endAt = event.ended_at,
                    data = event.content,
                    createdAt = event.created_at,
                    version = event.revision.toInt(),
                )
            }
        _ui.update {
            it.copy(
                spans = spans,
                dayCounts = emptyMap(),
                frontiers = emptyMap(),
                moreDays = if (filteredCursor != null) listOf(it.anchor) else emptyList(),
                hasMore = filteredCursor != null,
            )
        }
    }

    fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                kotlinx.coroutines.delay(350)
                reload()
            }
    }

    override fun onCleared() {
        TimelinePersistence.unregister(store)
        super.onCleared()
    }
}
