package `in`.voxagent.mobile.pulse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

internal val PulseColors =
    darkColorScheme(
        primary = PulseMint,
        onPrimary = Color.Black,
        surface = Color(0xFF151515),
        onSurface = Mist,
        background = Color(0xFF0A0A0A),
        onBackground = Mist,
        surfaceVariant = Color(0xFF202020),
        onSurfaceVariant = SmokeDark,
        outline = BorderSubtle,
        error = CoralPulse,
        secondaryContainer = Color(0xFF18352A),
        onSecondaryContainer = PulseMint,
    )

@Composable
fun PulseScreen(token: () -> String?, bottomInset: Dp = 88.dp) {
    val latestToken by rememberUpdatedState(token)
    val viewModel: PulseViewModel =
        viewModel(factory = viewModelFactory { initializer { PulseViewModel { latestToken() } } })
    val ui by viewModel.ui.collectAsState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.observe() }
    }
    PulseContent(ui, viewModel, bottomInset)
}

@Composable
internal fun PulseContent(ui: PulseUiState, viewModel: PulseViewModel, bottomInset: Dp = 88.dp) {
    BackHandler(ui.mode != PulseMode.Library) { viewModel.back() }
    MaterialTheme(colorScheme = PulseColors) {
        CompositionLocalProvider(LocalContentColor provides Mist) {
            VoxDarkScreen {
                Column(
                    Modifier.fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(bottom = bottomInset)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (ui.mode != PulseMode.Library)
                            IconButton(onClick = viewModel::back, enabled = !ui.saving) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Pulse")
                            }
                        Text(
                            when (ui.mode) {
                                PulseMode.Library -> "Pulse"
                                PulseMode.Editor -> "Customize chart"
                                else -> "Add a chart"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Mist,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (ui.mode == PulseMode.Library)
                            IconButton(
                                onClick = { viewModel.reload(refresh = true) },
                                enabled = !ui.busy,
                            ) {
                                Icon(Icons.Outlined.Refresh, "Refresh Pulse")
                            }
                        if (ui.mode == PulseMode.Library)
                            IconButton(
                                onClick = { viewModel.go(PulseMode.Suggestions) },
                                enabled = !ui.saving,
                            ) {
                                Icon(Icons.Outlined.Add, "Add to Pulse", tint = PulseMint)
                            }
                    }
                    HorizontalDivider(color = BorderSubtle)
                    if (ui.mode in setOf(PulseMode.Suggestions, PulseMode.Measurements)) {
                        Row(
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                    PulseMode.Suggestions to "Suggestions",
                                    PulseMode.Measurements to "Measurements",
                                )
                                .forEach { (mode, label) ->
                                    FilterChip(
                                        selected = ui.mode == mode,
                                        onClick = { viewModel.go(mode) },
                                        enabled = !ui.saving,
                                        label = { Text(label, fontSize = 12.sp) },
                                    )
                                }
                        }
                    }
                    if (ui.error != null) PulseErrorBar(ui.error) { viewModel.retry() }
                    when (ui.mode) {
                        PulseMode.Library -> PulseLibrary(ui, viewModel)
                        PulseMode.Suggestions -> PulseSuggestions(ui, viewModel)
                        PulseMode.Editor -> PulseEditor(ui, viewModel)
                        PulseMode.Measurements -> PulseMeasurements(ui, viewModel)
                    }
                }
            }
        }
    }
}
