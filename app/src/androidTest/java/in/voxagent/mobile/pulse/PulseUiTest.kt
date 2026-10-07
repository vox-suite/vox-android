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
    private val goal =
        GoalView(
            "saving",
            "A new bike",
            "saving",
            "at_least",
            80000.0,
            "INR",
            current = 20000.0,
            percent = 25.0,
            status = "on_track",
            remaining = 60000.0,
            deadline = "2026-12-31",
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
        compose.onNodeWithText("No charts yet").assertIsDisplayed()
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
        compose.onAllNodesWithText("0 hrs").assertCountEquals(2)
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
        compose.onNodeWithText("Ask Pulse").assertIsDisplayed()
        compose.onNodeWithText("Goal").assertIsDisplayed()
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
        compose.onNodeWithText("Add to Pulse").performScrollTo().assertIsNotEnabled()
        screenshot("editor-error")
    }

    @Test
    fun askPulseHasChatAndChartPreview() {
        show(
            PulseUiState(
                mode = PulseMode.Ask,
                loading = false,
                title = "Daily listening",
                definition = definition,
                measurement = measurement,
                preview = result,
                messages =
                    listOf(
                        ComposeMessage("user", "Daily Spotify hours"),
                        ComposeMessage("assistant", "Here is your reported listening duration."),
                    ),
            )
        )
        compose.onNodeWithText("Daily Spotify hours").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send to Pulse").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Group by week")
        compose.onNodeWithContentDescription("Send to Pulse").assertIsEnabled()
        screenshot("ask")
    }

    @Test
    fun savingsRejectInvalidEntriesAndAllowWithdrawal() {
        show(PulseUiState(loading = false, goals = listOf(goal)))
        compose.onNodeWithText("Add").assertIsNotEnabled()
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("NaN")
        compose.onNodeWithText("Add").assertIsNotEnabled()
        input.performTextReplacement("-500")
        compose.onNodeWithText("Add").assertIsEnabled()
        screenshot("saving")
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete goal?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun goalComposerShowsPreviewAndExplicitCreate() {
        show(
            PulseUiState(
                mode = PulseMode.Goal,
                loading = false,
                goalPreview = goal,
                goalDraft =
                    GoalDraft(
                        title = goal.title,
                        kind = goal.kind,
                        direction = goal.direction,
                        target = goal.target,
                        unit = goal.unit,
                    ),
            )
        )
        compose.onNodeWithText("Create goal").performScrollTo().assertIsEnabled()
        screenshot("goal")
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
        compose.onNodeWithContentDescription("Google Account Avatar").assertIsDisplayed()
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
