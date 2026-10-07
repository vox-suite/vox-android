package `in`.voxagent.mobile.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import `in`.voxagent.mobile.map.scene.SceneRenderer
import `in`.voxagent.mobile.map.scene.SceneSource
import `in`.voxagent.mobile.net.LiveHub
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

internal class MissionMapController(
    private val context: Context,
    private val token: () -> String?,
) {
    private val prefs = context.getSharedPreferences(PREFS_MAP_CACHE, Context.MODE_PRIVATE)

    init {
        MapLibre.getInstance(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    val mapView =
        MapView(
                context,
                org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context)
                    .textureMode(true),
            )
            .apply {
                setBackgroundColor("#040506".toColorInt())

                setOnTouchListener { _, _ -> true }
                onCreate(null)
            }

    private val mapDeferred = CompletableDeferred<MapLibreMap>()

    private val cachedLat =
        if (prefs.contains(KEY_CACHED_LAT)) {
            prefs.getFloat(KEY_CACHED_LAT, FALLBACK_LAT.toFloat()).toDouble()
        } else {
            FALLBACK_LAT
        }
    private val cachedLng =
        if (prefs.contains(KEY_CACHED_LNG)) {
            prefs.getFloat(KEY_CACHED_LNG, FALLBACK_LNG.toFloat()).toDouble()
        } else {
            FALLBACK_LNG
        }
    private val cachedBearing =
        if (prefs.contains(KEY_CACHED_BEARING)) {
            prefs.getFloat(KEY_CACHED_BEARING, MAP_BEARING.toFloat()).toDouble()
        } else {
            MAP_BEARING
        }

    private var homeCenter: LatLng? = LatLng(cachedLat, cachedLng)
    private var orbit: OrbitController? = null
    private var sceneRenderer: SceneRenderer? = null
    private var sceneActive = false
    private val sceneSource = SceneSource(token, LiveHub.get(token))
    private var sceneJob: Job? = null
    @Volatile private var callActive = false
    @Volatile private var voxSpeaking = false
    private var mapRef: MapLibreMap? = null

    fun start() {
        mapView.getMapAsync { map ->
            mapRef = map

            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false

            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isScrollGesturesEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false

            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(cachedLat, cachedLng))
                        .zoom(MAP_ZOOM)
                        .tilt(MAP_PITCH)
                        .bearing(cachedBearing)
                        .build()
                )
            )

            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                applyMissionControlLook(style)
                add3dBuildings(style)
                addHighlightLayer(style)
                addYouLayer(style)

                val renderer =
                    SceneRenderer(
                        map,
                        mapView,
                        onActiveChange = { active ->
                            sceneActive = active
                            if (active) {
                                map.setLatLngBoundsForCameraTarget(null)
                                map.setMinZoomPreference(10.0)
                                map.setMaxZoomPreference(19.0)
                            } else {
                                homeCenter?.let { applyHome(map, it) }
                            }
                        },
                        onCamera = { orbit?.pause(3_600_000) },
                    )
                renderer.ensure(style)
                sceneRenderer = renderer
                homeCenter?.let { renderer.setHome(it) }
                sceneJob =
                    CoroutineScope(Dispatchers.Main).launch {
                        sceneSource.scenes.collect { renderer.apply(it) }
                    }
                sceneSource.start()
            }

            val o =
                OrbitController(
                    map,
                    { if (sceneActive) null else homeCenter },
                    { now -> paintReaction(now) },
                    { if (callActive) 0.35 else 1.0 },
                )
            orbit = o
            o.start()
            mapDeferred.complete(map)
        }
    }

    suspend fun applyLocation(granted: Boolean) {
        val map = mapDeferred.await()
        val loc = resolveLocation(context, granted)
        homeCenter = loc

        prefs.edit {
            putFloat(KEY_CACHED_LAT, loc.latitude.toFloat())
            putFloat(KEY_CACHED_LNG, loc.longitude.toFloat())
            putFloat(KEY_CACHED_BEARING, map.cameraPosition.bearing.toFloat())
        }

        if (sceneActive) return
        orbit?.pause(2200)
        applyHome(map, loc)
    }

    fun setReaction(callActive: Boolean, voxSpeaking: Boolean) {
        this.callActive = callActive
        this.voxSpeaking = voxSpeaking
    }

    private fun paintReaction(nowMs: Long) {
        val style = mapRef?.style ?: return
        val you = style.getLayer(YOU_LAYER) as? CircleLayer
        val pulse = if (voxSpeaking) ((nowMs % 900L) / 900f) else 0f
        you?.setProperties(
            PropertyFactory.circleRadius(if (callActive) 7f + 3f else 7f),
            PropertyFactory.circleStrokeWidth(2f + pulse * 10f),
            PropertyFactory.circleStrokeOpacity(1f - pulse),
        )
        (style.getLayer(HIGHLIGHT_LAYER) as? FillExtrusionLayer)?.setProperties(
            PropertyFactory.fillExtrusionOpacity(
                if (voxSpeaking) (0.85f + 0.15f * kotlin.math.sin(nowMs / 220f)) else 1f
            )
        )
    }

    fun applyHome(map: MapLibreMap, loc: LatLng) {
        map.setLatLngBoundsForCameraTarget(
            LatLngBounds.from(
                loc.latitude + VIEW_PAD_DEG,
                loc.longitude + VIEW_PAD_DEG,
                loc.latitude - VIEW_PAD_DEG,
                loc.longitude - VIEW_PAD_DEG,
            )
        )
        map.setMinZoomPreference(15.6)
        map.setMaxZoomPreference(18.0)

        (map.style?.getSource(YOU_SOURCE) as? GeoJsonSource)?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
        )

        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(loc)
                    .zoom(MAP_ZOOM)
                    .tilt(MAP_PITCH)
                    .bearing(map.cameraPosition.bearing)
                    .build()
            ),
            1800,
        )

        sceneRenderer?.setHome(loc)
    }

    fun onLifecycle(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> mapView.onStop()
            else -> {}
        }
    }

    fun destroy() {
        sceneJob?.cancel()
        sceneSource.stop()
        sceneRenderer?.destroy()
        orbit?.stop()
        mapView.onDestroy()
    }
}
