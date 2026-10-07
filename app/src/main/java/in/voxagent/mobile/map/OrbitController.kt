package `in`.voxagent.mobile.map

import android.view.Choreographer
import kotlin.math.min
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal class OrbitController(
    private val map: MapLibreMap,
    private val getCenter: () -> LatLng?,
    private val onFrame: (Long) -> Unit,
    private val speedFactor: () -> Double,
) {
    private var pausedUntilMs = 0L
    private var lastFrameNs = 0L
    private var speed = 0.0
    private var running = false
    private val choreographer = Choreographer.getInstance()
    private val callback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                onFrame(System.currentTimeMillis())
                val last = if (lastFrameNs == 0L) frameTimeNanos else lastFrameNs
                val dt = min(0.05, (frameTimeNanos - last) / 1_000_000_000.0)
                lastFrameNs = frameTimeNanos
                val wantRun = System.currentTimeMillis() >= pausedUntilMs
                speed += ((if (wantRun) speedFactor() else 0.0) - speed) * min(1.0, dt * 0.8)
                val center = getCenter()
                if (center != null && speed > 0.001) {
                    val pos = map.cameraPosition
                    map.moveCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(pos)
                                .target(center)
                                .bearing(pos.bearing + ORBIT_DEG_PER_SEC * speed * dt)
                                .build()
                        )
                    )
                }
                choreographer.postFrameCallback(this)
            }
        }

    fun start() {
        if (running) return
        running = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(callback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(callback)
    }

    fun pause(ms: Long = 4500) {
        pausedUntilMs = System.currentTimeMillis() + ms
    }
}
