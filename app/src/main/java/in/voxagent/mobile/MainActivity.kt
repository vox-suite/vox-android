package `in`.voxagent.mobile

import androidx.core.graphics.toColorInt
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import `in`.voxagent.mobile.voice.VoiceEvent
import `in`.voxagent.mobile.voice.VoiceSession
import `in`.voxagent.mobile.voice.VoiceStatus
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.os.Build
import `in`.voxagent.mobile.BuildConfig
import `in`.voxagent.mobile.R
import `in`.voxagent.mobile.auth.AuthError
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.auth.UserProfile
import `in`.voxagent.mobile.location.LocationConsentApi
import `in`.voxagent.mobile.location.LocationConsentStatus
import `in`.voxagent.mobile.location.LocationTrackingManager
import `in`.voxagent.mobile.map.MissionMapBackground
import `in`.voxagent.mobile.sms.SmsConsentApi
import `in`.voxagent.mobile.sms.SmsConsentStatus
import `in`.voxagent.mobile.sms.SmsSyncWorker
import `in`.voxagent.mobile.phone.PhoneApi
import `in`.voxagent.mobile.phone.PhoneStatus
import `in`.voxagent.mobile.ui.ConsentScreen
import `in`.voxagent.mobile.ui.PhoneVerificationFlow
import `in`.voxagent.mobile.ui.LocationConsentScreen
import `in`.voxagent.mobile.ui.VoxAtmosphereBackground
import `in`.voxagent.mobile.ui.VoxBottomNav
import `in`.voxagent.mobile.ui.VoxLogo
import `in`.voxagent.mobile.ui.VoxNavTab
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxProfileSheet
import `in`.voxagent.mobile.ui.VoxWordmark
import `in`.voxagent.mobile.web.VoxWebScreen
import `in`.voxagent.mobile.ui.theme.BorderSubtle
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.Ink
import `in`.voxagent.mobile.ui.theme.PureWhite
import `in`.voxagent.mobile.ui.theme.Smoke
import `in`.voxagent.mobile.ui.theme.VoidBlack
import `in`.voxagent.mobile.ui.theme.VoxTheme
import `in`.voxagent.mobile.ui.voxGrain
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the decor view immediately renders Void Black to avoid any white flash
        window.decorView.setBackgroundColor("#040506".toColorInt())
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
    var showConsentScreen by remember { mutableStateOf(false) }
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

    LaunchedEffect(smsPermissionGranted) {
        // Re-applied on every launch (not just a fresh grant) so an interval change
        // here actually reaches devices that already granted SMS access.
        if (smsPermissionGranted) schedulePeriodicSync(activity)
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
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationPermissionGranted = granted }

    var locationConsentStatus by remember { mutableStateOf<LocationConsentStatus?>(null) }
    var showLocationConsentScreen by remember { mutableStateOf(false) }
    var activityRecognitionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> activityRecognitionGranted = granted }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> backgroundLocationGranted = granted }

    LaunchedEffect(
        activityRecognitionGranted,
        backgroundLocationGranted,
        locationPermissionGranted,
        locationConsentStatus,
    ) {
        if (locationConsentStatus?.granted != true) return@LaunchedEffect
        when {
            // Each of these is its own separate OS permission dialog, requested
            // one at a time — Android requires background location specifically
            // to be asked for only after foreground location is already granted.
            !locationPermissionGranted -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !activityRecognitionGranted -> activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            !backgroundLocationGranted -> backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else -> LocationTrackingManager.start(activity)
        }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val token = authManager.currentToken() ?: return@LaunchedEffect
        runCatching { PhoneApi.status(token) }
            .onSuccess { phoneStatus = it }
        runCatching { SmsConsentApi.getStatus(token) }
            .onSuccess { status ->
                consentStatus = status
                if (status.granted) {
                    if (!smsPermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                }
            }
        runCatching { LocationConsentApi.getStatus(token) }
            .onSuccess { locationConsentStatus = it }
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
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

    if (showConsentScreen) {
        var consentError by remember { mutableStateOf("") }
        ConsentScreen(
            loading = busy,
            errorMessage = consentError,
            onAllow = {
                val token = authManager.currentToken() ?: return@ConsentScreen
                busy = true
                consentError = ""
                scope.launch {
                    runCatching { SmsConsentApi.grant(token) }
                        .onSuccess {
                            consentStatus = it
                            showConsentScreen = false
                            if (!smsPermissionGranted) {
                                permissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        }
                        .onFailure { consentError = "Couldn't save: ${it}" }
                    busy = false
                }
            },
            onDecline = { showConsentScreen = false },
        )
        return
    }

    if (showLocationConsentScreen) {
        var locationConsentError by remember { mutableStateOf("") }
        LocationConsentScreen(
            loading = busy,
            errorMessage = locationConsentError,
            onAllow = {
                val token = authManager.currentToken() ?: return@LocationConsentScreen
                busy = true
                locationConsentError = ""
                scope.launch {
                    runCatching { LocationConsentApi.grant(token) }
                        .onSuccess {
                            locationConsentStatus = it
                            showLocationConsentScreen = false
                        }
                        .onFailure { locationConsentError = "Couldn't save: ${it}" }
                    busy = false
                }
            },
            onDecline = { showLocationConsentScreen = false },
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
        smsDataSharingGranted = consentStatus?.granted == true,
        onSignOut = {
            authManager.signOut()
            signedIn = false
            consentStatus = null
            locationConsentStatus = null
        },
        onSyncProfile = {
            scope.launch {
                authManager.signIn().onSuccess {
                    userProfileState = authManager.userProfile()
                }
            }
        },
        onReviewDataSharing = { showConsentScreen = true },
        onSyncSmsNow = {
            if (smsPermissionGranted) {
                triggerImmediateSync(activity)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        },
        onReviewLocationTracking = { showLocationConsentScreen = true },
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            VoxLogo(size = 80.dp, animated = true)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Vox",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "MOBILE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
        Text(
            text = "Talk to your agent, manage tasks, and work with your data — all in one place.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    smsDataSharingGranted: Boolean,
    onSignOut: () -> Unit,
    onSyncProfile: (() -> Unit)? = null,
    onReviewDataSharing: (() -> Unit)? = null,
    onSyncSmsNow: (() -> Unit)? = null,
    onReviewLocationTracking: (() -> Unit)? = null,
) {
    var selectedTab by remember { mutableStateOf(VoxNavTab.Home) }
    val latestToken by rememberUpdatedState(token)
    var showProfileSheet by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val voiceContext = LocalContext.current
    val voiceSession = remember { VoiceSession(voiceContext) }
    var voiceStatus by remember { mutableStateOf(VoiceStatus.IDLE) }
    var voiceSubtitle by remember { mutableStateOf("Duplex standby") }
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
    LaunchedEffect(voiceSession) {
        voiceSession.events.collect { event ->
            voiceSubtitle = when (event) {
                is VoiceEvent.UserTranscript -> "You: ${event.text}"
                is VoiceEvent.Delta -> "Vox: ${event.text}"
                is VoiceEvent.Thinking -> "Thinking…"
                is VoiceEvent.Done -> "Duplex standby"
                is VoiceEvent.Interrupted -> "Listening…"
                is VoiceEvent.Error -> "Error: ${event.message}"
            }
        }
    }
    DisposableEffect(voiceSession) {
        onDispose { voiceSession.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        // Fullscreen Ambient 3D Mission Map
        MissionMapBackground(
            modifier = Modifier.fillMaxSize(),
            locationPermissionGranted = locationPermissionGranted,
        )

        // Subtle ambient grain overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .voxGrain(opacity = 0.05f, blendMode = BlendMode.SrcOver, tileSize = 140.dp),
        )

      }

      // Agent Cockpit Card (shown only when the Agent tab is active on the left pill).
      // Drawn as a sibling AFTER the hazeSource Box (like the nav bar and profile sheet
      // below) so it blurs the map behind it instead of being part of its own source.
      AnimatedVisibility(
          visible = selectedTab == VoxNavTab.Agent,
          enter = fadeIn() + slideInVertically(initialOffsetY = { -40 }),
          exit = fadeOut() + slideOutVertically(targetOffsetY = { -40 }),
          modifier = Modifier
              .fillMaxWidth()
              .align(Alignment.Center)
              .padding(24.dp),
      ) {
          Surface(
              modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .hazeEffect(
                      state = hazeState,
                      style = HazeDefaults.style(
                          // Lower opacity than the profile sheet/nav pill: this card sits
                          // dead-center over the dark 3D map, which has far less brightness
                          // variance to begin with, so the same 0.65/0.5 overlay used
                          // elsewhere crushes the blurred backdrop to a flat black instead
                          // of reading as frosted glass.
                          backgroundColor = VoidBlack.copy(alpha = 0.35f),
                          tint = HazeTint(Ink.copy(alpha = 0.25f)),
                          blurRadius = 24.dp,
                      ),
                  ),
              shape = RoundedCornerShape(20.dp),
              color = Color.Transparent,
              border = BorderStroke(1.dp, BorderSubtle),
              shadowElevation = 16.dp,
          ) {
              Column(
                  modifier = Modifier.padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                  VoxLogo(size = 72.dp, animated = true)

                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(
                          text = "VOX AGENT",
                          color = PureWhite,
                          fontWeight = FontWeight.SemiBold,
                          fontSize = 18.sp,
                          letterSpacing = 1.sp,
                      )
                      Text(
                          text = voiceSubtitle,
                          color = Smoke,
                          fontFamily = FontFamily.Monospace,
                          fontSize = 12.sp,
                      )
                  }

                  Spacer(modifier = Modifier.height(4.dp))

                  VoxPrimaryButton(
                      text = when {
                          voiceStatus == VoiceStatus.CONNECTING -> "Connecting…"
                          voiceStatus == VoiceStatus.ACTIVE -> "End Call"
                          else -> "Talk to Vox"
                      },
                      onClick = {
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
                  )
              }
          }
      }

      // Span Timeline Tab — full-screen overlay, drawn ABOVE the map but BELOW the nav bar
      AnimatedVisibility(
          visible = selectedTab == VoxNavTab.Span,
          enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
          exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
          modifier = Modifier.fillMaxSize(),
      ) {
          if (token != null) {
              VoxWebScreen(
                  route = "timeline",
                  tokenProvider = { latestToken },
                  modifier = Modifier
                      .fillMaxSize()
                      .statusBarsPadding()
                      .navigationBarsPadding()
                      .padding(bottom = 80.dp),
              )
          }
      }

      AnimatedVisibility(
          visible = selectedTab == VoxNavTab.Layers,
          enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
          exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
          modifier = Modifier.fillMaxSize(),
      ) {
          if (token != null) {
              VoxWebScreen(
                  route = "pulse",
                  tokenProvider = { latestToken },
                  modifier = Modifier
                      .fillMaxSize()
                      .statusBarsPadding()
                      .navigationBarsPadding()
                      .padding(bottom = 80.dp),
              )
          }
      }

      // Floating Bottom Navigation Menu (Unified Translucent Container)
      VoxBottomNav(
          selectedTab = selectedTab,
          onTabSelected = { selectedTab = it },
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
            smsDataSharingGranted = smsDataSharingGranted,
            onReviewDataSharing = onReviewDataSharing,
            onSyncSmsNow = onSyncSmsNow,
            onReviewLocationTracking = onReviewLocationTracking,
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
