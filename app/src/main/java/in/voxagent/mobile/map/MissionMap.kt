package `in`.voxagent.mobile.map

import androidx.core.graphics.toColorInt
import androidx.core.content.edit
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.graphics.RectF
import android.os.Looper
import android.view.Choreographer
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonObject
import `in`.voxagent.mobile.ui.theme.VoidBlack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.min

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
// Open ocean, not a real place: renders as a blank ambient background until a
// real location is known, instead of implying the user is somewhere they aren't.
private const val FALLBACK_LAT = 0.0
private const val FALLBACK_LNG = 0.0
private const val MAP_ZOOM = 16.0
private const val MAP_PITCH = 60.0
private const val MAP_BEARING = -28.0
private const val ORBIT_DEG_PER_SEC = 4.0
private const val VIEW_PAD_DEG = 0.01
private const val BUILDINGS_LAYER = "vox-3d-buildings"
private const val HIGHLIGHT_SOURCE = "vox-highlight-source"
private const val HIGHLIGHT_LAYER = "vox-highlight-layer"
private const val YOU_SOURCE = "vox-you-source"
private const val YOU_LAYER = "vox-you-layer"

private const val PREFS_MAP_CACHE = "vox_map_cache"
private const val KEY_CACHED_LAT = "cached_lat"
private const val KEY_CACHED_LNG = "cached_lng"
private const val KEY_CACHED_BEARING = "cached_bearing"

/** Ambient orbiting 3D map, ported from vox-desktop's use-mission-map.ts. */
@Composable
fun MissionMapBackground(modifier: Modifier = Modifier, locationPermissionGranted: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { MissionMapController(context).also { it.start() } }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { controller.mapView },
        )
    }
}

private class MissionMapController(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_MAP_CACHE, Context.MODE_PRIVATE)

    init {
        MapLibre.getInstance(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    val mapView = MapView(
        context,
        // TextureView (not the default SurfaceView) so the map composites through the normal
        // View hierarchy and can be captured for the profile sheet's backdrop blur.
        org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).textureMode(true),
    ).apply {
        // Guarantee dark canvas before tiles composite to prevent white flashing
        setBackgroundColor("#040506".toColorInt())
        // Consume all touch events so map gestures (pan, zoom, pinch, tilt) are denied
        setOnTouchListener { _, _ -> true }
        onCreate(null)
    }

    private val mapDeferred = CompletableDeferred<MapLibreMap>()

    // Load cached coordinates so the initial frame immediately renders the saved location
    private val cachedLat = if (prefs.contains(KEY_CACHED_LAT)) {
        prefs.getFloat(KEY_CACHED_LAT, FALLBACK_LAT.toFloat()).toDouble()
    } else {
        FALLBACK_LAT
    }
    private val cachedLng = if (prefs.contains(KEY_CACHED_LNG)) {
        prefs.getFloat(KEY_CACHED_LNG, FALLBACK_LNG.toFloat()).toDouble()
    } else {
        FALLBACK_LNG
    }
    private val cachedBearing = if (prefs.contains(KEY_CACHED_BEARING)) {
        prefs.getFloat(KEY_CACHED_BEARING, MAP_BEARING.toFloat()).toDouble()
    } else {
        MAP_BEARING
    }

    private var homeCenter: LatLng? = LatLng(cachedLat, cachedLng)
    private var orbit: OrbitController? = null

    fun start() {
        mapView.getMapAsync { map ->
            // Disable all UI overlays
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false

            // Explicitly deny all interactive gesture controls (pan, zoom, tilt, rotate)
            map.uiSettings.setAllGesturesEnabled(false)
            map.uiSettings.isScrollGesturesEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false

            // Position camera immediately at the cached location for instant rendering
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(cachedLat, cachedLng))
                        .zoom(MAP_ZOOM)
                        .tilt(MAP_PITCH)
                        .bearing(cachedBearing)
                        .build(),
                ),
            )

            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                applyMissionControlLook(style)
                add3dBuildings(style)
                addHighlightLayer(style)
                addYouLayer(style)
            }

            val o = OrbitController(map) { homeCenter }
            orbit = o
            o.start()
            mapDeferred.complete(map)
        }
    }

    suspend fun applyLocation(granted: Boolean) {
        val map = mapDeferred.await()
        val loc = resolveLocation(context, granted)
        homeCenter = loc

        // Save location and bearing to cache so next launch immediately starts here
        prefs.edit {
            putFloat(KEY_CACHED_LAT, loc.latitude.toFloat())
            putFloat(KEY_CACHED_LNG, loc.longitude.toFloat())
            putFloat(KEY_CACHED_BEARING, map.cameraPosition.bearing.toFloat())
        }

        orbit?.pause(2200)
        applyHome(map, loc)
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
        orbit?.stop()
        mapView.onDestroy()
    }
}

