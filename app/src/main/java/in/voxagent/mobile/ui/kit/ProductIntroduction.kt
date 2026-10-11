package `in`.voxagent.mobile.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import kotlin.math.*

@Composable
internal fun ColumnScope.ProductIntroduction(spaces: Boolean, onCreate: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (spaces) "Think it through, layer by layer." else "Your life, as a living chart.",
            color = Mist,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 34.sp,
        )
        Text(
            if (spaces)
                "Start with a vision. Agents research in parallel, build on each other’s findings, and stack everything into a plan you can act on."
            else
                "Pulse turns your connected apps into small, live charts — what you listen to, what you play, where your money goes.",
            color = SmokeDark,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        Button(onClick = onCreate) {
            Text(if (spaces) "Create a space" else "Add your first chart")
        }
        if (spaces) SpaceLayers() else PulseHeart()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (if (spaces) listOf("Vision", "Research", "Context", "Plan")
                else listOf("Listening", "Gaming", "Spending", "Activity"))
                .forEach { Text(it, color = SmokeDark, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun SpaceLayers() {
    Canvas(
        Modifier.fillMaxWidth().height(270.dp).semantics {
            contentDescription =
                "Vision, web research, your context, and a clear plan stacked as layers"
        }
    ) {
        val scale = min(size.width / 360f, size.height / 300f)
        fun p(x: Float, y: Float) = Offset(size.width / 2 + x * scale, y * scale)
        val faint = Color(0xFF3A4048)
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(faint, scale, Offset(x, y))
                y += 20 * scale
            }
            x += 20 * scale
        }
        val widths = listOf(64f, 80f, 96f, 112f)
        widths.forEachIndexed { i, w ->
            val y = 40f + i * 65
            val h = w / 2
            val top =
                Path().apply {
                    moveTo(p(0f, y - h).x, p(0f, y - h).y)
                    listOf(p(w, y), p(0f, y + h), p(-w, y)).forEach { lineTo(it.x, it.y) }
                    close()
                }
            drawPath(top, Color(0xFF0A0B0C))
            drawPath(
                top,
                if (i == 0) Color(0xFFF5B83D) else Color(0xFF9AA3AE),
                style = Stroke(scale),
            )
            val lower =
                Path().apply {
                    val left = p(-w, y)
                    moveTo(left.x, left.y)
                    listOf(p(-w, y + 7), p(0f, y + h + 7), p(w, y + 7), p(w, y)).forEach {
                        lineTo(it.x, it.y)
                    }
                }
            drawPath(lower, Color(0xFF9AA3AE), style = Stroke(scale))
            if (i == 0) {
                drawLine(Color(0xFFF5B83D), p(0f, y + 10), p(0f, y - 22), scale)
                val flag =
                    Path().apply {
                        moveTo(p(0f, y - 22).x, p(0f, y - 22).y)
                        lineTo(p(17f, y - 16).x, p(17f, y - 16).y)
                        lineTo(p(0f, y - 10).x, p(0f, y - 10).y)
                        close()
                    }
                drawPath(flag, Color(0xFFF5B83D))
            } else if (i == 1) {
                (0..2).forEach { j ->
                    drawOval(
                        Color(0xFF9AA3AE),
                        p(-22f, y - 14 + j * 8),
                        androidx.compose.ui.geometry.Size(44 * scale, 18 * scale),
                        style = Stroke(scale),
                    )
                }
            } else if (i == 2) {
                repeat(12) { n ->
                    val a = n * PI / 6
                    val dot = p((cos(a) * 45).toFloat(), y + (sin(a) * 22).toFloat())
                    drawLine(faint, p(0f, y), dot, scale)
                    drawCircle(if (n == 3) Color(0xFFFF5A67) else Color(0xFF9AA3AE), 2 * scale, dot)
                }
            } else {
                drawOval(
                    Color(0xFF9AA3AE),
                    p(-35f, y - 17),
                    androidx.compose.ui.geometry.Size(70 * scale, 34 * scale),
                    style = Stroke(scale),
                )
                drawCircle(Color(0xFFF5B83D), 4 * scale, p(0f, y))
            }
        }
    }
}

@Composable
private fun PulseHeart() {
    Canvas(
        Modifier.fillMaxWidth().height(270.dp).semantics {
            contentDescription = "Wireframe heart with a green heartbeat line"
        }
    ) {
        val scale = min(size.width / 360f, size.height / 300f)
        fun p(x: Double, y: Double, z: Double) =
            Offset(
                (size.width / 2 + (x - y) * .866 * scale * 3).toFloat(),
                (125 * scale + (x + y) * .5 * scale * 3 - z * scale * 3).toFloat(),
            )
        val faint = Color(0xFF3A4048)
        var x = 0f
        while (x < size.width) {
            var y = 0f
            while (y < size.height) {
                drawCircle(faint, scale, Offset(x, y))
                y += 20 * scale
            }
            x += 20 * scale
        }
        fun heart(t: Double, y: Double): Offset {
            val factor = max(0.0, 1 - (y / 16).pow(2)).pow(.4)
            val hx = 16 * sin(t).pow(3)
            val hz = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t) + 2.5
            return p(hx * 1.9 * factor, y, hz * 1.9 * factor)
        }
        repeat(13) { i ->
            val y = -16.0 + 32.0 * (i + .5) / 13
            val outline =
                Path().apply {
                    repeat(96) { n ->
                        val point = heart(n / 96.0 * PI * 2, y)
                        if (n == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
            drawPath(
                outline,
                if (i == 6) Color(0xFFFF5A67)
                else Color(0xFFC9D1DA).copy(alpha = .25f + .6f * i / 13),
                style = Stroke(scale * .8f),
            )
        }
        repeat(28) { n ->
            val rib =
                Path().apply {
                    repeat(15) { i ->
                        val point = heart(n / 28.0 * PI * 2, -16.0 + 32.0 * i / 14)
                        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
            drawPath(rib, Color(0xFFC9D1DA).copy(alpha = .2f), style = Stroke(scale * .5f))
        }
        val beat =
            Path().apply {
                repeat(150) { n ->
                    val phase = n / 149.0
                    val z =
                        .1 * exp(-((phase - .18) / .03).pow(2)) -
                            .12 * exp(-((phase - .43) / .012).pow(2)) +
                            .46 * exp(-((phase - .47) / .011).pow(2)) -
                            .16 * exp(-((phase - .51) / .013).pow(2)) +
                            .16 * exp(-((phase - .7) / .04).pow(2))
                    val point = p(-52 + phase * 104, 0.0, z * 50)
                    if (n == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
        drawPath(beat, Color(0xFF3ECF8E), style = Stroke(scale * 1.6f))
    }
}
