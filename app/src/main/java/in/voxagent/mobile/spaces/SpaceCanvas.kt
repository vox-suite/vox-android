package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SpaceCanvas(graph: SpaceGraph, selectedId: String?, onSelect: (SpaceNode) -> Unit) {
    val positions = remember(graph.nodes.map { it.id to it.kind }) { radialLayout(graph.nodes) }
    var zoom by remember(graph.space.id) { mutableFloatStateOf(0.5f) }
    var pan by remember(graph.space.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        fun fit() {
            if (positions.isEmpty()) return
            val minX = positions.values.minOf { it.x }
            val maxX = positions.values.maxOf { it.x } + 220
            val minY = positions.values.minOf { it.y }
            val maxY = positions.values.maxOf { it.y } + 120
            zoom =
                minOf(
                        maxWidth.value / (maxX - minX + 80),
                        (maxHeight.value - 100) / (maxY - minY + 80),
                        1f,
                    )
                    .coerceIn(0.1f, 1f)
            pan =
                with(density) {
                    Offset(-(minX + maxX).dp.toPx() * zoom / 2, -(minY + maxY).dp.toPx() * zoom / 2)
                }
        }
        LaunchedEffect(graph.space.id, positions.size, maxWidth, maxHeight) { fit() }
        Box(
            Modifier.fillMaxSize().pointerInput(graph.space.id, widthPx, heightPx) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    val newZoom = (zoom * gestureZoom).coerceIn(0.1f, 2f)
                    val ratio = newZoom / zoom
                    pan =
                        pan * ratio +
                            (centroid - Offset(widthPx / 2, heightPx / 2)) * (1 - ratio) +
                            gesturePan
                    zoom = newZoom
                }
            }
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val origin = Offset(size.width / 2, size.height / 2)
                    drawCircle(
                        BorderSubtle.copy(alpha = 0.22f),
                        340.dp.toPx(),
                        origin,
                        style = Stroke(1.dp.toPx()),
                    )
                    drawCircle(
                        BorderSubtle.copy(alpha = 0.12f),
                        630.dp.toPx(),
                        origin,
                        style = Stroke(1.dp.toPx()),
                    )
                    graph.edges.forEach { edge ->
                        val from = positions[edge.from_node] ?: return@forEach
                        val to = positions[edge.to_node] ?: return@forEach
                        val selected =
                            selectedId == null ||
                                edge.from_node == selectedId ||
                                edge.to_node == selectedId
                        val start =
                            origin + Offset((from.x + 220).dp.toPx(), (from.y + 60).dp.toPx())
                        val end = origin + Offset(to.x.dp.toPx(), (to.y + 60).dp.toPx())
                        val bend = maxOf(80.dp.toPx(), kotlin.math.abs(end.x - start.x) / 2)
                        val path =
                            Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(start.x + bend, start.y, end.x - bend, end.y, end.x, end.y)
                            }
                        drawPath(
                            path,
                            if (selectedId != null && selected) CoralPulse
                            else BorderSubtle.copy(alpha = if (selected) 0.85f else 0.2f),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
                graph.nodes.forEach { node ->
                    val position = positions[node.id] ?: return@forEach
                    SpaceNodeCard(
                        node = node,
                        central = position.central,
                        selected = node.id == selectedId,
                        modifier =
                            Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        (widthPx / 2 + with(density) { position.x.dp.toPx() })
                                            .roundToInt(),
                                        (heightPx / 2 + with(density) { position.y.dp.toPx() })
                                            .roundToInt(),
                                    )
                                }
                                .width(220.dp)
                                .height(120.dp),
                        onClick = { onSelect(node) },
                    )
                }
            }
        }
        if (graph.nodes.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (graph.space.run_state == "running")
                    CircularProgressIndicator(Modifier.size(24.dp), color = Mist)
                Text(
                    if (graph.space.run_state == "running") "Vox is researching your idea…"
                    else "Your workspace is ready.",
                    color = Mist,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Use chat to steer the plan.",
                    color = SmokeDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Text(
            "RESEARCH  /  OPTIONS  /  PLAN",
            color = SmokeDark,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val next = (zoom / 1.3f).coerceAtLeast(0.1f)
                    pan *= next / zoom
                    zoom = next
                },
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text("−", fontSize = 20.sp)
            }
            OutlinedButton(onClick = { fit() }) { Text("Fit map", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val next = (zoom * 1.3f).coerceAtMost(2f)
                    pan *= next / zoom
                    zoom = next
                },
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text("+", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SpaceNodeCard(
    node: SpaceNode,
    modifier: Modifier = Modifier,
    central: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (central) Color(0xFF151515) else Color(0xFF0C0C0C),
        border =
            BorderStroke(
                1.dp,
                if (selected) CoralPulse
                else if (central) Mist.copy(alpha = 0.55f) else BorderSubtle,
            ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    node.kind.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    color = SmokeDark,
                    fontSize = 9.sp,
                )
                Text(
                    node.state,
                    fontSize = 9.sp,
                    color =
                        when (node.state) {
                            "running" -> CoralPulse
                            "stale" -> Color(0xFFF5B83D)
                            "rejected" -> SmokeDark
                            else -> Mist
                        },
                )
            }
            Text(
                node.title,
                color = if (node.state == "rejected") SmokeDark else Mist,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.body.isNotBlank())
                Text(
                    node.body,
                    color = SmokeDark,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
}
