package `in`.voxagent.mobile.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.spans.Span

@Composable
fun CategoryIndicator(span: Span, color: Color, modifier: Modifier = Modifier, dot: Dp = 6.dp) {
    val brand = when (span.source) {
        "spotify" -> R.drawable.ic_spotify
        "youtube" -> R.drawable.ic_youtube
        "playstation" -> R.drawable.ic_playstation
        "google_maps" -> R.drawable.ic_google_maps
        else -> null
    }
    val icon = span.schemaIconToken?.let { schemaIcon(it) }
    if (brand != null) {
        Image(painterResource(brand), contentDescription = null, modifier = modifier.size(dot * 3))
    } else if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier.size(dot * 2), colorFilter = ColorFilter.tint(color))
    } else {
        Box(modifier.size(dot).background(color, CircleShape))
    }
}
