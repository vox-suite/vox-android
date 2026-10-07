package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.*
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private data class PlanItem(val span: Span, val x: Float, val width: Float, val lane: Int)

@Composable
internal fun PlanningTimeline(days: List<LocalDate>, spans: List<Span>, onOpen: (Span) -> Unit, loading: Boolean = false) {
    val from = dayStartMs(days.first())
    val to = dayStartMs(days.last().plusDays(1))
    val width = when { days.size == 1 -> 2400f; days.size == 7 -> 2800f; else -> days.size * 100f }
    val scale = width / (to - from)
    val items = remember(spans, from, to, width) {
        val lanes = mutableListOf<Float>()
        spans.filter { it.startMs != null && it.startMs!! < to && (it.endMs ?: it.startMs!!) >= from }.sortedBy { it.startMs }.map { span ->
            val start = span.startMs!!
            val x = maxOf(0L, start - from) * scale
            val w = maxOf(180f, (minOf(span.endMs ?: start, to) - maxOf(start, from)) * scale).coerceAtMost(width - x)
            val lane = lanes.indexOfFirst { it + 8 <= x }.let { if (it < 0) lanes.size else it }
            if (lane == lanes.size) lanes.add(x + w) else lanes[lane] = x + w
            PlanItem(span, x, w, lane)
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentHeight = maxOf(maxHeight, (90 + (items.maxOfOrNull { it.lane } ?: 0) * 56).dp)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Box(Modifier.width((width + 56).dp).height(contentHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                    drawLine(Color(0xFF292929), Offset(0f, 32.dp.toPx()), Offset(size.width, 32.dp.toPx()))
                    fun line(time: Long, strong: Boolean) {
                        val x = (40 + (time - from) * scale).dp.toPx()
                        drawLine(if (strong) Color(0xFF242424) else Color.White.copy(alpha = .06f), Offset(x, 32.dp.toPx()), Offset(x, size.height), 1.dp.toPx(), pathEffect = if (strong) null else dash)
                    }
                    if (days.size == 1) repeat(24) { line(days.first().atStartOfDay(zone).plusHours(it.toLong()).toInstant().toEpochMilli(), false) }
                    else days.forEachIndexed { i, day ->
                        line(dayStartMs(day), days.size == 7 || i % 7 == 0)
                        if (days.size == 7) (1..7).forEach { line(day.atStartOfDay(zone).plusHours(it * 3L).toInstant().toEpochMilli(), false) }
                    }
                    if (now in from until to) {
                        val x = (40 + (now - from) * scale).dp.toPx()
                        drawLine(Color(0xFF7F1D1D), Offset(x, 32.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    }
                }
                val ticks = if (days.size == 1) (0..23).map { days.first().atStartOfDay(zone).plusHours(it.toLong()) }
                    else days.filterIndexed { i, _ -> days.size == 7 || i % 7 == 0 }.map { it.atStartOfDay(zone) }
                ticks.forEach { date -> Text(date.format(DateTimeFormatter.ofPattern(if (days.size == 1) "h a" else "MMM d")), fontSize = 10.sp, color = Color.Gray, modifier = Modifier.offset((20 + (date.toInstant().toEpochMilli() - from) * scale).dp, 8.dp)) }
                if (now in from until to) Text("Now", color = Color.White, fontSize = 10.sp, modifier = Modifier.offset((24 + (now - from) * scale).dp).background(Color(0xFF7F1D1D), RoundedCornerShape(6.dp)).padding(6.dp))
                items.forEach { item -> PlanChip(item, Modifier.offset((40 + item.x).dp, (42 + item.lane * 56).dp), onOpen) }
                if (items.isEmpty()) Text(if (loading) "Loading your plan…" else "No events in this date range", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.offset(40.dp, 64.dp))
            }
        }
    }
}

@Composable
private fun PlanChip(item: PlanItem, modifier: Modifier, onOpen: (Span) -> Unit) {
    val span = item.span
    val style = spanStyle(span)
    var preview by remember(span.id) { mutableStateOf(false) }
    val minutes = ((span.endMs ?: span.startMs ?: 0) - (span.startMs ?: 0)) / 60_000
    val badge = formatAmount(span) ?: if (minutes > 0) (if (isEstimated(span)) "≈ " else "") + when {
        minutes >= 1440 -> "${minutes / 1440}d"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    } else formatTime(span.startMs)
    Box(modifier.width(item.width.dp)) {
        Row(Modifier.alpha(if (span.status == SpanStatus.Cancelled) .45f else 1f).fillMaxWidth().heightIn(min = 48.dp).background(style.bg, RoundedCornerShape(8.dp)).border(1.dp, style.border, RoundedCornerShape(8.dp)).combinedClickable(onClick = { onOpen(span) }, onLongClick = { preview = true }).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryIndicator(span, style.dot, dot = 5.dp)
            Text(displayTitle(span), Modifier.weight(1f), fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(badge, fontSize = 9.sp, color = Color.White, modifier = Modifier.background(Color(0xFF292929), RoundedCornerShape(4.dp)).padding(4.dp))
        }
        DropdownMenu(expanded = preview, onDismissRequest = { preview = false }) {
            Column(Modifier.widthIn(max = 280.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayTitle(span), fontSize = 13.sp)
                Text("${span.source} · ${span.category} · ${statusLabel(span.status)}", fontSize = 11.sp, color = Color.Gray)
                span.startMs?.let { Text(dateFormat.format(Instant.ofEpochMilli(it)) + " · " + formatTime(it) + (span.endMs?.let { end -> " – " + formatTime(end) } ?: ""), fontSize = 11.sp) }
                formatAmount(span)?.let { Text(it, fontSize = 12.sp) }
                TextButton(onClick = { preview = false; onOpen(span) }) { Text("Open details") }
            }
        }
    }
}