/** Frame-synced bearing rotation around home */
private class OrbitController(private val map: MapLibreMap, private val getCenter: () -> LatLng?) {
    private var pausedUntilMs = 0L
    private var lastFrameNs = 0L
    private var speed = 0.0
    private var running = false
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val last = if (lastFrameNs == 0L) frameTimeNanos else lastFrameNs
            val dt = min(0.05, (frameTimeNanos - last) / 1_000_000_000.0)
            lastFrameNs = frameTimeNanos
            val wantRun = System.currentTimeMillis() >= pausedUntilMs
            speed += ((if (wantRun) 1.0 else 0.0) - speed) * min(1.0, dt * 0.8)
            val center = getCenter()
            if (center != null && speed > 0.001) {
                val pos = map.cameraPosition
                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(pos)
                            .target(center)
                            .bearing(pos.bearing + ORBIT_DEG_PER_SEC * speed * dt)
                            .build(),
                    ),
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

private fun applyMissionControlLook(style: Style) {
    for (layer: Layer in style.layers) {
        val id = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor("#0b0c0e"))
            is FillLayer -> when {
                "building" in id -> layer.setProperties(PropertyFactory.visibility(Property.NONE))
                "water" in id -> layer.setProperties(PropertyFactory.fillColor("#060708"))
                "land" in id -> layer.setProperties(PropertyFactory.fillColor("#121316"))
            }
            is LineLayer -> when {
                "building" in id -> layer.setProperties(PropertyFactory.visibility(Property.NONE))
                "road" in id || "street" in id || "path" in id || "bridge" in id || "tunnel" in id ->
                    layer.setProperties(
                        PropertyFactory.lineColor("#d0d1d2"),
                        PropertyFactory.lineOpacity(0.75f),
                    )
            }
            is FillExtrusionLayer -> if ("building" in id) {
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
            else -> {}
        }
    }
}

private fun findBuildingSource(style: Style): String? {
    val vectorSources = style.sources.filterIsInstance<VectorSource>()
    return vectorSources.firstOrNull {
        "openmaptiles" in it.id || "protomaps" in it.id || it.id == "composite"
    }?.id ?: vectorSources.firstOrNull()?.id
}

private fun extrusionBeforeId(style: Style): String? {
    val layers = style.layers
    val lastLine = layers.indexOfLast { it is LineLayer }
    return layers.drop(lastLine + 1).firstOrNull { it is SymbolLayer }?.id
}

private fun heightExpression(): Expression = Expression.interpolate(
    Expression.linear(),
    Expression.zoom(),
    Expression.stop(14, Expression.literal(0)),
    Expression.stop(
        14.2,
        Expression.coalesce(Expression.get("render_height"), Expression.get("height"), Expression.literal(12)),
    ),
)

private fun baseExpression(): Expression =
    Expression.coalesce(Expression.get("render_min_height"), Expression.get("min_height"), Expression.literal(0))

private fun addExtrusionLayer(style: Style, id: String, sourceId: String, sourceLayer: String?, color: String) {
    if (style.getLayer(id) != null) return
    val layer = FillExtrusionLayer(id, sourceId)
    if (sourceLayer != null) layer.setSourceLayer(sourceLayer)
    layer.setMinZoom(14f)
    layer.setProperties(
        PropertyFactory.fillExtrusionColor(color),
        PropertyFactory.fillExtrusionHeight(heightExpression()),
        PropertyFactory.fillExtrusionBase(baseExpression()),
        PropertyFactory.fillExtrusionOpacity(1f),
        PropertyFactory.fillExtrusionVerticalGradient(false),
    )
    val beforeId = extrusionBeforeId(style)
    if (beforeId != null) style.addLayerBelow(layer, beforeId) else style.addLayer(layer)
}

private fun add3dBuildings(style: Style) {
    if (style.getLayer(BUILDINGS_LAYER) != null) return
    val sourceId = findBuildingSource(style) ?: return
    addExtrusionLayer(style, BUILDINGS_LAYER, sourceId, "building", "#0f0f11")
}

