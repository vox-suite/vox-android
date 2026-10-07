package `in`.voxagent.mobile.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import `in`.voxagent.mobile.ui.theme.VoxTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConnectionsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun consentCanBeGrantedAndRevoked() {
        var consent by mutableStateOf(false)
        compose.setContent { VoxTheme { ConnectionConsent(consent, { consent = it }) } }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        assertTrue(consent)
        compose.onNode(isToggleable()).performClick().assertIsOff()
        assertFalse(consent)
    }

    @Test
    fun busyAccountDisablesPreferencesAndActions() {
        var calls = 0
        compose.setContent {
            VoxTheme {
                Column {
                    ConnectedAccountDetails(
                        connection =
                            ConnectionItem("fixture", "spotify", "Test account", "authorized"),
                        busy = true,
                        onTogglePreference = { _, _ -> calls++ },
                        onRefresh = { calls++ },
                        onDisconnect = { calls++ },
                    )
                }
            }
        }
        compose.onNodeWithText("Test account").assertIsDisplayed()
        compose.onNodeWithText("Refresh now").assertIsNotEnabled()
        compose.onNodeWithText("Disconnect").assertIsNotEnabled()
        compose.onAllNodes(isToggleable()).assertCountEquals(2)
        compose.onAllNodes(isToggleable())[0].assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[1].assertIsNotEnabled()
        assertEquals(0, calls)
    }

    @Test
    fun playStationTokenInputKeepsDraftAndOpensHelpLink() {
        var token by mutableStateOf("")
        var opened: String? = null
        compose.setContent {
            VoxTheme { PlayStationTokenInput(token, { token = it }, { opened = it }) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("fixture-token")
        assertEquals("fixture-token", token)
        compose.onNodeWithText("Show").performClick()
        compose.onNodeWithText("Hide").assertIsDisplayed()
        compose.onNodeWithText("How do I get this token?").performClick()
        compose.onNodeWithText("Open token page in browser").performClick()
        assertEquals("https://ca.account.sony.com/api/v1/ssocookie", opened)
    }
}
