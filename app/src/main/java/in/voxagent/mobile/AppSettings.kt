package `in`.voxagent.mobile

import androidx.activity.ComponentActivity

internal fun openAppSettings(activity: ComponentActivity) {
    activity.startActivity(
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", activity.packageName, null),
        )
    )
}
