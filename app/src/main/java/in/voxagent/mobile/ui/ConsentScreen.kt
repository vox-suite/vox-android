package `in`.voxagent.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.Slate

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
        "Extracted events (title, category, time) appear in your Vox Timeline. Retained for 90 days by default, then automatically deleted.",
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VoxWordmark()

        Text(
            text = "Timeline data sources",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
        )
        Text(
            text = "Before Vox reads any SMS on this device, here's exactly what that means.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )

        Column(
            modifier = Modifier
                .background(Ink, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Slate), RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            consentPoints.forEachIndexed { index, point ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = point.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = point.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (index != consentPoints.lastIndex) {
                    Row(Modifier.height(1.dp).background(Slate)) {}
                }
            }
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = CoralPulse,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(4.dp))

        VoxPrimaryButton(
            text = if (loading) "Saving…" else "Allow and continue",
            onClick = onAllow,
        )
        VoxSecondaryButton(text = "Not now", onClick = onDecline)
    }
}
