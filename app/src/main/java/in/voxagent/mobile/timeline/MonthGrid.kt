package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.monthGridDays
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.spansOnDay
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import java.time.LocalDate

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun MonthGrid(anchor: LocalDate, spans: List<Span>, onSelectDay: (LocalDate) -> Unit) {
    val grid = remember(anchor) { monthGridDays(anchor) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            WEEKDAYS.forEach {
                Text(
                    it.uppercase(),
                    color = SmokeDark,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    val items = remember(spans, day) { spansOnDay(spans, day) }
                    val inMonth = day.month == anchor.month
                    Column(
                        Modifier.weight(1f)
                            .fillMaxSize()
                            .clickable { onSelectDay(day) }
                            .alpha(if (inMonth) 1f else 0.4f)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (day == today) BorderSubtle else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                color = Mist,
                                fontSize = 13.sp,
                                fontWeight =
                                    if (day == today) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            items.take(3).forEach { s ->
                                Box(
                                    Modifier.size(6.dp)
                                        .clip(CircleShape)
                                        .background(spanStyle(s).dot)
                                )
                            }
                        }
                        if (items.size > 3)
                            Text(
                                "+${items.size - 3}",
                                color = SmokeDark,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                    }
                }
            }
        }
    }
}
