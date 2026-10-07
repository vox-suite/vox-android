package `in`.voxagent.mobile.map

import android.graphics.RectF
import com.google.gson.JsonObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal fun outerRings(geometry: Geometry?): List<List<Point>> =
    when (geometry) {
        is Polygon -> listOf(geometry.coordinates()[0])
        is MultiPolygon -> geometry.coordinates().map { it[0] }
        else -> emptyList()
    }

internal fun pointInRing(x: Double, y: Double, ring: List<Point>): Boolean {
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

internal fun footprintAt(map: MapLibreMap, loc: LatLng): Feature? {
    val style = map.style ?: return null
    if (style.getLayer(BUILDINGS_LAYER) == null) return null
    val point = map.projection.toScreenLocation(loc)
    val box = RectF(point.x - 80f, point.y - 80f, point.x + 80f, point.y + 80f)
    val hits = map.queryRenderedFeatures(box, BUILDINGS_LAYER)
    var bestRing: List<Point>? = null
    var bestProps: JsonObject? = null
    var best = Double.MAX_VALUE
    for (feature in hits) {
        for (ring in outerRings(feature.geometry())) {
            val d =
                if (pointInRing(loc.longitude, loc.latitude, ring)) {
                    -1.0
                } else {
                    ring.minOf {
                        (it.longitude() - loc.longitude).let { lx -> lx * lx } +
                            (it.latitude() - loc.latitude).let { ly -> ly * ly }
                    }
                }
            if (d < best) {
                best = d
                bestRing = ring
                bestProps = feature.properties()
            }
        }
    }
    val ring = bestRing ?: return null
    val props = bestProps ?: JsonObject()
    val h =
        (props.get("render_height")?.takeIf { !it.isJsonNull }?.asDouble
            ?: props.get("height")?.takeIf { !it.isJsonNull }?.asDouble
            ?: 12.0) + 1.0
    val cx = ring.sumOf { it.longitude() } / ring.size
    val cy = ring.sumOf { it.latitude() } / ring.size
    val scaled =
        ring.map {
            Point.fromLngLat(cx + (it.longitude() - cx) * 1.03, cy + (it.latitude() - cy) * 1.03)
        }
    val outProps =
        JsonObject().apply {
            for ((k, v) in props.entrySet()) add(k, v)
            addProperty("render_height", h)
            addProperty("height", h)
        }
    return Feature.fromGeometry(Polygon.fromLngLats(listOf(scaled)), outProps)
}

internal fun setHighlights(map: MapLibreMap, points: List<LatLng>) {
    val src = map.style?.getSource(HIGHLIGHT_SOURCE) as? GeoJsonSource ?: return
    src.setGeoJson(FeatureCollection.fromFeatures(points.mapNotNull { footprintAt(map, it) }))
}
