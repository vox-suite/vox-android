package `in`.voxagent.mobile.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant

class SessionStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "vox_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(token: String, expiresAt: Instant) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAt.toEpochMilli())
            .apply()
    }

    fun currentToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (Instant.now().toEpochMilli() >= expiresAtMillis) return null
        return token
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
