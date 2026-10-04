package `in`.voxagent.mobile.map.scene

import android.view.Choreographer
import `in`.voxagent.mobile.map.extrusionBeforeId
import `in`.voxagent.mobile.map.setHighlights
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

private const val PIN_SOURCE = "vox-scene-pins"
private const val ARC_SOURCE = "vox-scene-arcs"
private const val COLUMN_SOURCE = "vox-scene-columns"
private const val ARC_MS = 1200.0
private const val ARC_STEPS = 40
private const val COLUMN_HALF_M = 12.0
private const val COLUMN_MIN_H = 30.0
private const val COLUMN_MAX_H = 300.0
private val HIGHLIGHT_RETRY_MS = longArrayOf(0, 800, 1800, 3200)

private val PIN_COLORS = mapOf("place" to "#4cc9f0", "task" to "#ffd166", "spend" to "#ff6363")

private fun valid(lng: Double, lat: Double) =
    lng.isFinite() && lat.isFinite() && kotlin.math.abs(lng) <= 180 && kotlin.math.abs(lat) <= 90

private fun arcPath(from: List<Double>, to: List<Double>): List<Point> {
    val (x1, y1) = from
    val (x2, y2) = to
    val cx = (x1 + x2) / 2 - (y2 - y1) * 0.25
    val cy = (y1 + y2) / 2 + (x2 - x1) * 0.25
    return (0..ARC_STEPS).map { i ->
        val t = i.toDouble() / ARC_STEPS
        val u = 1 - t
        Point.fromLngLat(u * u * x1 + 2 * u * t * cx + t * t * x2, u * u * y1 + 2 * u * t * cy + t * t * y2)
    }
}

private fun square(lng: Double, lat: Double): Polygon {
    val dLat = COLUMN_HALF_M / 111320.0
    val dLng = dLat / cos(Math.toRadians(lat))
    return Polygon.fromLngLats(
        listOf(
            listOf(
                Point.fromLngLat(lng - dLng, lat - dLat),
                Point.fromLngLat(lng + dLng, lat - dLat),
                Point.fromLngLat(lng + dLng, lat + dLat),
                Point.fromLngLat(lng - dLng, lat + dLat),
                Point.fromLngLat(lng - dLng, lat - dLat),
            ),
        ),
    )
}

