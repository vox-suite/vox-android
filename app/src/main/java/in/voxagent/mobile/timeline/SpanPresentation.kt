package `in`.voxagent.mobile.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.zone
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val CATEGORIES =
    listOf(
        "todo",
        "meeting",
        "call",
        "meal",
        "expense",
        "ride",
        "travel",
        "visit",
        "reminder",
        "music",
        "gaming",
        "video",
    )

internal val dateFormat =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()).withZone(zone)

internal val timeOfDayFormat =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)

internal fun statusIcon(status: SpanStatus): ImageVector =
    when (status) {
        SpanStatus.Planned -> Icons.Outlined.CalendarToday
        SpanStatus.Active -> Icons.Outlined.PlayCircleOutline
        SpanStatus.WaitingUser -> Icons.Outlined.HourglassEmpty
        SpanStatus.Done -> Icons.Outlined.CheckCircle
        SpanStatus.Failed -> Icons.Outlined.Cancel
        SpanStatus.Cancelled -> Icons.Outlined.Block
    }

internal fun statusColor(status: SpanStatus): Color =
    when (status) {
        SpanStatus.Planned -> Color(0xFF7DD3FC)
        SpanStatus.Active -> Color(0xFFFCD34D)
        SpanStatus.WaitingUser -> Color(0xFFC4B5FD)
        SpanStatus.Done -> Color(0xFF6EE7B7)
        SpanStatus.Failed -> Color(0xFFF87171)
        SpanStatus.Cancelled -> Color(0xFFA1A1AA)
    }

internal fun statusLabel(status: SpanStatus) =
    when (status) {
        SpanStatus.Planned -> "Planned"
        SpanStatus.Active -> "In progress"
        SpanStatus.WaitingUser -> "Waiting for you"
        SpanStatus.Done -> "Done"
        SpanStatus.Failed -> "Failed"
        SpanStatus.Cancelled -> "Cancelled"
    }

internal fun categoryIcon(category: String): ImageVector =
    when (category.lowercase()) {
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

internal fun duration(startMs: Long, endMs: Long): String? {
    val minutes = (endMs - startMs) / 60_000
    if (minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) (if (m > 0) "$h h $m min" else "$h h") else "$m min"
}
