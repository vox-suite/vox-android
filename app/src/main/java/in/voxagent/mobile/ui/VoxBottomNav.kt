package `in`.voxagent.mobile.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.theme.VoxRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class VoxNavTab {
    Home,
    Agent,
    Layers,
    Span,
}

/**
 * Unified bottom navigation bar matching the tactile Vox design system.
 * Full-length translucent container with subtle border:
 * - Left: Vox Logo icon button
 * - Center: Red elongated pill with Mic icon and "Talk to Vox" text
 * - Right: Circular button displaying the user's Google avatar
 */
@Composable
fun VoxBottomNav(
    avatarUrl: String?,
    displayName: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: VoxNavTab = VoxNavTab.Home,
    onTabSelected: (VoxNavTab) -> Unit = {},
    talkText: String = "Speak",
    onTalkClick: (() -> Unit)? = null,
    onLogoClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    val containerSurfaceModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeDefaults.style(
                backgroundColor = VoidBlack.copy(alpha = 0.65f),
                tint = HazeTint(Ink.copy(alpha = 0.45f)),
                blurRadius = 24.dp,
            ),
        )
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Section 1 (Left): Translucent Pill with Vox Animated Logo + Agent Icon
        Surface(
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .then(containerSurfaceModifier),
            shape = RoundedCornerShape(50),
            color = if (hazeState != null) Color.Transparent else VoidBlack.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavPillButton(
                    selected = selectedTab == VoxNavTab.Home,
                    contentDescription = "Home Map",
                    onClick = {
                        if (onLogoClick != null) onLogoClick() else onTabSelected(VoxNavTab.Home)
                    },
                ) { tint ->
                    VoxLogo(
                        size = 24.dp,
                        color = tint,
                        animated = true,
                        modifier = Modifier.aspectRatio(1f),
                    )
                }

                NavPillButton(
                    selected = selectedTab == VoxNavTab.Span,
                    contentDescription = "Span",
                    onClick = { onTabSelected(VoxNavTab.Span) },
                ) { tint ->
                    SpanIcon(
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .aspectRatio(1f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Right cluster: Mic circular container and Avatar container separated by a gap
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Section 2: Translucent "Speak" Pill with Mic Icon and matched text (no glow)
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .then(containerSurfaceModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (onTalkClick != null) {
                                onTalkClick()
                            } else {
                                onTabSelected(
                                    if (selectedTab == VoxNavTab.Agent) VoxNavTab.Home else VoxNavTab.Agent,
                                )
                            }
                        },
                    ),
                shape = RoundedCornerShape(50),
                color = if (hazeState != null) Color.Transparent else VoidBlack.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Speak",
                        tint = PureWhite,
                        modifier = Modifier
                            .size(24.dp)
                            .aspectRatio(1f),
                    )
                }
            }

            // Section 3: Translucent Circular Avatar Container
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .then(containerSurfaceModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAvatarClick,
                    ),
                shape = CircleShape,
                color = if (hazeState != null) Color.Transparent else VoidBlack.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
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

/**
 * Microphone icon vector drawn with Compose Canvas.
 */
@Composable
fun MicIcon(
    modifier: Modifier = Modifier,
    tint: Color = PureWhite,
) {
    Icon(
        painter = painterResource(R.drawable.ic_mic),
        contentDescription = null,
        tint = tint,
        modifier = modifier.aspectRatio(1f),
    )
}

@Suppress("unused")
@Composable
private fun NavPillButton(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val tintColor = if (selected) PureWhite else PureWhite.copy(alpha = 0.38f)

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon(tintColor)
    }
}

/**
 * Isometric 3-layer stacked sheets icon matching the reference screenshot.
 */
@Suppress("unused")
@Composable
fun LayersIcon(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_stack),
        contentDescription = "Layers",
        tint = tint,
        modifier = modifier.aspectRatio(1f),
    )
}

/**
 * Geometric shapes icon (circle, cross, triangle, square) matching Tabler ti-icons.
 */
@Suppress("unused")
@Composable
fun AgentIcon(tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_shapes),
        contentDescription = "Agent Cockpit",
        tint = tint,
        modifier = modifier.aspectRatio(1f),
    )
}

/**
 * Gantt-chart timeline icon — mirrors the GanttChart lucide icon used on desktop
 * for the Span tab. Drawn as three staggered horizontal bars of varying width.
 */
