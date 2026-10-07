package `in`.voxagent.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.PureWhite

@Composable
internal fun NavDestinationButton(
    compact: Boolean = false,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val tint = if (selected) PureWhite else Color(0xFF6B6B6B)
    val highlight by
        animateColorAsState(
            targetValue = if (selected) PureWhite.copy(alpha = 0.12f) else Color.Transparent,
            label = "navHighlight",
        )
    Box(
        modifier =
            Modifier.size(if (compact) 36.dp else 42.dp)
                .clip(CircleShape)
                .background(highlight)
                .semantics { contentDescription = description }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

@Composable
fun CalendarIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            tint,
            topLeft = Offset(w * 0.1f, h * 0.18f),
            size = Size(w * 0.8f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.14f),
            style = stroke,
        )
        drawLine(
            tint,
            Offset(w * 0.1f, h * 0.42f),
            Offset(w * 0.9f, h * 0.42f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(w * 0.32f, h * 0.06f),
            Offset(w * 0.32f, h * 0.28f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(w * 0.68f, h * 0.06f),
            Offset(w * 0.68f, h * 0.28f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}
