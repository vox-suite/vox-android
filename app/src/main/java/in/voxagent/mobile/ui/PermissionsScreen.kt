package `in`.voxagent.mobile.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.location.LOCATION_RETENTION_DAYS
import `in`.voxagent.mobile.sms.SMS_RETENTION_DAYS
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Iron
import `in`.voxagent.mobile.ui.theme.LakeBlueDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.VoxFunnelDisplayFontFamily

private data class PermissionDetail(val title: String, val body: String)

private val smsDetails = listOf(
    PermissionDetail(
        "What's sent to a model",
        "Message content is sent to an AI model (Gemini) to classify what each message is about. Nothing is sent for messages that look like one-time passcodes or verification codes — those are filtered out on this device before upload.",
    ),
    PermissionDetail(
        "What's kept",
        "Extracted events (title, category, time) appear in your Vox Timeline. Retained for $SMS_RETENTION_DAYS days by default, then automatically deleted.",
    ),
    PermissionDetail(
        "Your control",
        "Turn this off at any time. Revoking stops new messages from being read; nothing already processed is retroactively affected.",
    ),
)

private val locationDetails = listOf(
    PermissionDetail(
        "How stops get a name",
        "When you stay somewhere for 5+ minutes, Vox takes one location reading for that stop and looks it up with Google Places to show a name like \"Westfield Mall\" instead of just \"Stationary.\" That single point is never recorded while you're moving.",
    ),
    PermissionDetail(
        "Runs in the background",
        "This keeps working even when the app is closed. It needs a separate \"Allow all the time\" location permission from the usual foreground-only one, plus Android's Activity Recognition permission.",
    ),
    PermissionDetail(
        "What's kept",
        "Completed periods of movement or a named stop (e.g. \"Driving for 45 min\" or \"Westfield Mall for 1h 30min\") appear in your Vox Timeline — not continuous stationary time. Retained for $LOCATION_RETENTION_DAYS days by default, then automatically deleted.",
    ),
    PermissionDetail(
        "Your control",
        "Turn this off at any time. Revoking stops tracking immediately; nothing already recorded is retroactively affected.",
    ),
)

@Composable
fun PermissionsScreen(
    micOn: Boolean,
    smsOn: Boolean,
    locationOn: Boolean,
    busy: Boolean,
    errorMessage: String,
    onToggleMic: (Boolean) -> Unit,
    onToggleSms: (Boolean) -> Unit,
    onToggleLocation: (Boolean) -> Unit,
    onSyncSms: () -> Unit,
    onBackfillSms: () -> Unit,
    onOpenSettings: () -> Unit,
    onAllowAll: () -> Unit,
    onClose: () -> Unit,
) {
    val allOn = micOn && smsOn && locationOn
    VoxDarkScreen {
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Permissions",
                        color = Mist,
                        fontFamily = VoxFunnelDisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                    )
                    Text(
                        text = "Choose what Vox can use. You can turn any of these off at any time.",
                        color = GraphiteDark,
                        fontSize = 13.sp,
                    )
                }
                PermissionRow(
                    title = "Microphone",
                    summary = "Lets you talk to Vox by voice. Audio is only captured while a voice session is active.",
                    details = emptyList(),
                    checked = micOn,
                    enabled = !busy,
                    onCheckedChange = onToggleMic,
                )
                PermissionRow(
                    title = "SMS",
                    summary = "Vox reads your text messages to spot bills, deliveries, appointments and other things that need action, and adds them to your Timeline.",
                    details = smsDetails,
                    checked = smsOn,
                    enabled = !busy,
                    onCheckedChange = onToggleSms,
                    actions = if (smsOn) {
                        listOf("Sync now" to onSyncSms, "Sync last 3 months" to onBackfillSms)
                    } else {
                        emptyList()
                    },
                )
                PermissionRow(
                    title = "Location & activity",
                    summary = "Vox notices when you start and stop driving, walking or cycling, and names the places you stay at for 5+ minutes. It is not a continuous GPS trail.",
                    details = locationDetails,
                    checked = locationOn,
                    enabled = !busy,
                    onCheckedChange = onToggleLocation,
                )
                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = CoralPulse, fontSize = 13.sp)
                }
                VoxTextButton(text = "Open system settings", onClick = onOpenSettings)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VoxPrimaryButton(
                    text = when {
                        busy -> "Saving…"
                        allOn -> "Done"
                        else -> "Allow all"
                    },
                    onClick = { if (!busy) { if (allOn) onClose() else onAllowAll() } },
                )
                if (!allOn) VoxTextButton(text = "Not now", onClick = onClose)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    details: List<PermissionDetail>,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Mist,
                    checkedTrackColor = LakeBlueDark,
                    uncheckedThumbColor = GraphiteDark,
                    uncheckedTrackColor = Iron,
                    uncheckedBorderColor = Iron,
                ),
            )
        }
        Text(text = summary, color = GraphiteDark, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (details.isNotEmpty()) {
                LinkText(if (expanded) "Hide details" else "Details") { expanded = !expanded }
            }
            actions.forEach { (label, onClick) -> LinkText(label, onClick) }
        }
        if (expanded) {
            details.forEach { detail ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = detail.title, color = Mist, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(text = detail.body, color = GraphiteDark, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = LakeBlueDark,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
