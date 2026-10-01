package `in`.voxagent.mobile.spans

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.ElectricSky
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ── Pixel constants (matching desktop HOUR_PX = 56) ─────────────────────────
private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 48.dp
private const val MIN_BLOCK_MINUTES = 25

// ── View mode ────────────────────────────────────────────────────────────────
enum class SpanViewMode { Day, Week, Month }

// ── SpanScreen ───────────────────────────────────────────────────────────────

private const val STALE_REFRESH_INTERVAL_MS = 3 * 60 * 1000L

@Composable
fun SpanScreen(
    token: String,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(SpanViewMode.Day) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var spans by remember { mutableStateOf<List<Span>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Span?>(null) }
    val scope = rememberCoroutineScope()

    // compute the day range for this view
    val days: List<LocalDate> = remember(anchor, mode) {
        when (mode) {
            SpanViewMode.Day -> listOf(anchor)
            SpanViewMode.Week -> (0..6).map { anchor.with(java.time.DayOfWeek.MONDAY).plusDays(it.toLong()) }
            SpanViewMode.Month -> {
                val first = anchor.withDayOfMonth(1)
                val startOffset = first.dayOfWeek.value % 7  // Sun=0
                val gridStart = first.minusDays(startOffset.toLong())
                val last = anchor.withDayOfMonth(anchor.lengthOfMonth())
                val endOffset = (6 - last.dayOfWeek.value % 7)
                val gridEnd = last.plusDays(endOffset.toLong())
                generateSequence(gridStart) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(gridEnd) }
                    .toList()
            }
        }
    }

    suspend fun refresh(showSpinner: Boolean) {
        if (showSpinner) loading = true
        error = null
        try {
            val from = days.first().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            val to = days.last().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
            spans = SpanApi.listSpans(from = from, to = to, bearerToken = token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            if (showSpinner) loading = false
        }
    }

    // Reload whenever the visible range changes.
    LaunchedEffect(days.first(), days.last(), token) {
        refresh(showSpinner = true)
    }

    // Data goes stale in the background (SMS sync, other devices): refresh
    // silently whenever this tab becomes active again, the app returns to the
    // foreground, or every few minutes while it stays open.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(active, days.first(), days.last(), token) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refresh(showSpinner = false)
            while (true) {
                delay(STALE_REFRESH_INTERVAL_MS)
                refresh(showSpinner = false)
            }
        }
    }

    fun goBack() {
        anchor = when (mode) {
            SpanViewMode.Day -> anchor.minusDays(1)
            SpanViewMode.Week -> anchor.minusWeeks(1)
            SpanViewMode.Month -> anchor.minusMonths(1)
        }
    }

    fun goForward() {
        anchor = when (mode) {
            SpanViewMode.Day -> anchor.plusDays(1)
            SpanViewMode.Week -> anchor.plusWeeks(1)
            SpanViewMode.Month -> anchor.plusMonths(1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080A0D))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 80.dp),  // clear the floating bottom nav
    ) {
        // ── Header ────────────────────────────────────────────────────────
        SpanHeader(
            anchor = anchor,
            mode = mode,
            days = days,
            loading = loading,
            onPrev = ::goBack,
            onNext = ::goForward,
            onModeChange = { mode = it },
            onRefresh = { scope.launch { refresh(showSpinner = true) } },
        )

        if (error != null) {
            Text(
                text = error!!,
                color = CoralPulse,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0A0A))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // ── Body ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when (mode) {
                SpanViewMode.Month -> SpanMonthGrid(
                    anchor = anchor,
                    days = days,
                    spans = spans,
                    onSelectSpan = { selected = it },
                    onSelectDay = {
                        anchor = it
                        mode = SpanViewMode.Day
                    },
                )
                else -> SpanDayWeekGrid(
                    days = days,
                    spans = spans,
                    onSelectSpan = { selected = it },
                )
            }
        }
    }

    // Span detail bottom sheet
    if (selected != null) {
        SpanDetailSheet(
            span = selected!!,
            token = token,
            onDismiss = { selected = null },
            onDeleted = {
                selected = null
                scope.launch {
                    val from = days.first().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
                    val to = days.last().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
                    spans = try { SpanApi.listSpans(from = from, to = to, bearerToken = token) } catch (e: CancellationException) { throw e } catch (_: Exception) { spans }
                }
            },
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun SpanHeader(
    anchor: LocalDate,
    mode: SpanViewMode,
    days: List<LocalDate>,
    loading: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onModeChange: (SpanViewMode) -> Unit,
    onRefresh: () -> Unit,
) {
    val rangeLabel = remember(mode, anchor, days) {
        when (mode) {
            SpanViewMode.Day -> anchor.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
            SpanViewMode.Week, SpanViewMode.Month -> {
                val start = days.first().format(DateTimeFormatter.ofPattern("MMM d"))
                val end = days.last().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                "$start – $end"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.85f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Date badge + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Mini calendar badge (matching desktop)
                Column(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = anchor.month.getDisplayName(TextStyle.SHORT, androidx.compose.ui.text.intl.Locale.current.platformLocale).uppercase(),
                        color = CoralPulse,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        lineHeight = 9.sp,
                    )
                    Text(
                        text = anchor.dayOfMonth.toString(),
                        color = PureWhite,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp,
                    )
                }

                Column {
                    Text(
                        text = anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        color = PureWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = rangeLabel,
                        color = Smoke,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    )
                }

                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = Smoke,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Manual refresh
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !loading,
                            onClick = onRefresh,
                        ),
                ) {
                    Text(
                        text = "⟳",
                        color = if (loading) Smoke.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }

                // Prev / Next nav
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Obsidian)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPrev,
                            ),
                    ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.7f, 0f)
                            lineTo(size.width * 0.2f, size.height * 0.5f)
                            lineTo(size.width * 0.7f, size.height)
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.7f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(18.dp)
                        .background(BorderSubtle),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onNext,
                        ),
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.3f, 0f)
                            lineTo(size.width * 0.8f, size.height * 0.5f)
                            lineTo(size.width * 0.3f, size.height)
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.7f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day / Week / Month segmented control
        SpanModeSegment(mode = mode, onModeChange = onModeChange)
    }
}

