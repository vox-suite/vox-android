package `in`.voxagent.mobile.spans

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DayEntry(
    val items: List<Span> = emptyList(),
    val cursor: String? = null,
    val done: Boolean = false,
    val loading: Boolean = false,
) {
    val hasMore: Boolean get() = !done && cursor != null
}

object SpanDayCache {
    private val _counts = MutableStateFlow<Map<LocalDate, DaySummary>>(emptyMap())
    val counts: StateFlow<Map<LocalDate, DaySummary>> = _counts.asStateFlow()

    private val _entries = MutableStateFlow<Map<LocalDate, DayEntry>>(emptyMap())
    val entries: StateFlow<Map<LocalDate, DayEntry>> = _entries.asStateFlow()

    fun clear() {
        _counts.value = emptyMap()
        _entries.value = emptyMap()
    }

    suspend fun loadCounts(token: String, from: LocalDate, to: LocalDate) {
        val found = SpansApi.getDays(token, from, to).associateBy { LocalDate.parse(it.day) }
        _counts.update { current ->
            val kept = current.filterKeys { it < from || it > to }
            kept + found
        }
    }

    suspend fun loadFirst(token: String, day: LocalDate) {
        patch(day) { it.copy(loading = true) }
        try {
            val page = SpansApi.getDayPage(token, day, null)
            patch(day) {
                DayEntry(page.items, page.nextCursor, page.nextCursor == null, false)
            }
        } catch (e: Exception) {
            patch(day) { it.copy(loading = false) }
            throw e
        }
    }

    suspend fun loadMore(token: String, day: LocalDate) {
        val current = _entries.value[day] ?: return
        val cursor = current.cursor
        if (!current.hasMore || current.loading || cursor == null) return
        patch(day) { it.copy(loading = true) }
        try {
            val page = SpansApi.getDayPage(token, day, cursor)
            patch(day) { latest ->
                val seen = latest.items.mapTo(HashSet()) { it.id }
                DayEntry(
                    latest.items + page.items.filter { it.id !in seen },
                    page.nextCursor,
                    page.nextCursor == null,
                    false,
                )
            }
        } catch (e: Exception) {
            patch(day) { it.copy(loading = false) }
            throw e
        }
    }

    private fun patch(day: LocalDate, change: (DayEntry) -> DayEntry) {
        _entries.update { it + (day to change(it[day] ?: DayEntry())) }
    }
}

fun summarize(spans: List<Span>, days: List<LocalDate>): Map<LocalDate, DaySummary> =
    days.mapNotNull { day ->
        val onDay = spansOnDay(spans, day)
        if (onDay.isEmpty()) null
        else
            day to
                DaySummary(
                    day.toString(),
                    onDay.size,
                    onDay.groupingBy { it.category }.eachCount().map { CategoryCount(it.key, it.value) },
                )
    }.toMap()
