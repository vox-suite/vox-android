package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.sourceLabel
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
internal fun SpanSheetHeader(
    span: Span,
    providerOwned: Boolean,
    infoOpen: Boolean,
    onToggleInfo: () -> Unit,
    onClose: () -> Unit,
    save: (JsonObject) -> Unit,
) {
    val style = spanStyle(span)
    var editingTitle by remember { mutableStateOf(false) }
    var titleDraft by remember(span.id) { mutableStateOf(span.title) }
    fun commitTitle() {
        val next = titleDraft.trim()
        editingTitle = false
        if (next.isEmpty()) {
            titleDraft = span.title
            return
        }
        if (next != span.title) save(buildJsonObject { put("title", next) })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp)
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
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    textStyle =
                        TextStyle(color = Mist, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Mist),
                    singleLine = true,
                )
                IconButton(onClick = { commitTitle() }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Save",
                        tint = Mist,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = {
                        titleDraft = span.title
                        editingTitle = false
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel",
                        tint = SmokeDark,
                        modifier = Modifier.size(16.dp),
                    )
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
                    IconButton(onClick = { editingTitle = true }, modifier = Modifier.size(24.dp)) {
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
            IconButton(onClick = { onToggleInfo() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Managed by ${sourceLabel(span)}",
                    tint = if (infoOpen) Mist else SmokeDark,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = SmokeDark,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
