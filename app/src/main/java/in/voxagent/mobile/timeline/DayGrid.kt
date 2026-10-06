package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.PlacedSpan
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.isEstimated
import `in`.voxagent.mobile.spans.spanCover
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.spanSubtitle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.formatTime
import `in`.voxagent.mobile.spans.layoutAllDay
import `in`.voxagent.mobile.spans.layoutDay
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.kit.RemoteImage
import androidx.compose.foundation.rememberScrollState as rememberHScroll
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

private val HOUR_DP = 128.dp
private val QUARTER_DP = 32.dp
private val CHIP_DP = 28.dp
private const val PX_PER_MIN = 128.0 / 60.0
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
                    val style = spanStyle(row.span)
                    Text(
                        displayTitle(row.span),
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
                for (h in 0..23) {
                    if (h > 0) {
                        Text(
                            hourLabel.format(LocalDateTime.of(2000, 1, 1, h, 0)).lowercase(),
                            color = SmokeDark,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.offset(x = 4.dp, y = HOUR_DP * h - 7.dp).width(GUTTER - 10.dp),
                        )
                    }
                    for (q in 1..3) {
                        Text(
                            ":${q * 15}",
                            color = SmokeDark.copy(alpha = 0.55f),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.offset(x = 4.dp, y = HOUR_DP * h + QUARTER_DP * q - 6.dp).width(GUTTER - 10.dp),
                        )
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .padding(start = GUTTER)
                        .fillMaxSize()
                        .drawBehind {
                            val step = HOUR_DP.toPx()
                            val quarter = QUARTER_DP.toPx()
                            for (h in 0..23) {
                                drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, h * step), Offset(size.width, h * step), 1f)
                                for (q in 1..3) {
                                    drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, h * step + q * quarter), Offset(size.width, h * step + q * quarter), 1f)
                                }
                            }
                            drawLine(BorderSubtle, Offset(0f, 0f), Offset(0f, size.height), 1f)
                        },
                ) {
                    val colWidth = maxWidth
                    placed.filter { !it.instant }.forEach { p ->
                        SpanBlock(p, p.span.id in parents, colWidth, onSelect)
                    }
                    placed.filter { it.instant }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstantRow(items: List<PlacedSpan>, colWidth: Dp, onSelect: (Span) -> Unit) {
    val first = items.first()
    val inset = first.depth * INDENT_DP + 3
    Row(
        Modifier
            .offset(x = colWidth * first.left.toFloat() + inset.dp, y = QUARTER_DP * (first.slot ?: 0))
            .width(colWidth * first.width.toFloat() - (inset + 3).dp)
            .height(QUARTER_DP)
            .horizontalScroll(rememberHScroll()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { InstantChip(it.span, onSelect) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstantChip(span: Span, onSelect: (Span) -> Unit) {
    val style = spanStyle(span)
    val iconOnly = span.source == "spotify" || span.source == "youtube"
    val cover = spanCover(span)
    val amount = formatAmount(span)
    val muted = span.status == SpanStatus.Cancelled
    var card by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(50)
    val chip = Modifier
        .alpha(if (muted) 0.4f else 1f)
        .height(CHIP_DP)
        .clip(shape)
        .background(style.bg)
        .border(BorderStroke(1.dp, style.border), shape)
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onSelect(span) },
            onLongClick = { if (iconOnly || cover != null) card = true },
        )
    if (iconOnly) {
        Box(chip.size(CHIP_DP), contentAlignment = Alignment.Center) { CategoryIndicator(span, style.dot) }
    } else {
        Row(
            chip.widthIn(max = 260.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIndicator(span, style.dot)
            Text(
                displayTitle(span),
                color = Mist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (muted) TextDecoration.LineThrough else null,
            )
            if (amount != null) Text(amount, color = Mist, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
    if (card) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, with(density) { (CHIP_DP + 4.dp).roundToPx() }),
            onDismissRequest = { card = false },
        ) {
            Row(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111215))
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cover != null) {
                    RemoteImage(
                        cover,
                        Modifier
                            .size(width = if (span.source == "youtube") 112.dp else 64.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(displayTitle(span), color = Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    spanSubtitle(span)?.let { Text(it, color = SmokeDark, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(formatTime(span.startMs), color = SmokeDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpanBlock(placed: PlacedSpan, hasChildren: Boolean, colWidth: Dp, onSelect: (Span) -> Unit) {
    val span = placed.span
    val style = spanStyle(span)
    val inset = placed.depth * INDENT_DP + 3
    val heightDp = maxOf(placed.height * PX_PER_MIN - 2, 22.0).dp
    val amount = formatAmount(span)
    val estimated = isEstimated(span)
    val muted = span.status == SpanStatus.Cancelled
    val border = when (span.status) {
        SpanStatus.Active -> BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
        SpanStatus.Failed -> BorderStroke(1.dp, CoralPulse)
        else -> BorderStroke(1.dp, style.border)
    }
    val bg = if (hasChildren) lerp(Color(0xFF111215), style.bg, 0.6f) else style.bg
    val shape = RoundedCornerShape(8.dp)
    val width = colWidth * placed.width.toFloat() - (inset + 3).dp
    val dashed = Modifier.drawBehind {
        drawRoundRect(
            color = style.border,
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        )
    }
    val timeText = (if (estimated) "≈ " else "") + formatTime(span.startMs) + (span.endMs?.let { " – ${formatTime(it)}" } ?: "")
    val canFitTwoLines = heightDp >= 38.dp && !hasChildren

    Box(
        modifier = Modifier
            .offset(x = colWidth * placed.left.toFloat() + inset.dp, y = (placed.top * PX_PER_MIN + 2).dp)
            .alpha(if (muted) 0.4f else 1f)
            .width(width)
            .height(heightDp)
            .clip(shape)
            .background(bg)
            .then(if (estimated) dashed else Modifier.border(border, shape))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(span) }
            .padding(horizontal = 8.dp, vertical = if (canFitTwoLines) 5.dp else 2.dp),
        contentAlignment = if (canFitTwoLines) Alignment.TopStart else Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                maxLines = if (canFitTwoLines) 2 else 1,
            ) {
                Text(
                    text = displayTitle(span),
                    color = Mist,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (muted) TextDecoration.LineThrough else null,
                )
                Text(
                    text = timeText,
                    color = style.subtext,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (amount != null) {
                    Text(
                        text = amount,
                        color = Mist,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
            CategoryIndicator(span, style.dot)
        }
    }
}
