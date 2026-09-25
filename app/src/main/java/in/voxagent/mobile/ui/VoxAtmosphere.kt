package `in`.voxagent.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.VoidBlack
import kotlin.random.Random

/** feTurbulence-style fractal grain, ported from vox-desktop's `.sign-in-noise` / `.vox-map-noise`. */
fun Modifier.voxGrain(
    opacity: Float,
    blendMode: BlendMode = BlendMode.Softlight,
    tileSize: Dp = 160.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val tilePx = with(density) { tileSize.roundToPx() }.coerceIn(32, 512)
    val noiseBrush = remember(tilePx) {
        val bitmap = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        val pixels = IntArray(tilePx * tilePx) {
            val v = random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        bitmap.setPixels(pixels, 0, tilePx, 0, 0, tilePx, tilePx)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ShaderBrush(shader)
    }
    drawWithContent {
        drawContent()
        drawRect(brush = noiseBrush, alpha = opacity, blendMode = blendMode)
    }
}

/** Full-bleed void-black backdrop with optional coral glow + grain, matching the desktop sign-in atmosphere. */
@Composable
fun VoxAtmosphereBackground(
    modifier: Modifier = Modifier,
    showGlow: Boolean = false,
    noiseOpacity: Float = 0.12f,
    noiseBlendMode: BlendMode = BlendMode.Softlight,
) {
    Box(modifier = modifier.fillMaxSize().background(VoidBlack)) {
        if (showGlow) {
            // Mirrors vox-desktop's `.sign-in-glow`: a soft multi-stop radial
            // wash (not a hard-edged circle) so it reads as a blob, not a box.
            Box(
                modifier = Modifier
                    .size(560.dp)
                    .offset((-180).dp, (-190).dp)
                    .blur(48.dp, BlurredEdgeTreatment.Unbounded)
                    .background(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to CoralPulse.copy(alpha = 0.32f),
                                0.32f to CoralPulse.copy(alpha = 0.14f),
                                0.55f to CoralPulse.copy(alpha = 0.05f),
                                0.76f to Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Box(modifier = Modifier.matchParentSize().voxGrain(opacity = noiseOpacity, blendMode = noiseBlendMode))
    }
}
