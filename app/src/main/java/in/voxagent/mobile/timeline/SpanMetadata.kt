package `in`.voxagent.mobile.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.sourceLabel
import `in`.voxagent.mobile.ui.theme.Mist
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
internal fun SpanMetadata(
    span: Span,
    providerOwned: Boolean,
    busy: Boolean,
    save: (JsonObject) -> Unit,
    pick: (Long?, (Long) -> Unit) -> Unit,
    saveTimes: (Long?, Long?) -> Unit,
) {
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

    AttributeRow(icon = categoryIcon(span.category), label = "Category") {
        val options =
            if (span.category in CATEGORIES) CATEGORIES else listOf(span.category) + CATEGORIES
        SelectPill(
            text = span.category.replaceFirstChar { it.uppercase() },
            icon = categoryIcon(span.category),
            iconTint = Mist,
            enabled = !busy,
            options =
                options.map { it.replaceFirstChar { c -> c.uppercase() } to categoryIcon(it) },
            onPick = { index -> save(buildJsonObject { put("category", options[index]) }) },
        )
    }

    AttributeRow(icon = Icons.Outlined.Schedule, label = "Start") {
        DateTimeValue(
            ms = span.startMs,
            placeholder = "Add start",
            enabled = !providerOwned && !busy,
        ) {
            pick(span.startMs) { picked ->
                val end = span.endMs
                val shifted =
                    if (end != null && span.startMs != null) end + (picked - span.startMs!!)
                    else end
                saveTimes(picked, shifted)
            }
        }
    }

    AttributeRow(icon = Icons.Outlined.Schedule, label = "End") {
        DateTimeValue(
            ms = span.endMs,
            placeholder = "No end time",
            enabled = !providerOwned && !busy,
            onClear =
                if (!providerOwned && span.endMs != null) {
                    { saveTimes(span.startMs, null) }
                } else null,
        ) {
            pick(span.endMs ?: span.startMs) { saveTimes(span.startMs, it) }
        }
    }

    val start = span.startMs
    val end = span.endMs
    if (start != null && end != null) {
        duration(start, end)?.let {
            AttributeRow(icon = Icons.Outlined.Timer, label = "Duration") {
                Text(it, color = Mist, fontSize = 13.5.sp)
            }
        }
    }

    AttributeRow(icon = Icons.Outlined.Layers, label = "Source") {
        Text(
            text = sourceLabel(span),
            color = Mist,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
