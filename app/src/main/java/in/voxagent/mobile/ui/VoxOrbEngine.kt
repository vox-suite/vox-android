package `in`.voxagent.mobile.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kotlin port of vox-bridge/assets/thinking-orb-engine.js's `frameRibbon`,
 * resolved for state="composing" (idle) at engine size 64 — the same
 * animation vox-desktop's sign-in orb uses. Values below (lanes=3, segs=44,
 * ghostN=38, rBase=0.935, rDepth=1.445, bandMul=3.9) are PRESETS.ribbon[64]
 * with the "composing" count/size scaling already baked in.
 */
internal object VoxOrbEngine {
    const val SPEED = 2.34f
    private const val ENGINE_SIZE = 64f
    private const val LANES = 3
    private const val SEGS = 44
    private const val GHOST_N = 38
    private const val R_BASE = 0.935f
    private const val R_DEPTH = 1.445f
    private const val RS_POW = 0.6f
    private const val R_MIN = 0.3f
    private const val BAND_MUL = 3.9f
    private const val WOB_MUL = 1f
    private const val CAM_TILT = 0.3f
    private const val TA = 0.55f

    data class Dot(val x: Float, val y: Float, val z: Float, val r: Float, val white: Float, val alpha: Float)

    private fun fibDir(i: Int, n: Int): Triple<Float, Float, Float> {
        val golden = PI.toFloat() * (3f - sqrt(5f))
        val y = 1f - 2f * (i + 0.5f) / n
        val rad = sqrt((1f - y * y).coerceAtLeast(0f))
        val a = i * golden
        return Triple(rad * cos(a), y, rad * sin(a))
    }

    /** yaw/tilt are held constant here (spin=0 in the "composing" preset). */
    private class Projector(yaw: Float, tilt: Float, val cx: Float, val cy: Float, val scale: Float) {
        private val st = sin(tilt)
        private val ct = cos(tilt)
        private val sy = sin(yaw)
        private val cyw = cos(yaw)

        fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val x1 = x * cyw + z * sy
            val z1 = -x * sy + z * cyw
            val y1 = y * ct - z1 * st
            val z2 = y * st + z1 * ct
            return Triple(cx + x1 * scale, cy - y1 * scale, z2)
        }
    }

    /** [t] is the JS engine's `sim` accumulator: elapsed seconds * SPEED. */
    fun frameRibbon(t: Float): List<Dot> {
        val cx = ENGINE_SIZE / 2f
        val cy = ENGINE_SIZE / 2f
        val r = ENGINE_SIZE / 2f * 0.78f
        val pt = Projector(yaw = 0f, tilt = CAM_TILT, cx = cx, cy = cy, scale = 1f)
        val rs = (ENGINE_SIZE / 300f).pow(RS_POW)

        val dots = ArrayList<Dot>(GHOST_N + LANES * SEGS * 4)

        for (i in 0 until GHOST_N) {
            val (dx, dy, dz) = fibDir(i, GHOST_N)
            val (px, py, z) = pt.project(dx * r, dy * r, dz * r)
            val depth = (z / r + 1f) / 2f
            dots.add(Dot(px, py, z, 0.8f * rs, 0.78f, 0.1f + 0.22f * depth))
        }

        // faceOn is unset for "ribbon" (only "ring" sets it) — camera plane
        // (ux,uy,uz)/(vx,vy,vz)/(nx,ny,nz) below is constant since spin=0.
        val ux = 1f
        val uy = 0f
        val uz = 0f
        val vx = -uz * sin(TA)
        val vy = cos(TA)
        val vz = ux * sin(TA)
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val lanes = max(1, round(LANES * BAND_MUL).toInt())

        for (w in 0 until lanes) {
            val laneOff = (w - (lanes - 1) / 2f) * 0.075f
            val edge = abs(w - (lanes - 1) / 2f) / max(1f, (lanes - 1) / 2f)
            for (k in 0 until SEGS) {
                val a = k / SEGS.toFloat() * 2f * PI.toFloat()
                val wob = (0.16f * sin(a * 3 - t * 1.7f + w * 0.22f) + 0.07f * sin(a * 5 + t * 1.1f)) * WOB_MUL
                val off = laneOff + wob
                val x = ux * cos(a) + vx * sin(a) + nx * off
                val y = uy * cos(a) + vy * sin(a) + ny * off
                val z = uz * cos(a) + vz * sin(a) + nz * off
                val l = sqrt(x * x + y * y + z * z)
                val (px, py, zr) = pt.project(x / l * r, y / l * r, z / l * r)
                val depth = (zr / r + 1f) / 2f
                dots.add(
                    Dot(
                        x = px,
                        y = py,
                        z = zr,
                        r = (R_BASE + R_DEPTH * depth) * (1f - 0.25f * edge) * rs,
                        white = 0.52f - 0.44f * depth + 0.18f * edge,
                        alpha = 0.4f + 0.6f * depth,
                    ),
                )
            }
        }

        return dots
            .asSequence()
            .filter { it.alpha >= 0.02f }
            .map { it.copy(r = max(R_MIN, it.r)) }
            .sortedBy { it.z }
            .toList()
    }
}
