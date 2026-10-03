package `in`.voxagent.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.VoxFunnelDisplayFontFamily

private val permissionPoints = listOf(
    "Microphone" to "Lets you talk to Vox by voice. Audio is only captured while a voice session is active.",
    "SMS" to "Vox reads your text messages to spot bills, deliveries, appointments and other things that need action, and adds them to your Timeline. Message text is sent to an AI model to extract details and kept 256 days.",
    "Location (all the time)" to "Vox notices when you start and stop driving, walking or cycling, and names the places you stay at for 5+ minutes. It is not a continuous GPS trail. Kept 90 days, then deleted.",
    "Physical activity" to "Android's Activity Recognition tells Vox when you begin or end a trip, so location is only read when it matters.",
)

@Composable
fun PermissionsPrompt(
    onAgree: () -> Unit,
    onLater: () -> Unit,
    loading: Boolean,
    errorMessage: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Allow Vox to work for you",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = VoxFunnelDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            Text(
                text = "Vox needs these permissions. Tap Agree once and Android will ask for each of them. You can revoke any of them later from your profile or system settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            permissionPoints.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = CoralPulse, fontSize = 13.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VoxPrimaryButton(text = if (loading) "Saving…" else "Agree", onClick = { if (!loading) onAgree() })
            VoxTextButton(text = "Remind me later", onClick = onLater)
        }
    }
}
