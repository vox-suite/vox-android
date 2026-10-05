package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.PlacedSpan
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.categoryStyle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.formatTime
import `in`.voxagent.mobile.spans.layoutAllDay
import `in`.voxagent.mobile.spans.layoutDay
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HOUR_DP = 56.dp
private const val PX_PER_MIN = 56.0 / 60.0
private const val INDENT_DP = 10
private val GUTTER = 54.dp
private val hourLabel = DateTimeFormatter.ofPattern("h a", Locale.getDefault())

@Composable
fun DayGrid(day: LocalDate, spans: List<Span>, onSelect: (Span) -> Unit, modifier: Modifier = Modifier) {
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
        val earliest = spans.mapNotNull { it.startMs }
            .map { Instant.ofEpochMilli(it).atZone(zone) }
            .filter { it.toLocalDate() == day }
            .minOfOrNull { it.hour }
        val hour = earliest ?: if (day == LocalDate.now()) LocalDateTime.now().hour - 2 else 8
        snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
        scroll.scrollTo(with(density) { (maxOf(0, hour - 1) * HOUR_DP.toPx()).toInt() })
    }

    Column(modifier.fillMaxSize()) {
        if (allDayRows > 0) {
            Box(Modifier.fillMaxWidth().height((allDayRows * 24 + 6).dp)) {
                allDay.forEach { row ->
                    val style = categoryStyle(row.span.category, row.span.schemaColorToken)
                    Text(
                        row.span.title,
                        color = Mist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(x = GUTTER + 2.dp, y = (row.row * 24 + 3).dp)
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
                    modifier = Modifier.width(GUTTER - 6.dp).align(Alignment.CenterStart).padding(start = 4.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(HOUR_DP * 24)) {
                for (h in 1..23) {
                    Text(
                        hourLabel.format(LocalDateTime.of(2000, 1, 1, h, 0)).lowercase(),
                        color = SmokeDark,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.offset(x = 4.dp, y = HOUR_DP * h - 7.dp).width(GUTTER - 10.dp),
                    )
                }
                BoxWithConstraints(
                    Modifier
                        .padding(start = GUTTER)
                        .fillMaxSize()
                        .drawBehind {
                            val step = HOUR_DP.toPx()
                            for (h in 0..23) {
                                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, h * step), Offset(size.width, h * step), 1f)
                            }
                            drawLine(BorderSubtle, Offset(0f, 0f), Offset(0f, size.height), 1f)
                        },
                ) {
                    val colWidth = maxWidth
                    placed.forEach { p ->
                        SpanBlock(p, p.span.id in parents, colWidth, onSelect)
                    }
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

@Composable
private fun SpanBlock(placed: PlacedSpan, hasChildren: Boolean, colWidth: Dp, onSelect: (Span) -> Unit) {
    val span = placed.span
    val style = categoryStyle(span.category, span.schemaColorToken)
    val inset = placed.depth * INDENT_DP + 3
    val heightDp = maxOf(placed.height * PX_PER_MIN - 2, 22.0).dp
    val showTime = !placed.instant && heightDp >= 48.dp
    val amount = formatAmount(span)
    val muted = span.status == SpanStatus.Cancelled
    val border = when (span.status) {
        SpanStatus.Active -> BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
        SpanStatus.Failed -> BorderStroke(1.dp, CoralPulse)
        else -> BorderStroke(1.dp, style.border)
    }
    val bg = if (hasChildren) lerp(Color(0xFF111215), style.bg, 0.6f) else style.bg
    val shape = RoundedCornerShape(if (placed.instant) 50 else 8)
    val width = colWidth * placed.width.toFloat() - (inset + 3).dp
    val base = Modifier
        .offset(x = colWidth * placed.left.toFloat() + inset.dp, y = (placed.top * PX_PER_MIN + 2).dp)
        .alpha(if (muted) 0.4f else 1f)
        .clip(shape)
        .background(bg)
        .border(border, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(span) }
    if (placed.instant) {
        Row(
            base.height(22.dp).widthIn(max = width).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIndicator(span, style.dot)
            Text(
                span.title,
                color = Mist,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (muted) TextDecoration.LineThrough else null,
            )
            if (amount != null) Text(amount, color = Mist, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    } else {
        Column(
            base.width(width).height(heightDp).padding(horizontal = 8.dp, vertical = if (showTime && !hasChildren) 8.dp else 3.dp),
            verticalArrangement = if (showTime) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    span.title,
                    color = Mist,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (muted) TextDecoration.LineThrough else null,
                )
                CategoryIndicator(span, style.dot)
            }
            if (showTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTime(span.startMs) + (span.endMs?.let { " – ${formatTime(it)}" } ?: ""),
                        color = style.subtext,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    if (amount != null) Text(amount, color = Mist, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
