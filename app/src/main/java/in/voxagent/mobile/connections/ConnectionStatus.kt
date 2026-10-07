package `in`.voxagent.mobile.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.SmokeDark

@Composable
internal fun PendingSetupBanner(
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1712))
                .border(BorderStroke(1.dp, Color(0xFF5A3D22)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = Color(0xFFFFB366),
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(text = message, color = Color(0xFFFFE0B2), fontSize = 12.sp)
        }
        Text(
            text = "Cancel",
            color = CoralPulse,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

internal val successMessage =
    Regex("^(Account connected|Timeline refreshed|Disconnected\\.|Setup cancelled)")

internal fun statusGlyph(message: String): Pair<String, Color> =
    when {
        successMessage.containsMatchIn(message) -> "✓" to Color(0xFF6EE7B7)
        message.startsWith("Waiting") -> "…" to SmokeDark
        else -> "!" to CoralPulse
    }

@Composable
internal fun StatusToastBar(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Obsidian)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val (glyph, glyphColor) = statusGlyph(message)
        Text(
            text = glyph,
            color = glyphColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(text = message, color = Mist, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            text = "✕",
            color = SmokeDark,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp),
        )
    }
}
