package `in`.voxagent.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
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
import `in`.voxagent.mobile.ui.theme.VoidBlack

@Composable
fun VoxBottomNav(
    avatarUrl: String?,
    displayName: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    destination: Destination = Destination.Home,
    onDestination: (Destination) -> Unit = {},
    talkState: TalkState = TalkState.Idle,
    onTalkClick: () -> Unit = {},
    hazeState: HazeState? = null,
) {
    val containerSurfaceModifier =
        if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style =
                    HazeDefaults.style(
                        backgroundColor = VoidBlack.copy(alpha = 0.65f),
                        tint = HazeTint(Ink.copy(alpha = 0.45f)),
                        blurRadius = 24.dp,
                    ),
            )
        } else Modifier

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val compact = maxWidth < 420.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier =
                    Modifier.height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .then(containerSurfaceModifier),
                shape = RoundedCornerShape(50),
                color = if (hazeState != null) Color.Transparent else VoidBlack.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp),
                ) {
                    NavDestinationButton(
                        compact = compact,
                        selected = destination == Destination.Home,
                        description = "Home",
                        onClick = { onDestination(Destination.Home) },
                    ) { tint ->
                        VoxLogo(
                            size = 30.dp,
                            color = tint,
                            animated = true,
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                    NavDestinationButton(
                        compact = compact,
                        selected = destination == Destination.Timeline,
                        description = "Timeline",
                        onClick = { onDestination(Destination.Timeline) },
                    ) { tint ->
                        CalendarIcon(tint = tint, modifier = Modifier.size(22.dp))
                    }
                    NavDestinationButton(
                        compact = compact,
                        selected = destination == Destination.Spaces,
                        description = "Spaces",
                        onClick = { onDestination(Destination.Spaces) },
                    ) { tint ->
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Hub,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    NavDestinationButton(
                        compact = compact,
                        selected = destination == Destination.Pulse,
                        description = "Pulse",
                        onClick = { onDestination(Destination.Pulse) },
                    ) { tint ->
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.MonitorHeart,
                            null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    NavDestinationButton(compact = compact, selected = destination == Destination.Updates,
                        description = "Updates", onClick = { onDestination(Destination.Updates) }) { tint ->
                        Text("◉", color = tint, modifier = Modifier.size(20.dp))
                    }
                    NavDestinationButton(
                        compact = compact,
                        selected = destination == Destination.Connections,
                        description = "Connected Apps",
                        onClick = { onDestination(Destination.Connections) },
                    ) { tint ->
                        Icon(
                            painter = painterResource(R.drawable.ic_plug),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val active = talkState == TalkState.Active
                val pillColor by
                    animateColorAsState(
                        targetValue =
                            when {
                                active -> CoralPulse.copy(alpha = 0.92f)
                                hazeState != null -> Color.Transparent
                                else -> VoidBlack.copy(alpha = 0.72f)
                            },
                        label = "talkPillColor",
                    )
                val pillBorder by
                    animateColorAsState(
                        targetValue =
                            when {
                                active -> CoralPulse
                                talkState == TalkState.Error -> CoralPulse.copy(alpha = 0.7f)
                                else -> BorderSubtle.copy(alpha = 0.6f)
                            },
                        label = "talkPillBorder",
                    )
                Surface(
                    modifier =
                        Modifier.height(52.dp)
                            .clip(RoundedCornerShape(50))
                            .then(containerSurfaceModifier)
                            .semantics {
                                contentDescription =
                                    when (talkState) {
                                        TalkState.Active -> "End call"
                                        TalkState.Connecting -> "Connecting to Vox"
                                        TalkState.Error -> "Retry voice call"
                                        TalkState.Idle -> "Talk to Vox"
                                    }
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTalkClick,
                            ),
                    shape = RoundedCornerShape(50),
                    color = pillColor,
                    border = BorderStroke(1.dp, pillBorder),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier =
                            Modifier.animateContentSize()
                                .padding(horizontal = if (compact) 14.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (active) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(12.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(PureWhite)
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = null,
                                tint =
                                    if (talkState == TalkState.Connecting)
                                        PureWhite.copy(alpha = 0.5f)
                                    else PureWhite,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (!compact)
                            Text(
                                text =
                                    when (talkState) {
                                        TalkState.Idle -> "Talk"
                                        TalkState.Connecting -> "Connecting…"
                                        TalkState.Active -> "End call"
                                        TalkState.Error -> "Retry"
                                    },
                                color = PureWhite,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                    }
                }

                Surface(
                    modifier =
                        Modifier.size(52.dp)
                            .clip(CircleShape)
                            .then(containerSurfaceModifier)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onAvatarClick,
                            ),
                    shape = CircleShape,
                    color =
                        if (hazeState != null) Color.Transparent else VoidBlack.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f)),
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VoxUserAvatar(
                            avatarUrl = avatarUrl,
                            displayName = displayName,
                            size = 40.dp,
                        )
                    }
                }
            }
        }
    }
}
