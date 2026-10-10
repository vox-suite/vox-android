package `in`.voxagent.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkManager
import `in`.voxagent.mobile.auth.AuthError
import `in`.voxagent.mobile.auth.AuthManager
import `in`.voxagent.mobile.net.VoxHttp
import `in`.voxagent.mobile.phone.PhoneApi
import `in`.voxagent.mobile.phone.PhoneStatus
import `in`.voxagent.mobile.ui.PermissionsScreen
import `in`.voxagent.mobile.ui.PhoneVerificationFlow
import kotlinx.coroutines.launch

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
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val work = WorkManager.getInstance(activity)
        listOf("sms_sync", "sms_sync_now", "sms_backfill").forEach(work::cancelUniqueWork)
    }

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionsPromptOpen by rememberSaveable { mutableStateOf(true) }
    var permissionsPromptForced by rememberSaveable { mutableStateOf(false) }
    var permissionsPromptBusy by remember { mutableStateOf(false) }
    var permissionsPromptError by remember { mutableStateOf("") }

    val runtimePermissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            micGranted = result[Manifest.permission.RECORD_AUDIO] ?: micGranted
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: locationPermissionGranted
            locationPermissionGranted = fine
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                micGranted = granted(Manifest.permission.RECORD_AUDIO)
                locationPermissionGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val token = authManager.currentToken() ?: return@LaunchedEffect
        runCatching { PhoneApi.status(token) }.onSuccess { phoneStatus = it }
    }

    if (!signedIn) {
        SignInScreen(
            statusMessage = statusMessage,
            onSignIn = {
                scope.launch {
                    val result = authManager.signIn()
                    signedIn = result.isSuccess
                    statusMessage =
                        if (result.isSuccess) {
                            ""
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is AuthError) error.message ?: "Sign-in failed"
                            else
                                "Sign-in failed: ${error?.let { it::class.simpleName }}: ${error?.message}"
                        }
                }
            },
        )
        return
    }

    val currentPhone = phoneStatus
    val phoneToken = authManager.currentToken()
    if (
        currentPhone != null &&
            phoneToken != null &&
            !currentPhone.phone_verified &&
            !phoneVerifySkipped
    ) {
        PhoneVerificationFlow(
            token = phoneToken,
            status = currentPhone,
            onVerified = { phoneStatus = PhoneStatus(has_phone = true, phone_verified = true) },
            onSkip = { phoneVerifySkipped = true },
        )
        return
    }

    val allPermissionsSet = micGranted && locationPermissionGranted
    LaunchedEffect(allPermissionsSet) {
        if (allPermissionsSet && !permissionsPromptForced) permissionsPromptOpen = false
    }

    if (
        permissionsPromptOpen &&
            (permissionsPromptForced || !allPermissionsSet)
    ) {
        fun saving(block: suspend (String) -> Unit) {
            val token = authManager.currentToken() ?: return
            permissionsPromptBusy = true
            permissionsPromptError = ""
            scope.launch {
                runCatching { block(token) }
                    .onFailure { permissionsPromptError = "Couldn't save: $it" }
                permissionsPromptBusy = false
            }
        }
        PermissionsScreen(
            micOn = micGranted,
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
            onToggleLocation = { on ->
                if (on) {
                    runtimePermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                } else {
                    openAppSettings(activity)
                }
            },
            onOpenSettings = { openAppSettings(activity) },
            onAllowAll = {
                saving { token ->
                    val wanted = buildList {
                        add(Manifest.permission.RECORD_AUDIO)
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
        if (
            signedIn &&
                (userProfileState.avatarUrl.isNullOrBlank() ||
                    userProfileState.displayName.isNullOrBlank())
        ) {
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
        },
        onSyncProfile = {
            scope.launch {
                authManager.signIn().onSuccess { userProfileState = authManager.userProfile() }
            }
        },
        onReviewPermissions = {
            permissionsPromptForced = true
            permissionsPromptOpen = true
        },
        permissionsAllSet = allPermissionsSet,
    )
}