private fun addHighlightLayer(style: Style) {
    if (style.getSource(HIGHLIGHT_SOURCE) == null) {
        style.addSource(GeoJsonSource(HIGHLIGHT_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    }
    addExtrusionLayer(style, HIGHLIGHT_LAYER, HIGHLIGHT_SOURCE, null, "#ff3b30")
}

private fun addYouLayer(style: Style) {
    if (style.getSource(YOU_SOURCE) == null) {
        style.addSource(GeoJsonSource(YOU_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    }
    if (style.getLayer(YOU_LAYER) != null) return
    val layer = CircleLayer(YOU_LAYER, YOU_SOURCE)
    layer.setProperties(
        PropertyFactory.circleRadius(7f),
        PropertyFactory.circleColor("#ff3b30"),
        PropertyFactory.circleStrokeWidth(2f),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
    )
    style.addLayer(layer)
}

private fun applyHome(map: MapLibreMap, loc: LatLng) {
    map.setLatLngBoundsForCameraTarget(
        LatLngBounds.from(
            loc.latitude + VIEW_PAD_DEG,
            loc.longitude + VIEW_PAD_DEG,
            loc.latitude - VIEW_PAD_DEG,
            loc.longitude - VIEW_PAD_DEG,
        ),
    )
    map.setMinZoomPreference(15.6)
    map.setMaxZoomPreference(18.0)

    (map.style?.getSource(YOU_SOURCE) as? GeoJsonSource)?.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude)),
    )

    map.easeCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(loc)
                .zoom(MAP_ZOOM)
                .tilt(MAP_PITCH)
                .bearing(map.cameraPosition.bearing)
                .build(),
        ),
        1800,
    )

    map.addOnCameraIdleListener(object : MapLibreMap.OnCameraIdleListener {
        override fun onCameraIdle() {
            map.removeOnCameraIdleListener(this)
            highlightBuildingAt(map, loc)
        }
    })
}

private fun outerRings(geometry: Geometry?): List<List<Point>> = when (geometry) {
    is Polygon -> listOf(geometry.coordinates()[0])
    is MultiPolygon -> geometry.coordinates().map { it[0] }
    else -> emptyList()
}

private fun pointInRing(x: Double, y: Double, ring: List<Point>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val xi = ring[i].longitude()
        val yi = ring[i].latitude()
        val xj = ring[j].longitude()
        val yj = ring[j].latitude()
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

// Highlights the home building in red, matching map-highlight.ts
private fun highlightBuildingAt(map: MapLibreMap, loc: LatLng) {
    val style = map.style ?: return
    if (style.getLayer(BUILDINGS_LAYER) == null) return
    val src = style.getSource(HIGHLIGHT_SOURCE) as? GeoJsonSource ?: return

    val point = map.projection.toScreenLocation(loc)
    val box = RectF(point.x - 80f, point.y - 80f, point.x + 80f, point.y + 80f)
    val hits = map.queryRenderedFeatures(box, BUILDINGS_LAYER)

    var bestRing: List<Point>? = null
    var bestProps: JsonObject? = null
    var best = Double.MAX_VALUE
    for (feature in hits) {
        for (ring in outerRings(feature.geometry())) {
            val d = if (pointInRing(loc.longitude, loc.latitude, ring)) {
                -1.0
            } else {
                ring.minOf { (it.longitude() - loc.longitude).let { lx -> lx * lx } + (it.latitude() - loc.latitude).let { ly -> ly * ly } }
            }
            if (d < best) {
                best = d
                bestRing = ring
                bestProps = feature.properties()
            }
        }
    }

    val ring = bestRing
    if (ring == null) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val props = bestProps ?: JsonObject()
    val h = (
        props.get("render_height")?.takeIf { !it.isJsonNull }?.asDouble
            ?: props.get("height")?.takeIf { !it.isJsonNull }?.asDouble
            ?: 12.0
        ) + 1.0
    val cx = ring.sumOf { it.longitude() } / ring.size
    val cy = ring.sumOf { it.latitude() } / ring.size
    val scaled = ring.map { Point.fromLngLat(cx + (it.longitude() - cx) * 1.03, cy + (it.latitude() - cy) * 1.03) }
    val outProps = JsonObject().apply {
        for ((k, v) in props.entrySet()) add(k, v)
        addProperty("render_height", h)
        addProperty("height", h)
    }
    src.setGeoJson(
        FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf(scaled)), outProps))),
    )
}

private val DEFAULT_LOCATION = LatLng(FALLBACK_LAT, FALLBACK_LNG)

private suspend fun resolveLocation(context: Context, granted: Boolean): LatLng {
    val permitted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted || !permitted) return DEFAULT_LOCATION
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
        if (!lm.isProviderEnabled(provider)) continue
        lm.getLastKnownLocation(provider)?.let { return LatLng(it.latitude, it.longitude) }
    }
    val fresh = withTimeoutOrNull(6000) { requestSingleLocation(lm) }
    return fresh?.let { LatLng(it.latitude, it.longitude) } ?: DEFAULT_LOCATION
}

@SuppressLint("MissingPermission")
private suspend fun requestSingleLocation(lm: LocationManager): Location? = suspendCancellableCoroutine { cont ->
    val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        LocationManager.GPS_PROVIDER
    } else {
        LocationManager.NETWORK_PROVIDER
    }
    val listener = object : LocationListener {
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
