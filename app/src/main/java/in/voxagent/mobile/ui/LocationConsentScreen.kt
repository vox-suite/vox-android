package `in`.voxagent.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Slate
import `in`.voxagent.mobile.ui.theme.VoxFunnelDisplayFontFamily

private data class LocationConsentPoint(val title: String, val body: String)

private val consentPoints = listOf(
    LocationConsentPoint(
        "What's tracked",
        "Vox uses your phone's Activity Recognition to notice when you start and stop driving, walking, or cycling — not a continuous GPS trail.",
    ),
    LocationConsentPoint(
        "How stops get a name",
        "When you stay somewhere for 5+ minutes, Vox takes one location reading for that stop and looks it up with Google Places to show a name like \"Westfield Mall\" instead of just \"Stationary.\" That single point is never recorded while you're moving.",
    ),
    LocationConsentPoint(
        "Runs in the background",
        "This keeps working even when the app is closed. It needs a separate \"Allow all the time\" location permission from the usual foreground-only one, plus Android's Activity Recognition permission.",
    ),
    LocationConsentPoint(
        "What's kept",
        "Completed periods of movement or a named stop (e.g. \"Driving for 45 min\" or \"Westfield Mall for 1h 30min\") appear in your Vox Timeline — not continuous stationary time. Retained for 90 days by default, then automatically deleted.",
    ),
    LocationConsentPoint(
        "Your control",
        "You can revoke this at any time from this screen. Revoking stops new tracking; nothing already recorded is retroactively affected.",
    ),
)

@Composable
fun LocationConsentScreen(
    onAllow: () -> Unit,
    onDecline: () -> Unit,
    loading: Boolean,
    errorMessage: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Location & activity tracking",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = VoxFunnelDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            Text(
                text = "Before Vox tracks movement on this device, here's exactly what that means.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.copy(alpha = 0.4f)))

        Column {
            consentPoints.forEachIndexed { index, point ->
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = point.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = point.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                if (index != consentPoints.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Slate.copy(alpha = 0.4f)))
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = CoralPulse, fontSize = 13.sp)
            }

            VoxPrimaryButton(
                text = if (loading) "Saving…" else "Allow and continue",
                onClick = onAllow,
            )
            VoxTextButton(text = "Not now", onClick = onDecline)
        }
    }
}
