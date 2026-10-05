package `in`.voxagent.mobile.spans

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class CategoryStyle(val bg: Color, val border: Color, val dot: Color, val subtext: Color)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)
private fun hex(value: Long) = Color(0xFF000000 or value)

private fun style(bg: Color, border: Color, dot: Color, sub: Color) = CategoryStyle(bg, border, dot, sub)

private val Violet = style(rgba(35, 25, 72, .88f), rgba(139, 92, 246, .35f), hex(0xa78bfa), rgba(196, 181, 253, .75f))
private val Blue = style(rgba(20, 38, 70, .88f), rgba(59, 130, 246, .35f), hex(0x60a5fa), rgba(147, 197, 253, .75f))
private val Green = style(rgba(13, 48, 30, .88f), rgba(16, 185, 129, .35f), hex(0x34d399), rgba(110, 231, 183, .75f))
private val Rose = style(rgba(60, 20, 38, .88f), rgba(244, 63, 94, .35f), hex(0xfb7185), rgba(253, 164, 175, .75f))
private val Amber = style(rgba(58, 32, 10, .88f), rgba(245, 158, 11, .35f), hex(0xfbbf24), rgba(253, 230, 138, .75f))
private val Orange = style(rgba(56, 26, 14, .88f), rgba(249, 115, 22, .35f), hex(0xfb923c), rgba(254, 215, 170, .75f))
private val Purple = style(rgba(42, 18, 76, .88f), rgba(168, 85, 247, .38f), hex(0xc084fc), rgba(233, 213, 255, .75f))
private val Slate = style(rgba(24, 26, 32, .9f), rgba(148, 163, 184, .25f), hex(0x94a3b8), rgba(203, 213, 225, .75f))
private val Fallback = style(rgba(26, 30, 42, .88f), rgba(100, 116, 139, .3f), hex(0x94a3b8), rgba(255, 255, 255, .65f))

private val CATEGORY_STYLES = mapOf(
    "meeting" to Violet, "call" to Violet, "reminder" to Violet,
    "commute" to Blue, "travel" to Blue, "driving" to Blue,
    "cycling" to Green, "ride" to Green, "running" to Green, "walking" to Green,
    "visit" to Rose, "appointment" to Rose,
    "expense" to Amber, "payment" to Amber, "delivery" to Amber,
    "meal" to Orange, "food" to Orange,
    "game" to Purple, "gaming" to Purple,
    "todo" to Slate,
)

private fun oklch(l: Double, c: Double, h: Double, alpha: Float): Color {
    val a = c * cos(Math.toRadians(h))
    val b = c * sin(Math.toRadians(h))
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    fun enc(x: Double): Float {
        val v = x.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
    }
    return Color(
        enc(4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3),
        enc(-1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3),
        enc(-0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3),
        alpha,
    )
}

private fun schemaStyle(token: Int): CategoryStyle {
    val t = if (token in 0..23) token else 0
    val hue = ((20 + 15 * t) % 360).toDouble()
    return CategoryStyle(
        bg = oklch(0.22, 0.14, hue, .9f),
        border = oklch(0.72, 0.14, hue, .35f),
        dot = oklch(0.72, 0.14, hue, 1f),
        subtext = oklch(0.72, 0.14, hue, .75f),
    )
}

fun categoryStyle(category: String, schemaColorToken: Int?): CategoryStyle =
    if (schemaColorToken != null) schemaStyle(schemaColorToken)
    else CATEGORY_STYLES[category.lowercase()] ?: Fallback

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)

fun formatTime(ms: Long?): String = if (ms == null) "" else timeFormat.format(Instant.ofEpochMilli(ms))

fun formatMoney(amount: Double, currency: String = "INR"): String =
    runCatching {
        NumberFormat.getCurrencyInstance().apply {
            this.currency = Currency.getInstance(currency)
            maximumFractionDigits = 0
        }.format(amount)
    }.getOrDefault("$amount $currency")

fun formatAmount(span: Span): String? = span.amount?.let { formatMoney(it, span.currency) }

private val SpotifyStyle = style(rgba(10, 42, 24, .9f), rgba(29, 185, 84, .45f), hex(0x1db954), rgba(134, 239, 172, .75f))
private val YouTubeStyle = style(rgba(58, 10, 16, .9f), rgba(255, 0, 51, .45f), hex(0xff0033), rgba(252, 165, 165, .8f))
private val PlayStationStyle = style(rgba(8, 30, 66, .9f), rgba(0, 112, 209, .5f), hex(0x0070d1), rgba(147, 197, 253, .8f))

fun spanStyle(span: Span): CategoryStyle = when (span.source) {
    "spotify" -> SpotifyStyle
    "youtube" -> YouTubeStyle
    "playstation" -> PlayStationStyle
    else -> categoryStyle(span.category, span.schemaColorToken)
}

fun displayTitle(span: Span): String =
    if (span.source == "playstation") span.title.removePrefix("PlayStation: ") else span.title

private fun dataObject(span: Span): JsonObject? = span.data as? JsonObject

private fun providerData(span: Span): JsonObject? = dataObject(span)?.get("provider_data") as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun spanCover(span: Span): String? = when (span.source) {
    "spotify" -> {
        val images = (providerData(span)?.get("album") as? JsonObject)?.get("images") as? JsonArray
        (images?.getOrNull(1) as? JsonObject)?.string("url") ?: (images?.getOrNull(0) as? JsonObject)?.string("url")
    }
    "playstation" -> dataObject(span)?.string("image_url")
    "youtube" -> providerData(span)?.string("video_id")?.takeIf { it.isNotEmpty() }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
    else -> null
}

fun spanSubtitle(span: Span): String? = when (span.source) {
    "spotify" -> ((providerData(span)?.get("artists") as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.string("name") }
        ?.joinToString(", "))?.takeIf { it.isNotEmpty() }
    "playstation" -> dataObject(span)?.string("platform")
    "youtube" -> when (providerData(span)?.string("action")) {
        "watch" -> "Watched on YouTube"
        "like" -> "Liked on YouTube"
        "playlist_addition" -> "Added to a playlist"
        else -> null
    }
    else -> null
}

fun isEstimated(span: Span): Boolean = (dataObject(span)?.get("estimated") as? JsonPrimitive)?.booleanOrNull == true

/** Entries whose title, time and status the provider owns (the server rejects edits). */
fun isProviderOwned(span: Span): Boolean = span.source in setOf("google_calendar", "spotify", "youtube")

fun sourceLabel(span: Span): String = when (span.source) {
    "spotify" -> "Spotify"
    "youtube" -> "YouTube"
    "playstation" -> "PlayStation"
    "google_calendar" -> "Google Calendar"
    "swiggy" -> "Swiggy"
    "zomato" -> "Zomato"
    else -> span.source.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
