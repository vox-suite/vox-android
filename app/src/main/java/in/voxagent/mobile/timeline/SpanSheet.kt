package `in`.voxagent.mobile.timeline

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.SpansApi
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.isEstimated
import `in`.voxagent.mobile.spans.isProviderOwned
import `in`.voxagent.mobile.spans.sourceLabel
import `in`.voxagent.mobile.spans.spanCover
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.kit.RemoteImage
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.VoxTextButton
import `in`.voxagent.mobile.ui.voxFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface SheetTarget {
    data class Edit(val span: Span) : SheetTarget
}

private val CATEGORIES = listOf("todo", "meeting", "call", "meal", "expense", "ride", "travel", "visit", "reminder", "music", "gaming", "video")

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()).withZone(zone)
private val timeOfDayFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)

private fun statusColor(status: SpanStatus): Color = when (status) {
    SpanStatus.Planned -> Color(0xFF7DD3FC)
    SpanStatus.Active -> Color(0xFFFCD34D)
    SpanStatus.WaitingUser -> Color(0xFFC4B5FD)
    SpanStatus.Done -> Color(0xFF6EE7B7)
    SpanStatus.Failed -> Color(0xFFF87171)
    SpanStatus.Cancelled -> Color(0xFFA1A1AA)
}

private fun statusLabel(status: SpanStatus) = when (status) {
    SpanStatus.Planned -> "Planned"
    SpanStatus.Active -> "In progress"
    SpanStatus.WaitingUser -> "Waiting for you"
    SpanStatus.Done -> "Done"
    SpanStatus.Failed -> "Failed"
    SpanStatus.Cancelled -> "Cancelled"
}

