package `in`.voxagent.mobile.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.tasks.await

private val TRACKED_ACTIVITIES = listOf(
    DetectedActivity.IN_VEHICLE,
    DetectedActivity.ON_FOOT,
    DetectedActivity.WALKING,
    DetectedActivity.RUNNING,
    DetectedActivity.ON_BICYCLE,
    DetectedActivity.STILL,
)

object LocationTrackingManager {
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    suspend fun start(context: Context) {
        val transitions = TRACKED_ACTIVITIES.flatMap { type ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            )
        }
        val request = ActivityTransitionRequest(transitions)
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(request, pendingIntent(context))
            .await()
    }

    suspend fun stop(context: Context) {
        runCatching {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent(context))
                .await()
        }
    }
}
