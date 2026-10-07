package `in`.voxagent.mobile.pulse

import java.text.NumberFormat

fun formatPulse(value: Double?, unit: String): String {
    if (value == null || !value.isFinite()) return "—"
    val formatter =
        NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = if (kotlin.math.abs(value) >= 100) 0 else 2
        }
    val symbol = mapOf("INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£")[unit]
    return if (symbol != null) symbol + formatter.format(value)
    else "${formatter.format(value)} ${if (unit == "hours") "hrs" else unit}".trim()
}
