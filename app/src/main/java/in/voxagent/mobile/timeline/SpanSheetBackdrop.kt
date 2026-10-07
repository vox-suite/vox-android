package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.spanCover
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.ui.kit.RemoteImage

@Composable
internal fun BoxScope.SpanSheetBackdrop(span: Span) {
    val cover = spanCover(span)
    val style = spanStyle(span)
    if (cover != null) {
        Box(modifier = Modifier.matchParentSize().clipToBounds()) {
            RemoteImage(
                url = cover,
                modifier =
                    Modifier.fillMaxSize()
                        .scale(1.15f)
                        .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                alpha = 0.35f,
                blur = true,
            )
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0C0D10).copy(alpha = 0.55f),
                                Color(0xFF0C0D10).copy(alpha = 0.82f),
                                Color(0xFF0C0D10).copy(alpha = 0.98f),
                            )
                        )
                    )
            )
        }
    } else if (span.source != "spotify") {
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(style.dot.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.85f,
                    )
                )
            }
        )
    }
}
