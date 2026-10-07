package `in`.voxagent.mobile.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import `in`.voxagent.mobile.ui.theme.VoidBlack

@Composable
fun MissionMapBackground(
    modifier: Modifier = Modifier,
    locationPermissionGranted: Boolean,
    token: () -> String?,
    callActive: Boolean = false,
    voxSpeaking: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { MissionMapController(context, token).also { it.start() } }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> controller.onLifecycle(event) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }

    LaunchedEffect(controller, locationPermissionGranted) {
        controller.applyLocation(locationPermissionGranted)
    }

    LaunchedEffect(controller, callActive, voxSpeaking) {
        controller.setReaction(callActive, voxSpeaking)
    }

    Box(modifier = modifier.fillMaxSize().background(VoidBlack)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { controller.mapView })
    }
}
