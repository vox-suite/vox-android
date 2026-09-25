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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Graphite
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.Obsidian
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Slate
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.theme.VoxRed
import `in`.voxagent.mobile.R
import android.net.Uri
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

enum class VoxNavTab {
    Home,
    Agent,
}

/**
 * Floating bottom navigation menu matching the tactile Raycast / Vox design system.
 * Left: Pill with Home (Layers) and Agent (Bot/User) icon toggles.
 * Right: Circular button displaying the user's avatar from Google Sign-In.
 */
@Composable
fun VoxBottomNav(
    selectedTab: VoxNavTab,
    onTabSelected: (VoxNavTab) -> Unit,
    avatarUrl: String?,
    displayName: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Pill Container with 2 icons
        Surface(
            shape = RoundedCornerShape(50),
            color = VoidBlack,
            border = BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavPillButton(
                    selected = selectedTab == VoxNavTab.Home,
                    contentDescription = "Home Map",
                    onClick = { onTabSelected(VoxNavTab.Home) },
                ) { tint ->
                    VoxLogo(
                        size = 22.dp,
                        color = tint,
                        animated = true,
                    )
                }

                NavPillButton(
                    selected = selectedTab == VoxNavTab.Agent,
                    contentDescription = "Agent Cockpit",
                    onClick = { onTabSelected(VoxNavTab.Agent) },
                ) { tint ->
                    AgentIcon(tint = tint, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Right Circle with User Avatar
        Surface(
            shape = CircleShape,
            color = VoidBlack,
            border = BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAvatarClick,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                VoxUserAvatar(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    size = 46.dp,
                )
            }
        }
    }
}

@Composable
private fun NavPillButton(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val bgColor = if (selected) VoxRed else Color.Transparent
    val tintColor = if (selected) PureWhite else Smoke

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bgColor)
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
@Composable
fun LayersIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Top rhombus
        val topPath = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.32f)
            lineTo(w * 0.5f, h * 0.52f)
            lineTo(w * 0.12f, h * 0.32f)
            close()
        }
        drawPath(topPath, color = tint, style = stroke)

        // Middle chevron
        val midPath = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.5f, h * 0.72f)
            lineTo(w * 0.88f, h * 0.52f)
        }
        drawPath(midPath, color = tint, style = stroke)

        // Bottom chevron
        val botPath = Path().apply {
            moveTo(w * 0.12f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.92f)
            lineTo(w * 0.88f, h * 0.72f)
        }
        drawPath(botPath, color = tint, style = stroke)
    }
}

/**
 * Agent / Bot avatar icon matching the second tab in the screenshot.
 */
@Composable
fun AgentIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Outer circle
        drawCircle(
            color = tint,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = stroke,
        )

        // Head
        drawCircle(
            color = tint,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.38f),
            style = stroke,
        )

        // Shoulders arc
        val shouldersPath = Path().apply {
            moveTo(w * 0.25f, h * 0.76f)
            quadraticBezierTo(
                w * 0.5f, h * 0.56f,
                w * 0.75f, h * 0.76f,
            )
        }
        drawPath(shouldersPath, color = tint, style = stroke)
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
                    context.contentResolver.openInputStream(Uri.parse(avatarUrl))?.use { it.readBytes() }
                } else {
                    val request = Request.Builder()
                        .url(avatarUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .build()
                    VoxHttp.client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.bytes()
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
    onSyncProfile: (() -> Unit)? = null,
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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Ink,
                border = BorderStroke(1.dp, BorderSubtle),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

                    Spacer(modifier = Modifier.height(4.dp))

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

                    VoxSecondaryButton(
                        text = "Sign Out",
                        modifier = Modifier.fillMaxWidth(),
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