@Composable
private fun SpanModeSegment(
    mode: SpanViewMode,
    onModeChange: (SpanViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SpanViewMode.entries.forEach { tab ->
            val active = mode == tab
            val bgColor by animateColorAsState(
                targetValue = if (active) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                animationSpec = tween(200),
                label = "tab_bg",
            )
            val textColor by animateColorAsState(
                targetValue = if (active) PureWhite else Smoke,
                animationSpec = tween(200),
                label = "tab_text",
            )
            Text(
                text = tab.name,
                color = textColor,
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onModeChange(tab) },
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }
}

// ── Day / Week time grid ──────────────────────────────────────────────────────

@Composable
private fun SpanDayWeekGrid(
    days: List<LocalDate>,
    spans: List<Span>,
    onSelectSpan: (Span) -> Unit,
) {
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
    val gutterWidthPx = with(density) { GUTTER_WIDTH.toPx() }

    // Placed spans per day
    val perDay = remember(days, spans) {
        days.map { day ->
            val placed = layoutSpansForDay(spans, day, hourHeightPx)
            day to placed
        }
    }

    // All-day spans
    val allDay = remember(spans, days) {
        spans.filter { s ->
            val b = spanBoundsMillis(s) ?: return@filter false
            b.second - b.first >= 24L * 60 * 60 * 1000
        }
    }

    // Current time (updated every minute)
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalTime.now()
        }
    }

    // Scroll to current hour (or earliest span)
    val scrollState = rememberScrollState()
    val totalHeightPx = hourHeightPx * 24

    LaunchedEffect(days.first()) {
        val earliest = spans
            .mapNotNull { s -> s.startAt?.let { parseIso(it)?.toLocalTime() } }
            .minByOrNull { it.toSecondOfDay() }
        val scrollHour = earliest?.hour?.minus(1) ?: (now.hour - 2)
        scrollState.scrollTo((max(0, scrollHour) * hourHeightPx).roundToInt())
    }

    val nowTopFraction = (now.hour * 60 + now.minute).toFloat() / (24 * 60)

    Column(modifier = Modifier.fillMaxSize()) {
        // Column headers
        if (days.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink.copy(alpha = 0.75f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(0.dp),
                    ),
            ) {
                Spacer(modifier = Modifier.width(GUTTER_WIDTH))
                days.forEach { day ->
                    val isToday = day == LocalDate.now()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, androidx.compose.ui.text.intl.Locale.current.platformLocale).uppercase(),
                            color = if (isToday) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 0.5.sp,
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .then(
                                    if (isToday) Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                    else Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                ),
                        ) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                color = if (isToday) PureWhite else Color.White.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // All-day band
        if (allDay.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Ink)
                    .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(0.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(modifier = Modifier.padding(start = GUTTER_WIDTH)) {
                    allDay.forEach { span ->
                        val dot = categoryDotColor(span.category, span.schemaColorToken)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(categoryBgColor(span.category, span.schemaColorToken)),
                            modifier = Modifier
                                .padding(end = 3.dp, top = 3.dp, bottom = 3.dp)
                                .clickable { onSelectSpan(span) },
                        ) {
                            Text(
                                text = span.title,
                                color = PureWhite,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "all day",
                    color = Smoke,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // Main scrollable time grid
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val gridWidth = maxWidth
            val dayColumnWidth: Dp = (gridWidth - GUTTER_WIDTH) / days.size

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                // The whole grid at fixed height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HOUR_HEIGHT * 24),
                ) {
                    // ── Hour grid + gutter labels ──────────────────────────
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gutterPx = GUTTER_WIDTH.toPx()
                        val colW = if (days.isNotEmpty()) (size.width - gutterPx) / days.size else (size.width - gutterPx)
                        val hH = HOUR_HEIGHT.toPx()

                        // horizontal hour lines
                        for (h in 0..24) {
                            val y = h * hH
                            val isMajor = h % 6 == 0
                            drawLine(
                                color = Color.White.copy(alpha = if (isMajor) 0.14f else 0.07f),
                                start = Offset(gutterPx, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (isMajor) 0.8.dp.toPx() else 0.5.dp.toPx(),
                            )
                        }

                        // half-hour dashed lines
                        for (h in 0..23) {
                            val y = h * hH + hH * 0.5f
                            drawLine(
                                color = Color.White.copy(alpha = 0.03f),
                                start = Offset(gutterPx, y),
                                end = Offset(size.width, y),
                                strokeWidth = 0.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx())),
                            )
                        }

                        // gutter vertical separator line
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(gutterPx, 0f),
                            end = Offset(gutterPx, size.height),
                            strokeWidth = 0.5.dp.toPx(),
                        )

                        // vertical column separators (multi-day)
                        for (d in 1 until days.size) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(gutterPx + colW * d, 0f),
                                end = Offset(gutterPx + colW * d, size.height),
                                strokeWidth = 0.5.dp.toPx(),
                            )
                        }
                    }

                    // Gutter hour labels
                    Column(modifier = Modifier.width(GUTTER_WIDTH)) {
                        (0..23).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(HOUR_HEIGHT)
                                    .padding(end = 6.dp),
                                contentAlignment = Alignment.TopEnd,
                            ) {
                                if (h > 0) {
                                    Text(
                                        text = LocalTime.of(h, 0)
                                            .format(DateTimeFormatter.ofPattern("h a")),
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        modifier = Modifier.offset(y = (-6).dp),
                                    )
                                }
                            }
                        }
                    }

                    // Span blocks drawn on top of grid
                    perDay.forEachIndexed { colIndex, (day, placed) ->
                        val isToday = day == LocalDate.now()
                        val colLeft = GUTTER_WIDTH + dayColumnWidth * colIndex

                        // Today highlight
                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .offset(x = colLeft)
                                    .width(dayColumnWidth)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.04f)),
                            )
                        }

                        placed.forEach { p ->
                            val spanLeft = colLeft + dayColumnWidth * p.left
                            val spanWidth = dayColumnWidth * p.width
                            val spanTop = p.topFraction * totalHeightPx
                            val spanHeight = p.heightFraction * totalHeightPx

                            with(density) {
                                SpanBlock(
                                    placed = p,
                                    modifier = Modifier
                                        .offset(
                                            x = spanLeft + 2.dp,
                                            y = spanTop.toDp() + 1.dp,
                                        )
                                        .width(spanWidth - 4.dp)
                                        .height(max(spanHeight, with(density) { 18.dp.toPx() }).toDp() - 2.dp),
                                    onClick = { onSelectSpan(p.span) },
                                )
                            }
                        }
                    }

                    // Current time laser line
                    val nowOffset = HOUR_HEIGHT * (now.hour + now.minute / 60f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = nowOffset)
                            .drawBehind {
                                drawLine(
                                    color = CoralPulse.copy(alpha = 0.7f),
                                    start = Offset(gutterWidthPx, 0f),
                                    end = Offset(size.width, 0f),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                                    ),
                                )
                                // Dot on left
                                drawCircle(
                                    color = CoralPulse,
                                    radius = 3.dp.toPx(),
                                    center = Offset(gutterWidthPx, 0f),
                                )
                            },
                    )
                }
            }
        }
    }
}

