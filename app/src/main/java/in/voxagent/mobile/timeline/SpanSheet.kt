package `in`.voxagent.mobile.timeline

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import `in`.voxagent.mobile.ui.theme.SmokeDark
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

private val CATEGORIES = listOf(
    "todo", "meeting", "call", "meal", "expense", "ride",
    "travel", "visit", "reminder", "music", "gaming", "video"
)

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()).withZone(zone)
private val timeOfDayFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)

private fun statusIcon(status: SpanStatus): ImageVector = when (status) {
    SpanStatus.Planned -> Icons.Outlined.CalendarToday
    SpanStatus.Active -> Icons.Outlined.PlayCircleOutline
    SpanStatus.WaitingUser -> Icons.Outlined.HourglassEmpty
    SpanStatus.Done -> Icons.Outlined.CheckCircle
    SpanStatus.Failed -> Icons.Outlined.Cancel
    SpanStatus.Cancelled -> Icons.Outlined.Block
}

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

private fun categoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "video" -> Icons.Outlined.Videocam
    "todo" -> Icons.Outlined.CheckCircleOutline
    "meeting" -> Icons.Outlined.People
    "call" -> Icons.Outlined.Phone
    "meal" -> Icons.Outlined.Restaurant
    "expense" -> Icons.Outlined.AccountBalanceWallet
    "ride" -> Icons.Outlined.DirectionsCar
    "travel" -> Icons.Outlined.Flight
    "visit" -> Icons.Outlined.Place
    "reminder" -> Icons.Outlined.Notifications
    "music" -> Icons.Outlined.MusicNote
    "gaming" -> Icons.Outlined.SportsEsports
    else -> @Suppress("DEPRECATION") Icons.Outlined.Label
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
        containerColor = Color(0xFF0C0D10),
        contentColor = Mist,
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            if (cover != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clipToBounds(),
                ) {
                    RemoteImage(
                        url = cover,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.15f)
                            .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                        alpha = 0.35f,
                        blur = true,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0C0D10).copy(alpha = 0.55f),
                                        Color(0xFF0C0D10).copy(alpha = 0.82f),
                                        Color(0xFF0C0D10).copy(alpha = 0.98f),
                                    ),
                                ),
                            ),
                    )
                }
            } else if (span.source != "spotify") {
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(style.dot.copy(alpha = 0.35f), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.85f,
                            ),
                        )
                    },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Drag handle pill overlaid on top of blurred cover
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Mist.copy(alpha = 0.30f)),
                    )
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(style.bg)
                            .border(BorderStroke(1.dp, style.border), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CategoryIndicator(span, style.dot, dot = 6.dp)
                    }
                    if (editingTitle) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BasicTextField(
                                value = titleDraft,
                                onValueChange = { titleDraft = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                textStyle = TextStyle(color = Mist, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                cursorBrush = SolidColor(Mist),
                                singleLine = true,
                            )
                            IconButton(
                                onClick = { commitTitle() },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Outlined.Check, contentDescription = "Save", tint = Mist, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { titleDraft = span.title; editingTitle = false },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = SmokeDark, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = displayTitle(span),
                                color = Mist,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!providerOwned) {
                                IconButton(
                                    onClick = { editingTitle = true },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit title",
                                        tint = SmokeDark,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (providerOwned) {
                        IconButton(
                            onClick = { infoOpen = !infoOpen },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Managed by ${sourceLabel(span)}",
                                tint = if (infoOpen) Mist else SmokeDark,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = SmokeDark,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                val amount = formatAmount(span)
                if (amount != null || isEstimated(span)) {
                    Row(
                        modifier = Modifier.padding(start = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        amount?.let { Text(it, color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        if (isEstimated(span)) Text("Estimated time", color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                if (infoOpen) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) {
                        Text(
                            "Title, time and status are managed by ${sourceLabel(span)}. You can edit notes, category and collections.",
                            color = GraphiteDark,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                // Attributes List
                Column(Modifier.padding(top = 6.dp)) {
                    // Status
                    AttributeRow(icon = statusIcon(span.status), label = "Status") {
                        SelectPill(
                            text = statusLabel(span.status),
                            icon = statusIcon(span.status),
                            iconTint = statusColor(span.status),
                            enabled = !providerOwned && !busy,
                            options = SpanStatus.entries.map { statusLabel(it) to statusIcon(it) },
                            onPick = { index ->
                                save(buildJsonObject { put("status", SpanStatus.entries[index].wire) })
                            },
                        )
                    }

                    // Category
                    AttributeRow(icon = categoryIcon(span.category), label = "Category") {
                        val options = if (span.category in CATEGORIES) CATEGORIES else listOf(span.category) + CATEGORIES
                        SelectPill(
                            text = span.category.replaceFirstChar { it.uppercase() },
                            icon = categoryIcon(span.category),
                            iconTint = Mist,
                            enabled = !busy,
                            options = options.map { it.replaceFirstChar { c -> c.uppercase() } to categoryIcon(it) },
                            onPick = { index ->
                                save(buildJsonObject { put("category", options[index]) })
                            },
                        )
                    }

                    // Start
                    AttributeRow(icon = Icons.Outlined.Schedule, label = "Start") {
                        DateTimeValue(
                            ms = span.startMs,
                            placeholder = "Add start",
                            enabled = !providerOwned && !busy,
                        ) {
                            pick(span.startMs) { picked ->
                                val end = span.endMs
                                val shifted = if (end != null && span.startMs != null) end + (picked - span.startMs!!) else end
                                saveTimes(picked, shifted)
                            }
                        }
                    }

                    // End
                    AttributeRow(icon = Icons.Outlined.Schedule, label = "End") {
                        DateTimeValue(
                            ms = span.endMs,
                            placeholder = "No end time",
                            enabled = !providerOwned && !busy,
                            onClear = if (!providerOwned && span.endMs != null) { { saveTimes(span.startMs, null) } } else null,
                        ) {
                            pick(span.endMs ?: span.startMs) { saveTimes(span.startMs, it) }
                        }
                    }

                    // Duration
                    val start = span.startMs
                    val end = span.endMs
                    if (start != null && end != null) {
                        duration(start, end)?.let {
                            AttributeRow(icon = Icons.Outlined.Timer, label = "Duration") {
                                Text(it, color = Mist, fontSize = 13.5.sp)
                            }
                        }
                    }

                    // Source
                    AttributeRow(icon = Icons.Outlined.Layers, label = "Source") {
                        Text(
                            text = sourceLabel(span),
                            color = Mist,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // Notes
                    AttributeRow(
                        icon = Icons.Outlined.Description,
                        label = "Notes",
                        verticalAlignment = Alignment.Top,
                    ) {
                        BasicTextField(
                            value = notesDraft,
                            onValueChange = { notesDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .onFocusChanged {
                                    if (!it.isFocused && notesDraft != span.notes) {
                                        save(buildJsonObject { put("notes", notesDraft) })
                                    }
                                },
                            textStyle = TextStyle(color = Mist, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(Mist),
                            decorationBox = { innerTextField ->
                                if (notesDraft.isEmpty()) {
                                    Text("Add notes", color = SmokeDark, fontSize = 13.5.sp)
                                }
                                innerTextField()
                            },
                            minLines = 1,
                            maxLines = 4,
                            enabled = !busy,
                        )
                    }

                    // Collections
                    if (collections.isNotEmpty()) {
                        AttributeRow(
                            icon = Icons.Outlined.FolderOpen,
                            label = "Collections",
                            verticalAlignment = Alignment.Top,
                        ) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                collections.forEach { c ->
                                    VoxChip(c.name, c.id in span.collectionIds) {
                                        if (!busy) toggleCollection(c.id, c.id !in span.collectionIds)
                                    }
                                }
                            }
                        }
                    }

                    // Created
                    val createdMs = span.createdMs
                    if (createdMs != null) {
                        val instant = Instant.ofEpochMilli(createdMs)
                        AttributeRow(icon = Icons.Outlined.CalendarMonth, label = "Created") {
                            Text(
                                text = "${dateFormat.format(instant)} at ${timeOfDayFormat.format(instant)}",
                                color = SmokeDark,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Text(error, color = CoralPulse, fontSize = 12.sp)
                }

                // Delete entry
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !busy) { remove() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = CoralPulse,
                    )
                    Text(
                        text = "Delete entry",
                        color = CoralPulse,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributeRow(
    icon: ImageVector,
    label: String,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = verticalAlignment,
    ) {
        Row(
            modifier = Modifier
                .width(115.dp)
                .then(
                    if (verticalAlignment == Alignment.Top) Modifier.padding(top = 8.dp) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = SmokeDark,
            )
            Text(
                text = label,
                color = SmokeDark,
                fontSize = 13.sp,
            )
        }
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun SelectPill(
    text: String,
    icon: ImageVector,
    iconTint: Color = Mist,
    enabled: Boolean,
    options: List<Pair<String, ImageVector>>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = iconTint,
            )
            Text(
                text = text,
                color = Mist,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SmokeDark,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF16171A),
            modifier = Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
        ) {
            options.forEachIndexed { index, (optText, optIcon) ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = optIcon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (optText == text) iconTint else SmokeDark,
                        )
                    },
                    text = {
                        Text(
                            optText,
                            color = if (optText == text) Mist else GraphiteDark,
                            fontSize = 13.5.sp,
                        )
                    },
                    onClick = {
                        open = false
                        onPick(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun DateTimeValue(
    ms: Long?,
    placeholder: String,
    enabled: Boolean,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ms == null) {
            Text(placeholder, color = SmokeDark, fontSize = 13.5.sp)
        } else {
            val instant = Instant.ofEpochMilli(ms)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Mist, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)) {
                        append(dateFormat.format(instant))
                    }
                    withStyle(SpanStyle(color = SmokeDark, fontSize = 13.5.sp)) {
                        append(" at ")
                        append(timeOfDayFormat.format(instant))
                    }
                },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (onClear != null && ms != null && enabled) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "Clear",
                color = SmokeDark,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClear() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
