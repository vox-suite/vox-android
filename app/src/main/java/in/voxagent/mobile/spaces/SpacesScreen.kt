package `in`.voxagent.mobile.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.voxagent.mobile.ui.VoxDarkScreen
import `in`.voxagent.mobile.ui.theme.*

internal val SpacesColors =
    darkColorScheme(
        primary = Mist,
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Mist,
        surface = Obsidian,
        onSurface = Mist,
        surfaceVariant = Color(0xFF191919),
        onSurfaceVariant = SmokeDark,
        secondaryContainer = Color(0xFF202020),
        onSecondaryContainer = Mist,
        outline = BorderSubtle,
        error = CoralPulse,
    )

@Composable
fun SpacesScreen(token: () -> String?, bottomInset: Dp = 88.dp) {
    val latestToken by rememberUpdatedState(token)
    val viewModel: SpacesViewModel =
        viewModel(factory = viewModelFactory { initializer { SpacesViewModel { latestToken() } } })
    val ui by viewModel.ui.collectAsState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.observe() }
    }
    SpacesContent(ui, viewModel, bottomInset)
}

@Composable
internal fun SpacesContent(ui: SpacesUiState, viewModel: SpacesViewModel, bottomInset: Dp) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var chat by rememberSaveable(ui.selectedId) { mutableStateOf(false) }
    var selectedNodeId by rememberSaveable(ui.selectedId) { mutableStateOf<String?>(null) }
    var dropTarget by remember { mutableStateOf<Space?>(null) }
    var confirmCommit by remember { mutableStateOf(false) }
    val graph = ui.graph
    LaunchedEffect(graph?.nodes?.map { it.id }) {
        if (graph != null && graph.nodes.none { it.id == selectedNodeId }) selectedNodeId = null
    }
    BackHandler(ui.selectedId != null && !creating && !chat && selectedNodeId == null) {
        viewModel.open(null)
    }
    MaterialTheme(colorScheme = SpacesColors) {
        VoxDarkScreen {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomInset)) {
                if (ui.selectedId == null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("VOX / SPACES")
                            Text(
                                "Spaces",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = Mist,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        FilledTonalButton(
                            onClick = { creating = true },
                            enabled = !ui.busy,
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Text(
                                "New space",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    Text(
                        "Explore an idea. Compare options. Build a plan.",
                        fontSize = 12.sp,
                        color = SmokeDark,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
                    )
                    HorizontalDivider(
                        color = BorderSubtle,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    ErrorBanner(ui.error, viewModel::reload)
                    when {
                        ui.loading && ui.spaces.isEmpty() -> SpacesLoading()
                        ui.spaces.isEmpty() && ui.error == null ->
                            Column(
                                Modifier.weight(1f).padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Eyebrow("00 SPACES")
                                Text(
                                    "Map out a decision.",
                                    color = Mist,
                                    fontSize = 30.sp,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    "Start a space to explore trips, fitness routines or big decisions. Vox reads your past data and lays the options out around your goal.",
                                    color = SmokeDark,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Button(
                                    onClick = { creating = true },
                                    modifier = Modifier.padding(top = 24.dp),
                                ) {
                                    Text("Create first space")
                                }
                            }
                        else ->
                            LazyColumn(
                                Modifier.weight(1f),
                                contentPadding = PaddingValues(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(ui.spaces, key = { it.id }) { space ->
                                    SpaceLibraryCard(
                                        space,
                                        ui.busy,
                                        { viewModel.open(space.id) },
                                        { dropTarget = space },
                                    )
                                }
                            }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.open(null) }, enabled = !ui.busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to spaces", tint = Mist)
                        }
                        Column(Modifier.weight(1f)) {
                            Eyebrow("VOX / SPACE")
                            Text(
                                graph?.space?.title ?: "Loading space…",
                                fontSize = 16.sp,
                                color = Mist,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = viewModel::reload, enabled = !ui.busy) {
                            Icon(Icons.Outlined.Refresh, "Refresh space", tint = Mist)
                        }
                        if (graph != null)
                            IconButton(onClick = { chat = true }) {
                                Icon(Icons.Outlined.ChatBubbleOutline, "Space chat", tint = Mist)
                            }
                    }
                    ErrorBanner(ui.error, viewModel::reload)
                    if (graph == null) {
                        if (ui.loading) SpacesLoading()
                    } else {
                        SpaceWorkspace(
                            graph,
                            ui.busy,
                            selectedNodeId,
                            { selectedNodeId = it.id },
                            { chat = true },
                            { confirmCommit = true },
                        )
                    }
                }
            }
            if (creating)
                CreateSpaceSheet(
                    ui.busy,
                    ui.error,
                    { creating = false },
                    { title, intent -> viewModel.create(title, intent) { creating = false } },
                )
            if (chat && graph != null) SpaceChatSheet(ui, { chat = false }, viewModel)
            graph
                ?.nodes
                ?.find { it.id == selectedNodeId }
                ?.let { node ->
                    SpaceNodeSheet(
                        node,
                        graph.space.editable,
                        ui.busy,
                        ui.error,
                        { selectedNodeId = null },
                        viewModel,
                    )
                }
            dropTarget?.let { space ->
                AlertDialog(
                    onDismissRequest = { if (!ui.busy) dropTarget = null },
                    title = { Text("Drop this space?") },
                    text = { Text("“${space.title}” will be removed from your Spaces library.") },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.drop(space.id) { dropTarget = null } },
                            enabled = !ui.busy,
                        ) {
                            Text(if (ui.busy) "Dropping…" else "Drop space")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { dropTarget = null }, enabled = !ui.busy) {
                            Text("Cancel")
                        }
                    },
                )
            }
            if (confirmCommit && graph != null) {
                val count =
                    graph.nodes.count { it.state == "done" && it.kind in setOf("plan", "step") }
                AlertDialog(
                    onDismissRequest = { confirmCommit = false },
                    title = { Text("Commit this plan?") },
                    text = {
                        Text(
                            "Create a collection and $count planned timeline items from the completed plan and step cards. This space becomes read-only."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmCommit = false
                                viewModel.commit()
                            },
                            enabled = !ui.busy && graph.canCommit,
                        ) {
                            Text("Commit plan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCommit = false }) { Text("Keep exploring") }
                    },
                )
            }
            ui.commitResult?.let { result ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissResult,
                    title = { Text("Plan committed") },
                    text = {
                        Text(
                            "Created a collection with ${result.committed_spans_count} planned timeline items."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissResult) { Text("Done") }
                    },
                )
            }
        }
    }
}
