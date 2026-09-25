package `in`.voxagent.mobile.sms

private val otpKeywords = listOf(
    "otp",
    "verification code",
    "one-time password",
    "one time password",
    "security code",
)

fun looksLikeOtp(body: String): Boolean {
    val lower = body.lowercase()
    if (otpKeywords.none { lower.contains(it) }) return false
    return lower.split(Regex("[^0-9]")).any { it.length in 4..8 }
}
