package `in`.voxagent.mobile.map

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
import org.maplibre.geojson.FeatureCollection

internal fun applyMissionControlLook(style: Style) {
    for (layer: Layer in style.layers) {
        val id = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor("#0b0c0e"))
            is FillLayer ->
                when {
                    "building" in id ->
                        layer.setProperties(PropertyFactory.visibility(Property.NONE))
                    "water" in id -> layer.setProperties(PropertyFactory.fillColor("#060708"))
                    "land" in id -> layer.setProperties(PropertyFactory.fillColor("#121316"))
                }
            is LineLayer ->
                when {
                    "building" in id ->
                        layer.setProperties(PropertyFactory.visibility(Property.NONE))
                    "road" in id ||
                        "street" in id ||
                        "path" in id ||
                        "bridge" in id ||
                        "tunnel" in id ->
                        layer.setProperties(
                            PropertyFactory.lineColor("#d0d1d2"),
                            PropertyFactory.lineOpacity(0.75f),
                        )
                }
            is FillExtrusionLayer ->
                if ("building" in id) {
                    layer.setProperties(PropertyFactory.visibility(Property.NONE))
                }
            else -> {}
        }
    }
}

internal fun findBuildingSource(style: Style): String? {
    val vectorSources = style.sources.filterIsInstance<VectorSource>()
    return vectorSources
        .firstOrNull { "openmaptiles" in it.id || "protomaps" in it.id || it.id == "composite" }
        ?.id ?: vectorSources.firstOrNull()?.id
}

internal fun extrusionBeforeId(style: Style): String? {
    val layers = style.layers
    val lastLine = layers.indexOfLast { it is LineLayer }
    return layers.drop(lastLine + 1).firstOrNull { it is SymbolLayer }?.id
}

internal fun heightExpression(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(14, Expression.literal(0)),
        Expression.stop(
            14.2,
            Expression.coalesce(
                Expression.get("render_height"),
                Expression.get("height"),
                Expression.literal(12),
            ),
        ),
    )

internal fun baseExpression(): Expression =
    Expression.coalesce(
        Expression.get("render_min_height"),
        Expression.get("min_height"),
        Expression.literal(0),
    )

internal fun addExtrusionLayer(
    style: Style,
    id: String,
    sourceId: String,
    sourceLayer: String?,
    color: String,
) {
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

internal fun add3dBuildings(style: Style) {
    if (style.getLayer(BUILDINGS_LAYER) != null) return
    val sourceId = findBuildingSource(style) ?: return
    addExtrusionLayer(style, BUILDINGS_LAYER, sourceId, "building", "#0f0f11")
}

internal fun addHighlightLayer(style: Style) {
    if (style.getSource(HIGHLIGHT_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(HIGHLIGHT_SOURCE, FeatureCollection.fromFeatures(emptyList()))
        )
    }
    addExtrusionLayer(style, HIGHLIGHT_LAYER, HIGHLIGHT_SOURCE, null, "#ff3b30")
}

internal fun addYouLayer(style: Style) {
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
