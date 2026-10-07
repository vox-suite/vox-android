package `in`.voxagent.mobile.spaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import `in`.voxagent.mobile.ui.Destination
import `in`.voxagent.mobile.ui.VoxBottomNav
import `in`.voxagent.mobile.ui.theme.VoxTheme
import org.junit.Rule
import org.junit.Test

class SpacesUiTest {
    @get:Rule val compose = createComposeRule()
    private val vm = SpacesViewModel { null }
    private val space =
        Space(
            "fixture-space",
            "Weekend getaway",
            "A quiet weekend near Bangalore. Compare driving time, budget and places to stay.",
            created_at = "2026-10-07T06:00:00Z",
        )
    private val graph =
        SpaceGraph(
            space,
            listOf(
                SpaceNode(
                    "goal",
                    "goal",
                    "A quiet weekend",
                    "Two days, a realistic budget and room to unwind.",
                ),
                SpaceNode(
                    "research",
                    "research",
                    "Driving time",
                    "Compare nearby destinations within four hours.",
                ),
                SpaceNode(
                    "budget",
                    "budget",
                    "Budget",
                    "Set a budget after reviewing recent expenditure.",
                ),
                SpaceNode(
                    "option",
                    "option",
                    "Coorg",
                    "Coffee estates, short walks and quiet stays.",
                ),
                SpaceNode(
                    "option2",
                    "option",
                    "Chikmagalur",
                    "Hill country with scenic drives and homestays.",
                ),
                SpaceNode(
                    "plan",
                    "plan",
                    "Weekend itinerary",
                    "Leave Saturday morning. Return Sunday evening.",
                ),
            ),
            listOf(
                SpaceEdge("e1", "goal", "research"),
                SpaceEdge("e2", "goal", "budget"),
                SpaceEdge("e3", "goal", "option"),
                SpaceEdge("e4", "goal", "option2"),
                SpaceEdge("e5", "option", "plan"),
            ),
        )

    private fun show(ui: SpacesUiState) {
        compose.setContent {
            VoxTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) { SpacesContent(ui, vm, 88.dp) }
            }
        }
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        settle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/Download/vox-spaces-qa").use { descriptor
            ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        automation
            .executeShellCommand("screencap -p /sdcard/Download/vox-spaces-qa/$name.png")
            .use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.readBytes()
                }
            }
    }

    @Test
    fun navigationSelectsSpacesOnNarrowPhone() {
        var destination by mutableStateOf(Destination.Home)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VoxTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    VoxBottomNav(
                        null,
                        null,
                        {},
                        destination = destination,
                        onDestination = { destination = it },
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        settle()
        compose.onNodeWithContentDescription("Spaces").assertIsDisplayed().performClick()
        settle()
        org.junit.Assert.assertEquals(Destination.Spaces, destination)
        compose.onNodeWithContentDescription("Google Account Avatar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Talk to Vox").assertIsDisplayed()
        screenshot("navigation")
    }

    @Test
    fun emptyLibraryOpensCreateSheetAndRequiresBothFields() {
        show(SpacesUiState(loading = false))
        compose.onNodeWithText("Map out a decision.").assertIsDisplayed()
        screenshot("empty")
        compose.onNodeWithText("Create first space").performClick()
        settle()
        compose.onNodeWithText("Create space").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Weekend getaway")
        compose
            .onNode(hasSetTextAction() and hasText("Vision & intent"))
            .performTextInput("Compare quiet places nearby and plan a two-day trip.")
        settle()
        compose.onNodeWithText("Create space").assertIsEnabled()
        screenshot("create")
    }

    @Test
    fun radialMapCardsAndNodeEditingWorkOnPhone() {
        show(SpacesUiState(selectedId = space.id, graph = graph, loading = false))
        compose.onNodeWithText("Fit map").assertIsDisplayed()
        screenshot("map")
        compose.onNodeWithText("+").performClick()
        settle()
        compose.onNodeWithText("A quiet weekend").assertIsDisplayed().performClick()
        settle()
        compose.onNodeWithText("SELECTED / GOAL").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        settle()
        compose.onNodeWithText("Show cards").performClick()
        settle()
        compose.onNodeWithText("A quiet weekend").assertIsDisplayed().performClick()
        settle()
        compose
            .onNode(hasSetTextAction() and hasText("Title"))
            .performTextReplacement("A relaxed weekend")
        settle()
        compose.onNodeWithText("Save changes").assertIsEnabled()
        compose.onNodeWithText("Reject").assertIsDisplayed()
        compose.onNodeWithText("Dig deeper").assertIsDisplayed()
        screenshot("node")
    }

    @Test
    fun committedSpaceDisablesEditingAndCommit() {
        show(
            SpacesUiState(
                selectedId = space.id,
                graph = graph.copy(space = space.copy(state = "committed")),
                loading = false,
            )
        )
        compose.onNodeWithText("Read-only").assertIsNotEnabled()
        compose.onNodeWithText("Show cards").performClick()
        settle()
        compose.onNodeWithText("A quiet weekend").performClick()
        settle()
        compose.onNodeWithText("This space is read-only.").assertIsDisplayed()
        compose.onNodeWithText("Reject").assertDoesNotExist()
        screenshot("committed")
    }

    @Test
    fun chatShowsHistoryAndRetainsUnsentDraft() {
        show(
            SpacesUiState(
                selectedId = space.id,
                graph = graph,
                loading = false,
                messages =
                    listOf(
                        SpaceMessage(
                            "message",
                            "assistant",
                            "I’m comparing nearby destinations and a realistic budget.",
                            "2026-10-07T06:15:00Z",
                        )
                    ),
            )
        )
        compose.onNodeWithContentDescription("Space chat").performClick()
        settle()
        compose
            .onNodeWithText("I’m comparing nearby destinations and a realistic budget.")
            .assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Make it a day trip")
        settle()
        compose.onNodeWithContentDescription("Send message").assertIsEnabled()
        compose.onNodeWithText("Make it a day trip").assertIsDisplayed()
        screenshot("chat")
    }

    @Test
    fun commitDialogExplainsTimelineEffect() {
        show(SpacesUiState(selectedId = space.id, graph = graph, loading = false))
        compose.onNodeWithText("Commit plan").performClick()
        settle()
        compose.onNodeWithText("Commit this plan?").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Create a collection and 1 planned timeline items from the completed plan and step cards. This space becomes read-only."
            )
            .assertIsDisplayed()
        screenshot("commit")
    }

    @Test
    fun libraryCardsAndDropConfirmationMatchDesktop() {
        show(SpacesUiState(spaces = listOf(space), loading = false))
        compose.onNodeWithText(space.title).assertIsDisplayed()
        screenshot("library")
        compose.onNodeWithContentDescription("Drop ${space.title}").performClick()
        settle()
        compose.onNodeWithText("Drop this space?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        settle()
        compose.onNodeWithText(space.title).assertIsDisplayed()
    }
}
