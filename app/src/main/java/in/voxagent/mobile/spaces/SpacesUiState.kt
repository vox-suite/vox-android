package `in`.voxagent.mobile.spaces

data class SpacesUiState(
    val spaces: List<Space> = emptyList(),
    val selectedId: String? = null,
    val graph: SpaceGraph? = null,
    val messages: List<SpaceMessage> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val commitResult: CommitSpaceResult? = null,
)
