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
        val since = consent.synced_until?.let { java.time.Instant.parse(it).toEpochMilli() }
            ?: (System.currentTimeMillis() - consent.retention_days * MILLIS_PER_DAY)
        RemoteLog.i(TAG, "sync starting, reading messages since=$since")

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
                RemoteLog.i(TAG, "uploading batch of ${uploadable.size} messages (page had ${messages.size})")
                runCatching { SmsBatchApi.submitBatch(uploadable, token) }
                    .onSuccess {
                        totalUploaded += uploadable.size
                        showToast(applicationContext, "Vox: synced ${uploadable.size} SMS")
                    }
                    .onFailure {
                        RemoteLog.e(TAG, "batch upload failed: $it")
                        return@withContext Result.retry()
                    }
            }

            val newestMillis = java.time.Instant.parse(messages.last().received_at).toEpochMilli()
            latestSeen = newestMillis

            if (messages.size < BATCH_SIZE) break
        }

        RemoteLog.i(TAG, "sync finished: read=$totalRead uploaded=$totalUploaded otpSkipped=$totalOtpSkipped")
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sms_sync"
        private const val BATCH_SIZE = 100
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

private suspend fun showToast(context: Context, message: String) {
    withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP or Gravity.END, 24, 100)
        }.show()
    }
}
