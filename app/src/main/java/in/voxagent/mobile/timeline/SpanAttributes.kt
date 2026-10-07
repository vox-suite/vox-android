package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import java.time.Instant

@Composable
internal fun AttributeRow(
    icon: ImageVector,
    label: String,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = verticalAlignment,
    ) {
        Row(
            modifier =
                Modifier.width(115.dp)
                    .then(
                        if (verticalAlignment == Alignment.Top) Modifier.padding(top = 8.dp)
                        else Modifier
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
            Text(text = label, color = SmokeDark, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun SelectPill(
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
            modifier =
                Modifier.fillMaxWidth()
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
internal fun DateTimeValue(
    ms: Long?,
    placeholder: String,
    enabled: Boolean,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
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
                text =
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Mist,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                            )
                        ) {
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
                modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .clickable { onClear() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
