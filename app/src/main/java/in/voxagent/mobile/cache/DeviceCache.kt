package `in`.voxagent.mobile.cache

import android.content.Context
import `in`.voxagent.mobile.auth.SessionStore
import `in`.voxagent.mobile.net.VoxJson
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

object DeviceCaches {
    private var generation = 0
    private val resets = mutableSetOf<() -> Unit>()

    @Synchronized fun epoch() = generation

    @Synchronized fun active(expected: Int) = expected == generation

    @Synchronized
    fun register(reset: () -> Unit) {
        resets.add(reset)
    }

    @Synchronized
    fun unregister(reset: () -> Unit) {
        resets.remove(reset)
    }

    @Synchronized
    fun clear(root: File) {
        generation++
        resets.toList().forEach { it() }
        listOf("timeline", "pulse", "spaces", "projects").forEach {
            File(root, it).deleteRecursively()
        }
    }

    @Synchronized
    fun read(file: File, expected: Int): String? =
        if (!active(expected)) null else runCatching { file.readText() }.getOrNull()

    @Synchronized
    fun write(file: File, expected: Int, content: String) {
        if (!active(expected)) return
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(content)
            check(temporary.renameTo(file))
        }
    }
}

class DeviceCache(context: Context, private val name: String) {
    val epoch = DeviceCaches.epoch()
    private val session = SessionStore(context.applicationContext)
    private val user = session.getUserId() ?: session.getUserEmail()
    val identity =
        user?.let {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte ->
                "%02x".format(byte)
            }
        }
    @PublishedApi
    internal val file = identity?.let { File(context.noBackupFilesDir, "$name/$it.json") }

    suspend inline fun <reified T> read(): T? =
        withContext(Dispatchers.IO) {
            file
                ?.let { DeviceCaches.read(it, epoch) }
                ?.let { runCatching { VoxJson.decodeFromString<T>(it) }.getOrNull() }
        }

    suspend inline fun <reified T> write(data: T) =
        withContext(Dispatchers.IO) {
            file?.let { DeviceCaches.write(it, epoch, VoxJson.encodeToString(data)) }
        }
}
