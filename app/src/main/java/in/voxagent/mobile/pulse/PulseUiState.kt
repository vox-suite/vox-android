package `in`.voxagent.mobile.pulse

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PulseMode {
    Library,
    Suggestions,
    Ask,
    Goal,
    Editor,
    Board,
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
    val goals: List<GoalView> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val discovery: DiscoveryResponse? = null,
    val messages: List<ComposeMessage> = emptyList(),
    val goalMessages: List<ComposeMessage> = emptyList(),
    val title: String = "",
    val definition: PulseDefinition? = null,
    val measurement: Measurement? = null,
    val preview: PulseResult? = null,
    val goalDraft: GoalDraft? = null,
    val goalPreview: GoalView? = null,
    val editor: PulseSuggestion? = null,
    val saveKey: String = UUID.randomUUID().toString(),
    val board: BoardDetails? = null,
    val boardResults: List<BoardResult> = emptyList(),
    val chartPreviews: Map<String, ChartPreview> = emptyMap(),
)
