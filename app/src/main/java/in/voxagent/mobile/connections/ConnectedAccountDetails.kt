package `in`.voxagent.mobile.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.VoidBlack

@Composable
internal fun ColumnScope.ConnectedAccountDetails(
    connection: ConnectionItem,
    busy: Boolean,
    onTogglePreference: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VoidBlack)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text = "ACCOUNT",
                color = SmokeDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = connection.account_display_id ?: "Connected account",
                color = Mist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column {
            Text(
                text = "LAST SYNCED",
                color = SmokeDark,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = formatSyncDate(connection.last_synced_at),
                color = Mist,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    if (!connection.failure_code.isNullOrBlank()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2016))
                    .border(BorderStroke(1.dp, Color(0xFF6B4826)), RoundedCornerShape(10.dp))
                    .padding(12.dp)
        ) {
            Text(
                text =
                    if (
                        connection.failure_code == "reconnect_required" ||
                            connection.failure_code == "consent_required"
                    ) {
                        "Reconnect this account to restore sync access."
                    } else {
                        "The last sync failed. Try refreshing."
                    },
                color = Color(0xFFFFD4A3),
                fontSize = 12.sp,
            )
        }
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VoidBlack)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
    ) {
        PreferenceToggleRow(
            title = "Sync to timeline",
            description = "Synchronize events & sessions to your daily timeline",
            checked = connection.sync_timeline,
            disabled = busy,
            onCheckedChange = { onTogglePreference("sync_timeline", it) },
        )
        Box(
            modifier =
                Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle.copy(alpha = 0.5f))
        )
        PreferenceToggleRow(
            title = "Allow assistant reads",
            description = "Permit Vox assistant to access activity context",
            checked = connection.assistant_read,
            disabled = busy,
            onCheckedChange = { onTogglePreference("assistant_read", it) },
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onRefresh,
            enabled = !busy && connection.authorization_state == "authorized",
            shape = RoundedCornerShape(10.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = BorderSubtle.copy(alpha = 0.5f),
                    contentColor = Mist,
                ),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.weight(1f).height(44.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text("Refresh now", fontSize = 13.sp)
            }
        }

        Button(
            onClick = onDisconnect,
            enabled = !busy,
            shape = RoundedCornerShape(10.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF261214),
                    contentColor = CoralPulse,
                ),
            border = BorderStroke(1.dp, Color(0xFF5E272B)),
            modifier = Modifier.weight(1f).height(44.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_unplug),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text("Disconnect", fontSize = 13.sp)
            }
        }
    }
}
