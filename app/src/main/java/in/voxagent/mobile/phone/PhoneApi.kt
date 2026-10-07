package `in`.voxagent.mobile.phone

import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.net.VoxHttpException
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PhoneStatus(val has_phone: Boolean = false, val phone_verified: Boolean = false)

@Serializable private data class LinkPhoneRequest(val phone_number: String)

@Serializable private data class ConfirmRequest(val code: String)

@Serializable private data class StartResponse(val phone_last4: String? = null)

private val json = Json { ignoreUnknownKeys = true }
private val e164 = Regex("^\\+[1-9]\\d{6,14}$")

fun normalizePhone(raw: String): String? {
    val compact = raw.filterNot { it.isWhitespace() || it in "-()." }
    return compact.takeIf { e164.matches(it) }
}

fun phoneErrorMessage(error: Throwable): String =
    when (error) {
        is VoxHttpException ->
            when (error.statusCode) {
                400 -> "That code is invalid or has expired."
                404 -> "Add a phone number first."
                409 -> "That number is already verified on another Vox account."
                429 -> "Too many codes requested. Try again later."
                502,
                503 -> "We couldn't send the code right now. Try again in a moment."
                else -> "Verification failed (${error.statusCode})."
            }
        is IOException -> "Couldn't reach Vox. Check your connection."
        else -> "Something went wrong. Please try again."
    }

object PhoneApi {
    suspend fun status(token: String): PhoneStatus =
        json.decodeFromString(
            PhoneStatus.serializer(),
            VoxHttp.postJson("/v1/me", bearerToken = token),
        )

    suspend fun link(token: String, phoneNumber: String) {
        val body = json.encodeToString(LinkPhoneRequest.serializer(), LinkPhoneRequest(phoneNumber))
        VoxHttp.postJson("/v1/me/phone", body, token)
    }

    suspend fun startVerification(token: String): String {
        val response = VoxHttp.postJson("/v1/me/phone/verify/start", "{}", token)
        return json.decodeFromString(StartResponse.serializer(), response).phone_last4.orEmpty()
    }

    suspend fun confirm(token: String, code: String) {
        val body = json.encodeToString(ConfirmRequest.serializer(), ConfirmRequest(code))
        VoxHttp.postJson("/v1/me/phone/verify/confirm", body, token)
    }
}
