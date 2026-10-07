package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.layoutAllDay
import `in`.voxagent.mobile.spans.layoutDay
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal val HOUR_DP = 128.dp

internal val QUARTER_DP = 32.dp

internal val CHIP_DP = 28.dp

internal const val PX_PER_MIN = 128.0 / 60.0

internal const val INDENT_DP = 10

internal val GUTTER = 54.dp

internal val hourLabel = DateTimeFormatter.ofPattern("h a", Locale.getDefault())

@Composable
fun DayGrid(
    day: LocalDate,
    spans: List<Span>,
    onSelect: (Span) -> Unit,
    modifier: Modifier = Modifier,
    hasMore: Boolean = false,
    frontierMs: Long? = null,
    onLoadMore: () -> Unit = {},
) {
    val placed = remember(spans, day) { layoutDay(spans, day) }
    val parents = remember(placed) { placed.mapNotNull { it.span.parentId }.toSet() }
    val allDay = remember(spans, day) { layoutAllDay(spans, listOf(day)) }
    val allDayRows = (allDay.maxOfOrNull { it.row } ?: -1) + 1
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(day, spans.isEmpty()) {
        val earliest =
            spans
                .mapNotNull { it.startMs }
                .map { Instant.ofEpochMilli(it).atZone(zone) }
                .filter { it.toLocalDate() == day }
                .minOfOrNull { it.hour }
        val hour = earliest ?: if (day == LocalDate.now()) LocalDateTime.now().hour - 2 else 8
        snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
        scroll.scrollTo(with(density) { (maxOf(0, hour - 1) * HOUR_DP.toPx()).toInt() })
    }

    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    LaunchedEffect(hasMore, frontierMs, spans.size) {
        if (!hasMore || frontierMs == null) return@LaunchedEffect
        val hours =
            Instant.ofEpochMilli(frontierMs).atZone(zone).let { it.hour + it.minute / 60f }
        val frontierPx = with(density) { (HOUR_DP * hours).toPx() }
        snapshotFlow { scroll.value }
            .first { it + screenPx * 1.5f >= frontierPx }
        onLoadMore()
    }

    Column(modifier.fillMaxSize()) {
        if (allDayRows > 0) {
            Box(Modifier.fillMaxWidth().height((allDayRows * 24 + 6).dp)) {
                allDay.forEach { row ->
                    val style = spanStyle(row.span)
                    Text(
                        displayTitle(row.span),
                        color = Mist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier.offset(x = GUTTER + 2.dp, y = (row.row * 24 + 3).dp)
                                .padding(end = GUTTER + 8.dp)
                                .height(20.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(lerp(Color(0xFF111214), style.dot, 0.3f))
                                .clickable { onSelect(row.span) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "all day",
                    color = SmokeDark,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier =
                        Modifier.width(GUTTER - 6.dp)
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(HOUR_DP * 24)) {
                for (h in 0..23) {
                    if (h > 0) {
                        Text(
                            hourLabel.format(LocalDateTime.of(2000, 1, 1, h, 0)).lowercase(),
                            color = SmokeDark,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier =
                                Modifier.offset(x = 4.dp, y = HOUR_DP * h - 7.dp)
                                    .width(GUTTER - 10.dp),
                        )
                    }
                    for (q in 1..3) {
                        Text(
                            ":${q * 15}",
                            color = SmokeDark.copy(alpha = 0.55f),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier =
                                Modifier.offset(x = 4.dp, y = HOUR_DP * h + QUARTER_DP * q - 6.dp)
                                    .width(GUTTER - 10.dp),
                        )
                    }
                }
                BoxWithConstraints(
                    Modifier.padding(start = GUTTER).fillMaxSize().drawBehind {
                        val step = HOUR_DP.toPx()
                        val quarter = QUARTER_DP.toPx()
                        for (h in 0..23) {
                            drawLine(
                                Color.White.copy(alpha = 0.09f),
                                Offset(0f, h * step),
                                Offset(size.width, h * step),
                                1f,
                            )
                            for (q in 1..3) {
                                drawLine(
                                    Color.White.copy(alpha = 0.035f),
                                    Offset(0f, h * step + q * quarter),
                                    Offset(size.width, h * step + q * quarter),
                                    1f,
                                )
                            }
                        }
                        drawLine(BorderSubtle, Offset(0f, 0f), Offset(0f, size.height), 1f)
                    }
                ) {
                    val colWidth = maxWidth
                    placed
                        .filter { !it.instant }
                        .forEach { p -> SpanBlock(p, p.span.id in parents, colWidth, onSelect) }
                    placed
                        .filter { it.instant }
                        .groupBy { listOf(it.depth, it.left, it.width, it.slot) }
                        .values
                        .forEach { items -> InstantRow(items, colWidth, onSelect) }
                    if (day == now.toLocalDate()) {
                        val top = ((now.hour * 60 + now.minute) * PX_PER_MIN).dp
                        Canvas(Modifier.fillMaxWidth().height(1.dp).offset(y = top)) {
                            drawLine(
                                CoralPulse.copy(alpha = 0.7f),
                                Offset(0f, 0f),
                                Offset(size.width, 0f),
                                2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            )
                        }
                    }
                }
            }
        }
    }
}
