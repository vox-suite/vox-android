package `in`.voxagent.mobile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import `in`.voxagent.mobile.auth.UserProfile
import `in`.voxagent.mobile.connections.ConnectionsScreen
import `in`.voxagent.mobile.map.MissionMapBackground
import `in`.voxagent.mobile.pulse.PulseScreen
import `in`.voxagent.mobile.spaces.SpacesScreen
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.timeline.SheetTarget
import `in`.voxagent.mobile.timeline.SpanSheet
import `in`.voxagent.mobile.timeline.TimelineScreen
import `in`.voxagent.mobile.ui.Destination
import `in`.voxagent.mobile.ui.TalkState
import `in`.voxagent.mobile.ui.VoxBottomNav
import `in`.voxagent.mobile.ui.VoxProfileSheet
import `in`.voxagent.mobile.ui.voxGrain
import `in`.voxagent.mobile.voice.VoiceSession
import `in`.voxagent.mobile.voice.VoiceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    token: String?,
    userProfile: UserProfile,
    locationPermissionGranted: Boolean,
    onSignOut: () -> Unit,
    onSyncProfile: (() -> Unit)? = null,
    onReviewPermissions: (() -> Unit)? = null,
    permissionsAllSet: Boolean = false,
) {
    val latestToken by rememberUpdatedState(token)
    var destination by remember { mutableStateOf(Destination.Home) }
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    var timelineReload by remember { mutableIntStateOf(0) }
    var timelineCollections by remember { mutableStateOf<List<SpanCollection>>(emptyList()) }
    var showProfileSheet by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val voiceContext = LocalContext.current
    val voiceSession = remember { VoiceSession(voiceContext) }
    var voiceStatus by remember { mutableStateOf(VoiceStatus.IDLE) }
    var recordAudioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(voiceContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val recordAudioLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            recordAudioGranted = granted
            if (granted) voiceSession.start()
        }
    LaunchedEffect(voiceSession) { voiceSession.status.collect { voiceStatus = it } }
    DisposableEffect(voiceSession) { onDispose { voiceSession.stop() } }

    var voxSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(voiceStatus) {
        while (voiceStatus == VoiceStatus.ACTIVE) {
            voxSpeaking = voiceSession.isVoxSpeaking()
            delay(100)
        }
        voxSpeaking = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            MissionMapBackground(
                modifier = Modifier.fillMaxSize(),
                locationPermissionGranted = locationPermissionGranted,
                token = { latestToken },
                callActive = voiceStatus == VoiceStatus.ACTIVE,
                voxSpeaking = voxSpeaking,
            )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .voxGrain(opacity = 0.05f, blendMode = BlendMode.SrcOver, tileSize = 140.dp)
            )
        }

        if (destination == Destination.Timeline && token != null) {
            TimelineScreen(
                token = { latestToken },
                bottomInset = 88.dp,
                onOpenSpan = { sheet = SheetTarget.Edit(it) },
                reloadSignal = timelineReload,
                onCollections = { timelineCollections = it },
            )
            sheet?.let { target ->
                SpanSheet(
                    target = target,
                    collections = timelineCollections,
                    token = { latestToken },
                    onClose = { sheet = null },
                    onSaved = { timelineReload += 1 },
                )
            }
        }

        if (destination == Destination.Pulse && token != null) {
            PulseScreen(token = { latestToken }, bottomInset = 88.dp)
        }

        if (destination == Destination.Spaces && token != null) {
            SpacesScreen(token = { latestToken }, bottomInset = 88.dp)
        }

        if (destination == Destination.Connections && token != null) {
            ConnectionsScreen(
                token = { latestToken },
                bottomInset = 88.dp,
                onBack = { destination = Destination.Home },
            )
        }

        VoxBottomNav(
            destination = destination,
            onDestination = { destination = it },
            talkState =
                when (voiceStatus) {
                    VoiceStatus.CONNECTING -> TalkState.Connecting
                    VoiceStatus.ACTIVE -> TalkState.Active
                    VoiceStatus.ERROR -> TalkState.Error
                    VoiceStatus.IDLE -> TalkState.Idle
                },
            onTalkClick = {
                when (voiceStatus) {
                    VoiceStatus.IDLE,
                    VoiceStatus.ERROR -> {
                        if (recordAudioGranted) {
                            voiceSession.start()
                        } else {
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    VoiceStatus.ACTIVE,
                    VoiceStatus.CONNECTING -> voiceSession.stop()
                }
            },
            avatarUrl = userProfile.avatarUrl,
            displayName = userProfile.displayName,
            onAvatarClick = { showProfileSheet = true },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        VoxProfileSheet(
            displayName = userProfile.displayName,
            email = userProfile.email,
            avatarUrl = userProfile.avatarUrl,
            visible = showProfileSheet,
            hazeState = hazeState,
            onDismiss = { showProfileSheet = false },
            onSignOut = onSignOut,
            onSyncProfile = onSyncProfile,
            onReviewPermissions = onReviewPermissions,
            permissionsAllSet = permissionsAllSet,
        )
    }
}
