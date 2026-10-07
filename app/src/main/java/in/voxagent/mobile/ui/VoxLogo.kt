package `in`.voxagent.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.Mist
import kotlin.math.min

@Composable
fun VoxLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Mist,
    animated: Boolean = false,
) {
    var simTime by remember { mutableFloatStateOf(0f) }
    if (animated) {
        LaunchedEffect(Unit) {
            var lastNanos = withFrameNanos { it }
            while (true) {
                val nowNanos = withFrameNanos { it }
                val dt = min(0.1f, (nowNanos - lastNanos) / 1_000_000_000f)
                lastNanos = nowNanos
                simTime += dt * VoxOrbEngine.SPEED
            }
        }
    }

    Canvas(modifier = modifier.size(size).semantics { contentDescription = "Vox" }) {
        val scale = this.size.minDimension / 64f
        if (animated) {
            for (dot in VoxOrbEngine.frameRibbon(simTime)) {
                val dotAlpha = (dot.alpha * color.alpha).coerceIn(0f, 1f)
                val dotColor =
                    if (color == Mist) {
                        val w = dot.white.coerceIn(0f, 1f)
                        val g = 1f - w
                        Color(g, g, g, dot.alpha)
                    } else {
                        color.copy(alpha = dotAlpha)
                    }
                drawCircle(
                    color = dotColor,
                    radius = dot.r * scale,
                    center = Offset(dot.x * scale, dot.y * scale),
                )
            }
        } else {
            val dots = VoxLogoDots.DOTS
            var i = 0
            while (i < dots.size) {
                val cx = dots[i] * scale
                val cy = dots[i + 1] * scale
                val r = (dots[i + 2] * scale).coerceAtLeast(0.4f)
                val op = dots[i + 3]
                drawCircle(color = color.copy(alpha = op), radius = r, center = Offset(cx, cy))
                i += 4
            }
        }
    }
}
