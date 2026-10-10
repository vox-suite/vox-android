package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.PulseMeasurements(ui: PulseUiState, viewModel: PulseViewModel) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(query,{query=it},label={Text("Search measurements")},modifier=Modifier.fillMaxWidth().padding(16.dp))
    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (!ui.busy && ui.measurements.isEmpty()) item { Text("Connect an app or import history to discover measurements. Missing activity remains unknown.") }
        items(ui.measurements.filter { (it.title+it.description).contains(query,ignoreCase=true) },key={it.id}) { measurement ->
            OutlinedCard(onClick={viewModel.choose(measurement)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(measurement.title); Text(measurement.description,style=MaterialTheme.typography.bodySmall); Text(measurement.unit,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
