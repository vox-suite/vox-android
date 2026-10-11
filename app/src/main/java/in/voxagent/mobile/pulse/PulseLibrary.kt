package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.kit.ProductIntroduction
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun ColumnScope.PulseLibrary(ui: PulseUiState, viewModel: PulseViewModel) {
    if (ui.canvas == null && (ui.loading || ui.busy)) {
        PulseLoading("Loading Pulse…")
        return
    }
    val empty = ui.canvas?.charts.isNullOrEmpty()
    if (empty && ui.error == null) {
        ProductIntroduction(spaces = false) { viewModel.go(PulseMode.Suggestions) }
        return
    }
    val control = `in`.voxagent.mobile.control.LocalAppControl.current
    val context = control?.context?.collectAsState()?.value
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(context?.pulseId, ui.canvas?.charts?.map { it.id }) {
        val index = ui.canvas?.charts?.indexOfFirst { it.id == context?.pulseId } ?: -1
        if (index >= 0) scroll.animateScrollToItem(index)
    }
    LazyColumn(
        Modifier.weight(1f),
        state = scroll,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(ui.canvas?.charts.orEmpty(), key = { "chart:${it.id}" }) { chart ->
            val override = ui.chartPreviews[chart.id]
            val definition = override?.definition ?: chart.definition
            var confirmDelete by remember { mutableStateOf(false) }
            if (confirmDelete)
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete chart?") },
                    text = { Text(chart.title) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDelete = false
                                viewModel.removeChart(chart)
                            },
                            enabled = !ui.saving,
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                    },
                )
            Column(
                modifier =
                    Modifier.clickable { control?.patch { it.copy(pulseId = chart.id) } }
                        .then(
                            if (context?.pulseId == chart.id)
                                Modifier.border(
                                    1.dp,
                                    PulseMint.copy(alpha = .5f),
                                    RoundedCornerShape(14.dp),
                                )
                            else Modifier
                        ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        PulseRangeControls(definition, enabled = !ui.saving) {
                            viewModel.range(chart, it)
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = !ui.saving) {
                        Text("Delete", color = SmokeDark, fontSize = 11.sp)
                    }
                }
                if (override?.loading == true)
                    Text("Loading range…", color = SmokeDark, fontSize = 11.sp)
                override?.error?.let { PulseErrorBar(it) { viewModel.range(chart, definition) } }
                PulseChartCard(
                    chart.title,
                    definition,
                    if (override == null) chart.result else override.result,
                )
            }
        }
        if (ui.canvas?.next_cursor != null)
            item {
                OutlinedButton(
                    onClick = { viewModel.reload(more = true) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Load more")
                }
            }
    }
}