class SceneRenderer(
    private val map: MapLibreMap,
    private val mapView: MapView,
    private val onActiveChange: (Boolean) -> Unit,
    private val onCamera: () -> Unit,
) {
    private var home: LatLng? = null
    private var lastRev = -1L
    private var active = false
    private var highlights: List<LatLng> = emptyList()
    private var arcs: List<Pair<List<Point>, Int>> = emptyList()
    private var startedAt = 0L
    private var framing = false
    private val choreographer = Choreographer.getInstance()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000.0
            var running = false
            val features = arcs.map { (points, delayMs) ->
                val progress = min(1.0, max(0.0, (elapsed - delayMs) / ARC_MS))
                if (progress < 1.0) running = true
                val count = max(2, kotlin.math.ceil(progress * ARC_STEPS).toInt() + 1)
                Feature.fromGeometry(LineString.fromLngLats(if (progress > 0) points.take(count) else emptyList()))
            }
            (map.style?.getSource(ARC_SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(features))
            framing = running
            if (running) choreographer.postFrameCallback(this)
        }
    }

    fun ensure(style: Style) {
        if (style.getSource(PIN_SOURCE) != null) return
        for (id in listOf(PIN_SOURCE, ARC_SOURCE, COLUMN_SOURCE)) {
            style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyList())))
        }
        val columns = FillExtrusionLayer("vox-scene-columns", COLUMN_SOURCE).withProperties(
            PropertyFactory.fillExtrusionColor("#ff6363"),
            PropertyFactory.fillExtrusionHeight(Expression.get("h")),
            PropertyFactory.fillExtrusionBase(0f),
            PropertyFactory.fillExtrusionOpacity(0.85f),
        )
        val before = extrusionBeforeId(style)
        if (before != null) style.addLayerBelow(columns, before) else style.addLayer(columns)
        style.addLayer(
            LineLayer("vox-scene-arcs", ARC_SOURCE).withProperties(
                PropertyFactory.lineColor("#ff6363"),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )
        style.addLayer(
            CircleLayer("vox-scene-pin-halo", PIN_SOURCE).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleOpacity(0.25f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            ),
        )
        style.addLayer(
            CircleLayer("vox-scene-pin-dot", PIN_SOURCE).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#ffffff"),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            ),
        )
        style.addLayer(
            SymbolLayer("vox-scene-pin-label", PIN_SOURCE).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textColor("#ffffff"),
                PropertyFactory.textHaloColor("#050607"),
                PropertyFactory.textHaloWidth(1.5f),
            ),
        )
    }

    private var ready = false
    private var pendingHighlights = false
    private val idleListener = MapView.OnDidBecomeIdleListener {
        ready = true
        if (pendingHighlights) paintHighlights()
    }

    init {
        mapView.addOnDidBecomeIdleListener(idleListener)
    }

    private fun paintHighlights() {
        handler.removeCallbacksAndMessages(null)
        if (!ready) {
            pendingHighlights = true
            return
        }
        pendingHighlights = false
        for (ms in HIGHLIGHT_RETRY_MS) {
            handler.postDelayed({ setHighlights(map, listOfNotNull(home) + highlights) }, ms)
        }
    }

    fun setHome(point: LatLng) {
        home = point
        paintHighlights()
    }

    fun apply(scene: MapScene) {
        if (scene.rev < lastRev) return
        val changed = scene.rev != lastRev
        lastRev = scene.rev
        val style = map.style ?: return

        (style.getSource(PIN_SOURCE) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                scene.pins.filter { valid(it.lng, it.lat) }.map {
                    Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).apply {
                        addStringProperty("id", it.id)
                        addStringProperty("label", listOfNotNull(it.label, it.state).joinToString(" · "))
                        addStringProperty("color", PIN_COLORS[it.kind] ?: PIN_COLORS.getValue("place"))
                        addStringProperty("state", it.state ?: "")
                    }
                },
            ),
        )

        val columns = scene.columns.filter { valid(it.lng, it.lat) }
        val maxValue = max(1.0, columns.maxOfOrNull { it.value } ?: 1.0)
        (style.getSource(COLUMN_SOURCE) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(
                columns.map {
                    Feature.fromGeometry(square(it.lng, it.lat)).apply {
                        addNumberProperty("h", COLUMN_MIN_H + (COLUMN_MAX_H - COLUMN_MIN_H) * (it.value / maxValue))
                    }
                },
            ),
        )

        arcs = scene.arcs
            .filter { it.from.size == 2 && it.to.size == 2 && valid(it.from[0], it.from[1]) && valid(it.to[0], it.to[1]) }
            .map { arcPath(it.from, it.to) to it.delayMs }
        if (changed) startedAt = System.nanoTime()
        choreographer.removeFrameCallback(frame)
        choreographer.postFrameCallback(frame)

        highlights = scene.highlights.filter { valid(it.lng, it.lat) }.map { LatLng(it.lat, it.lng) }
        paintHighlights()

        if (scene.isActive != active) {
            active = scene.isActive
            onActiveChange(active)
        }
        val camera = scene.camera
        if (changed && camera != null && valid(camera.lng, camera.lat)) {
            onCamera()
            map.easeCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(camera.lat, camera.lng))
                        .zoom(camera.zoom ?: 16.0)
                        .tilt(camera.pitch ?: 60.0)
                        .bearing(camera.bearing ?: map.cameraPosition.bearing)
                        .build(),
                ),
                camera.durationMs ?: 2500,
            )
        }
    }

    fun destroy() {
        ready = false
        mapView.removeOnDidBecomeIdleListener(idleListener)
        choreographer.removeFrameCallback(frame)
        handler.removeCallbacksAndMessages(null)
    }
}
