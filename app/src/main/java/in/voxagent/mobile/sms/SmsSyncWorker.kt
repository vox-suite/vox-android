package `in`.voxagent.mobile.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import `in`.voxagent.mobile.logging.RemoteLog
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
            ?: return@withContext Result.retry().also { RemoteLog.w(TAG, "no auth token, retrying later") }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            RemoteLog.w(TAG, "READ_SMS not granted, nothing can be read")
            return@withContext Result.failure()
        }

        val reader = SmsReader(applicationContext)
        val consent = runCatching { SmsConsentApi.getStatus(token) }
            .getOrElse {
                RemoteLog.e(TAG, "consent status fetch failed: $it")
                return@withContext Result.retry()
            }
        if (!consent.granted) {
            RemoteLog.w(TAG, "SMS consent not granted on server, nothing to sync")
            return@withContext Result.failure()
        }
        // The server's cursor is the source of truth; keep the local copy in step with it.
        val serverCursor = consent.synced_until?.let { java.time.Instant.parse(it).toEpochMilli() }
        serverCursor?.let { SmsSyncStore.advance(applicationContext, it) }
        var cursor = SmsSyncStore.cursor(applicationContext)
        RemoteLog.i(TAG, "sync starting: server_cursor=$serverCursor local_cursor=$cursor")

        var totalRead = 0
        var totalUploaded = 0
        var totalOtpSkipped = 0

        suspend fun syncPage(messages: List<SmsMessage>): Boolean {
            totalRead += messages.size
            val uploadable = messages.mapNotNull { message ->
                sanitizeSmsBody(message.body)?.let { message.copy(body = it) }
            }
            totalOtpSkipped += messages.size - uploadable.size
            val newestMillis = java.time.Instant.parse(messages.last().received_at).toEpochMilli()
            var syncedMillis = newestMillis
            if (uploadable.isNotEmpty()) {
                RemoteLog.i(TAG, "uploading batch of ${uploadable.size} messages (page had ${messages.size})")
                val response = runCatching { SmsBatchApi.submitBatch(uploadable, token) }
                    .getOrElse {
                        RemoteLog.e(TAG, "batch upload failed: $it")
                        return false
                    }
                totalUploaded += uploadable.size
                showToast(applicationContext, "Vox: synced ${uploadable.size} SMS")
                response.synced_until?.let {
                    syncedMillis = maxOf(syncedMillis, java.time.Instant.parse(it).toEpochMilli())
                }
            }
            SmsSyncStore.advance(applicationContext, syncedMillis)
            cursor = syncedMillis
            return true
        }

        val backfillDays = inputData.getInt(KEY_BACKFILL_DAYS, 0)
        if (backfillDays > 0) {
            // One-off history import: messages already uploaded are de-duplicated by the server.
            var since = System.currentTimeMillis() - backfillDays * DAY_MILLIS
            while (true) {
                val messages = reader.readSince(since, BATCH_SIZE)
                if (messages.isEmpty()) break
                if (!syncPage(messages)) return@withContext Result.retry()
                since = java.time.Instant.parse(messages.last().received_at).toEpochMilli()
                if (messages.size < BATCH_SIZE) break
            }
            RemoteLog.i(TAG, "backfill finished: days=$backfillDays read=$totalRead uploaded=$totalUploaded otpSkipped=$totalOtpSkipped")
            return@withContext Result.success()
        }

        if (cursor == null) {
            // First ever sync: only the most recent messages, not the whole inbox.
            val first = reader.readLatest(FIRST_SYNC_MESSAGES)
            if (first.isNotEmpty() && !syncPage(first)) return@withContext Result.retry()
        }

        while (true) {
            val messages = reader.readSince(cursor ?: 0L, BATCH_SIZE)
            if (messages.isEmpty()) break
            if (!syncPage(messages)) return@withContext Result.retry()
            if (messages.size < BATCH_SIZE) break
        }

        RemoteLog.i(TAG, "sync finished: read=$totalRead uploaded=$totalUploaded otpSkipped=$totalOtpSkipped")
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms_sync"
        const val KEY_BACKFILL_DAYS = "backfill_days"
        const val BACKFILL_DAYS = 90
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val BATCH_SIZE = 256
        private const val FIRST_SYNC_MESSAGES = 256
    }
}

private suspend fun showToast(context: Context, message: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP or Gravity.END, 24, 100)
        }.show()
    }
}
