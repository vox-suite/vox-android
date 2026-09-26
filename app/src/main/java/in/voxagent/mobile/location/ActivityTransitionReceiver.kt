package `in`.voxagent.mobile.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.temporal.ChronoUnit

// A stop shorter than this is noise (a red light, a queue) — not worth naming or a Places API call.
private const val MIN_VISIT_MINUTES = 5

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val store = PendingSegmentStore(context)
        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                var hasNewSegment = false
                for (event in result.transitionEvents) {
                    val activity = mapActivity(event.activityType) ?: continue
                    val eventMillis = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)
                    when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                            store.openSegmentStart(activity, eventMillis)
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                            val segment = store.closeOpenSegment(eventMillis)?.let { resolveVisit(context, it) }
                            if (segment != null) {
                                store.enqueue(segment)
                                hasNewSegment = true
                            }
                        }
                    }
                }

                if (hasNewSegment) {
                    WorkManager.getInstance(context)
                        .enqueue(OneTimeWorkRequestBuilder<LocationUploadWorker>().build())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Movement segments pass through unchanged; a `still` segment is dropped unless it's
     * long enough to count as a real visit, in which case it gets a single location fix. */
    private suspend fun resolveVisit(context: Context, segment: LocationSegment): LocationSegment? {
        if (segment.activity != ActivityKind.still) return segment

        val minutes = ChronoUnit.MINUTES.between(
            Instant.parse(segment.started_at),
            Instant.parse(segment.ended_at),
        )
        if (minutes < MIN_VISIT_MINUTES) return null

        val location = runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        }.getOrNull() ?: return null

        return segment.copy(lat = location.latitude, lng = location.longitude)
    }

    private fun mapActivity(type: Int): ActivityKind? = when (type) {
        DetectedActivity.IN_VEHICLE -> ActivityKind.driving
        DetectedActivity.ON_FOOT, DetectedActivity.WALKING, DetectedActivity.RUNNING -> ActivityKind.walking
        DetectedActivity.ON_BICYCLE -> ActivityKind.cycling
        DetectedActivity.STILL -> ActivityKind.still
        else -> null
    }
}
