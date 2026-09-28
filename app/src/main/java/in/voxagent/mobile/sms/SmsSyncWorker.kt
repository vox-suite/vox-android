package `in`.voxagent.mobile.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.voxagent.mobile.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsSyncWorker"

class SmsSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = AuthManager(applicationContext).currentToken()
            ?: return@withContext Result.retry().also { Log.w(TAG, "no auth token, retrying later") }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_SMS not granted, nothing can be read")
            return@withContext Result.failure()
        }

        val reader = SmsReader(applicationContext)
        val syncPrefs = SyncPrefs(applicationContext)
        val since = syncPrefs.lastSyncedMillis()
        Log.i(TAG, "sync starting, reading messages since=$since")

        var latestSeen = since
        var totalRead = 0
        var totalUploaded = 0
        var totalOtpSkipped = 0

        while (true) {
            val messages = reader.readSince(latestSeen, BATCH_SIZE)
            if (messages.isEmpty()) break
            totalRead += messages.size

            val uploadable = messages.filterNot { looksLikeOtp(it.body) }
            totalOtpSkipped += messages.size - uploadable.size
            if (uploadable.isNotEmpty()) {
                Log.i(TAG, "uploading batch of ${uploadable.size} messages (page had ${messages.size})")
                runCatching { SmsBatchApi.submitBatch(uploadable, token) }
                    .onSuccess {
                        totalUploaded += uploadable.size
                        showToast(applicationContext, "Vox: synced ${uploadable.size} SMS")
                    }
                    .onFailure {
                        Log.e(TAG, "batch upload failed: $it")
                        return@withContext Result.retry()
                    }
            }

            val newestMillis = java.time.Instant.parse(messages.last().received_at).toEpochMilli()
            latestSeen = newestMillis
            syncPrefs.setLastSyncedMillis(latestSeen)

            if (messages.size < BATCH_SIZE) break
        }

        syncPrefs.completeBackfill()
        Log.i(TAG, "sync finished: read=$totalRead uploaded=$totalUploaded otpSkipped=$totalOtpSkipped")
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms_sync"
        private const val BATCH_SIZE = 100
    }
}

private suspend fun showToast(context: Context, message: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP or Gravity.END, 24, 100)
        }.show()
    }
}
