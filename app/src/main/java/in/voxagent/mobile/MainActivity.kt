package `in`.voxagent.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.os.Build
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.location.LocationConsentApi
import `in`.voxagent.mobile.location.LocationConsentStatus
import `in`.voxagent.mobile.location.LocationTrackingManager
import `in`.voxagent.mobile.map.MissionMapBackground
import `in`.voxagent.mobile.sms.SmsConsentApi
import `in`.voxagent.mobile.sms.SmsConsentStatus
import `in`.voxagent.mobile.sms.SmsSyncWorker
import `in`.voxagent.mobile.ui.ConsentScreen
import `in`.voxagent.mobile.ui.LocationConsentScreen
import `in`.voxagent.mobile.ui.VoxAtmosphereBackground
import `in`.voxagent.mobile.ui.VoxPrimaryButton
import `in`.voxagent.mobile.ui.VoxSecondaryButton
import `in`.voxagent.mobile.ui.VoxSectionLabel
import `in`.voxagent.mobile.ui.VoxStatusRow
import `in`.voxagent.mobile.ui.VoxStatusTone
import `in`.voxagent.mobile.ui.VoxLogo
import `in`.voxagent.mobile.ui.VoxWordmark
import `in`.voxagent.mobile.ui.theme.CoralPulse
import `in`.voxagent.mobile.ui.theme.VoxTheme
import `in`.voxagent.mobile.ui.voxGrain
import androidx.compose.ui.graphics.BlendMode
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

@Composable
fun AppRoot(authManager: AuthManager, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(authManager.currentToken() != null) }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        smsPermissionGranted = granted
        if (granted) schedulePeriodicSync(activity)
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
        val trackingReady = locationConsentStatus?.granted == true &&
            locationPermissionGranted && activityRecognitionGranted && backgroundLocationGranted
        if (trackingReady) {
            LocationTrackingManager.start(activity)
        }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val token = authManager.currentToken() ?: return@LaunchedEffect
        runCatching { SmsConsentApi.getStatus(token) }
            .onSuccess { consentStatus = it }
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
                        "Sign-in failed: ${result.exceptionOrNull()?.message}"
                    }
                }
            },
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

    HomeScreen(
        consentStatus = consentStatus,
        smsPermissionGranted = smsPermissionGranted,
        locationPermissionGranted = locationPermissionGranted,
        locationConsentStatus = locationConsentStatus,
        activityRecognitionGranted = activityRecognitionGranted,
        backgroundLocationGranted = backgroundLocationGranted,
        statusMessage = statusMessage,
        onReviewDataSharing = { showConsentScreen = true },
        onGrantSmsAccess = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
        onSyncNow = {
            schedulePeriodicSync(activity)
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
            WorkManager.getInstance(activity).enqueue(request)
            statusMessage = "Sync started"
        },
        onRevokeDataSharing = {
            val token = authManager.currentToken() ?: return@HomeScreen
            scope.launch {
                runCatching { SmsConsentApi.revoke(token) }
                    .onSuccess { consentStatus = SmsConsentStatus(granted = false, retention_days = 90) }
            }
        },
        onReviewLocationTracking = { showLocationConsentScreen = true },
        onGrantActivityRecognition = {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        },
        onGrantBackgroundLocation = {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        },
        onRevokeLocationTracking = {
            val token = authManager.currentToken() ?: return@HomeScreen
            scope.launch {
                runCatching { LocationConsentApi.revoke(token) }
                    .onSuccess {
                        locationConsentStatus =
                            LocationConsentStatus(granted = false, retention_days = 90)
                        LocationTrackingManager.stop(activity)
                    }
            }
        },
        onSignOut = {
            authManager.signOut()
            signedIn = false
            consentStatus = null
            locationConsentStatus = null
        },
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

        Text(
            text = "Sync your activity timeline",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Sign in with the same account you use on Vox desktop.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = CoralPulse, textAlign = TextAlign.Center)
        }
        VoxPrimaryButton(text = "Sign in with Google", onClick = onSignIn)
    }
}

