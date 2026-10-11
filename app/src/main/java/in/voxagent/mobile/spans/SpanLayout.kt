package `in`.voxagent.mobile.spans

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

val zone: ZoneId
    get() = ZoneId.systemDefault()

fun dayStartMs(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % 7).toLong())

fun monthGridDays(anchor: LocalDate): List<LocalDate> {
    val first = anchor.withDayOfMonth(1)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val last = first.plusMonths(1).minusDays(1)
    val gridEnd = last.plusDays((6 - last.dayOfWeek.value % 7).toLong())
    val total = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1
    val target = if (total <= 35) 35 else 42
    return List(target) { gridStart.plusDays(it.toLong()) }
}
