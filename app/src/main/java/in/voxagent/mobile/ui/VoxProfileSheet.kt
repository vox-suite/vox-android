package `in`.voxagent.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.VoidBlack

@Composable
fun VoxProfileSheet(
    displayName: String?,
    email: String?,
    avatarUrl: String?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    onSyncProfile: (() -> Unit)? = null,
    onReviewPermissions: (() -> Unit)? = null,
    permissionsAllSet: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(VoidBlack.copy(alpha = 0.65f))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .then(
                            if (hazeState != null) {
                                Modifier.hazeEffect(
                                    state = hazeState,
                                    style =
                                        HazeDefaults.style(
                                            backgroundColor = VoidBlack.copy(alpha = 0.65f),
                                            tint = HazeTint(Ink.copy(alpha = 0.5f)),
                                            blurRadius = 24.dp,
                                        ),
                                )
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = if (hazeState != null) Color.Transparent else Ink.copy(alpha = 0.82f),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BorderSubtle)
                    )

                    VoxUserAvatar(avatarUrl = avatarUrl, displayName = displayName, size = 64.dp)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayName ?: "Vox User",
                            color = PureWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email,
                                color = Smoke,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (onReviewPermissions != null) {
                        VoxSettingsGroup {
                            VoxSettingsRow(
                                label = "Permissions",
                                trailingText = if (permissionsAllSet) "all set" else "Review",
                                trailingTone =
                                    if (permissionsAllSet) VoxStatusTone.Success
                                    else VoxStatusTone.Neutral,
                                showDivider = false,
                                onClick = {
                                    onDismiss()
                                    onReviewPermissions()
                                },
                            )
                        }
                    }

                    if (avatarUrl.isNullOrBlank() && onSyncProfile != null) {
                        VoxPrimaryButton(
                            text = "Sync Google Profile",
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                onDismiss()
                                onSyncProfile()
                            },
                        )
                    }

                    VoxTextButton(
                        text = "Sign Out",
                        tone = CoralPulse,
                        onClick = {
                            onDismiss()
                            onSignOut()
                        },
                    )
                }
            }
        }
    }
}
