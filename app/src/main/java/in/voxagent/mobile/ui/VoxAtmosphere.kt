package `in`.voxagent.mobile.ui

import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.VoidBlack
import kotlin.random.Random

fun Modifier.voxGrain(
    opacity: Float,
    blendMode: BlendMode = BlendMode.Softlight,
    tileSize: Dp = 160.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val tilePx = with(density) { tileSize.roundToPx() }.coerceIn(32, 512)
    val noiseBrush =
        remember(tilePx) {
            val bitmap = createBitmap(tilePx, tilePx)
            val random = Random(42)
            val pixels =
                IntArray(tilePx * tilePx) {
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

@Composable
fun VoxAtmosphereBackground(
    modifier: Modifier = Modifier,
    showGlow: Boolean = false,
    noiseOpacity: Float = 0.12f,
    noiseBlendMode: BlendMode = BlendMode.Softlight,
) {
    Box(modifier = modifier.fillMaxSize().background(VoidBlack)) {
        if (showGlow) {

            Box(
                modifier =
                    Modifier.requiredSize(750.dp)
                        .offset((-255).dp, (-285).dp)
                        .graphicsLayer {
                            scaleX = 1.5f
                            scaleY = 0.8f
                        }
                        .background(
                            brush =
                                Brush.radialGradient(
                                    colorStops =
                                        arrayOf(
                                            0.0f to CoralPulse.copy(alpha = 0.55f),
                                            0.1f to CoralPulse.copy(alpha = 0.526f),
                                            0.2f to CoralPulse.copy(alpha = 0.461f),
                                            0.3f to CoralPulse.copy(alpha = 0.373f),
                                            0.4f to CoralPulse.copy(alpha = 0.275f),
                                            0.5f to CoralPulse.copy(alpha = 0.181f),
                                            0.6f to CoralPulse.copy(alpha = 0.103f),
                                            0.7f to CoralPulse.copy(alpha = 0.047f),
                                            0.8f to CoralPulse.copy(alpha = 0.015f),
                                            0.9f to CoralPulse.copy(alpha = 0.002f),
                                            1.0f to CoralPulse.copy(alpha = 0.0f),
                                        )
                                )
                        )
            )
        }
        Box(
            modifier =
                Modifier.matchParentSize()
                    .voxGrain(opacity = noiseOpacity, blendMode = noiseBlendMode)
        )
    }
}
