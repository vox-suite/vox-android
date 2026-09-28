package `in`.voxagent.mobile.stt

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "SttModel"
private const val MODEL_URL =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin"
private const val MODEL_FILENAME = "ggml-tiny.en.bin"

private val downloadClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .build()

data class SttDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val done: Boolean,
)

private fun modelsDir(context: Context): File =
    File(context.filesDir, "models").apply { mkdirs() }

fun sttModelPath(context: Context): File = File(modelsDir(context), MODEL_FILENAME)

fun isSttModelDownloaded(context: Context): Boolean = sttModelPath(context).exists()

suspend fun downloadSttModel(
    context: Context,
    onProgress: (SttDownloadProgress) -> Unit = {},
) {
    if (isSttModelDownloaded(context)) return

    withContext(Dispatchers.IO) {
        val destination = sttModelPath(context)
        val tmpFile = File(destination.parentFile, "$MODEL_FILENAME.part")

        val request = Request.Builder().url(MODEL_URL).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("STT model download failed: ${response.code}")
            }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }

            var downloaded = 0L
            var lastEmit = System.currentTimeMillis()
            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            onProgress(SttDownloadProgress(downloaded, total, done = false))
                            lastEmit = now
                        }
                    }
                }
            }
            if (!tmpFile.renameTo(destination)) {
                throw java.io.IOException("Failed to move downloaded model into place")
            }
            onProgress(SttDownloadProgress(downloaded, total, done = true))
        }
    }
}

@Volatile
private var cachedEngine: WhisperContext? = null

suspend fun getSttEngine(context: Context): WhisperContext {
    cachedEngine?.let { return it }
    return withContext(Dispatchers.IO) {
        synchronized(SttModel) {
            cachedEngine?.let { return@withContext it }
            val path = sttModelPath(context)
            check(path.exists()) { "Local Whisper model not found. Please download it first." }
            Log.i(TAG, "Loading Whisper context from ${path.absolutePath}")
            WhisperContext.createContextFromFile(path.absolutePath).also { cachedEngine = it }
        }
    }
}

private object SttModel
