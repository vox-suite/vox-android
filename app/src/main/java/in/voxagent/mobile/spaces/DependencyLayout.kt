package `in`.voxagent.mobile.spaces

import kotlin.math.ceil

data class NodePosition(val x: Float, val y: Float, val central: Boolean = false)

internal const val SPACE_NODE_WIDTH = 300f

internal fun nodeHeight(node: SpaceNode): Float {
    val lines = node.body.lines().sumOf { maxOf(1, ceil(it.length / 38.0).toInt()) }
    return (86 + ceil(node.title.length / 32.0).toInt() * 20 + lines * 18)
        .toFloat()
        .coerceIn(130f, 240f)
}

fun dependencyLayout(nodes: List<SpaceNode>, edges: List<SpaceEdge>): Map<String, NodePosition> {
    val byId = nodes.associateBy { it.id }
    val parents = nodes.associate { it.id to mutableSetOf<String>() }
    edges
        .filter { it.from_node in byId && it.to_node in byId }
        .forEach { parents.getValue(it.to_node).add(it.from_node) }
    nodes.forEach { node ->
        parents.getValue(node.id).addAll(node.derived_from.filter { it in byId })
    }
    val ordered = nodes.sortedWith(compareBy<SpaceNode> { it.created_at }.thenBy { it.id })
    val pending = ordered.toMutableList()
    val ranks = mutableMapOf<String, Int>()
    while (pending.isNotEmpty()) {
        val ready = pending.filter { parents.getValue(it.id).all(ranks::containsKey) }
        if (ready.isEmpty()) break
        ready.forEach { node ->
            ranks[node.id] =
                (parents.getValue(node.id).maxOfOrNull { ranks.getValue(it) } ?: -1) + 1
            pending.remove(node)
        }
    }
    pending.forEach { ranks[it.id] = (ranks.values.maxOrNull() ?: -1) + 1 }
    val positions = mutableMapOf<String, NodePosition>()
    ordered
        .groupBy { ranks.getValue(it.id) }
        .toSortedMap()
        .forEach { (rank, layer) ->
            val sorted =
                layer.sortedBy { node ->
                    parents
                        .getValue(node.id)
                        .mapNotNull { positions[it]?.y }
                        .let { if (it.isEmpty()) 0f else it.average().toFloat() }
                }
            val total =
                sorted.sumOf { nodeHeight(it).toDouble() }.toFloat() + (sorted.size - 1) * 32
            var y = -total / 2
            sorted.forEach { node ->
                val saved =
                    node.position?.takeIf {
                        it.x.isFinite() && it.y.isFinite() && (it.x != 0f || it.y != 0f)
                    }
                positions[node.id] =
                    NodePosition(
                        saved?.x ?: rank * (SPACE_NODE_WIDTH + 80),
                        saved?.y ?: y,
                        node.kind == "goal",
                    )
                y += nodeHeight(node) + 32
            }
        }
    return positions
}
