package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.spans.PlacedSpan
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.formatTime
import `in`.voxagent.mobile.spans.isEstimated
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpanBlock(
    placed: PlacedSpan,
    hasChildren: Boolean,
    colWidth: Dp,
    onSelect: (Span) -> Unit,
) {
    val span = placed.span
    val style = spanStyle(span)
    val inset = placed.depth * INDENT_DP + 3
    val heightDp = maxOf(placed.height * PX_PER_MIN - 2, 22.0).dp
    val amount = formatAmount(span)
    val estimated = isEstimated(span)
    val muted = span.status == SpanStatus.Cancelled
    val border =
        when (span.status) {
            SpanStatus.Active -> BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
            SpanStatus.Failed -> BorderStroke(1.dp, CoralPulse)
            else -> BorderStroke(1.dp, style.border)
        }
    val bg = if (hasChildren) lerp(Color(0xFF111215), style.bg, 0.6f) else style.bg
    val shape = RoundedCornerShape(8.dp)
    val width = colWidth * placed.width.toFloat() - (inset + 3).dp
    val dashed =
        Modifier.drawBehind {
            drawRoundRect(
                color = style.border,
                cornerRadius = CornerRadius(8.dp.toPx()),
                style =
                    Stroke(
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
            )
        }
    val timeText =
        (if (estimated) "≈ " else "") +
            formatTime(span.startMs) +
            (span.endMs?.let { " – ${formatTime(it)}" } ?: "")
    val canFitTwoLines = heightDp >= 38.dp && !hasChildren

    Box(
        modifier =
            Modifier.offset(
                    x = colWidth * placed.left.toFloat() + inset.dp,
                    y = (placed.top * PX_PER_MIN + 2).dp,
                )
                .alpha(if (muted) 0.4f else 1f)
                .width(width)
                .height(heightDp)
                .clip(shape)
                .background(bg)
                .then(if (estimated) dashed else Modifier.border(border, shape))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    onSelect(span)
                }
                .padding(horizontal = 8.dp, vertical = if (canFitTwoLines) 5.dp else 2.dp),
        contentAlignment = if (canFitTwoLines) Alignment.TopStart else Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                maxLines = if (canFitTwoLines) 2 else 1,
            ) {
                Text(
                    text = displayTitle(span),
                    color = Mist,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (muted) TextDecoration.LineThrough else null,
                )
                Text(
                    text = timeText,
                    color = style.subtext,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (amount != null) {
                    Text(
                        text = amount,
                        color = Mist,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
            CategoryIndicator(span, style.dot)
        }
    }
}
