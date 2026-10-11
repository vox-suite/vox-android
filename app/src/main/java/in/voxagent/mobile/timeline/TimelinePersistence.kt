package `in`.voxagent.mobile.timeline

import `in`.voxagent.mobile.net.VoxJson
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString

object TimelinePersistence {
    private val generation
        get() = `in`.voxagent.mobile.cache.DeviceCaches.epoch()

    private val stores = mutableSetOf<TimelineDayStore>()

    @Synchronized
    fun register(store: TimelineDayStore): Int {
        stores.add(store)
        return generation
    }

    @Synchronized fun sessionEpoch() = generation

    @Synchronized fun active(expected: Int) = expected == generation

    @Synchronized
    fun unregister(store: TimelineDayStore) {
        stores.remove(store)
    }

    @Synchronized
    fun clear(root: File) {
        `in`.voxagent.mobile.cache.DeviceCaches.clear(root)
        stores.forEach { it.clear() }
        File(root, "timeline").deleteRecursively()
    }

    fun userKey(userId: String): String =
        MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Synchronized
    fun read(root: File, user: String, expected: Int): TimelineSnapshot? {
        if (expected != generation) return null
        return runCatching {
                VoxJson.decodeFromString<TimelineSnapshot>(
                    File(root, "timeline/$user.json").readText()
                )
            }
            .getOrNull()
    }

    @Synchronized
    fun write(root: File, user: String, expected: Int, data: TimelineSnapshot) {
        if (expected != generation) return
        runCatching {
            val directory = File(root, "timeline").apply { mkdirs() }
            val temporary = File(directory, "$user.tmp")
            temporary.writeText(VoxJson.encodeToString(data))
            check(temporary.renameTo(File(directory, "$user.json")))
        }
    }
}
