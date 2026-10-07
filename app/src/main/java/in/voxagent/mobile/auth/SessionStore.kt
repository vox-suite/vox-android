package `in`.voxagent.mobile.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant

class SessionStore(context: Context) {
    private val masterKey =
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            "vox_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun save(
        token: String,
        expiresAt: Instant,
        email: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
    ) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putLong(KEY_EXPIRES_AT, expiresAt.toEpochMilli())
            if (email != null) putString(KEY_EMAIL, email)
            if (displayName != null) putString(KEY_DISPLAY_NAME, displayName)
            if (avatarUrl != null) putString(KEY_AVATAR_URL, avatarUrl)
        }
    }

    fun updateProfile(
        email: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
    ) {
        prefs.edit {
            if (email != null) putString(KEY_EMAIL, email)
            if (displayName != null) putString(KEY_DISPLAY_NAME, displayName)
            if (avatarUrl != null) putString(KEY_AVATAR_URL, avatarUrl)
        }
    }

    fun currentToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (Instant.now().toEpochMilli() >= expiresAtMillis) return null
        return token
    }

    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getUserDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    fun getUserAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "user_display_name"
        private const val KEY_AVATAR_URL = "user_avatar_url"
    }
}
