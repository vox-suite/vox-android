package `in`.voxagent.mobile.control

import androidx.compose.runtime.staticCompositionLocalOf
import `in`.voxagent.mobile.net.VoxJson
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal val AppControlJson = Json(VoxJson) { encodeDefaults = true }

@Serializable
data class TimelineFilter(
    val startAt: String,
    val endAt: String,
    val timezone: String,
    val spendingOnly: Boolean = true,
    val merchant: String? = null,
    val category: String? = null,
)

@Serializable
data class AppContext(
    val revision: Long = 0,
    val page: String = "agent",
    val spaceId: String? = null,
    val pulseId: String? = null,
    val selectedItemId: String? = null,
    val timezone: String = ZoneId.systemDefault().id,
    val timeline: TimelineFilter? = null,
    val terminalOpen: Boolean = false,
    val resourceDetails: JsonObject? = null,
)

@Serializable
data class AppCommand(
    val commandId: String,
    val expiresAt: String,
    val expectedRevision: Long? = null,
    val action: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

class AppControlState {
    private val mutable = MutableStateFlow(AppContext())
    val context = mutable.asStateFlow()
    private val ticks = MutableStateFlow(0L)
    val actions = ticks.asStateFlow()

    fun patch(change: (AppContext) -> AppContext) {
        val current = mutable.value
        val next = change(current).copy(revision = current.revision)
        if (current != next) mutable.value = next.copy(revision = current.revision + 1)
    }

    fun navigate(page: String) = patch {
        it.copy(
            page = page,
            spaceId = null,
            pulseId = null,
            selectedItemId = null,
            resourceDetails = null,
        )
    }

    fun changed() {
        ticks.value++
    }

    fun reset() {
        mutable.value = AppContext()
        ticks.value = 0
    }
}

val LocalAppControl = staticCompositionLocalOf<AppControlState?> { null }

internal fun JsonObject.text(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun resolveTimelineFilter(args: JsonObject): TimelineFilter {
    val zone = ZoneId.of(args.text("timezone") ?: ZoneId.systemDefault().id)
    val today = LocalDate.now(zone)
    val start: LocalDate
    val end: LocalDate
    when (args.text("range")) {
        "today" -> {
            start = today
            end = today
        }
        "yesterday" -> {
            start = today.minusDays(1)
            end = start
        }
        "this_week",
        "last_week" -> {
            start =
                today.minusDays(
                    (today.dayOfWeek.value % 7).toLong() +
                        if (args.text("range") == "last_week") 7 else 0
                )
            end = start.plusDays(6)
        }
        "custom" -> {
            start = LocalDate.parse(args.text("startDate"))
            end = LocalDate.parse(args.text("endDate"))
        }
        else -> error("Choose today, yesterday, this week, last week, or a custom date range")
    }
    require(!end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(start, end) < 366) {
        "Choose a valid range of up to one year"
    }
    return TimelineFilter(
        start.atStartOfDay(zone).toInstant().toString(),
        end.plusDays(1).atStartOfDay(zone).toInstant().toString(),
        zone.id,
        merchant = args.text("merchant"),
        category = args.text("category"),
    )
}
