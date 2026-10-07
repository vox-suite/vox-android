package `in`.voxagent.mobile.sms

import android.content.Context
import androidx.core.content.edit

object SmsSyncStore {
    private const val PREFS = "sms_sync"
    private const val KEY_CURSOR = "last_synced_ms"
    private const val KEY_BACKFILL_VERSION = "backfill_version"

    fun cursor(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_CURSOR)) prefs.getLong(KEY_CURSOR, 0L) else null
    }

    fun advance(context: Context, millis: Long) {
        val current = cursor(context)
        if (current == null || millis > current) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putLong(KEY_CURSOR, millis)
            }
        }
    }

    fun backfillVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BACKFILL_VERSION, 0)

    fun markBackfilled(context: Context, version: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_BACKFILL_VERSION, version)
        }
    }
}
