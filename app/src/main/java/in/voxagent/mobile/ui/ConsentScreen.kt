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
import `in`.voxagent.mobile.sms.SMS_RETENTION_DAYS
import `in`.voxagent.mobile.ui.theme.Slate
import `in`.voxagent.mobile.ui.theme.VoxFunnelDisplayFontFamily

private data class ConsentPoint(val title: String, val body: String)

private val consentPoints = listOf(
    ConsentPoint(
        "What's read",
        "Vox reads SMS messages on this device to build your activity timeline — payments, deliveries, appointments, and similar events.",
    ),
    ConsentPoint(
        "What's sent to a model",
        "Message content is sent to an AI model (Gemini) to classify what each message is about. Nothing is sent for messages that look like one-time passcodes or verification codes — those are filtered out on this device before upload.",
    ),
    ConsentPoint(
        "What's kept",
        "Extracted events (title, category, time) appear in your Vox Timeline. Retained for $SMS_RETENTION_DAYS days by default, then automatically deleted.",
    ),
    ConsentPoint(
        "Your control",
        "You can revoke this at any time from this screen. Revoking stops new messages from being read; nothing already processed is retroactively affected.",
    ),
)

@Composable
fun ConsentScreen(
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
                text = "Timeline data sources",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = VoxFunnelDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            Text(
                text = "Before Vox reads any SMS on this device, here's exactly what that means.",
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
                Text(
                    text = errorMessage,
                    color = CoralPulse,
                    fontSize = 13.sp,
                )
            }

            VoxPrimaryButton(
                text = if (loading) "Saving…" else "Allow and continue",
                onClick = onAllow,
            )
            VoxTextButton(text = "Not now", onClick = onDecline)
        }
    }
}
