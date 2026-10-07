package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text,
        color = SmokeDark,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
    )
}

@Composable
internal fun StatusLabel(status: String) {
    Text(
        "●  ${status.uppercase()}",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color =
            when (status) {
                "running",
                "failed" -> CoralPulse
                "committed" -> Color(0xFFCFE3F1)
                else -> Color(0xFFF5B83D)
            },
    )
}

@Composable
internal fun ErrorBanner(error: String?, onRetry: (() -> Unit)? = null) {
    if (error == null) return
    Surface(
        color = EmberHush,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, color = CoralPulse, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = Mist) }
        }
    }
}

@Composable
internal fun ColumnScope.SpacesLoading() {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = Mist)
    }
}

internal fun spaceDate(value: String, time: Boolean = false): String =
    runCatching {
            val instant = Instant.parse(value)
            DateTimeFormatter.ofPattern(if (time) "HH:mm" else "dd MMM yyyy")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
        .getOrDefault("")
