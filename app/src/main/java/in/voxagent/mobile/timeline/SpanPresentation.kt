package `in`.voxagent.mobile.timeline

import `in`.voxagent.mobile.spans.SpanStatus

internal fun statusLabel(status: SpanStatus) =
    when (status) {
        SpanStatus.Planned -> "Planned"
        SpanStatus.Active -> "In progress"
        SpanStatus.WaitingUser -> "Waiting for you"
        SpanStatus.Done -> "Done"
        SpanStatus.Failed -> "Failed"
        SpanStatus.Cancelled -> "Cancelled"
    }

internal val dateFormat =
    java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.getDefault())
        .withZone(`in`.voxagent.mobile.spans.zone)
