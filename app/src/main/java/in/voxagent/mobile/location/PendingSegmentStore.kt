package `in`.voxagent.mobile.location

import androidx.core.content.edit
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private const val MAX_PENDING = 500

class PendingSegmentStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "location_tracking_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init {
        val legacy = context.getSharedPreferences("location_tracking", Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            prefs.edit {
                legacy.getString("pending_segments", null)?.let { putString("pending_segments", it) }
                legacy.getString("open_activity", null)?.let { putString("open_activity", it) }
                if (legacy.contains("open_started_at")) putLong("open_started_at", legacy.getLong("open_started_at", -1L))
            }
            legacy.edit { clear() }
        }
    }

    fun openSegmentStart(activity: ActivityKind, atMillis: Long) {
        prefs.edit {
            putString("open_activity", activity.name)
            putLong("open_started_at", atMillis)
        }
    }

    fun closeOpenSegment(endedAtMillis: Long): LocationSegment? {
        val activityName = prefs.getString("open_activity", null)
        val startedAtMillis = prefs.getLong("open_started_at", -1L)
        prefs.edit {
            remove("open_activity")
            remove("open_started_at")
        }
        if (activityName == null || startedAtMillis < 0 || endedAtMillis <= startedAtMillis) return null
        val activity = runCatching { ActivityKind.valueOf(activityName) }.getOrNull() ?: return null
        return LocationSegment(
            activity = activity,
            started_at = java.time.Instant.ofEpochMilli(startedAtMillis).toString(),
            ended_at = java.time.Instant.ofEpochMilli(endedAtMillis).toString(),
        )
    }

    fun enqueue(segment: LocationSegment) {
        val pending = (pendingSegments() + segment).takeLast(MAX_PENDING)
        prefs.edit { putString("pending_segments", json.encodeToString(pending)) }
    }

    fun pendingSegments(): List<LocationSegment> {
        val raw = prefs.getString("pending_segments", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<LocationSegment>>(raw) }.getOrDefault(emptyList())
    }

    fun clearUploaded(uploaded: List<LocationSegment>) {
        val remaining = pendingSegments() - uploaded.toSet()
        prefs.edit { putString("pending_segments", json.encodeToString(remaining)) }
    }
}
