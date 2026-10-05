package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.formatTime
import `in`.voxagent.mobile.spans.isAllDay
import `in`.voxagent.mobile.spans.spansOnDay
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.kit.VoxEmpty
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekAgenda(
    days: List<LocalDate>,
    selected: LocalDate,
    spans: List<Span>,
    onSelectDay: (LocalDate) -> Unit,
    onSelectSpan: (Span) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val today = LocalDate.now()
    val daySpans = remember(spans, selected) { spansOnDay(spans, selected) }
    Column(
        Modifier.fillMaxSize().pointerInput(days) {
            var dx = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dx > 160f) onPrev() else if (dx < -160f) onNext()
                    dx = 0f
                },
                onDragCancel = { dx = 0f },
            ) { _, delta -> dx += delta }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { day ->
                val isToday = day == today
                val isSel = day == selected
                val has = spansOnDay(spans, day).isNotEmpty()
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelectDay(day) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), color = SmokeDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(if (isSel) Mist else if (isToday) BorderSubtle else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(day.dayOfMonth.toString(), color = if (isSel) Obsidian else Mist, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (has) GraphiteDark else Color.Transparent))
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = BorderSubtle)
        if (daySpans.isEmpty()) {
            VoxEmpty("Nothing planned")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(daySpans, key = { it.id }) { span -> AgendaRow(span, onSelectSpan) }
            }
        }
    }
}

@Composable
private fun AgendaRow(span: Span, onSelect: (Span) -> Unit) {
    val style = spanStyle(span)
    val muted = span.status == SpanStatus.Cancelled
    val amount = formatAmount(span)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(style.bg).border(1.dp, style.border, RoundedCornerShape(12.dp)).clickable { onSelect(span) }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(74.dp)) {
            if (isAllDay(span)) {
                Text("All day", color = style.subtext, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text(formatTime(span.startMs), color = style.subtext, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                span.endMs?.let { Text(formatTime(it), color = SmokeDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(displayTitle(span), color = Mist, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (muted) TextDecoration.LineThrough else null)
            if (span.status != SpanStatus.Planned) Text(span.status.label, color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (amount != null) Text(amount, color = Mist, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        CategoryIndicator(span, style.dot, dot = 8.dp)
    }
}
