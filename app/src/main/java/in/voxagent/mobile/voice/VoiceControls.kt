package `in`.voxagent.mobile.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite

@Composable
fun VoiceControls(
    status: VoiceStatus,
    muted: Boolean,
    speaking: Boolean,
    onMute: () -> Unit,
    onStop: () -> Unit,
    onSend: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var sendFailed by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        color = Obsidian.copy(alpha = 0.96f),
        contentColor = PureWhite,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        status == VoiceStatus.CONNECTING -> "Connecting to Vox…"
                        muted -> "Microphone muted"
                        speaking -> "Vox is speaking"
                        else -> "Vox is listening"
                    }
                )
                TextButton(onClick = onStop) { Text("End call") }
            }
            if (status == VoiceStatus.ACTIVE) {
                TextButton(onClick = onMute) {
                    Text(if (muted) "Unmute microphone" else "Mute microphone")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 2000) {
                            text = it
                            sendFailed = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Type to Vox") },
                    singleLine = true,
                    isError = sendFailed,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                if (onSend(text.trim())) {
                                    text = ""
                                    sendFailed = false
                                } else sendFailed = true
                            },
                            enabled = text.isNotBlank(),
                        ) {
                            Text("Send")
                        }
                    },
                )
                if (sendFailed) Text("Could not send. Try again.")
            }
        }
    }
}
