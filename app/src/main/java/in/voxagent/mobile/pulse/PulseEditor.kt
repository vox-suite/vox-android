package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun ColumnScope.PulseEditor(ui: PulseUiState, viewModel: PulseViewModel) {
    val definition = ui.definition ?: return
    val measurement = ui.measurement ?: return
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                ui.title,
                viewModel::title,
                label = { Text("Chart name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !ui.saving,
                singleLine = true,
            )
        }
        item {
            PulseChoice(
                "Group by",
                definition.bucket ?: definition.dimension.orEmpty(),
                measurement.buckets + measurement.dimensions,
                !ui.saving,
            ) { value ->
                viewModel.change(
                    definition.copy(
                        bucket = value.takeIf { it in measurement.buckets },
                        dimension = value.takeIf { it in measurement.dimensions },
                        chart_type =
                            if (value in measurement.dimensions) "bar"
                            else definition.chart_type.takeUnless { it == "pie" } ?: "bar",
                    )
                )
            }
        }
        item {
            PulseChoice(
                "Period",
                definition.period_days.toString(),
                listOf("7", "30", "90", "365"),
                !ui.saving &&
                    measurement.kind != "recurring_cost_projection" &&
                    measurement.profile.timing != "first_to_last_played",
            ) {
                viewModel.change(definition.copy(period_days = it.toInt(), offset_days = 0))
            }
        }
        item {
            PulseChoice(
                "Chart",
                definition.chart_type,
                if (definition.bucket != null) listOf("bar", "line", "area", "stat")
                else if (measurement.profile.currency.isNotBlank()) listOf("bar")
                else listOf("bar", "pie"),
                !ui.saving,
            ) {
                viewModel.change(definition.copy(chart_type = it))
            }
        }
        if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = PulseMint) }
        item { PulseChartCard(ui.title.ifBlank { measurement.title }, definition, ui.preview) }
        item {
            Button(
                onClick = { viewModel.save() },
                enabled = !ui.busy && ui.title.isNotBlank() && ui.preview?.canSave == true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ui.saving) "Adding…"
                    else if (ui.busy) "Loading preview…" else "Add to Pulse"
                )
            }
        }
    }
}