@Composable
private fun HomeScreen(
    consentStatus: SmsConsentStatus?,
    smsPermissionGranted: Boolean,
    locationPermissionGranted: Boolean,
    locationConsentStatus: LocationConsentStatus?,
    activityRecognitionGranted: Boolean,
    backgroundLocationGranted: Boolean,
    statusMessage: String,
    onReviewDataSharing: () -> Unit,
    onGrantSmsAccess: () -> Unit,
    onSyncNow: () -> Unit,
    onRevokeDataSharing: () -> Unit,
    onReviewLocationTracking: () -> Unit,
    onGrantActivityRecognition: () -> Unit,
    onGrantBackgroundLocation: () -> Unit,
    onRevokeLocationTracking: () -> Unit,
    onSignOut: () -> Unit,
) {
    val consentGranted = consentStatus?.granted == true
    val locationConsentGranted = locationConsentStatus?.granted == true
    val locationTrackingActive =
        locationConsentGranted && activityRecognitionGranted && backgroundLocationGranted

    Box(modifier = Modifier.fillMaxSize()) {
        MissionMapBackground(
            modifier = Modifier.fillMaxSize(),
            locationPermissionGranted = locationPermissionGranted,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .voxGrain(opacity = 0.07f, blendMode = BlendMode.SrcOver, tileSize = 140.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            VoxWordmark()

            VoxSectionLabel("Status")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VoxStatusRow("Account", "Signed in", VoxStatusTone.Success)
                VoxStatusRow(
                    "Data sharing",
                    if (consentGranted) "Enabled" else "Not enabled",
                    if (consentGranted) VoxStatusTone.Success else VoxStatusTone.Warning,
                )
                VoxStatusRow(
                    "SMS access",
                    if (smsPermissionGranted) "Granted" else "Not granted",
                    if (smsPermissionGranted) VoxStatusTone.Success else VoxStatusTone.Neutral,
                )
                VoxStatusRow(
                    "Location tracking",
                    when {
                        locationTrackingActive -> "Active"
                        locationConsentGranted -> "Awaiting permissions"
                        else -> "Not enabled"
                    },
                    when {
                        locationTrackingActive -> VoxStatusTone.Success
                        locationConsentGranted -> VoxStatusTone.Warning
                        else -> VoxStatusTone.Warning
                    },
                )
            }

            if (statusMessage.isNotEmpty()) {
                Text(text = statusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            VoxSectionLabel("Actions")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!consentGranted) {
                    VoxPrimaryButton(text = "Review data sharing", onClick = onReviewDataSharing)
                } else if (!smsPermissionGranted) {
                    VoxPrimaryButton(text = "Grant SMS access", onClick = onGrantSmsAccess)
                } else {
                    VoxPrimaryButton(text = "Sync now", onClick = onSyncNow)
                }

                if (consentGranted) {
                    VoxSecondaryButton(text = "Turn off data sharing", onClick = onRevokeDataSharing)
                }

                if (!locationConsentGranted) {
                    VoxPrimaryButton(text = "Review location tracking", onClick = onReviewLocationTracking)
                } else if (!activityRecognitionGranted) {
                    VoxPrimaryButton(
                        text = "Enable activity recognition",
                        onClick = onGrantActivityRecognition,
                    )
                } else if (!backgroundLocationGranted) {
                    VoxPrimaryButton(
                        text = "Enable background location",
                        onClick = onGrantBackgroundLocation,
                    )
                }
                if (locationConsentGranted) {
                    VoxSecondaryButton(
                        text = "Turn off location tracking",
                        onClick = onRevokeLocationTracking,
                    )
                }

                VoxSecondaryButton(text = "Sign out", onClick = onSignOut)
            }
        }
    }
}

private fun schedulePeriodicSync(activity: ComponentActivity) {
    val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(activity).enqueueUniquePeriodicWork(
        SmsSyncWorker.UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
