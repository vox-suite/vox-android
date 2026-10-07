package `in`.voxagent.mobile.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng

internal val DEFAULT_LOCATION = LatLng(FALLBACK_LAT, FALLBACK_LNG)

internal suspend fun resolveLocation(context: Context, granted: Boolean): LatLng {
    val permitted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    if (!granted || !permitted) return DEFAULT_LOCATION
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
        if (!lm.isProviderEnabled(provider)) continue
        lm.getLastKnownLocation(provider)?.let {
            return LatLng(it.latitude, it.longitude)
        }
    }
    val fresh = withTimeoutOrNull(6000) { requestSingleLocation(lm) }
    return fresh?.let { LatLng(it.latitude, it.longitude) } ?: DEFAULT_LOCATION
}

@SuppressLint("MissingPermission")
private suspend fun requestSingleLocation(lm: LocationManager): Location? =
    suspendCancellableCoroutine { cont ->
        val provider =
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }
        val listener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location) { _, _, _ -> }
                }
            }
        try {
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null) { _, _, _ -> }
        }
        cont.invokeOnCancellation { lm.removeUpdates(listener) }
    }
