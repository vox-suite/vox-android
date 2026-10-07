package `in`.voxagent.mobile

import androidx.activity.ComponentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import `in`.voxagent.mobile.sms.SmsSyncWorker
import java.util.concurrent.TimeUnit

internal fun schedulePeriodicSync(activity: ComponentActivity) {
    val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(1, TimeUnit.HOURS).build()
    WorkManager.getInstance(activity)
        .enqueueUniquePeriodicWork(
            SmsSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
}

internal fun triggerImmediateSync(activity: ComponentActivity) {
    val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().build()

    WorkManager.getInstance(activity)
        .enqueueUniqueWork("sms_sync_now", ExistingWorkPolicy.KEEP, request)
}

internal fun triggerBackfillSync(activity: ComponentActivity) {
    val request =
        OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInputData(
                workDataOf(SmsSyncWorker.KEY_BACKFILL_DAYS to SmsSyncWorker.BACKFILL_DAYS)
            )
            .build()
    WorkManager.getInstance(activity)
        .enqueueUniqueWork("sms_backfill", ExistingWorkPolicy.KEEP, request)
}
