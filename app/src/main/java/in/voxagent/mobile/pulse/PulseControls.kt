package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*

@Composable
internal fun PulseRangeControls(
    definition: PulseDefinition,
    buckets: List<String> = emptyList(),
    enabled: Boolean = true,
    onChange: (PulseDefinition) -> Unit,
) {
    if (definition.bucket == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 366 to "Year").forEach {
                (days, label) ->
                FilterChip(
                    selected = definition.period_days == days,
                    onClick = { onChange(definition.withRange(days)) },
                    enabled = enabled,
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        if (definition.period_days < 366)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        onChange(
                            definition.copy(
                                offset_days = definition.offset_days + definition.period_days
                            )
                        )
                    },
                    enabled = enabled && definition.offset_days + 2 * definition.period_days <= 366,
                ) {
                    Text("Earlier", fontSize = 11.sp)
                }
                Text(
                    if (definition.offset_days == 0) "Current window"
                    else "${definition.offset_days} days earlier",
                    fontSize = 11.sp,
                    color = SmokeDark,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onChange(definition.later()) },
                    enabled = enabled && definition.offset_days > 0,
                ) {
                    Text("Later", fontSize = 11.sp)
                }
            }
        val allowed = if (definition.period_days > 365) buckets.filter { it != "day" } else buckets
        if (allowed.size > 1 && definition.chart_type != "stat")
            PulseChoice("Group by", definition.bucket, allowed, enabled) {
                onChange(definition.copy(bucket = it))
            }
    }
}

@Composable
internal fun PulseChoice(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: ${value.replace('_', ' ')}", fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replace('_', ' ')) },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ColumnScope.PulseLoading(text: String) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = PulseMint)
        Text(text, color = SmokeDark, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
internal fun PulseErrorBar(error: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(error, color = CoralPulse, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