private fun duration(startMs: Long, endMs: Long): String? {
    val minutes = (endMs - startMs) / 60_000
    if (minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) (if (m > 0) "$h h $m min" else "$h h") else "$m min"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpanSheet(
    target: SheetTarget,
    collections: List<SpanCollection>,
    token: () -> String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val initial = (target as SheetTarget.Edit).span
    var span by remember(initial.id) { mutableStateOf(initial) }
    val providerOwned = isProviderOwned(span)
    val style = spanStyle(span)
    val cover = spanCover(span)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingTitle by remember { mutableStateOf(false) }
    var titleDraft by remember(initial.id) { mutableStateOf(initial.title) }
    var notesDraft by remember(initial.id) { mutableStateOf(initial.notes) }
    var infoOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun save(patch: JsonObject, after: (Span) -> Span = { it }) {
        val bearer = token() ?: return
        busy = true
        error = ""
        scope.launch {
            try {
                span = after(SpansApi.updateSpan(bearer, span.id, patch))
                onSaved()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message ?: "Couldn't save"
            } finally {
                busy = false
            }
        }
    }

    fun saveTimes(startMs: Long?, endMs: Long?) {
        if (startMs != null && endMs != null && endMs < startMs) {
            error = "End must be after start"
            return
        }
        save(buildJsonObject {
            put("start_at", startMs?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull)
            put("end_at", endMs?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull)
        })
    }

    fun pick(current: Long?, onPicked: (Long) -> Unit) {
        val base = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() } ?: LocalDateTime.now()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                onPicked(LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant().toEpochMilli())
            }, base.hour, base.minute, false).show()
        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
    }

    fun commitTitle() {
        val next = titleDraft.trim()
        editingTitle = false
        if (next.isEmpty()) {
            titleDraft = span.title
            return
        }
        if (next != span.title) save(buildJsonObject { put("title", next) })
    }

    fun toggleCollection(id: String, member: Boolean) {
        val bearer = token() ?: return
        busy = true
        scope.launch {
            try {
                SpansApi.setSpanCollection(bearer, id, span.id, member)
                span = span.copy(collectionIds = if (member) span.collectionIds + id else span.collectionIds - id)
                onSaved()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message ?: "Couldn't update"
            } finally {
                busy = false
            }
        }
    }

    fun remove() {
        val bearer = token() ?: return
        busy = true
        scope.launch {
            try {
                SpansApi.deleteSpan(bearer, span.id)
                onSaved()
                onClose()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message ?: "Couldn't delete"
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Obsidian,
        contentColor = Mist,
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (cover != null) {
                Box(Modifier.matchParentSize()) {
                    RemoteImage(cover, Modifier.fillMaxSize(), alpha = 0.3f)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.5f), Obsidian.copy(alpha = 0.75f), Obsidian.copy(alpha = 0.92f))),
                        ),
                    )
                }
            } else if (span.source != "spotify") {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(style.dot.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.9f,
                            ),
                        )
                    },
                )
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(style.bg).border(BorderStroke(1.dp, style.border), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { CategoryIndicator(span, style.dot, dot = 7.dp) }
                    Column(Modifier.weight(1f)) {
                        if (editingTitle) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(titleDraft, { titleDraft = it }, Modifier.weight(1f), singleLine = true, colors = voxFieldColors())
                                Glyph("✓") { commitTitle() }
                                Glyph("✕") { titleDraft = span.title; editingTitle = false }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(displayTitle(span), color = Mist, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                                if (!providerOwned) Glyph("✎") { editingTitle = true }
                            }
                        }
                        val amount = formatAmount(span)
                        if (amount != null || isEstimated(span)) {
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                amount?.let { Text(it, color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                                if (isEstimated(span)) Text("Estimated time", color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    if (providerOwned) Glyph("ⓘ") { infoOpen = !infoOpen }
                    Glyph("✕", onClick = onClose)
                }
                if (infoOpen) {
                    Text(
                        "Title, time and status are managed by ${sourceLabel(span)}. You can edit notes, category and collections.",
                        color = GraphiteDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(Modifier.padding(top = 10.dp)) {
                    AttributeRow("Status") {
                        MenuValue(
                            text = statusLabel(span.status),
                            leading = { Box(Modifier.size(8.dp).background(statusColor(span.status), RoundedCornerShape(50))) },
                            enabled = !providerOwned && !busy,
                            options = SpanStatus.entries.map { statusLabel(it) },
                            onPick = { index -> save(buildJsonObject { put("status", SpanStatus.entries[index].wire) }) },
                        )
                    }
                    AttributeRow("Category") {
                        val options = if (span.category in CATEGORIES) CATEGORIES else listOf(span.category) + CATEGORIES
                        MenuValue(
                            text = span.category.replaceFirstChar { it.uppercase() },
                            enabled = !busy,
                            options = options.map { it.replaceFirstChar { c -> c.uppercase() } },
                            onPick = { index -> save(buildJsonObject { put("category", options[index]) }) },
                        )
                    }
                    AttributeRow("Start") {
                        TimeValue(span.startMs, "Add start", !providerOwned && !busy) {
                            pick(span.startMs) { picked ->
                                val end = span.endMs
                                val shifted = if (end != null && span.startMs != null) end + (picked - span.startMs!!) else end
                                saveTimes(picked, shifted)
                            }
                        }
                    }
                    AttributeRow("End") {
                        Column {
                            TimeValue(span.endMs, "No end time", !providerOwned && !busy) { pick(span.endMs ?: span.startMs) { saveTimes(span.startMs, it) } }
                            if (span.endMs != null && !providerOwned) {
                                Text(
                                    "Clear",
                                    color = SmokeDark,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { saveTimes(span.startMs, null) }.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    val start = span.startMs
                    val end = span.endMs
                    if (start != null && end != null) {
                        duration(start, end)?.let { AttributeRow("Duration") { Text(it, color = Mist, fontSize = 14.sp) } }
                    }
                    AttributeRow("Source") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryIndicator(span, style.dot)
                            Text(sourceLabel(span), color = Mist, fontSize = 14.sp)
                        }
                    }
                    AttributeRow("Notes") {
                        OutlinedTextField(
                            notesDraft,
                            { notesDraft = it },
                            Modifier.fillMaxWidth().onFocusChanged {
                                if (!it.isFocused && notesDraft != span.notes) save(buildJsonObject { put("notes", notesDraft) })
                            },
                            placeholder = { Text("Add notes") },
                            minLines = 2,
                            enabled = !busy,
                            colors = voxFieldColors(),
                        )
                    }
                    if (collections.isNotEmpty()) {
                        AttributeRow("Collections") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                collections.forEach { c ->
                                    VoxChip(c.name, c.id in span.collectionIds) { if (!busy) toggleCollection(c.id, c.id !in span.collectionIds) }
                                }
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = CoralPulse, fontSize = 12.sp)
                VoxTextButton("Delete entry", tone = CoralPulse) { if (!busy) remove() }
            }
        }
    }
}

@Composable
private fun Glyph(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = SmokeDark, fontSize = 16.sp) }
}

@Composable
private fun AttributeRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = GraphiteDark, fontSize = 13.sp, modifier = Modifier.width(96.dp).padding(top = 2.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun MenuValue(
    text: String,
    enabled: Boolean,
    options: List<String>,
    onPick: (Int) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .alpha(if (enabled) 1f else 0.6f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(text, color = Mist, fontSize = 14.sp)
            if (enabled) Text("⌄", color = SmokeDark, fontSize = 14.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Obsidian) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option, color = Mist) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

@Composable
private fun TimeValue(ms: Long?, placeholder: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp),
    ) {
        if (ms == null) {
            Text(placeholder, color = SmokeDark, fontSize = 14.sp)
        } else {
            val instant = Instant.ofEpochMilli(ms)
            Text(dateFormat.format(instant), color = Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(timeOfDayFormat.format(instant), color = SmokeDark, fontSize = 12.sp)
        }
    }
}
