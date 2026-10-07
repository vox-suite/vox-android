package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun ColumnScope.PulseSuggestions(ui: PulseUiState, viewModel: PulseViewModel) {
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (ui.busy)
                        "Generating suggestions from your activity — this can take a minute…"
                    else
                        ui.discovery?.let {
                            "Based on ${it.record_count} recorded entries across ${it.source_count} data sources."
                        } ?: "Looking through your activity…",
                    color = SmokeDark,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                OutlinedButton(onClick = { viewModel.discover(more = true) }, enabled = !ui.busy) {
                    Text("Generate more")
                }
            }
        }
        if (ui.busy)
            item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = PulseMint,
                    trackColor = BorderSubtle,
                )
            }
        if (!ui.busy && ui.discovery?.suggestions?.isEmpty() == true)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "No new charts with enough recorded data yet.",
                        color = Mist,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Sync your connected apps or add dated entries. Saved and dismissed charts are excluded.",
                        color = SmokeDark,
                        fontSize = 12.sp,
                    )
                    ui.discovery.connections.forEach { c ->
                        Text(
                            "${c.connector_id.replace('_', ' ')}${if (c.authorization_state != "authorized") " · reconnect needed" else if (!c.assistant_read || !c.sync_timeline) " · capture or read access off" else ""}",
                            color = SmokeDark,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        items(ui.discovery?.suggestions.orEmpty(), key = { it.title + it.definition.toString() }) {
            suggestion ->
            PulseChartCard(suggestion.title, suggestion.definition, suggestion.preview) {
                Text(suggestion.reason, color = SmokeDark, fontSize = 12.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = { viewModel.save(suggestion) },
                        enabled = !ui.busy && suggestion.preview.canSave,
                    ) {
                        Text("Add to Pulse", fontSize = 11.sp)
                    }
                    TextButton(onClick = { viewModel.edit(suggestion) }, enabled = !ui.busy) {
                        Text("Customize", fontSize = 11.sp)
                    }
                    TextButton(onClick = { viewModel.dismiss(suggestion) }, enabled = !ui.busy) {
                        Text("Dismiss", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
