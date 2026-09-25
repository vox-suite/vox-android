package `in`.voxagent.mobile.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.voxagent.mobile.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = AuthManager(applicationContext).currentToken()
            ?: return@withContext Result.retry()

        val reader = SmsReader(applicationContext)
        val syncPrefs = SyncPrefs(applicationContext)
        val since = syncPrefs.lastSyncedMillis()

        var latestSeen = since

        while (true) {
            val messages = reader.readSince(latestSeen, BATCH_SIZE)
            if (messages.isEmpty()) break

            val uploadable = messages.filterNot { looksLikeOtp(it.body) }
            if (uploadable.isNotEmpty()) {
                runCatching { SmsBatchApi.submitBatch(uploadable, token) }
                    .onFailure { return@withContext Result.retry() }
            }

            val newestMillis = java.time.Instant.parse(messages.last().received_at).toEpochMilli()
            latestSeen = newestMillis
            syncPrefs.setLastSyncedMillis(latestSeen)

            if (messages.size < BATCH_SIZE) break
        }

        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms_sync"
        private const val BATCH_SIZE = 100
    }
}
