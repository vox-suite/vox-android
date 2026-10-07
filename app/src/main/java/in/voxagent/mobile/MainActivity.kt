package `in`.voxagent.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import `in`.voxagent.mobile.voice.VoiceSession
import `in`.voxagent.mobile.voice.VoiceStatus
import kotlinx.coroutines.delay
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.os.Build
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.auth.AuthError
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.auth.UserProfile
import `in`.voxagent.mobile.map.MissionMapBackground
import `in`.voxagent.mobile.sms.SmsConsentApi
import `in`.voxagent.mobile.sms.SmsConsentStatus
import `in`.voxagent.mobile.sms.SmsSyncStore
import `in`.voxagent.mobile.sms.SmsSyncWorker
import `in`.voxagent.mobile.phone.PhoneApi
import `in`.voxagent.mobile.phone.PhoneStatus
import `in`.voxagent.mobile.ui.PermissionsScreen
import `in`.voxagent.mobile.ui.PhoneVerificationFlow
import `in`.voxagent.mobile.ui.VoxAtmosphereBackground
import `in`.voxagent.mobile.ui.TalkState
import androidx.compose.runtime.mutableIntStateOf
import `in`.voxagent.mobile.ui.Destination
import `in`.voxagent.mobile.spans.SpanCollection
import `in`.voxagent.mobile.connections.ConnectionsScreen
import `in`.voxagent.mobile.timeline.SpanSheet
import `in`.voxagent.mobile.timeline.SheetTarget
import `in`.voxagent.mobile.timeline.TimelineScreen
import `in`.voxagent.mobile.ui.VoxBottomNav
import `in`.voxagent.mobile.ui.VoxLogo
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxProfileSheet
import `in`.voxagent.mobile.ui.theme.GraphiteDark
import `in`.voxagent.mobile.ui.theme.Mist
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.VoxSpaceGroteskFontFamily
import `in`.voxagent.mobile.ui.theme.VoxTheme
import `in`.voxagent.mobile.ui.voxGrain
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the decor view and window immediately render Void Black to avoid any white flash
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF040506.toInt()))
        window.decorView.setBackgroundColor(0xFF040506.toInt())
        `in`.voxagent.mobile.logging.RemoteLog.init(applicationContext)
        authManager = AuthManager(applicationContext)

        setContent {
            VoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(authManager = authManager, activity = this)
                }
            }
        }
    }
}

