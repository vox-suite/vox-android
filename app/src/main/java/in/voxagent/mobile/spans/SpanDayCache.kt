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

data class DayKey(val scope: String, val day: LocalDate)

object SpanDayCache {
    private val _counts = MutableStateFlow<Map<DayKey, DaySummary>>(emptyMap())
    val counts: StateFlow<Map<DayKey, DaySummary>> = _counts.asStateFlow()

    private val _entries = MutableStateFlow<Map<DayKey, DayEntry>>(emptyMap())
    val entries: StateFlow<Map<DayKey, DayEntry>> = _entries.asStateFlow()

    @Volatile private var revision: Long? = null

    fun clear() {
        _counts.value = emptyMap()
        _entries.value = emptyMap()
        revision = null
    }

    suspend fun loadCounts(token: String, scope: String, from: LocalDate, to: LocalDate) {
        val result = SpansApi.getDays(token, from, to, scope.ifEmpty { null })
        if (scope.isEmpty()) revision = result.revision
        val found = result.days.associateBy { DayKey(scope, LocalDate.parse(it.day)) }
        _counts.update { current ->
            current.filterKeys { it.scope != scope || it.day < from || it.day > to } + found
        }
    }

    suspend fun revalidate(token: String): Boolean {
        val known = revision ?: return false
        val today = LocalDate.now()
        val changed = !SpansApi.getDays(token, today, today, null, known).unchanged
        if (changed) revision = null
        return changed
    }

    suspend fun loadFirst(token: String, scope: String, day: LocalDate) {
        val key = DayKey(scope, day)
        patch(key) { it.copy(loading = true) }
        try {
            val page = SpansApi.getDayPage(token, day, null, scope.ifEmpty { null })
            patch(key) { DayEntry(page.items, page.nextCursor, page.nextCursor == null, false) }
        } catch (e: Exception) {
            patch(key) { it.copy(loading = false) }
            throw e
        }
    }

    suspend fun loadMore(token: String, scope: String, day: LocalDate) {
        val key = DayKey(scope, day)
        val current = _entries.value[key] ?: return
        val cursor = current.cursor
        if (!current.hasMore || current.loading || cursor == null) return
        patch(key) { it.copy(loading = true) }
        try {
            val page = SpansApi.getDayPage(token, day, cursor, scope.ifEmpty { null })
            patch(key) { latest ->
                val seen = latest.items.mapTo(HashSet()) { it.id }
                DayEntry(
                    latest.items + page.items.filter { it.id !in seen },
                    page.nextCursor,
                    page.nextCursor == null,
                    false,
                )
            }
        } catch (e: Exception) {
            patch(key) { it.copy(loading = false) }
            throw e
        }
    }

    private fun patch(key: DayKey, change: (DayEntry) -> DayEntry) {
        _entries.update { it + (key to change(it[key] ?: DayEntry())) }
    }
}
