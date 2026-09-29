package `in`.voxagent.mobile.logging

import android.content.Context
import android.util.Log
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Serializable
private data class LogLine(val ts: String, val level: String, val tag: String, val message: String)

@Serializable
private data class LogBatch(
    val platform: String,
    val app_version: String,
    val device_id: String,
    val lines: List<LogLine>,
)

object RemoteLog {
    private const val MAX_BUFFERED = 500
    private const val BATCH_SIZE = 200
    private const val FLUSH_INTERVAL_MS = 5_000L

    private val queue = ConcurrentLinkedQueue<LogLine>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("remote_log", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
        val auth = AuthManager(appContext)

        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                val token = auth.currentToken() ?: continue
                val lines = ArrayList<LogLine>(BATCH_SIZE)
                while (lines.size < BATCH_SIZE) lines.add(queue.poll() ?: break)
                if (lines.isEmpty()) continue
                val body = Json.encodeToString(
                    LogBatch.serializer(),
                    LogBatch("android", BuildConfig.VERSION_NAME, deviceId, lines),
                )
                runCatching { VoxHttp.postJson("/v1/logs/batches", body, token) }
                    .onFailure { lines.reversed().forEach { queue.offer(it) } }
            }
        }
    }

    fun d(tag: String, message: String, tr: Throwable? = null) { Log.d(tag, message, tr); enqueue("D", tag, message, tr) }
    fun i(tag: String, message: String, tr: Throwable? = null) { Log.i(tag, message, tr); enqueue("I", tag, message, tr) }
    fun w(tag: String, message: String, tr: Throwable? = null) { Log.w(tag, message, tr); enqueue("W", tag, message, tr) }
    fun e(tag: String, message: String, tr: Throwable? = null) { Log.e(tag, message, tr); enqueue("E", tag, message, tr) }

    private fun enqueue(level: String, tag: String, message: String, tr: Throwable?) {
        val text = if (tr == null) message else "$message | ${Log.getStackTraceString(tr)}"
        queue.offer(LogLine(Instant.now().toString(), level, tag, text))
        while (queue.size > MAX_BUFFERED) queue.poll()
    }
}
