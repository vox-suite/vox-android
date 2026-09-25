package `in`.voxagent.mobile.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.net.VoxHttp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class AuthExchangeRequest(val id_token: String)

@Serializable
private data class AuthExchangeResponse(
    val token: String,
    val user_id: String,
    val expires_at: String,
    val has_phone: Boolean = false,
)

data class UserProfile(
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
)

private data class GoogleAuthResult(
    val idToken: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
)

class AuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val sessionStore = SessionStore(context)

    fun currentToken(): String? = sessionStore.currentToken()

    fun userProfile(): UserProfile = UserProfile(
        displayName = sessionStore.getUserDisplayName(),
        email = sessionStore.getUserEmail(),
        avatarUrl = sessionStore.getUserAvatarUrl(),
    )

    suspend fun signIn(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val authResult = requestGoogleAuth()
                ?: error("No Google ID token returned")
            val requestJson = json.encodeToString(
                AuthExchangeRequest.serializer(),
                AuthExchangeRequest(id_token = authResult.idToken),
            )
            val responseJson = VoxHttp.postJson("/v1/auth/exchange", requestJson)
            val response = json.decodeFromString(
                AuthExchangeResponse.serializer(),
                responseJson,
            )
            sessionStore.save(
                token = response.token,
                expiresAt = Instant.parse(response.expires_at),
                email = authResult.email,
                displayName = authResult.displayName,
                avatarUrl = authResult.avatarUrl,
            )
        }
    }

    fun signOut() {
        sessionStore.clear()
    }

    private suspend fun requestGoogleAuth(): GoogleAuthResult? {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleId = GoogleIdTokenCredential.createFrom(credential.data)
            val (jwtEmail, jwtDisplayName, jwtAvatarUrl) = parseJwtPayload(googleId.idToken)
            val email = googleId.id.takeIf { it.isNotBlank() } ?: jwtEmail
            val displayName = googleId.displayName?.takeIf { it.isNotBlank() } ?: jwtDisplayName
            val avatarUrl = googleId.profilePictureUri?.toString()?.takeIf { it.isNotBlank() } ?: jwtAvatarUrl
            return GoogleAuthResult(
                idToken = googleId.idToken,
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
            )
        }
        return null
    }

    suspend fun tryRefreshProfile(): UserProfile? = withContext(Dispatchers.IO) {
        runCatching {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                val (jwtEmail, jwtDisplayName, jwtAvatarUrl) = parseJwtPayload(googleId.idToken)
                val email = googleId.id.takeIf { it.isNotBlank() } ?: jwtEmail
                val displayName = googleId.displayName?.takeIf { it.isNotBlank() } ?: jwtDisplayName
                val avatarUrl = googleId.profilePictureUri?.toString()?.takeIf { it.isNotBlank() } ?: jwtAvatarUrl

                sessionStore.updateProfile(
                    email = email,
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                )
                UserProfile(displayName = displayName, email = email, avatarUrl = avatarUrl)
            } else null
        }.getOrNull()
    }

    private fun parseJwtPayload(jwt: String): Triple<String?, String?, String?> {
        return try {
            val parts = jwt.split(".")
            if (parts.size >= 2) {
                val decoded = String(
                    android.util.Base64.decode(
                        parts[1],
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
                    ),
                )
                val json = org.json.JSONObject(decoded)
                val email = json.optString("email").takeIf { it.isNotEmpty() }
                val name = json.optString("name").takeIf { it.isNotEmpty() }
                    ?: json.optString("given_name").takeIf { it.isNotEmpty() }
                val picture = json.optString("picture").takeIf { it.isNotEmpty() }
                    ?: json.optString("avatar_url").takeIf { it.isNotEmpty() }
                    ?: json.optString("picture_url").takeIf { it.isNotEmpty() }
                Triple(email, name, picture)
            } else {
                Triple(null, null, null)
            }
        } catch (_: Exception) {
            Triple(null, null, null)
        }
    }
}
