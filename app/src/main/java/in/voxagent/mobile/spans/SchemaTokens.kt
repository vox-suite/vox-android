package `in`.voxagent.mobile.spans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The canonical 24-slot color/icon palette for data_schemas.color_token/
 * icon_token. Must stay in sync with vox-shared's src/schema_tokens.rs
 * (same L/C/H values, same OKLCH->sRGB conversion math -- Compose has no
 * native OKLCH ColorSpace, so this ports Ottosson's OKLab formulas rather
 * than approximating with hand-picked hex) and vox-desktop's
 * src/lib/schema-tokens.ts (which renders the same values via native CSS
 * oklch() instead, needing no conversion).
 */
data class Oklch(val l: Float, val c: Float, val h: Float)

private const val PALETTE_L = 0.72f
private const val PALETTE_C = 0.14f

val SCHEMA_COLOR_TOKENS: List<Oklch> = listOf(
    20f, 35f, 50f, 65f, 80f, 95f, 110f, 125f, 140f, 155f, 170f, 185f,
    200f, 215f, 230f, 245f, 260f, 275f, 290f, 305f, 320f, 335f, 350f, 5f,
).map { Oklch(PALETTE_L, PALETTE_C, it) }

private val SCHEMA_ICONS: List<ImageVector> = listOf(
    Icons.Filled.AccountBalanceWallet, // wallet
    Icons.Filled.MonitorHeart, // heart-pulse
    Icons.Filled.Restaurant, // utensils
    Icons.Filled.DirectionsCar, // car
    Icons.Filled.Home, // home
    Icons.Filled.Work, // briefcase
    Icons.Filled.Flight, // plane
    Icons.Filled.FitnessCenter, // dumbbell
    Icons.AutoMirrored.Filled.MenuBook, // book-open
    Icons.Filled.MusicNote, // music
    Icons.Filled.Movie, // film
    Icons.Filled.CameraAlt, // camera
    Icons.Filled.SportsEsports, // gamepad-2
    Icons.Filled.ShoppingBag, // shopping-bag
    Icons.Filled.LocalCafe, // coffee
    Icons.Filled.LocalPharmacy, // pill
    Icons.Filled.Bedtime, // moon
    Icons.Filled.WbSunny, // cloud-sun
    Icons.Filled.Call, // phone-call
    Icons.Filled.ChatBubble, // message-circle
    Icons.Filled.LocationOn, // map-pin
    Icons.Filled.Celebration, // party-popper
    Icons.Filled.Group, // users
    Icons.Filled.Laptop, // laptop
)

/** Reference OKLCH -> sRGB conversion (Bjorn Ottosson's OKLab formulas). */
fun oklchToColor(token: Oklch): Color {
    val hRad = Math.toRadians(token.h.toDouble())
    val a = (token.c * cos(hRad)).toFloat()
    val b = (token.c * sin(hRad)).toFloat()

    val l_ = token.l + 0.3963377774f * a + 0.2158037573f * b
    val m_ = token.l - 0.1055613458f * a - 0.0638541728f * b
    val s_ = token.l - 0.0894841775f * a - 1.2914855480f * b

    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_

    val rLin = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val gLin = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val bLin = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

    return Color(gammaEncode(rLin), gammaEncode(gLin), gammaEncode(bLin))
}

private fun gammaEncode(linear: Float): Float {
    val c = linear.coerceIn(0f, 1f)
    return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
}

fun schemaColor(token: Int?): Color? {
    val t = token?.let { SCHEMA_COLOR_TOKENS.getOrNull(it) } ?: return null
    return oklchToColor(t)
}

/** Same hue/chroma as [schemaColor], darkened for use as a chip background. */
fun schemaBgColor(token: Int?): Color? {
    val t = token?.let { SCHEMA_COLOR_TOKENS.getOrNull(it) } ?: return null
    return oklchToColor(t.copy(l = 0.22f)).copy(alpha = 0.9f)
}

fun schemaIcon(token: Int?): ImageVector? {
    val t = token ?: return null
    return SCHEMA_ICONS.getOrNull(t)
}

/**
 * Renders a span's category icon (schema_icon_token) when present,
 * falling back to a plain colored dot for spans with no schema (manually
 * created tasks, or spans predating the schema system).
 */
@Composable
fun CategoryIndicator(span: Span, dotColor: Color, dotSize: Dp) {
    val icon = schemaIcon(span.schemaIconToken)
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, tint = dotColor, modifier = Modifier.size(dotSize * 2.4f))
    } else {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}
