package `in`.voxagent.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.Mist

/**
 * Vox orb logo ported directly from Vox Desktop.
 * Renders the thinking-orb stippled/dithered point cloud onto a Compose Canvas.
 */
@Composable
fun VoxLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Mist,
    animated: Boolean = false,
) {
    val rotationAnim = if (animated) {
        val transition = rememberInfiniteTransition(label = "VoxLogoSpin")
        val r by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "VoxLogoRotation",
        )
        r
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer(rotationZ = rotationAnim)
            .semantics { contentDescription = "Vox" },
    ) {
        val scale = this.size.minDimension / 64f
        val dots = VoxLogoDots.DOTS
        val len = dots.size
        var i = 0
        while (i < len) {
            val cx = dots[i] * scale
            val cy = dots[i + 1] * scale
            val r = (dots[i + 2] * scale).coerceAtLeast(0.4f)
            val op = dots[i + 3]
            drawCircle(
                color = color.copy(alpha = op),
                radius = r,
                center = Offset(cx, cy),
            )
            i += 4
        }
    }
}
