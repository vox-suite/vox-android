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

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val store = PendingSegmentStore(context)
        val bootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        var hasNewSegment = false

        for (event in result.transitionEvents) {
            val activity = mapActivity(event.activityType) ?: continue
            val eventMillis = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)
            when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    store.openSegmentStart(activity, eventMillis)
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    val segment = store.closeOpenSegment(eventMillis)
                    if (segment != null && segment.activity != ActivityKind.still) {
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
    }

    private fun mapActivity(type: Int): ActivityKind? = when (type) {
        DetectedActivity.IN_VEHICLE -> ActivityKind.driving
        DetectedActivity.ON_FOOT, DetectedActivity.WALKING, DetectedActivity.RUNNING -> ActivityKind.walking
        DetectedActivity.ON_BICYCLE -> ActivityKind.cycling
        DetectedActivity.STILL -> ActivityKind.still
        else -> null
    }
}
