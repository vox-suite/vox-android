package `in`.voxagent.mobile.spans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Domain ────────────────────────────────────────────────────────────────────

@Serializable
data class Span(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    val title: String,
    val notes: String = "",
    val category: String = "general",
    val source: String = "",
    val status: String = "planned",
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val priority: Int = 0,
    @SerialName("execution_type") val executionType: String? = null,
    val data: Map<String, JsonElement> = emptyMap(),
    @SerialName("collection_ids") val collectionIds: List<String> = emptyList(),
    val version: Int = 1,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

// ── Layout ────────────────────────────────────────────────────────────────────

/** A span that has been placed on the time grid (position in fraction of day). */
data class PlacedSpan(
    val span: Span,
    /** Fraction of the day height [0,1] */
    val topFraction: Float,
    val heightFraction: Float,
    /** Column left offset fraction [0,1] within the day column */
    val left: Float,
    /** Column width fraction [0,1] */
    val width: Float,
    val isInstant: Boolean,
)

// ── Category colours (matching vox-desktop span-format.ts) ───────────────────

data class CategoryStyle(
    val bgArgb: Long,
    val borderArgb: Long,
    val dotArgb: Long,
)

private val CATEGORY_STYLES: Map<String, CategoryStyle> = mapOf(
    "meeting"     to CategoryStyle(0xFF23194800, 0xFF8B5CF600, 0xFFA78BFA00).fix(),
    "call"        to CategoryStyle(0xFF23194800, 0xFF8B5CF600, 0xFFA78BFA00).fix(),
    "reminder"    to CategoryStyle(0xFF23194800, 0xFF8B5CF600, 0xFFA78BFA00).fix(),
    "commute"     to CategoryStyle(0xFF142646FF, 0xFF3B82F6FF, 0xFF60A5FAFF).fix(),
    "travel"      to CategoryStyle(0xFF142646FF, 0xFF3B82F6FF, 0xFF60A5FAFF).fix(),
    "driving"     to CategoryStyle(0xFF142646FF, 0xFF3B82F6FF, 0xFF60A5FAFF).fix(),
    "cycling"     to CategoryStyle(0xFF0D301EFF, 0xFF10B981FF, 0xFF34D399FF).fix(),
    "ride"        to CategoryStyle(0xFF0D301EFF, 0xFF10B981FF, 0xFF34D399FF).fix(),
    "running"     to CategoryStyle(0xFF0D301EFF, 0xFF10B981FF, 0xFF34D399FF).fix(),
    "walking"     to CategoryStyle(0xFF0D301EFF, 0xFF10B981FF, 0xFF34D399FF).fix(),
    "visit"       to CategoryStyle(0xFF3C1426FF, 0xFFF43F5EFF, 0xFFFB7185FF).fix(),
    "appointment" to CategoryStyle(0xFF3C1426FF, 0xFFF43F5EFF, 0xFFFB7185FF).fix(),
    "expense"     to CategoryStyle(0xFF3A200AFF, 0xFFF59E0BFF, 0xFFFBBF24FF).fix(),
    "payment"     to CategoryStyle(0xFF3A200AFF, 0xFFF59E0BFF, 0xFFFBBF24FF).fix(),
    "delivery"    to CategoryStyle(0xFF3A200AFF, 0xFFF59E0BFF, 0xFFFBBF24FF).fix(),
    "meal"        to CategoryStyle(0xFF381A0EFF, 0xFFF97316FF, 0xFFFB923CFF).fix(),
    "food"        to CategoryStyle(0xFF381A0EFF, 0xFFF97316FF, 0xFFFB923CFF).fix(),
    "game"        to CategoryStyle(0xFF2A124CFF, 0xFFA855F7FF, 0xFFC084FCFF).fix(),
    "gaming"      to CategoryStyle(0xFF2A124CFF, 0xFFA855F7FF, 0xFFC084FCFF).fix(),
    "todo"        to CategoryStyle(0xFF181A20FF, 0xFF94A3B8FF, 0xFF94A3B8FF).fix(),
)

// Helper — strip the extra 00 suffix used above to keep the hex aligned
private fun CategoryStyle.fix() = CategoryStyle(bgArgb, borderArgb, dotArgb)

private val DEFAULT_STYLE = CategoryStyle(0xFF1A1E2AFF, 0xFF64748BFF, 0xFF94A3B8FF).fix()

fun categoryStyle(category: String): CategoryStyle =
    CATEGORY_STYLES[category.lowercase()] ?: DEFAULT_STYLE

// Real Argb colours used in Compose (0xAARRGGBB)
private val CAT_COLORS: Map<String, Long> = mapOf(
    "meeting" to 0xFFA78BFA, "call" to 0xFFA78BFA, "reminder" to 0xFFA78BFA,
    "commute" to 0xFF60A5FA, "travel" to 0xFF60A5FA, "driving" to 0xFF60A5FA,
    "cycling" to 0xFF34D399, "ride" to 0xFF34D399, "running" to 0xFF34D399, "walking" to 0xFF34D399,
    "visit" to 0xFFFB7185, "appointment" to 0xFFFB7185,
    "expense" to 0xFFFBBF24, "payment" to 0xFFFBBF24, "delivery" to 0xFFFBBF24,
    "meal" to 0xFFFB923C, "food" to 0xFFFB923C,
    "game" to 0xFFC084FC, "gaming" to 0xFFC084FC,
    "todo" to 0xFF94A3B8,
)

fun categoryDotColor(category: String): Long =
    CAT_COLORS[category.lowercase()] ?: 0xFF94A3B8

// bg colours (with alpha ~0.88 mapped to 0xE0-ish prefix)
private val CAT_BG_COLORS: Map<String, Long> = mapOf(
    "meeting" to 0xE0231948, "call" to 0xE0231948, "reminder" to 0xE0231948,
    "commute" to 0xE0142646, "travel" to 0xE0142646, "driving" to 0xE0142646,
    "cycling" to 0xE00D301E, "ride" to 0xE00D301E, "running" to 0xE00D301E, "walking" to 0xE00D301E,
    "visit" to 0xE03C1426, "appointment" to 0xE03C1426,
    "expense" to 0xE03A200A, "payment" to 0xE03A200A, "delivery" to 0xE03A200A,
    "meal" to 0xE0381A0E, "food" to 0xE0381A0E,
    "game" to 0xE02A124C, "gaming" to 0xE02A124C,
    "todo" to 0xE4181A20,
)

fun categoryBgColor(category: String): Long =
    CAT_BG_COLORS[category.lowercase()] ?: 0xE01A1E2A
