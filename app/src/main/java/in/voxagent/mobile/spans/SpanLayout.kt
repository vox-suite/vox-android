package `in`.voxagent.mobile.spans

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val MINUTE = 60_000L
private const val DAY_MINUTES = 24 * 60
const val MIN_BLOCK_MINUTES = 25.0
const val SLOT_MINUTES = 15
private const val SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES
private const val CHILD_HEADER_MINUTES = 22.0

val zone: ZoneId get() = ZoneId.systemDefault()

data class PlacedSpan(
    val span: Span,
    val top: Double,
    val height: Double,
    val left: Double,
    val width: Double,
    val depth: Int,
    val instant: Boolean,
    val slot: Int? = null,
)

private class Node(val span: Span, var start: Double, var end: Double, val instant: Boolean) {
    val children = mutableListOf<Node>()
}

fun dayStartMs(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

private fun bounds(span: Span): Pair<Long, Long>? {
    val start = span.startMs ?: return null
    val end = span.endMs ?: start
    return start to maxOf(start, end)
}

fun isAllDay(span: Span): Boolean {
    val b = bounds(span) ?: return false
    return b.second - b.first >= DAY_MINUTES * MINUTE
}

fun spansOnDay(spans: List<Span>, day: LocalDate): List<Span> {
    val start = dayStartMs(day)
    val end = dayStartMs(day.plusDays(1))
    return spans.filter { span ->
        val b = bounds(span) ?: return@filter false
        if (b.first == b.second) b.first in start until end else b.second > start && b.first < end
    }.sortedWith(compareByDescending<Span> { isAllDay(it) }.thenBy { it.startMs ?: 0L })
}

private fun pack(nodes: List<Node>, left: Double, width: Double, depth: Int, out: MutableList<PlacedSpan>) {
    val instants = nodes.filter { it.instant }
    val durations = nodes.filter { !it.instant }
    val sorted = durations.sortedWith(compareBy<Node> { it.start }.thenByDescending { it.end })
    var cluster = mutableListOf<Pair<Node, Int>>()
    var columnEnds = mutableListOf<Double>()
    var clusterEnd = Double.NEGATIVE_INFINITY

    fun flush() {
        val cols = columnEnds.size
        for ((node, col) in cluster) {
            val w = width / cols
            val l = left + col * w
            out += PlacedSpan(node.span, node.start, node.end - node.start, l, w, depth, false)
            for (child in node.children) {
                child.start = maxOf(child.start, node.start + CHILD_HEADER_MINUTES)
                child.end = maxOf(child.end, child.start + MIN_BLOCK_MINUTES)
            }
            pack(node.children, l, w, depth + 1, out)
        }
        cluster = mutableListOf()
        columnEnds = mutableListOf()
    }

    for (node in sorted) {
        if (node.start >= clusterEnd) {
            flush()
            clusterEnd = Double.NEGATIVE_INFINITY
        }
        var col = columnEnds.indexOfFirst { it <= node.start }
        if (col == -1) {
            col = columnEnds.size
            columnEnds.add(node.end)
        } else {
            columnEnds[col] = node.end
        }
        cluster.add(node to col)
        clusterEnd = maxOf(clusterEnd, node.end)
    }
    flush()

    // Instant entries belong to one of the 96 quarter-hour slots; a slot's entries sit side by side.
    for (node in instants.sortedBy { it.start }) {
        val slot = minOf((node.start / SLOT_MINUTES).toInt(), SLOTS_PER_DAY - 1)
        out += PlacedSpan(node.span, (slot * SLOT_MINUTES).toDouble(), SLOT_MINUTES.toDouble(), left, width, depth, true, slot)
        pack(node.children, left, width, depth + 1, out)
    }
}

fun layoutDay(spans: List<Span>, day: LocalDate): List<PlacedSpan> {
    val dayStart = dayStartMs(day)
    val dayEnd = dayStartMs(day.plusDays(1))
    val nodes = LinkedHashMap<String, Node>()
    for (span in spans) {
        val b = bounds(span) ?: continue
        if (isAllDay(span)) continue
        val (start, end) = b
        val instant = end == start
        val outside = if (instant) start < dayStart || start >= dayEnd else end <= dayStart || start >= dayEnd
        if (outside) continue
        val s = (maxOf(start, dayStart) - dayStart).toDouble() / MINUTE
        val e = (minOf(end, dayEnd) - dayStart).toDouble() / MINUTE
        val top = if (instant) s else minOf(s, DAY_MINUTES - MIN_BLOCK_MINUTES)
        nodes[span.id] = Node(span, top, if (instant) top else maxOf(e, top + MIN_BLOCK_MINUTES), instant)
    }
    val roots = mutableListOf<Node>()
    for (node in nodes.values) {
        val parent = node.span.parentId?.let { nodes[it] }
        if (parent != null && parent !== node) parent.children.add(node) else roots.add(node)
    }
    val out = mutableListOf<PlacedSpan>()
    pack(roots, 0.0, 1.0, 0, out)
    return out
}

data class AllDayRow(val span: Span, val startCol: Int, val endCol: Int, val row: Int)

fun layoutAllDay(spans: List<Span>, days: List<LocalDate>): List<AllDayRow> {
    if (days.isEmpty()) return emptyList()
    val first = dayStartMs(days[0])
    val dayMs = DAY_MINUTES * MINUTE
    val rowEnds = mutableListOf<Int>()
    val out = mutableListOf<AllDayRow>()
    val items = spans.filter(::isAllDay).mapNotNull { span ->
        val (start, end) = bounds(span)!!
        val startCol = maxOf(0L, Math.floorDiv(start - first, dayMs)).toInt()
        val endCol = minOf((days.size - 1).toLong(), Math.floorDiv(end - 1 - first, dayMs)).toInt()
        if (endCol >= 0 && startCol < days.size && startCol <= endCol) Triple(span, startCol, endCol) else null
    }.sortedBy { it.second }
    for ((span, startCol, endCol) in items) {
        var row = rowEnds.indexOfFirst { it < startCol }
        if (row == -1) {
            row = rowEnds.size
            rowEnds.add(endCol)
        } else {
            rowEnds[row] = endCol
        }
        out += AllDayRow(span, startCol, endCol, row)
    }
    return out
}

fun monthGridDays(anchor: LocalDate): List<LocalDate> {
    val first = anchor.withDayOfMonth(1)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val last = first.plusMonths(1).minusDays(1)
    val gridEnd = last.plusDays((6 - last.dayOfWeek.value % 7).toLong())
    val total = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1
    val target = if (total <= 35) 35 else 42
    return List(target) { gridStart.plusDays(it.toLong()) }
}
