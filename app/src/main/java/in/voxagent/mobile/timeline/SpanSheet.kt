package `in`.voxagent.mobile.timeline

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.spans.SpansApi
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.isEstimated
import `in`.voxagent.mobile.spans.isProviderOwned
import `in`.voxagent.mobile.spans.sourceLabel
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface SheetTarget {
    data class Edit(val span: Span) : SheetTarget
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
        save(
            buildJsonObject {
                put(
                    "start_at",
                    startMs?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull,
                )
                put(
                    "end_at",
                    endMs?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull,
                )
            }
        )
    }

    fun pick(current: Long?, onPicked: (Long) -> Unit) {
        val base =
            current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
                ?: LocalDateTime.now()
        DatePickerDialog(
                context,
                { _, y, m, d ->
                    TimePickerDialog(
                            context,
                            { _, h, min ->
                                onPicked(
                                    LocalDateTime.of(y, m + 1, d, h, min)
                                        .atZone(zone)
                                        .toInstant()
                                        .toEpochMilli()
                                )
                            },
                            base.hour,
                            base.minute,
                            false,
                        )
                        .show()
                },
                base.year,
                base.monthValue - 1,
                base.dayOfMonth,
            )
            .show()
    }

    fun toggleCollection(id: String, member: Boolean) {
        val bearer = token() ?: return
        busy = true
        scope.launch {
            try {
                SpansApi.setSpanCollection(bearer, id, span.id, member)
                span =
                    span.copy(
                        collectionIds =
                            if (member) span.collectionIds + id else span.collectionIds - id
                    )
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
            modifier =
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        ) {
            SpanSheetBackdrop(span)
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Mist.copy(alpha = 0.30f))
                    )
                }

                SpanSheetHeader(
                    span,
                    providerOwned,
                    infoOpen,
                    { infoOpen = !infoOpen },
                    onClose,
                    ::save,
                )

                val amount = formatAmount(span)
                if (amount != null || isEstimated(span)) {
                    Row(
                        modifier = Modifier.padding(start = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        amount?.let {
                            Text(
                                it,
                                color = GraphiteDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (isEstimated(span))
                            Text(
                                "Estimated time",
                                color = GraphiteDark,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                    }
                }

                if (infoOpen) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "Title, time and status are managed by ${sourceLabel(span)}. You can edit notes, category and collections.",
                            color = GraphiteDark,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                Column(Modifier.padding(top = 6.dp)) {
                    SpanMetadata(span, providerOwned, busy, ::save, ::pick, ::saveTimes)

                    AttributeRow(
                        icon = Icons.Outlined.Description,
                        label = "Notes",
                        verticalAlignment = Alignment.Top,
                    ) {
                        BasicTextField(
                            value = notesDraft,
                            onValueChange = { notesDraft = it },
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp),
                                    )
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
                                        if (!busy)
                                            toggleCollection(c.id, c.id !in span.collectionIds)
                                    }
                                }
                            }
                        }
                    }

                    val createdMs = span.createdMs
                    if (createdMs != null) {
                        val instant = Instant.ofEpochMilli(createdMs)
                        AttributeRow(icon = Icons.Outlined.CalendarMonth, label = "Created") {
                            Text(
                                text =
                                    "${dateFormat.format(instant)} at ${timeOfDayFormat.format(instant)}",
                                color = SmokeDark,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Text(error, color = CoralPulse, fontSize = 12.sp)
                }

                Row(
                    modifier =
                        Modifier.padding(top = 10.dp)
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