@SuppressLint("InlinedApi")
@Composable
fun AppRoot(authManager: AuthManager, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(authManager.currentToken() != null) }
    LaunchedEffect(Unit) {
        VoxHttp.unauthorized.collect {
            authManager.signOut()
            signedIn = false
        }
    }
    var phoneStatus by remember { mutableStateOf<PhoneStatus?>(null) }
    var phoneVerifySkipped by remember { mutableStateOf(false) }
    var consentStatus by remember { mutableStateOf<SmsConsentStatus?>(null) }
    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_SMS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(smsPermissionGranted, consentStatus) {
        // Re-applied on every launch (not just a fresh grant) so an interval change
        // here actually reaches devices that already granted SMS access.
        if (smsPermissionGranted && consentStatus?.granted == true) {
            schedulePeriodicSync(activity)
            // One automatic 90-day import per backfill version; unique work keeps a
            // relaunch from starting a second copy while one is still running.
            if (SmsSyncStore.backfillVersion(activity) < SmsSyncWorker.BACKFILL_VERSION) {
                triggerBackfillSync(activity)
            }
        } else if (consentStatus?.granted == false) {
            WorkManager.getInstance(activity).cancelUniqueWork(SmsSyncWorker.UNIQUE_WORK_NAME)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        smsPermissionGranted = granted
        if (granted) {
            schedulePeriodicSync(activity)
            triggerImmediateSync(activity)
        }
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionsPromptOpen by rememberSaveable { mutableStateOf(true) }
    var permissionsPromptForced by rememberSaveable { mutableStateOf(false) }
    var permissionsPromptBusy by remember { mutableStateOf(false) }
    var permissionsPromptError by remember { mutableStateOf("") }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        micGranted = result[Manifest.permission.RECORD_AUDIO] ?: micGranted
        if (result[Manifest.permission.READ_SMS] == true) {
            smsPermissionGranted = true
            schedulePeriodicSync(activity)
            triggerImmediateSync(activity)
        }
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: locationPermissionGranted
        locationPermissionGranted = fine
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micGranted = granted(Manifest.permission.RECORD_AUDIO)
                smsPermissionGranted = granted(Manifest.permission.READ_SMS)
                locationPermissionGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val token = authManager.currentToken() ?: return@LaunchedEffect
        runCatching { PhoneApi.status(token) }
            .onSuccess { phoneStatus = it }
        runCatching { SmsConsentApi.getStatus(token) }
            .onSuccess { consentStatus = it }
    }

    if (!signedIn) {
        SignInScreen(
            statusMessage = statusMessage,
            onSignIn = {
                scope.launch {
                    val result = authManager.signIn()
                    signedIn = result.isSuccess
                    statusMessage = if (result.isSuccess) {
                        ""
                    } else {
                        val error = result.exceptionOrNull()
                        if (error is AuthError) error.message ?: "Sign-in failed"
                        else "Sign-in failed: ${error?.let { it::class.simpleName }}: ${error?.message}"
                    }
                }
            },
        )
        return
    }

    val currentPhone = phoneStatus
    val phoneToken = authManager.currentToken()
    if (currentPhone != null && phoneToken != null && !currentPhone.phone_verified && !phoneVerifySkipped) {
        PhoneVerificationFlow(
            token = phoneToken,
            status = currentPhone,
            onVerified = { phoneStatus = PhoneStatus(has_phone = true, phone_verified = true) },
            onSkip = { phoneVerifySkipped = true },
        )
        return
    }

    val allPermissionsSet = micGranted && smsPermissionGranted && locationPermissionGranted &&
        consentStatus?.granted == true
    LaunchedEffect(allPermissionsSet) {
        if (allPermissionsSet && !permissionsPromptForced) permissionsPromptOpen = false
    }

    if (permissionsPromptOpen && consentStatus != null &&
        (permissionsPromptForced || !allPermissionsSet)
    ) {
        fun saving(block: suspend (String) -> Unit) {
            val token = authManager.currentToken() ?: return
            permissionsPromptBusy = true
            permissionsPromptError = ""
            scope.launch {
                runCatching { block(token) }.onFailure { permissionsPromptError = "Couldn't save: $it" }
                permissionsPromptBusy = false
            }
        }
        PermissionsScreen(
            micOn = micGranted,
            smsOn = consentStatus?.granted == true && smsPermissionGranted,
            locationOn = locationPermissionGranted,
            busy = permissionsPromptBusy,
            errorMessage = permissionsPromptError,
            onToggleMic = { on ->
                if (on) {
                    runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                } else {
                    openAppSettings(activity)
                }
            },
            onToggleSms = { on ->
                saving { token ->
                    if (on) {
                        if (consentStatus?.granted != true) consentStatus = SmsConsentApi.grant(token)
                        if (smsPermissionGranted) {
                            schedulePeriodicSync(activity)
                            triggerImmediateSync(activity)
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    } else {
                        SmsConsentApi.revoke(token)
                        consentStatus = consentStatus?.copy(granted = false)
                        WorkManager.getInstance(activity).cancelUniqueWork(SmsSyncWorker.UNIQUE_WORK_NAME)
                    }
                }
            },
            onToggleLocation = { on ->
                if (on) {
                    runtimePermissionsLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                } else {
                    openAppSettings(activity)
                }
            },
            onSyncSms = { triggerImmediateSync(activity) },
            onBackfillSms = { triggerBackfillSync(activity) },
            onOpenSettings = { openAppSettings(activity) },
            onAllowAll = {
                saving { token ->
                    if (consentStatus?.granted != true) consentStatus = SmsConsentApi.grant(token)
                    val wanted = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
                        add(Manifest.permission.READ_SMS)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    runtimePermissionsLauncher.launch(wanted.toTypedArray())
                }
            },
            onClose = {
                permissionsPromptOpen = false
                permissionsPromptForced = false
            },
        )
        return
    }

    var userProfileState by remember(signedIn) { mutableStateOf(authManager.userProfile()) }

    LaunchedEffect(signedIn) {
        if (signedIn && (userProfileState.avatarUrl.isNullOrBlank() || userProfileState.displayName.isNullOrBlank())) {
            val refreshed = authManager.tryRefreshProfile()
            if (refreshed != null) {
                userProfileState = refreshed
            }
        }
    }

    HomeScreen(
        token = authManager.currentToken(),
        userProfile = userProfileState,
        locationPermissionGranted = locationPermissionGranted,
        onSignOut = {
            authManager.signOut()
            signedIn = false
            consentStatus = null
        },
        onSyncProfile = {
            scope.launch {
                authManager.signIn().onSuccess {
                    userProfileState = authManager.userProfile()
                }
            }
        },
        onReviewPermissions = {
            permissionsPromptForced = true
            permissionsPromptOpen = true
        },
        permissionsAllSet = allPermissionsSet,
    )
}

@Composable
private fun SignInScreen(statusMessage: String, onSignIn: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        VoxAtmosphereBackground(showGlow = true, noiseOpacity = 0.22f)
        SignInContent(statusMessage = statusMessage, onSignIn = onSignIn)
    }
}

