package `in`.voxagent.mobile.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark

@Composable
fun <T> VoxSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                .padding(2.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) BorderSubtle else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelect(value)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Mist else GraphiteDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun VoxChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) BorderSubtle else Color.Transparent)
                .border(
                    BorderStroke(1.dp, if (selected) Mist.copy(alpha = 0.5f) else BorderSubtle),
                    RoundedCornerShape(50),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Mist else GraphiteDark, fontSize = 12.sp)
    }
}

@Composable
fun VoxErrorBar(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Obsidian)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = CoralPulse, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            "Retry",
            color = Mist,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry,
                    )
                    .padding(start = 12.dp),
        )
    }
}

@Composable
fun VoxEmpty(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            color = SmokeDark,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
        )
    }
}
