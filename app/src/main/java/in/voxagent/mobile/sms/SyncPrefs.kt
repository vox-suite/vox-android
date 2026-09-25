package `in`.voxagent.mobile.sms

import android.content.Context

class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sms_sync", Context.MODE_PRIVATE)

    fun lastSyncedMillis(): Long = prefs.getLong(KEY_LAST_SYNCED, 0L)

    fun setLastSyncedMillis(value: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED, value).apply()
    }

    companion object {
        private const val KEY_LAST_SYNCED = "last_synced_millis"
    }
}
