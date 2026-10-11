package `in`.voxagent.mobile.timeline

import `in`.voxagent.mobile.contracts.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class TimelineDayEntry(
    val items: List<TimelineEventWithEvidence> = emptyList(),
    val cursor: String? = null,
    val rev: String? = null,
    val loading: Boolean = false,
    val error: String = "",
)

@Serializable
data class TimelineSnapshot(
    val timezone: String,
    val counts: Map<String, TimelineDaySummary>,
    val entries: Map<String, TimelineDayEntry>,
    val groups: List<TimelineGroup> = emptyList(),
)

class TimelineDayStore(
    val zone: ZoneId,
    private val summaries: suspend (String, List<LocalDate>, String) -> TimelineDaysResponse,
    private val query: suspend (String, LocalDate, String?) -> TimelinePage,
) {
    private val _counts = MutableStateFlow<Map<String, TimelineDaySummary>>(emptyMap())
    val counts = _counts.asStateFlow()
    private val _entries = MutableStateFlow<Map<String, TimelineDayEntry>>(emptyMap())
    val entries = _entries.asStateFlow()
    private val locks = mutableMapOf<String, Mutex>()
    private var epoch = 0

    private fun key(scope: String, day: LocalDate) = "$scope|$day"

    fun entry(scope: String, day: LocalDate) = _entries.value[key(scope, day)] ?: TimelineDayEntry()

    fun count(scope: String, day: LocalDate) = _counts.value[key(scope, day)]

    fun clear() {
        epoch++
        _counts.value = emptyMap()
        _entries.value = emptyMap()
    }

    fun restore(snapshot: TimelineSnapshot) {
        if (snapshot.timezone != zone.id) return
        _counts.value = snapshot.counts
        _entries.value = snapshot.entries.mapValues { it.value.copy(loading = false, error = "") }
    }

    fun snapshot() =
        TimelineSnapshot(
            zone.id,
            _counts.value
                .toList()
                .sortedByDescending { it.first.substringAfter('|') }
                .take(800)
                .toMap(),
            _entries.value
                .filterValues { it.rev != null }
                .toList()
                .sortedByDescending { it.first.substringAfter('|') }
                .take(90)
                .toMap(),
        )

    suspend fun refresh(scope: String, days: List<LocalDate>, prefetch: Boolean = false) {
        if (days.isEmpty()) return
        val generation = epoch
        val result = summaries(scope, days, zone.id)
        if (generation != epoch) return
        val found = result.days.associateBy { it.day }
        _counts.update { current ->
            current.toMutableMap().apply {
                days.forEach { day ->
                    val row = found[day.toString()]
                    if (row == null) remove(key(scope, day)) else put(key(scope, day), row)
                }
            }
        }
        if (prefetch) {
            for (batch in days.chunked(7)) pool(batch, 3) { first(scope, it, generation) }
        } else pool(days, 6) { first(scope, it, generation) }
    }

    private suspend fun pool(
        days: List<LocalDate>,
        parallel: Int,
        run: suspend (LocalDate) -> Unit,
    ) = coroutineScope {
        days.chunked(parallel).forEach { batch -> batch.map { async { run(it) } }.awaitAll() }
    }

    private suspend fun first(scope: String, day: LocalDate, generation: Int) =
        lock(key(scope, day)).withLock {
            if (generation != epoch) return@withLock
            val row = count(scope, day)
            val current = entry(scope, day)
            if (row == null || row.count == 0L) {
                patch(scope, day, TimelineDayEntry(rev = "0"))
                return@withLock
            }
            if (current.rev == row.rev && current.error.isEmpty()) return@withLock
            fetch(scope, day, null, row.rev, generation)
        }

    suspend fun more(scope: String, day: LocalDate) {
        val requested = entry(scope, day)
        if (requested.loading || requested.cursor == null) return
        val generation = epoch
        lock(key(scope, day)).withLock {
            val current = entry(scope, day)
            if (
                generation != epoch ||
                    current.cursor != requested.cursor ||
                    current.rev != requested.rev
            )
                return@withLock
            val cursor = current.cursor
            if (current.loading) return@withLock
            fetch(scope, day, cursor, current.rev, generation)
        }
    }

    private fun lock(key: String) = locks.getOrPut(key) { Mutex() }

    private fun patch(scope: String, day: LocalDate, entry: TimelineDayEntry) {
        _entries.update { it + (key(scope, day) to entry) }
    }

    private suspend fun fetch(
        scope: String,
        day: LocalDate,
        cursor: String?,
        rev: String?,
        generation: Int,
    ) {
        patch(scope, day, entry(scope, day).copy(loading = true, error = ""))
        try {
            val page = query(scope, day, cursor)
            if (generation != epoch) return
            val items =
                if (cursor == null) page.events
                else (entry(scope, day).items + page.events).distinctBy { it.event.id }
            patch(scope, day, TimelineDayEntry(items, page.next_cursor, rev))
        } catch (e: CancellationException) {
            if (generation == epoch) patch(scope, day, entry(scope, day).copy(loading = false))
            throw e
        } catch (e: Exception) {
            if (generation == epoch)
                patch(
                    scope,
                    day,
                    entry(scope, day)
                        .copy(loading = false, error = e.message ?: "Could not load day"),
                )
        }
    }
}