// ── Span block (calendar event chip) ─────────────────────────────────────────

@Composable
private fun SpanBlock(
    placed: PlacedSpan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val span = placed.span
    val dotColor = Color(categoryDotColor(span.category, span.schemaColorToken))
    val bgColor = Color(categoryBgColor(span.category, span.schemaColorToken))
    val borderColor = dotColor.copy(alpha = 0.35f)

    val startLabel = span.startAt?.let { parseIso(it)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("h:mm a")) } ?: ""
    val endLabel = span.endAt?.let { parseIso(it)?.toLocalTime()?.format(DateTimeFormatter.ofPattern("h:mm a")) } ?: ""

    val isShort = placed.heightFraction * 24 * 60 < 35

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        if (isShort) {
            // Short chip: single-line title + dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = span.title,
                    color = PureWhite,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CategoryIndicator(span = span, dotColor = dotColor, dotSize = 5.dp)
            }
        } else {
            // Full chip: title + time
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = span.title,
                        color = PureWhite,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CategoryIndicator(span = span, dotColor = dotColor, dotSize = 6.dp)
                }
                if (startLabel.isNotEmpty()) {
                    Text(
                        text = if (endLabel.isNotEmpty()) "$startLabel – $endLabel" else startLabel,
                        color = dotColor.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

// ── Month grid ────────────────────────────────────────────────────────────────

@Composable
private fun SpanMonthGrid(
    anchor: LocalDate,
    days: List<LocalDate>,
    spans: List<Span>,
    onSelectSpan: (Span) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    val spansForDay: (LocalDate) -> List<Span> = { day ->
        spans.filter { s ->
            val start = s.startAt?.let { parseIso(it)?.toLocalDate() }
            start == day
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Weekday header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink.copy(alpha = 0.75f))
                .padding(vertical = 6.dp),
        ) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { label ->
                Text(
                    text = label,
                    color = Smoke,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Month cells
        val rows = days.chunked(7)
        rows.forEach { week ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                week.forEach { day ->
                    val isCurrentMonth = day.month == anchor.month
                    val isToday = day == LocalDate.now()
                    val daySpans = spansForDay(day)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(0.dp))
                            .background(if (isToday) Color.White.copy(alpha = 0.015f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelectDay(day) },
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            // Day number
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .then(
                                        if (isToday) Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                        else Modifier.padding(vertical = 1.dp),
                                    )
                                    .align(Alignment.End),
                            ) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    color = when {
                                        isToday -> PureWhite
                                        isCurrentMonth -> Color.White.copy(alpha = 0.8f)
                                        else -> Color.White.copy(alpha = 0.25f)
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                            }

                            // Span chips (up to 2)
                            daySpans.take(2).forEach { span ->
                                val dotColor = Color(categoryDotColor(span.category, span.schemaColorToken))
                                val bgColor = Color(categoryBgColor(span.category, span.schemaColorToken))
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = bgColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onSelectSpan(span) },
                                ) {
                                    Text(
                                        text = span.title,
                                        color = PureWhite,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                    )
                                }
                            }

                            // Overflow count
                            if (daySpans.size > 2) {
                                Text(
                                    text = "+${daySpans.size - 2} more",
                                    color = Smoke,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Span detail bottom sheet ──────────────────────────────────────────────────

@Composable
private fun SpanDetailSheet(
    span: Span,
    token: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val dotColor = Color(categoryDotColor(span.category, span.schemaColorToken))
    val startLabel = span.startAt?.let { parseIso(it)?.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")) } ?: "—"
    val endLabel = span.endAt?.let { parseIso(it)?.format(DateTimeFormatter.ofPattern("h:mm a")) } ?: ""

    // Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF0D0E12),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category dot + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = span.title,
                        color = PureWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Time range
                Text(
                    text = if (endLabel.isNotEmpty()) "$startLabel – $endLabel" else startLabel,
                    color = Smoke,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )

                // Category badge
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(dotColor.copy(alpha = 0.15f))
                        .border(0.5.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = span.category,
                        color = dotColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }

                // Notes
                if (span.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = span.notes,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }

                // Error
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error!!, color = CoralPulse, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Delete button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A0A0A))
                        .border(1.dp, CoralPulse.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable(enabled = !busy) {
                            scope.launch {
                                busy = true
                                try {
                                    SpanApi.deleteSpan(span.id, token)
                                    onDeleted()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (busy) "Deleting…" else "Delete Span",
                        color = CoralPulse,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ── Layout engine (mirrors span-layout.ts) ───────────────────────────────────

internal fun spanBoundsMillis(span: Span): Pair<Long, Long>? {
    val startIso = span.startAt ?: return null
    val start = parseIso(startIso)?.toInstant(ZoneId.systemDefault())?.toEpochMilli() ?: return null
    val end = span.endAt?.let { parseIso(it)?.toInstant(ZoneId.systemDefault())?.toEpochMilli() } ?: start
    return start to maxOf(start, end)
}

internal fun parseIso(iso: String): ZonedDateTime? = try {
    ZonedDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
} catch (_: Exception) { null }

private fun ZonedDateTime.toInstant(zone: ZoneId): java.time.Instant = this.toInstant()

/** Place spans in a single-day column, returning [PlacedSpan] with fractional positions. */
internal fun layoutSpansForDay(spans: List<Span>, day: LocalDate, hourHeightPx: Float): List<PlacedSpan> {
    val dayStartMs = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEndMs = dayStartMs + 24L * 60 * 60 * 1000
    val dayDurationMs = dayEndMs - dayStartMs

    data class Entry(val span: Span, val startMs: Long, val endMs: Long, val isInstant: Boolean)

    val entries = spans.mapNotNull { span ->
        val b = spanBoundsMillis(span) ?: return@mapNotNull null
        // filter to this day
        if (b.second <= dayStartMs || b.first >= dayEndMs) return@mapNotNull null
        val clampedStart = maxOf(b.first, dayStartMs)
        val clampedEnd = minOf(b.second, dayEndMs)
        val minDurationMs = MIN_BLOCK_MINUTES * 60_000L
        val effectiveEnd = maxOf(clampedEnd, clampedStart + minDurationMs)
        Entry(span, clampedStart, effectiveEnd, b.second - b.first < 60_000)
    }.sortedWith(compareBy({ it.startMs }, { -(it.endMs - it.startMs) }))

    if (entries.isEmpty()) return emptyList()

    // Simple greedy column packing (mirrors TS pack())
    data class ColEntry(val entry: Entry, var col: Int)

    val placed = mutableListOf<ColEntry>()
    val columnEnds = mutableListOf<Long>()

    for (e in entries) {
        // find first free column
        val col = columnEnds.indexOfFirst { it <= e.startMs }.takeIf { it >= 0 }
            ?: run { columnEnds.add(0L); columnEnds.lastIndex }
        columnEnds[col] = e.endMs
        placed.add(ColEntry(e, col))
    }

    val totalCols = columnEnds.size

    return placed.map { ce ->
        val topFraction = (ce.entry.startMs - dayStartMs).toFloat() / dayDurationMs
        val heightFraction = (ce.entry.endMs - ce.entry.startMs).toFloat() / dayDurationMs
        val leftFraction = ce.col.toFloat() / totalCols
        val widthFraction = 1f / totalCols
        PlacedSpan(
            span = ce.entry.span,
            topFraction = topFraction,
            heightFraction = heightFraction,
            left = leftFraction,
            width = widthFraction,
            isInstant = ce.entry.isInstant,
        )
    }
}
