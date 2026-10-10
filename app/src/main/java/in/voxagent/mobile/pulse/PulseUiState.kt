package `in`.voxagent.mobile.pulse

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PulseMode {
    Library,
    Suggestions,
    Measurements,
    Editor,
}

data class ChartPreview(
    val definition: PulseDefinition,
    val result: PulseResult? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class PulseUiState(
    val mode: PulseMode = PulseMode.Library,
    val canvas: PulseCanvas? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val measurements: List<Measurement> = emptyList(),
    val discovery: DiscoveryResponse? = null,
    val title: String = "",
    val definition: PulseDefinition? = null,
    val measurement: Measurement? = null,
    val preview: PulseResult? = null,
    val editor: PulseSuggestion? = null,
    val saveKey: String = UUID.randomUUID().toString(),
    val chartPreviews: Map<String, ChartPreview> = emptyMap(),
)
