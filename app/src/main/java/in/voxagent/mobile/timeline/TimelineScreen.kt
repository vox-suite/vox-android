package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.voxagent.mobile.net.LiveHub
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.ui.VoxDarkScreen
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.kit.VoxErrorBar
import `in`.voxagent.mobile.ui.kit.VoxSegmented
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimelineScreen(
    token: () -> String?,
    bottomInset: Dp,
    onOpenSpan: (Span) -> Unit,
    reloadSignal: Int,
    onCollections: (List<SpanCollection>) -> Unit,
) {
    val vm: TimelineViewModel = viewModel(factory = viewModelFactory { initializer { TimelineViewModel(token) } })
    val ui by vm.ui.collectAsState()

    LaunchedEffect(reloadSignal) { if (reloadSignal > 0) vm.reload() }
    LaunchedEffect(ui.collections) { onCollections(ui.collections) }
    LaunchedEffect(Unit) {
        val live = LiveHub.get(token)
        live.start()
        live.events.collect { event ->
            if (event.type.startsWith("span_") || event.type == "live_reconnected") vm.reload()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            vm.reload()
        }
    }

    VoxDarkScreen {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomInset)) {
            Header(ui, vm)
            CollectionChips(ui, vm)
            if (ui.error.isNotEmpty()) VoxErrorBar(ui.error, onRetry = vm::reload)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.mode) {
                    ViewMode.Day -> DayGrid(ui.anchor, ui.spans, onOpenSpan)
                    ViewMode.Week -> WeekAgenda(ui.days, ui.selectedDay, ui.spans, vm::selectDay, onOpenSpan, vm::previous, vm::next)
                    ViewMode.Month -> MonthGrid(ui.anchor, ui.spans, vm::openDay)
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun Header(ui: TimelineUi, vm: TimelineViewModel) {
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }
    val titleFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val shortFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val shortYearFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()) }
    val days = ui.days
    val range = when (ui.mode) {
        ViewMode.Day -> ui.anchor.format(dayFmt)
        else -> "${days.first().format(shortFmt)} – ${days.last().format(shortYearFmt)}"
    }
    val badgeTextStyle = remember {
        TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
    val titleTextStyle = remember {
        TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Obsidian)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = ui.anchor.format(monthFmt).uppercase(),
                    color = CoralPulse,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    lineHeight = 10.sp,
                    style = badgeTextStyle,
                )
                Text(
                    text = ui.anchor.dayOfMonth.toString(),
                    color = Mist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    style = badgeTextStyle,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = ui.collection?.name ?: ui.anchor.format(titleFmt),
                    color = Mist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    style = titleTextStyle,
                    maxLines = 1,
                )
                Text(
                    text = range,
                    color = SmokeDark,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    style = titleTextStyle,
                    maxLines = 1,
                )
            }
            ui.collection?.let { Text(it.kind, color = GraphiteDark, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
            Glyph(if (ui.loading) "…" else "↻") { vm.reload() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph("‹") { vm.previous() }
            Glyph("›") { vm.next() }
            Text("Today", color = Mist, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.today() }.padding(horizontal = 10.dp, vertical = 6.dp))
            Box(Modifier.weight(1f))
            VoxSegmented(
                options = listOf(ViewMode.Day to "Day", ViewMode.Week to "Week", ViewMode.Month to "Month"),
                selected = ui.mode,
                onSelect = vm::setMode,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun Glyph(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Obsidian).border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Mist, fontSize = 18.sp) }
}

@Composable
private fun CollectionChips(ui: TimelineUi, vm: TimelineViewModel) {
    if (ui.collections.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VoxChip("All", ui.collectionId == null) { vm.selectCollection(null) }
        ui.collections.forEach { c -> VoxChip(c.name, ui.collectionId == c.id) { vm.selectCollection(c.id) } }
    }
}
