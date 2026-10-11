package `in`.voxagent.mobile.pulse

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import `in`.voxagent.mobile.ui.*
import `in`.voxagent.mobile.ui.theme.VoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PulseUiTest {
    @get:Rule val compose = createComposeRule()
    private val vm = PulseViewModel { null }
    private val definition =
        PulseDefinition(measurement_id = "spotify_hours", bucket = "day", chart_type = "line")
    private val result =
        PulseResult(
            points =
                listOf(
                    PulsePoint("Oct 5", 2.5),
                    PulsePoint("Oct 6", null),
                    PulsePoint("Oct 7", 0.0),
                ),
            unit = "hours",
            source = "spotify",
            description = "Reported listening duration; playback end is unknown.",
            record_count = 12,
        )
    private val measurement =
        Measurement("spotify_hours", "Listening", buckets = listOf("day", "week", "month"))
    private val suggestion =
        PulseSuggestion(
            "Daily listening",
            "Your recent Spotify activity",
            definition,
            measurement,
            result,
        )

    private fun show(ui: PulseUiState) {
        compose.setContent { VoxTheme(darkTheme = true) { PulseContent(ui, vm) } }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in
            listOf(
                "mkdir -p /sdcard/Download/vox-pulse-qa",
                "screencap -p /sdcard/Download/vox-pulse-qa/$name.png",
            )) {
            automation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.readBytes()
                }
            }
        }
    }

    @Test
    fun emptyPulseHasAddAction() {
        show(PulseUiState(loading = false))
        compose.onNodeWithText("Your life, as a living chart.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to Pulse").assertIsDisplayed()
        screenshot("empty")
    }

    @Test
    fun chartDataRetainsMissingCoverageAndRealZero() {
        show(
            PulseUiState(
                loading = false,
                canvas =
                    PulseCanvas(
                        charts =
                            listOf(SavedPulseChart("chart", "Daily listening", definition, result))
                    ),
            )
        )
        compose.onNodeWithText("Daily listening").assertIsDisplayed()
        compose.onNodeWithText("View data").performScrollTo().performClick()
        compose.onNodeWithText("No recorded coverage").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("0 hrs", useUnmergedTree = true).assertCountEquals(2)
        screenshot("chart")
    }

    @Test
    fun suggestionsUseFullPageCreationAndAllowRecordedZero() {
        show(
            PulseUiState(
                mode = PulseMode.Suggestions,
                loading = false,
                discovery = DiscoveryResponse(listOf(suggestion), 12, 1),
            )
        )
        compose.onNodeWithText("Add to Pulse").performScrollTo().assertIsEnabled()
        screenshot("suggestions")
    }

    @Test
    fun unavailablePreviewCannotBeSaved() {
        show(
            PulseUiState(
                mode = PulseMode.Editor,
                loading = false,
                title = "Daily listening",
                definition = definition,
                measurement = measurement,
                preview = result.copy(error = "Reconnect Spotify"),
            )
        )
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Add to Pulse"))
        compose.onNodeWithText("Add to Pulse").assertIsNotEnabled()
        screenshot("editor-error")
    }

    @Test
    fun pulseAndSingleConnectionsShortcutFitNarrowNavigation() {
        var destination by mutableStateOf(Destination.Home)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VoxTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PulseContent(PulseUiState(loading = false), vm)
                    VoxBottomNav(
                        null,
                        null,
                        {},
                        destination = destination,
                        onDestination = { destination = it },
                        modifier = Modifier.width(320.dp).align(Alignment.BottomStart),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pulse").assertIsDisplayed().performClick()
        assertEquals(Destination.Pulse, destination)
        compose.onNodeWithContentDescription("Connected Apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Talk to Vox").assertIsDisplayed()
        screenshot("navigation")
    }

    @Test
    fun profileKeepsPermissionsWithoutDuplicateConnections() {
        compose.setContent {
            VoxTheme(darkTheme = true) {
                VoxProfileSheet(
                    "Test User",
                    "fixture@example.com",
                    null,
                    true,
                    {},
                    {},
                    onReviewPermissions = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Connected Apps").assertDoesNotExist()
        compose.onNodeWithText("Connections").assertDoesNotExist()
        compose.onNodeWithText("Permissions").assertIsDisplayed()
        screenshot("profile")
    }
}