@Composable
fun SpanIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.10f
        val radius = strokeW / 2f
        val barH = strokeW

        // Row 1: full bar from 0.1 to 0.9
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.20f - barH / 2),
            size = androidx.compose.ui.geometry.Size(w * 0.80f, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        // Row 2: shorter bar from 0.1 to 0.55
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.50f - barH / 2),
            size = androidx.compose.ui.geometry.Size(w * 0.45f, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        // Row 3: bar shifted right from 0.35 to 0.90
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.35f, h * 0.80f - barH / 2),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
    }
}

/**
 * Asynchronously loads and caches the user's avatar from Google Sign-In,
 * falling back gracefully to a stylized initial letter when offline or unavailable.
 */
@Composable
fun VoxUserAvatar(
    avatarUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    var avatarBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val cacheKey = avatarUrl.hashCode().toString()
                val cacheFile = File(context.cacheDir, "vox_user_avatar_$cacheKey.png")
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val cachedBm = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (cachedBm != null) {
                        avatarBitmap = cachedBm.asImageBitmap()
                        return@withContext
                    }
                }

                val bytes: ByteArray? = if (avatarUrl.startsWith("content://") || avatarUrl.startsWith("file://")) {
                    context.contentResolver.openInputStream(avatarUrl.toUri())?.use { it.readBytes() }
                } else {
                    val request = Request.Builder()
                        .url(avatarUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .build()
                    VoxHttp.client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body.bytes()
                        } else null
                    }
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (decoded != null) {
                        runCatching { cacheFile.writeBytes(bytes) }
                        avatarBitmap = decoded.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                // Ignore network issues, fallback renders
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Obsidian)
            .border(BorderStroke(1.dp, BorderSubtle), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = avatarBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = displayName ?: "Google User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!displayName.isNullOrBlank()) {
            Text(
                text = displayName.trim().first().uppercase(),
                color = PureWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Google Account Avatar",
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

/**
 * Slide-up User Profile and settings panel shown when the avatar is clicked.
 */
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
    smsDataSharingGranted: Boolean = false,
    onReviewDataSharing: (() -> Unit)? = null,
    onSyncSmsNow: (() -> Unit)? = null,
    onReviewLocationTracking: (() -> Unit)? = null,
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
            modifier = Modifier
                .fillMaxSize()
                .background(VoidBlack.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeDefaults.style(
                                    backgroundColor = VoidBlack.copy(alpha = 0.65f),
                                    tint = HazeTint(Ink.copy(alpha = 0.5f)),
                                    blurRadius = 24.dp,
                                ),
                            )
                        } else Modifier,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BorderSubtle),
                    )

                    // Avatar & Details
                    VoxUserAvatar(
                        avatarUrl = avatarUrl,
                        displayName = displayName,
                        size = 64.dp,
                    )

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

                    if (onReviewDataSharing != null || onReviewLocationTracking != null || onReviewPermissions != null) {
                        VoxSettingsGroup {
                            if (onReviewPermissions != null) {
                                VoxSettingsRow(
                                    label = "Permissions",
                                    trailingText = if (permissionsAllSet) "all set" else "Review",
                                    trailingTone = if (permissionsAllSet) VoxStatusTone.Success else VoxStatusTone.Neutral,
                                    showDivider = onReviewDataSharing != null || onReviewLocationTracking != null,
                                    onClick = {
                                        onDismiss()
                                        onReviewPermissions()
                                    },
                                )
                            }
                            if (onReviewDataSharing != null) {
                                VoxSettingsRow(
                                    label = "Data sharing",
                                    trailingText = if (smsDataSharingGranted) "on" else "Review",
                                    trailingTone = if (smsDataSharingGranted) VoxStatusTone.Success else VoxStatusTone.Neutral,
                                    showDivider = (smsDataSharingGranted && onSyncSmsNow != null) || onReviewLocationTracking != null,
                                    onClick = {
                                        onDismiss()
                                        onReviewDataSharing()
                                    },
                                )
                            }
                            if (smsDataSharingGranted && onSyncSmsNow != null) {
                                VoxSettingsRow(
                                    label = "Sync SMS now",
                                    showDivider = onReviewLocationTracking != null,
                                    onClick = {
                                        onDismiss()
                                        onSyncSmsNow()
                                    },
                                )
                            }
                            if (onReviewLocationTracking != null) {
                                VoxSettingsRow(
                                    label = "Location tracking",
                                    showDivider = false,
                                    onClick = {
                                        onDismiss()
                                        onReviewLocationTracking()
                                    },
                                )
                            }
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
