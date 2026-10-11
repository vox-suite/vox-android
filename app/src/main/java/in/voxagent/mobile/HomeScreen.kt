package `in`.voxagent.mobile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.repeatOnLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import `in`.voxagent.mobile.auth.UserProfile
import `in`.voxagent.mobile.connections.ConnectionsScreen
import `in`.voxagent.mobile.map.MissionMapBackground
import `in`.voxagent.mobile.pulse.PulseScreen
import `in`.voxagent.mobile.spaces.SpacesScreen
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
    val voiceContext = LocalContext.current
    val control = remember(token) { `in`.voxagent.mobile.control.AppControlState() }
    val appContext by control.context.collectAsState()
    val destination =
        when (appContext.page) {
            "timeline" -> Destination.Timeline
            "spaces" -> Destination.Spaces
            "pulse" -> Destination.Pulse
            "connections" -> Destination.Connections
            "updates" -> Destination.Updates
            else -> Destination.Home
        }
    fun navigate(destination: Destination) =
        control.navigate(
            when (destination) {
                Destination.Timeline -> "timeline"
                Destination.Spaces -> "spaces"
                Destination.Pulse -> "pulse"
                Destination.Connections -> "connections"
                Destination.Updates -> "updates"
                else -> "agent"
            }
        )
    val deviceLink =
        remember(control) {
            `in`.voxagent.mobile.control.AppDeviceLink(voiceContext, control) { latestToken }
        }
    var sheet by remember { mutableStateOf<`in`.voxagent.mobile.spans.Span?>(null) }
    var showProfileSheet by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val voiceSession =
        remember(deviceLink) { VoiceSession(voiceContext) { deviceLink.awaitDevice() } }
    var voiceStatus by remember { mutableStateOf(VoiceStatus.IDLE) }
    var voiceMuted by remember { mutableStateOf(false) }
    val voiceSnackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val permissionScope = androidx.compose.runtime.rememberCoroutineScope()
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
            else
                permissionScope.launch {
                    voiceSnackbar.showSnackbar("Microphone permission is needed to call Vox")
                }
        }
    LaunchedEffect(voiceSession) { voiceSession.status.collect { voiceStatus = it } }
    DisposableEffect(voiceSession, lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) voiceSession.stop()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(voiceSession, deviceLink) {
        onDispose {
            voiceSession.dispose()
            deviceLink.dispose()
        }
    }
    LaunchedEffect(lifecycleOwner, deviceLink) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            deviceLink.start()
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                deviceLink.stop()
            }
        }
    }
    LaunchedEffect(voiceSession) {
        voiceSession.events.collect { event ->
            if (event is `in`.voxagent.mobile.voice.VoiceEvent.Error)
                voiceSnackbar.showSnackbar(event.message)
        }
    }
    LaunchedEffect(voiceStatus) { if (voiceStatus != VoiceStatus.ACTIVE) voiceMuted = false }
    LaunchedEffect(token) { if (token == null) voiceSession.stop() }

    var voxSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(voiceStatus) {
        while (voiceStatus == VoiceStatus.ACTIVE) {
            voxSpeaking = voiceSession.isVoxSpeaking()
            delay(100)
        }
        voxSpeaking = false
    }

    androidx.compose.runtime.CompositionLocalProvider(
        `in`.voxagent.mobile.control.LocalAppControl provides control
    ) {
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
                            .voxGrain(
                                opacity = 0.05f,
                                blendMode = BlendMode.SrcOver,
                                tileSize = 140.dp,
                            )
                )
            }

            if (destination == Destination.Timeline && token != null) {
                TimelineScreen(
                    token = { latestToken },
                    bottomInset = 88.dp,
                    onOpenSpan = { sheet = it },
                )
                sheet?.let { event ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { sheet = null },
                        title = { androidx.compose.material3.Text(event.title) },
                        text = {
                            androidx.compose.material3.Text(
                                event.notes + "\n" + (event.data?.toString() ?: "")
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { sheet = null }) {
                                androidx.compose.material3.Text("Close")
                            }
                        },
                    )
                }
            }

            if (destination == Destination.Updates && token != null) {
                `in`.voxagent.mobile.updates.UpdatesScreen(
                    token = { latestToken },
                    bottomInset = 88.dp,
                )
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
                    onBack = { navigate(Destination.Home) },
                )
            }

            if (voiceStatus == VoiceStatus.ACTIVE || voiceStatus == VoiceStatus.CONNECTING) {
                `in`.voxagent.mobile.voice.VoiceControls(
                    status = voiceStatus,
                    muted = voiceMuted,
                    speaking = voxSpeaking,
                    onMute = {
                        voiceMuted = !voiceMuted
                        voiceSession.setMuted(voiceMuted)
                    },
                    onStop = { voiceSession.stop() },
                    onSend = { voiceSession.sendText(it) },
                    modifier =
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
                )
            }
            androidx.compose.material3.SnackbarHost(
                hostState = voiceSnackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
            )

            VoxBottomNav(
                destination = destination,
                onDestination = { navigate(it) },
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
                onSignOut = {
                    voiceSession.stop()
                    deviceLink.stop()
                    onSignOut()
                },
                onSyncProfile = onSyncProfile,
                onReviewPermissions = onReviewPermissions,
                permissionsAllSet = permissionsAllSet,
            )
        }
    }
}
