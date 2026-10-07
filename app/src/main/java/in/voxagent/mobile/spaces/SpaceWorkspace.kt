package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun ColumnScope.SpaceWorkspace(
    graph: SpaceGraph,
    busy: Boolean,
    selectedId: String?,
    onSelect: (SpaceNode) -> Unit,
    onChat: () -> Unit,
    onCommit: () -> Unit,
) {
    var cards by rememberSaveable(graph.space.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusLabel(
            graph.space.run_state.takeIf { it == "running" || it == "failed" } ?: graph.space.state
        )
        Text(
            "${graph.nodes.size} cards",
            color = SmokeDark,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { cards = !cards }, contentPadding = PaddingValues(8.dp)) {
            Text(if (cards) "Show map" else "Show cards", fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = BorderSubtle)
    if (cards) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(graph.nodes, key = { it.id }) { node ->
                SpaceNodeCard(
                    node,
                    Modifier.fillMaxWidth(),
                    node.kind == "goal",
                    node.id == selectedId,
                ) {
                    onSelect(node)
                }
            }
            if (graph.nodes.isEmpty())
                item { Text("Use chat to steer the plan.", color = SmokeDark) }
        }
    } else Box(Modifier.weight(1f)) { SpaceCanvas(graph, selectedId, onSelect) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f)) {
            Text("Space chat", fontSize = 12.sp)
        }
        Button(
            onClick = onCommit,
            modifier = Modifier.weight(1f),
            enabled = !busy && graph.canCommit,
        ) {
            Text(
                if (!graph.space.editable) "Read-only"
                else if (busy) "Updating…" else "Commit plan",
                fontSize = 12.sp,
            )
        }
    }
}
