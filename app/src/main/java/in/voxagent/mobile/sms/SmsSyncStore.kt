package `in`.voxagent.mobile.sms

import android.content.Context

/** Local copy of the last synced SMS timestamp (epoch millis); only ever moves forward. */
object SmsSyncStore {
    private const val PREFS = "sms_sync"
    private const val KEY_CURSOR = "last_synced_ms"

    fun cursor(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_CURSOR)) prefs.getLong(KEY_CURSOR, 0L) else null
    }

    fun advance(context: Context, millis: Long) {
        val current = cursor(context)
        if (current == null || millis > current) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_CURSOR, millis).apply()
        }
    }
}
