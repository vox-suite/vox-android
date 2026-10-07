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

private const val REDACTED = "[REDACTED]"
private const val LABEL = "(?:otp|verification code|one-time password|one time password|security code)"

// Codes must be explicitly attached to an OTP label; group 1 is the secret digits.
private val directCode = Regex("""\b$LABEL\s*(?:is\s*|:|=|-)?\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE)
private val codeFirst = Regex(
    """\b([0-9]{4,8})\s+(?:is\s+|as\s+)?(?:(?:your|the|this)\s+)?$LABEL\b""",
    RegexOption.IGNORE_CASE,
)
// "Your OTP for txn of INR 500 at AMAZON on card XX1234 is 123456": code at the end of the sentence.
private val trailingCode = Regex("""\b$LABEL\b[^\n]{0,160}?\bis\s*([0-9]{4,8})\b""", RegexOption.IGNORE_CASE)

private fun redactCodes(body: String, pattern: Regex): String? {
    var found = false
    val result = pattern.replace(body) { match ->
        found = true
        val code = match.groups[1]!!.range
        val start = match.range.first
        match.value.substring(0, code.first - start) + REDACTED + match.value.substring(code.last + 1 - start)
    }
    return if (found) result else null
}

private val financialNumbers = Regex(
    """(?:inr|rs\.?|₹|usd|\$|eur|gbp)\s*[0-9][0-9,.]*|(?:card|a/c|acct|account)\s*(?:ending\s*(?:in\s*)?|no\.?\s*)?[*xX -]*[0-9]{4}\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Returns the message with any authentication code redacted, the message unchanged when it is
 * not an OTP, or null when it must stay on the phone (pure OTPs and ambiguous layouts).
 */
fun sanitizeSmsBody(body: String): String? {
    if (!looksLikeOtp(body)) return body
    val lower = body.lowercase()
    val financial = listOf("card", "transaction", "txn", "payment", "pay ", "purchase")
        .any { lower.contains(it) }
    val amount = listOf("inr", "rs.", "rs ", "₹", "usd", "$", "eur", "gbp")
        .any { lower.contains(it) }
    if (!financial || !amount) return null
    var cleaned = redactCodes(body, directCode)
    cleaned = redactCodes(cleaned ?: body, codeFirst) ?: cleaned
    if (cleaned == null) cleaned = redactCodes(body, trailingCode)
    val text = cleaned ?: if (body.contains(REDACTED)) body else return null
    val safeRanges = financialNumbers.findAll(text).map { it.range }.toList()
    val unsafe = Regex("""\b[0-9]{4,8}\b""").findAll(text).any { number ->
        safeRanges.none { it.first <= number.range.first && it.last >= number.range.last }
    }
    return if (unsafe) null else text
}
