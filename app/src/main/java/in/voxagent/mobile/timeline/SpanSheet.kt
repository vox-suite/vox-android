package `in`.voxagent.mobile.timeline

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.ExecutionType
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.SpansApi
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.zone
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxTextButton
import `in`.voxagent.mobile.ui.kit.VoxChip
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.voxFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed interface SheetTarget {
    data class Edit(val span: Span) : SheetTarget
    data class New(val day: LocalDate) : SheetTarget
}

private val CATEGORIES = listOf("todo", "meeting", "call", "meal", "expense", "ride", "travel", "visit", "reminder")
private val STATUSES = SpanStatus.entries

private enum class Execution(val label: String, val type: ExecutionType?) {
    LogOnly("Just log it", null),
    Autonomous("Do it for me", ExecutionType.Autonomous),
    Interactive("Do it, ask me first", ExecutionType.Interactive),
    Remind("Remind me", ExecutionType.ManualHuman),
}

private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zone)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpanSheet(
    target: SheetTarget,
    collections: List<SpanCollection>,
    token: () -> String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val span = (target as? SheetTarget.Edit)?.span
    val calendarOwned = span?.source == "google_calendar"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draftStart = remember(target) {
        (target as? SheetTarget.New)?.let { t ->
            val now = LocalDateTime.now()
            val base = if (t.day == LocalDate.now()) now.plusHours(1).withMinute(0).withSecond(0).withNano(0) else t.day.atTime(9, 0)
            base.atZone(zone).toInstant().toEpochMilli()
        }
    }
    var title by remember { mutableStateOf(span?.title ?: "") }
    var notes by remember { mutableStateOf(span?.notes ?: "") }
    var category by remember { mutableStateOf(span?.category ?: "todo") }
    var status by remember { mutableStateOf(span?.status ?: if ((draftStart ?: Long.MAX_VALUE) < System.currentTimeMillis()) SpanStatus.Done else SpanStatus.Planned) }
    var startMs by remember { mutableStateOf(span?.startMs ?: draftStart) }
    var endMs by remember { mutableStateOf(span?.endMs ?: draftStart?.plus(3_600_000L)) }
    var execution by remember { mutableStateOf(Execution.LogOnly) }
    var amount by remember { mutableStateOf(span?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var collectionIds by remember { mutableStateOf(span?.collectionIds ?: emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun pick(current: Long?, onPicked: (Long) -> Unit) {
        val base = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() } ?: LocalDateTime.now()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                onPicked(LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant().toEpochMilli())
            }, base.hour, base.minute, false).show()
        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
    }

    fun save() {
        val bearer = token() ?: return
        if (title.isBlank()) {
            error = "Give it a title"
            return
        }
        val s = startMs
        val e = endMs
        if (s != null && e != null && e < s) {
            error = "End must be after start"
            return
        }
        val parsedAmount = amount.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        busy = true
        error = ""
        scope.launch {
            try {
                val startJson = s?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull
                val endJson = e?.let { JsonPrimitive(Instant.ofEpochMilli(it).toString()) } ?: JsonNull
                if (span != null) {
                    SpansApi.updateSpan(bearer, span.id, buildJsonObject {
                        put("notes", notes)
                        put("category", category.trim().ifEmpty { "general" })
                        if (!calendarOwned) {
                            put("title", title.trim())
                            put("status", status.wire)
                            put("start_at", startJson)
                            put("end_at", endJson)
                        }
                    })
                    val before = span.collectionIds.toSet()
                    val after = collectionIds.toSet()
                    after.filter { it !in before }.forEach { SpansApi.setSpanCollection(bearer, it, span.id, true) }
                    before.filter { it !in after }.forEach { SpansApi.setSpanCollection(bearer, it, span.id, false) }
                } else {
                    SpansApi.createSpan(bearer, buildJsonObject {
                        put("title", title.trim())
                        put("status", status.wire)
                        put("start_at", startJson)
                        put("end_at", endJson)
                        put("notes", notes)
                        put("category", category.trim().ifEmpty { "general" })
                        put("execution_type", execution.type?.wire?.let { JsonPrimitive(it) } ?: JsonNull)
                        put(
                            "data",
                            if (parsedAmount != null) buildJsonObject { put("amount", parsedAmount); put("currency", "INR") } else JsonObject(emptyMap()),
                        )
                        put("collection_ids", buildJsonArray { collectionIds.forEach { add(JsonPrimitive(it)) } })
                    })
                }
                onSaved()
                onClose()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex.message ?: "Couldn't save"
                busy = false
            }
        }
    }

    fun remove() {
        val bearer = token() ?: return
        val id = span?.id ?: return
        busy = true
        scope.launch {
            try {
                SpansApi.deleteSpan(bearer, id)
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
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (span != null) "Edit span" else "New span", color = Mist, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (span != null) Text(span.source, color = SmokeDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                span?.let { formatAmount(it) }?.let { Text(it, color = GraphiteDark, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
            }
            if (calendarOwned) {
                Text("Title, time and status are managed by Google Calendar. You can edit local notes and collections.", color = GraphiteDark, fontSize = 12.sp)
            }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !calendarOwned && !busy, colors = voxFieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Start", startMs, !calendarOwned && !busy, Modifier.weight(1f)) { pick(startMs) { startMs = it } }
                TimeField("End", endMs, !calendarOwned && !busy, Modifier.weight(1f)) { pick(endMs ?: startMs) { endMs = it } }
            }
            if (!calendarOwned && (startMs != null || endMs != null)) {
                VoxTextButton("Clear times") {
                    startMs = null
                    endMs = null
                }
            }
            Text("Category", color = GraphiteDark, fontSize = 12.sp)
            OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, colors = voxFieldColors())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORIES.forEach { c -> VoxChip(c, category == c) { category = c } }
            }
            Text("Status", color = GraphiteDark, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STATUSES.forEach { s -> VoxChip(s.label, status == s) { if (!calendarOwned && !busy) status = s } }
            }
            if (span == null) {
                Text("Vox should", color = GraphiteDark, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Execution.entries.forEach { x -> VoxChip(x.label, execution == x) { execution = x } }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !busy,
                    colors = voxFieldColors(),
                )
            }
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2, enabled = !busy, colors = voxFieldColors())
            if (collections.isNotEmpty()) {
                Text("Collections", color = GraphiteDark, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    collections.forEach { c ->
                        VoxChip(c.name, c.id in collectionIds) {
                            collectionIds = if (c.id in collectionIds) collectionIds - c.id else collectionIds + c.id
                        }
                    }
                }
            }
            if (error.isNotEmpty()) Text(error, color = CoralPulse, fontSize = 12.sp)
            VoxPrimaryButton(if (busy) "Saving…" else if (span != null) "Save" else "Add") { if (!busy) save() }
            if (span != null) VoxTextButton("Delete", tone = CoralPulse) { if (!busy) remove() }
        }
    }
}

@Composable
private fun TimeField(label: String, ms: Long?, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release) onClick() }
    }
    OutlinedTextField(
        value = ms?.let { dateTimeFormat.format(Instant.ofEpochMilli(it)) } ?: "",
        onValueChange = {},
        modifier = modifier,
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        colors = voxFieldColors(),
        interactionSource = source,
    )
}
