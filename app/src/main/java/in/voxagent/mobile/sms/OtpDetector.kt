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

// Match only codes explicitly attached to an OTP label; ambiguous layouts stay local.
private val labeledCode = Regex(
    """\b(?:otp|verification code|one-time password|one time password|security code)\s*(?:is\s*|:|=|-)?\s*[0-9]{4,8}\b|\b[0-9]{4,8}\s+(?:is\s+)?(?:your\s+)?(?:otp|verification code|one-time password|one time password|security code)\b""",
    RegexOption.IGNORE_CASE,
)

fun sanitizeSmsBody(body: String): String? {
    if (!looksLikeOtp(body)) return body
    val lower = body.lowercase()
    val financial = listOf("card", "transaction", "txn", "payment", "pay ", "purchase")
        .any { lower.contains(it) }
    val amount = listOf("inr", "rs.", "rs ", "₹", "usd", "$", "eur", "gbp")
        .any { lower.contains(it) }
    if (!financial || !amount || !labeledCode.containsMatchIn(body)) return null
    val safeRanges = (labeledCode.findAll(body) + financialNumbers.findAll(body)).map { it.range }.toList()
    if (Regex("""\b[0-9]{4,8}\b""").findAll(body).any { number ->
        safeRanges.none { it.first <= number.range.first && it.last >= number.range.last }
    }) return null
    return labeledCode.replace(body, "OTP [REDACTED]")
}

private val financialNumbers = Regex(
    """(?:inr|rs\.?|₹|usd|\$|eur|gbp)\s*[0-9][0-9,.]*|(?:card|a/c|acct|account)\s*(?:ending\s*(?:in\s*)?|no\.?\s*)?[*xX -]*[0-9]{4}\b""",
    RegexOption.IGNORE_CASE,
)
