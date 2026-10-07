package `in`.voxagent.mobile.spaces

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class NodePosition(val x: Float, val y: Float, val central: Boolean = false)

fun radialLayout(nodes: List<SpaceNode>): Map<String, NodePosition> {
    val goal = nodes.firstOrNull { it.kind == "goal" } ?: nodes.firstOrNull()
    val research = setOf("data", "research", "budget", "risk", "limit")
    val plans = setOf("plan", "decision", "step")
    val groups =
        listOf(
            nodes.filter { it != goal && it.kind in research },
            nodes.filter { it != goal && it.kind !in research && it.kind !in plans },
            nodes.filter { it != goal && it.kind in plans },
        )
    return buildMap {
        goal?.let { put(it.id, NodePosition(-110f, -60f, true)) }
        val sectors = listOf(-PI, -PI / 3, PI / 3)
        groups.forEachIndexed { sector, group ->
            group.forEachIndexed { i, node ->
                val lane = i / 3
                val count = minOf(3, group.size - lane * 3)
                val angle = sectors[sector] + ((i % 3) - (count - 1) / 2.0) * 0.75
                val distance = 340 + lane * 290
                put(
                    node.id,
                    NodePosition(
                        (cos(angle) * distance - 110).toFloat(),
                        (sin(angle) * distance - 60).toFloat(),
                    ),
                )
            }
        }
    }
}
