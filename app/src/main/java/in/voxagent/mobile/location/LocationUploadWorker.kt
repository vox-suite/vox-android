package `in`.voxagent.mobile.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.voxagent.mobile.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = AuthManager(applicationContext).currentToken()
            ?: return@withContext Result.retry()

        val store = PendingSegmentStore(applicationContext)
        val pending = store.pendingSegments()
        if (pending.isEmpty()) return@withContext Result.success()

        runCatching { LocationSegmentApi.submitSegments(pending, token) }
            .onSuccess { store.clearUploaded(pending) }
            .onFailure { return@withContext Result.retry() }

        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "location_upload"
    }
}
