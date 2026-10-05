package `in`.voxagent.mobile.map.scene

import kotlinx.serialization.Serializable

@Serializable
data class MapScene(
    val rev: Long = 0,
    val camera: SceneCamera? = null,
    val pins: List<ScenePin> = emptyList(),
    val arcs: List<SceneArc> = emptyList(),
    val columns: List<SceneColumn> = emptyList(),
    val highlights: List<SceneHighlight> = emptyList(),
) {
    val isActive get() = camera != null || pins.isNotEmpty() || arcs.isNotEmpty() ||
        columns.isNotEmpty() || highlights.isNotEmpty()
}

@Serializable
data class SceneCamera(
    val lng: Double,
    val lat: Double,
    val zoom: Double? = null,
    val pitch: Double? = null,
    val bearing: Double? = null,
    val durationMs: Int? = null,
)

@Serializable
data class ScenePin(
    val id: String,
    val lng: Double,
    val lat: Double,
    val label: String? = null,
    val kind: String,
    val state: String? = null,
)

@Serializable
data class SceneArc(
    val id: String,
    val from: List<Double>,
    val to: List<Double>,
    val label: String? = null,
    val delayMs: Int = 0,
)

@Serializable
data class SceneColumn(
    val id: String,
    val lng: Double,
    val lat: Double,
    val value: Double,
    val label: String? = null,
)

@Serializable
data class SceneHighlight(val id: String, val lng: Double, val lat: Double)
