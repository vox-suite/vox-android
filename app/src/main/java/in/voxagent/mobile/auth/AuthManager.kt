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

class AuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val sessionStore = SessionStore(context)

    fun currentToken(): String? = sessionStore.currentToken()

    suspend fun signIn(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val idToken = requestGoogleIdToken()
                ?: error("No Google ID token returned")
            val requestJson = json.encodeToString(
                AuthExchangeRequest.serializer(),
                AuthExchangeRequest(id_token = idToken),
            )
            val responseJson = VoxHttp.postJson("/v1/auth/exchange", requestJson)
            val response = json.decodeFromString(
                AuthExchangeResponse.serializer(),
                responseJson,
            )
            sessionStore.save(response.token, Instant.parse(response.expires_at))
        }
    }

    fun signOut() {
        sessionStore.clear()
    }

    private suspend fun requestGoogleIdToken(): String? {
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
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        return null
    }
}
