package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.*
import kotlin.math.*

internal val PulseMint = Color(0xFF3ECF8E)

@Composable
internal fun PulseChartCard(
    title: String,
    definition: PulseDefinition,
    result: PulseResult?,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val points =
        if (definition.bucket == null) result?.points.orEmpty().take(definition.top_n ?: 8)
        else result?.points.orEmpty()
    val observed = points.filter { it.value?.isFinite() == true }
    val headline =
        if (definition.bucket == null) observed.maxByOrNull { it.value!! }
        else observed.lastOrNull()
    val total =
        if (definition.chart_type == "stat")
            result?.total ?: observed.takeIf { it.isNotEmpty() }?.sumOf { it.value!! }
        else headline?.value
    var showData by rememberSaveable(title) { mutableStateOf(false) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF151515),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Mist, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                listOfNotNull(
                        result?.source?.replace('_', ' ')?.takeIf { it.isNotBlank() },
                        definition.bucket
                            ?.takeUnless { definition.measurement_id.startsWith("legacy:") }
                            ?.let {
                                if (definition.period_days >= 366) "Last year"
                                else
                                    "${definition.period_days} days${if (definition.offset_days > 0) " · ${definition.offset_days} days earlier" else ""}"
                            },
                    )
                    .joinToString(" · "),
                color = SmokeDark,
                fontSize = 11.sp,
            )
            Text(
                formatPulse(total.takeIf { result?.error == null }, result?.unit.orEmpty()),
                color = Mist,
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (definition.chart_type != "stat")
                Text(headline?.label.orEmpty(), color = SmokeDark, fontSize = 11.sp)
            when {
                result?.error != null -> Text(result.error, color = CoralPulse, fontSize = 12.sp)
                observed.isEmpty() ->
                    Box(
                        Modifier.fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF0D0D0D), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (result == null) "Chart data unavailable. Refresh to retry."
                            else "No recorded data in this range.",
                            color = SmokeDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                definition.chart_type != "stat" -> {
                    PulsePlot(
                        points,
                        definition.chart_type,
                        definition.bucket == null,
                        Modifier.fillMaxWidth()
                            .height(
                                if (definition.bucket == null && definition.chart_type == "bar")
                                    maxOf(160, points.size * 28).dp
                                else 190.dp
                            ),
                    ) {
                        selected = it
                    }
                    selected
                        ?.let { points.getOrNull(it) }
                        ?.let { point ->
                            Text(
                                "${point.label}: ${if (point.value == null) "No recorded coverage" else formatPulse(point.value, result?.unit.orEmpty())}",
                                color = PulseMint,
                                fontSize = 12.sp,
                            )
                        }
                    if (definition.chart_type == "pie")
                        points
                            .filter { (it.value ?: 0.0) > 0 }
                            .forEachIndexed { i, p ->
                                Text(
                                    "●  ${p.label}  ${formatPulse(p.value, result?.unit.orEmpty())}",
                                    color = chartColors[i % chartColors.size],
                                    fontSize = 11.sp,
                                )
                            }
                }
            }
            if (result != null) {
                if (result.description.isNotBlank())
                    Text(
                        result.description,
                        color = SmokeDark,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                if (result.record_count >= 0)
                    Text(
                        "${result.record_count} recorded entries${if (result.undated_count > 0) " · ${result.undated_count} undated" else ""}${if (result.quality.isNotBlank()) " · ${result.quality.replace('_', ' ')}" else ""}",
                        color = SmokeDark,
                        fontSize = 10.sp,
                    )
                result.data_as_of?.let {
                    Text(
                        "Data as of ${it.replace('T', ' ').take(16)}",
                        color = SmokeDark,
                        fontSize = 10.sp,
                    )
                }
                if (points.isNotEmpty()) {
                    TextButton(
                        onClick = { showData = !showData },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            if (showData) "Hide data" else "View data",
                            color = PulseMint,
                            fontSize = 11.sp,
                        )
                    }
                    if (showData)
                        Column(
                            Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            result.points.forEach { point ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        point.label,
                                        color = Mist,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        if (point.value == null) "No recorded coverage"
                                        else formatPulse(point.value, result.unit),
                                        color = SmokeDark,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                }
            }
            footer()
        }
    }
}
