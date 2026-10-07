package `in`.voxagent.mobile.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxStatusPill
import `in`.voxagent.mobile.ui.VoxStatusTone
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.SmokeDark
import `in`.voxagent.mobile.ui.theme.SuccessGreen
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionDetailSheet(
    connector: ConnectorDescriptor,
    connection: ConnectionItem?,
    brand: BrandMeta,
    busy: Boolean,
    pendingSetup: Boolean,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onConnect: (consent: Boolean, npsso: String?) -> Unit,
    onCancelPending: () -> Unit,
    onTogglePreference: (key: String, value: Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var consent by remember { mutableStateOf(false) }
    var npssoToken by remember { mutableStateOf("") }

    val isConnected = connection != null
    val needsAttention =
        connection != null &&
            (!connection.failure_code.isNullOrBlank() ||
                connection.authorization_state != "authorized")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Obsidian,
        contentColor = Mist,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .drawBehind {
                        val glowRadius = 320.dp.toPx()
                        val glowCenter = Offset(size.width, 0f)
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    0.0f to brand.color.copy(alpha = 0.35f),
                                    0.38f to brand.color.copy(alpha = 0.15f),
                                    0.70f to brand.color.copy(alpha = 0.03f),
                                    1.0f to Color.Transparent,
                                    center = glowCenter,
                                    radius = glowRadius,
                                ),
                            radius = glowRadius,
                            center = glowCenter,
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrandMark(brand = brand, name = connector.name, size = 52.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connector.name,
                        color = Mist,
                        fontFamily = VoxSpaceGroteskFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                    )
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        when {
                            needsAttention ->
                                VoxStatusPill(
                                    text = "needs attention",
                                    tone = VoxStatusTone.Warning,
                                )
                            isConnected ->
                                VoxStatusPill(text = "connected", tone = VoxStatusTone.Success)
                            else ->
                                VoxStatusPill(text = "not connected", tone = VoxStatusTone.Neutral)
                        }
                    }
                }
            }

            Text(text = connector.description, color = Smoke, fontSize = 13.sp, lineHeight = 19.sp)

            if (!statusMessage.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E22))
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                ) {
                    val (glyph, glyphColor) = statusGlyph(statusMessage)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = glyph,
                            color = glyphColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = statusMessage, color = Mist, fontSize = 12.sp)
                    }
                }
            }

            if (connection != null) {
                ConnectedAccountDetails(
                    connection,
                    busy,
                    onTogglePreference,
                    onRefresh,
                    onDisconnect,
                )
            }

            if (connector.id == "playstation") {
                PlayStationTokenInput(npssoToken, { npssoToken = it }, onOpenExternal)
            }

            ConnectionConsent(consent, { consent = it })

            if (pendingSetup) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onCancelPending,
                        shape = RoundedCornerShape(50),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = CoralPulse.copy(alpha = 0.15f),
                                contentColor = CoralPulse,
                            ),
                        border = BorderStroke(1.dp, CoralPulse.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Cancel Pending Setup", fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "Complete sign-in in your browser, then return to Vox.",
                        color = SmokeDark,
                        fontSize = 11.sp,
                    )
                }
            } else {
                val canConnect =
                    consent &&
                        (connector.id != "playstation" || npssoToken.trim().isNotEmpty()) &&
                        !busy

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    VoxPrimaryButton(
                        text =
                            if (isConnected) "Reconnect ${connector.name}"
                            else "Connect ${connector.name}",
                        onClick = { onConnect(consent, npssoToken.trim().ifEmpty { null }) },
                        icon = {
                            if (busy) {
                                CircularProgressIndicator(
                                    color = VoidBlack,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_plug),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                    if (!consent) {
                        Text(
                            text = "Accept the consent above to connect.",
                            color = SmokeDark,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreferenceToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    disabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = !disabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                color = SmokeDark,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !disabled,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PureWhite,
                    checkedTrackColor = SuccessGreen,
                    uncheckedThumbColor = SmokeDark,
                    uncheckedTrackColor = Obsidian,
                    uncheckedBorderColor = BorderSubtle,
                ),
        )
    }
}
