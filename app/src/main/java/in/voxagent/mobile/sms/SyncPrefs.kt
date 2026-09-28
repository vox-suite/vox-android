package `in`.voxagent.mobile.sms

import android.content.Context

class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sms_sync", Context.MODE_PRIVATE)

    fun lastSyncedMillis(): Long = prefs.getLong(KEY_LAST_SYNCED, 0L)

    fun setLastSyncedMillis(value: Long) {
        prefs.edit().putLong(KEY_LAST_SYNCED, value).apply()
    }

    fun backfillStartedFor(): String? = prefs.getString(KEY_BACKFILL_STARTED, null)

    fun backfillDoneFor(): String? = prefs.getString(KEY_BACKFILL_DONE, null)

    fun startBackfill(consentGrantedAt: String, fromMillis: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SYNCED, fromMillis)
            .putString(KEY_BACKFILL_STARTED, consentGrantedAt)
            .apply()
    }

    fun completeBackfill() {
        backfillStartedFor()?.let { prefs.edit().putString(KEY_BACKFILL_DONE, it).apply() }
    }

    companion object {
        private const val KEY_LAST_SYNCED = "last_synced_millis"
        private const val KEY_BACKFILL_STARTED = "backfill_started_for"
        private const val KEY_BACKFILL_DONE = "backfill_done_for"
    }
}
