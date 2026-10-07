package `in`.voxagent.mobile.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState as rememberHScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import `in`.voxagent.mobile.spans.PlacedSpan
import `in`.voxagent.mobile.spans.Span
import `in`.voxagent.mobile.spans.SpanStatus
import `in`.voxagent.mobile.spans.displayTitle
import `in`.voxagent.mobile.spans.formatAmount
import `in`.voxagent.mobile.spans.formatTime
import `in`.voxagent.mobile.spans.spanCover
import `in`.voxagent.mobile.spans.spanStyle
import `in`.voxagent.mobile.spans.spanSubtitle
import `in`.voxagent.mobile.ui.kit.CategoryIndicator
import `in`.voxagent.mobile.ui.kit.RemoteImage
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InstantRow(items: List<PlacedSpan>, colWidth: Dp, onSelect: (Span) -> Unit) {
    val first = items.first()
    val inset = first.depth * INDENT_DP + 3
    Row(
        Modifier.offset(
                x = colWidth * first.left.toFloat() + inset.dp,
                y = QUARTER_DP * (first.slot ?: 0),
            )
            .width(colWidth * first.width.toFloat() - (inset + 3).dp)
            .height(QUARTER_DP)
            .horizontalScroll(rememberHScroll()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { InstantChip(it.span, onSelect) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InstantChip(span: Span, onSelect: (Span) -> Unit) {
    val style = spanStyle(span)
    val iconOnly = span.source == "spotify" || span.source == "youtube"
    val cover = spanCover(span)
    val amount = formatAmount(span)
    val muted = span.status == SpanStatus.Cancelled
    var card by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(50)
    val chip =
        Modifier.alpha(if (muted) 0.4f else 1f)
            .height(CHIP_DP)
            .clip(shape)
            .background(style.bg)
            .border(BorderStroke(1.dp, style.border), shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onSelect(span) },
                onLongClick = { if (iconOnly || cover != null) card = true },
            )
    if (iconOnly) {
        Box(chip.size(CHIP_DP), contentAlignment = Alignment.Center) {
            CategoryIndicator(span, style.dot)
        }
    } else {
        Row(
            chip.widthIn(max = 260.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIndicator(span, style.dot)
            Text(
                displayTitle(span),
                color = Mist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (muted) TextDecoration.LineThrough else null,
            )
            if (amount != null)
                Text(amount, color = Mist, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
    if (card) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, with(density) { (CHIP_DP + 4.dp).roundToPx() }),
            onDismissRequest = { card = false },
        ) {
            Row(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111215))
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cover != null) {
                    RemoteImage(
                        cover,
                        Modifier.size(
                                width = if (span.source == "youtube") 112.dp else 64.dp,
                                height = 64.dp,
                            )
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        displayTitle(span),
                        color = Mist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    spanSubtitle(span)?.let {
                        Text(
                            it,
                            color = SmokeDark,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatTime(span.startMs),
                        color = SmokeDark,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
