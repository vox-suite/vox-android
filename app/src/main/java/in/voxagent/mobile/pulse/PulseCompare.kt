package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

private val timeCharts = listOf("line", "area", "bar")

@Composable
internal fun PulseCompare(
    definition: PulseDefinition,
    primary: Measurement,
    catalog: List<Measurement>,
    enabled: Boolean,
    onChange: (PulseDefinition) -> Unit,
) {
    val bucket = definition.bucket ?: return
    val compare = definition.compare_with
    val candidates =
        catalog.filter { it.id != definition.measurement_id && it.id !in compare && bucket in it.buckets }
    var open by remember { mutableStateOf(false) }
    fun title(id: String) = catalog.firstOrNull { it.id == id }?.title ?: "Measurement"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Compare with", fontSize = 12.sp, color = SmokeDark)
        if (compare.isNotEmpty())
            (listOf(primary.id) + compare).forEachIndexed { index, id ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp)
                            .background(seriesColors[index % seriesColors.size], CircleShape)
                    )
                    Text(
                        if (index == 0) primary.title else title(id),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    if (index > 0)
                        TextButton(
                            onClick = {
                                onChange(definition.copy(compare_with = compare - id))
                            },
                            enabled = enabled,
                        ) {
                            Text("Remove", fontSize = 11.sp)
                        }
                }
            }
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled && compare.size < 5 && candidates.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (candidates.isEmpty()) "Nothing else to compare"
                    else "Add a measurement to compare…",
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                candidates.forEach { measurement ->
                    DropdownMenuItem(
                        text = { Text(measurement.title) },
                        onClick = {
                            open = false
                            onChange(
                                definition.copy(
                                    compare_with = compare + measurement.id,
                                    dimension = null,
                                    chart_type =
                                        if (definition.chart_type in timeCharts) definition.chart_type
                                        else "line",
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}
