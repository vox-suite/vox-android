package `in`.voxagent.mobile.pulse

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import kotlin.math.*

internal val chartColors =
    listOf(PulseMint, Color(0xFFA2AAA4), Color(0xFF77B6A1), Color(0xFF6B766E), Color(0xFFD4DAD6))

@Composable
internal fun PulsePlot(
    points: List<PulsePoint>,
    type: String,
    categorical: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(160, 166, 162) }
    }
    Canvas(
        modifier
            .background(Color(0xFF0D0D0D), RoundedCornerShape(10.dp))
            .semantics {
                contentDescription =
                    "$type chart with ${points.size} recorded groups; use View data for values"
            }
            .pointerInput(points, type) {
                detectTapGestures { offset ->
                    val index =
                        if (categorical && type == "bar")
                            ((offset.y - 12.dp.toPx()) / (size.height - 24.dp.toPx()) * points.size)
                                .toInt()
                        else
                            ((offset.x - 32.dp.toPx()) / (size.width - 48.dp.toPx()) *
                                    (points.size - 1))
                                .roundToInt()
                    onSelect(index.coerceIn(0, points.lastIndex))
                }
            }
    ) {
        paint.textSize = 10.sp.toPx()
        val padding = 16.dp.toPx()
        val finite = points.mapNotNull { it.value?.takeIf(Double::isFinite) }
        val max = maxOf(finite.maxOrNull() ?: 1.0, 0.0)
        val min = minOf(finite.minOrNull() ?: 0.0, 0.0)
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        if (type == "pie") {
            val positive = points.filter { (it.value ?: 0.0) > 0 }
            val total = positive.sumOf { it.value!! }
            if (total > 0) {
                val diameter = minOf(size.width, size.height) - 44.dp.toPx()
                val top = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                var angle = -90f
                positive.forEachIndexed { i, p ->
                    val sweep = (p.value!! / total * 360).toFloat()
                    drawArc(
                        chartColors[i % chartColors.size],
                        angle,
                        (sweep - 2).coerceAtLeast(0.1f),
                        false,
                        top,
                        Size(diameter, diameter),
                        style = Stroke(22.dp.toPx()),
                    )
                    angle += sweep
                }
            }
        } else if (categorical && type == "bar") {
            val labelWidth = size.width * 0.35f
            val width = size.width - labelWidth - padding
            val row = (size.height - 24.dp.toPx()) / points.size
            val zero = labelWidth + (-min / span * width).toFloat()
            points.forEachIndexed { i, p ->
                val y = 12.dp.toPx() + row * (i + 0.5f)
                drawContext.canvas.nativeCanvas.drawText(
                    p.label.take(16),
                    padding,
                    y + 4.dp.toPx(),
                    paint,
                )
                p.value?.takeIf(Double::isFinite)?.let { value ->
                    val x = labelWidth + ((value - min) / span * width).toFloat()
                    var px = minOf(zero, x)
                    while (px < maxOf(zero, x)) {
                        drawLine(
                            PulseMint,
                            Offset(px, y - 6.dp.toPx()),
                            Offset(px, y + 6.dp.toPx()),
                            1.dp.toPx(),
                        )
                        px += 4.dp.toPx()
                    }
                }
            }
        } else {
            val left = 32.dp.toPx()
            val right = size.width - padding
            val top = padding
            val bottom = size.height - 24.dp.toPx()
            fun point(i: Int, value: Double) =
                Offset(
                    left + (right - left) * i / maxOf(1, points.lastIndex),
                    bottom - ((value - min) / span * (bottom - top)).toFloat(),
                )
            val zero = point(0, 0.0).y
            repeat(4) { i ->
                val y = top + (bottom - top) * i / 3
                drawLine(BorderSubtle.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), 1f)
            }
            if (type == "bar") {
                val width = minOf(14.dp.toPx(), (right - left) / points.size * 0.5f)
                points.forEachIndexed { i, p ->
                    p.value?.takeIf(Double::isFinite)?.let { value ->
                        val xy = point(i, value)
                        var y = minOf(zero, xy.y)
                        while (y < maxOf(zero, xy.y)) {
                            drawLine(
                                PulseMint,
                                Offset(xy.x - width / 2, y),
                                Offset(xy.x + width / 2, y),
                                1.dp.toPx(),
                            )
                            y += 4.dp.toPx()
                        }
                    }
                }
            } else {

                val segments = mutableListOf<List<Offset>>()
                var current = mutableListOf<Offset>()
                points.forEachIndexed { i, p ->
                    if (p.value?.isFinite() == true) current.add(point(i, p.value))
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
                        val fill =
                            Path().apply {
                                addPath(path)
                                lineTo(segment.last().x, zero)
                                lineTo(segment.first().x, zero)
                                close()
                            }
                        drawPath(fill, PulseMint.copy(alpha = 0.15f))
                    }
                    drawPath(path, PulseMint, style = Stroke(2.dp.toPx()))
                    segment.forEach { drawCircle(PulseMint, 2.dp.toPx(), it) }
                }
            }
            listOf(0, points.lastIndex / 2, points.lastIndex).distinct().forEach { i ->
                val label = points[i].label.takeLast(10)
                val x =
                    point(i, 0.0).x.coerceIn(left, maxOf(left, right - paint.measureText(label)))
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 7.dp.toPx(), paint)
            }
        }
    }
}
