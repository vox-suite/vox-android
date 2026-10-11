package `in`.voxagent.mobile.pulse

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*
import java.util.Locale
import kotlin.math.*

internal val seriesColors =
    listOf(
        Color(0xFF3ECF8E),
        Color(0xFFF5B83D),
        Color(0xFF6EA8FE),
        Color(0xFFFF5A67),
        Color(0xFFC084FC),
        Color(0xFF22D3EE),
    )

internal enum class SeriesMode {
    Single,
    Dual,
    Relative,
}

internal fun seriesMode(series: List<PulseSeries>): SeriesMode =
    when (series.map { it.unit }.toSet().size) {
        1 -> SeriesMode.Single
        2 -> SeriesMode.Dual
        else -> SeriesMode.Relative
    }

private fun compact(value: Double): String =
    when {
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
        abs(value) >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000)
        abs(value) >= 100 -> String.format(Locale.US, "%.0f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }

@Composable
internal fun PulseMultiPlot(
    series: List<PulseSeries>,
    type: String,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val mode = seriesMode(series)
    val units = series.map { it.unit }.distinct()
    val count = series.maxOfOrNull { it.points.size } ?: 0
    val left = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val right = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT } }
    Canvas(
        modifier
            .background(Color(0xFF0D0D0D), RoundedCornerShape(10.dp))
            .semantics {
                contentDescription =
                    "$type chart comparing ${series.size} measurements over $count periods; use View data for values"
            }
            .pointerInput(series, type) {
                detectTapGestures { offset ->
                    val inset = 32.dp.toPx()
                    val span = (size.width - inset - (if (mode == SeriesMode.Dual) inset else 16.dp.toPx()))
                    val ratio = ((offset.x - inset) / span).coerceIn(0f, 1f)
                    val index =
                        if (type == "bar") (ratio * count).toInt() else (ratio * (count - 1)).roundToInt()
                    onSelect(index.coerceIn(0, max(0, count - 1)))
                }
            }
    ) {
        if (count == 0) return@Canvas
        left.color = android.graphics.Color.rgb(160, 166, 162)
        right.color = left.color
        left.textSize = 10.sp.toPx()
        right.textSize = left.textSize
        val padLeft = 32.dp.toPx()
        val padRight = if (mode == SeriesMode.Dual) 32.dp.toPx() else 16.dp.toPx()
        val xLeft = padLeft
        val xRight = size.width - padRight
        val top = 16.dp.toPx()
        val bottom = size.height - 24.dp.toPx()

        fun finite(index: Int) = series[index].points.mapNotNull { it.value?.takeIf(Double::isFinite) }

        val scales =
            series.indices.map { i ->
                val group =
                    when (mode) {
                        SeriesMode.Single -> series.indices.toList()
                        SeriesMode.Dual -> series.indices.filter { series[it].unit == series[i].unit }
                        SeriesMode.Relative -> listOf(i)
                    }
                val values = group.flatMap { finite(it) }
                val lo = if (mode == SeriesMode.Relative) 0.0 else min(values.minOrNull() ?: 0.0, 0.0)
                val hi = max(values.maxOrNull() ?: 1.0, lo + 1e-9)
                lo to hi
            }

        fun x(index: Int) =
            if (type == "bar") xLeft + (xRight - xLeft) * (index + 0.5f) / count
            else xLeft + (xRight - xLeft) * index / max(1, count - 1)

        fun y(series: Int, value: Double): Float {
            val (lo, hi) = scales[series]
            return bottom - ((value - lo) / (hi - lo) * (bottom - top)).toFloat()
        }

        repeat(4) { i ->
            val y = top + (bottom - top) * i / 3
            drawLine(BorderSubtle.copy(alpha = 0.45f), Offset(xLeft, y), Offset(xRight, y), 1f)
        }
        if (mode != SeriesMode.Relative) {
            val (hi0, lo0) = scales[0].second to scales[0].first
            drawContext.canvas.nativeCanvas.drawText(compact(hi0), 2.dp.toPx(), top + 4.dp.toPx(), left)
            drawContext.canvas.nativeCanvas.drawText(compact(lo0), 2.dp.toPx(), bottom, left)
        }
        if (mode == SeriesMode.Dual) {
            val index = series.indexOfFirst { it.unit == units[1] }
            val (lo, hi) = scales[index]
            drawContext.canvas.nativeCanvas.drawText(compact(hi), size.width - 2.dp.toPx(), top + 4.dp.toPx(), right)
            drawContext.canvas.nativeCanvas.drawText(compact(lo), size.width - 2.dp.toPx(), bottom, right)
        }

        if (type == "bar") {
            val slot = (xRight - xLeft) / count
            val width = slot * 0.8f / series.size
            series.forEachIndexed { s, item ->
                item.points.forEachIndexed { i, point ->
                    point.value?.takeIf(Double::isFinite)?.let { value ->
                        val baseline = y(s, 0.0)
                        val peak = y(s, value)
                        val xStart = x(i) - slot * 0.4f + width * s
                        drawRect(
                            seriesColors[s % seriesColors.size].copy(alpha = 0.9f),
                            Offset(xStart, min(baseline, peak)),
                            Size(max(1f, width - 1.dp.toPx()), abs(baseline - peak)),
                        )
                    }
                }
            }
        } else {
            series.forEachIndexed { s, item ->
                val color = seriesColors[s % seriesColors.size]
                val segments = mutableListOf<List<Offset>>()
                var current = mutableListOf<Offset>()
                item.points.forEachIndexed { i, point ->
                    val value = point.value?.takeIf(Double::isFinite)
                    if (value != null) current.add(Offset(x(i), y(s, value)))
                    else if (current.isNotEmpty()) {
                        segments.add(current)
                        current = mutableListOf()
                    }
                }
                if (current.isNotEmpty()) segments.add(current)
                segments.forEach { segment ->
                    val path =
                        Path().apply {
                            moveTo(segment.first().x, segment.first().y)
                            segment.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                    if (type == "area") {
                        val base = y(s, 0.0)
                        val fill =
                            Path().apply {
                                addPath(path)
                                lineTo(segment.last().x, base)
                                lineTo(segment.first().x, base)
                                close()
                            }
                        drawPath(fill, color.copy(alpha = 0.1f))
                    }
                    drawPath(path, color, style = Stroke(1.8.dp.toPx()))
                    segment.forEach { drawCircle(color, 1.8.dp.toPx(), it) }
                }
            }
        }
        val labels = series.first().points
        listOf(0, labels.lastIndex / 2, labels.lastIndex).distinct().forEach { i ->
            val label = labels[i].label.takeLast(10)
            val xPos = x(i).coerceIn(xLeft, max(xLeft, xRight - left.measureText(label)))
            drawContext.canvas.nativeCanvas.drawText(label, xPos, size.height - 7.dp.toPx(), left)
        }
    }
}