@Composable
private fun SignInContent(statusMessage: String, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.fillMaxHeight(0.25f))
        VoxLogo(modifier = Modifier.offset(y = (-20).dp), size = 80.dp, animated = true)

        Column(
            modifier = Modifier.offset(y = (-18).dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Vox",
                color = Mist,
                fontFamily = VoxSpaceGroteskFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(androidx.compose.ui.graphics.Color(0xFF3A1418))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "MOBILE",
                    color = Mist,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
        Text(
            text = "Talk to your agent, manage tasks, and work with your data — all in one place.",
            color = GraphiteDark,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = CoralPulse, textAlign = TextAlign.Center)
        }
        VoxPrimaryButton(
            text = "Continue with Google",
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                )
            },
            onClick = onSignIn,
        )
        }
    }
}

/**
 * Clean ambient Home screen:
 * Full-screen 3D ambient map background, tactical header, and floating bottom navigation menu.
 * Clutter (status rows and raw action buttons) has been cleared from the main viewport.
 */
@Composable
private fun HomeScreen(
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
            ContextCompat.checkSelfPermission(
                voiceContext,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        recordAudioGranted = granted
        if (granted) voiceSession.start()
    }
    LaunchedEffect(voiceSession) {
        voiceSession.status.collect { voiceStatus = it }
    }
    DisposableEffect(voiceSession) {
        onDispose { voiceSession.stop() }
    }

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
        // Fullscreen Ambient 3D Mission Map
        MissionMapBackground(
            modifier = Modifier.fillMaxSize(),
            locationPermissionGranted = locationPermissionGranted,
            token = { latestToken },
            callActive = voiceStatus == VoiceStatus.ACTIVE,
            voxSpeaking = voxSpeaking,
        )

        // Subtle ambient grain overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .voxGrain(opacity = 0.05f, blendMode = BlendMode.SrcOver, tileSize = 140.dp),
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
          talkState = when (voiceStatus) {
              VoiceStatus.CONNECTING -> TalkState.Connecting
              VoiceStatus.ACTIVE -> TalkState.Active
              VoiceStatus.ERROR -> TalkState.Error
              VoiceStatus.IDLE -> TalkState.Idle
          },
          onTalkClick = {
              when (voiceStatus) {
                  VoiceStatus.IDLE, VoiceStatus.ERROR -> {
                      if (recordAudioGranted) {
                          voiceSession.start()
                      } else {
                          recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      }
                  }
                  VoiceStatus.ACTIVE, VoiceStatus.CONNECTING -> voiceSession.stop()
              }
          },
          avatarUrl = userProfile.avatarUrl,
          displayName = userProfile.displayName,
          onAvatarClick = { showProfileSheet = true },
          hazeState = hazeState,
          modifier = Modifier.align(Alignment.BottomCenter),
      )

        // Slide-up Profile Sheet when avatar on right is tapped
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
            onOpenConnections = { destination = Destination.Connections },
        )
    }
}

private fun schedulePeriodicSync(activity: ComponentActivity) {
    val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(1, TimeUnit.HOURS).build()
    WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
        SmsSyncWorker.UNIQUE_WORK_NAME,
        // UPDATE (not KEEP) so this change actually replaces the 15-minute schedule
        // already running on devices that installed the app before this change.
        ExistingPeriodicWorkPolicy.UPDATE,
        request,
    )
}

private fun triggerImmediateSync(activity: ComponentActivity) {
    val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
    // Unique so repeated taps or app opens never run overlapping syncs.
    WorkManager.getInstance(activity).enqueueUniqueWork(
        "sms_sync_now",
        ExistingWorkPolicy.KEEP,
        request,
    )
}

private fun triggerBackfillSync(activity: ComponentActivity) {
    val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
        .setInputData(workDataOf(SmsSyncWorker.KEY_BACKFILL_DAYS to SmsSyncWorker.BACKFILL_DAYS))
        .build()
    WorkManager.getInstance(activity).enqueueUniqueWork(
        "sms_backfill",
        ExistingWorkPolicy.KEEP,
        request,
    )
}

private fun openAppSettings(activity: ComponentActivity) {
    activity.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", activity.packageName, null),
        ),
    )
}
