package tech.kulish.habits

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object Dates {
    /** The day rolls over at 03:00, so a late-evening check-in after midnight still counts. */
    fun today(): LocalDate = LocalDateTime.now().minusHours(3).toLocalDate()

    val dayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
    private val dayFull = listOf("Понеділок", "Вівторок", "Середа", "Четвер", "Пʼятниця", "Субота", "Неділя")
    private val monthGen = listOf(
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня",
    )

    fun long(d: LocalDate): String =
        "${dayFull[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${monthGen[d.monthValue - 1]}"

    fun short(d: LocalDate): String =
        "${dayShort[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${monthGen[d.monthValue - 1]}"

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
}

/** Ukrainian plural: 1 день, 2 дні, 5 днів. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

fun fmt(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString()
    else String.format(Locale.US, "%.1f", v).replace('.', ',')

fun fmtTime(minuteOfDay: Int): String = String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

/** Pace as m:ss per km, or null when it cannot be computed. */
fun pace(distKm: Double, minutes: Double): String? {
    if (distKm <= 0.0 || minutes <= 0.0) return null
    val secs = Math.round(minutes * 60.0 / distKm)
    return "${secs / 60}:${String.format(Locale.US, "%02d", secs % 60)}"
}

fun Habit.distField(): Field? = fields.firstOrNull { it.unit.trim().lowercase() == "км" }
fun Habit.timeField(): Field? = fields.firstOrNull { it.unit.trim().lowercase() == "хв" }

typealias DayMap = Map<LocalDate, Entry>

fun entriesOf(data: AppData, habitId: String): DayMap =
    data.entries.filter { it.habitId == habitId }.associateBy { LocalDate.parse(it.date) }

fun isDone(e: Entry?): Boolean = e?.status == DONE

fun doneInWeek(m: DayMap, d: LocalDate): Int {
    val start = Dates.weekStart(d)
    return (0..6).count { isDone(m[start.plusDays(it.toLong())]) }
}

/** Latest done day on or before [d]. */
fun lastDone(m: DayMap, d: LocalDate): LocalDate? =
    m.entries.filter { it.value.status == DONE && !it.key.isAfter(d) }.map { it.key }.maxByOrNull { it.toEpochDay() }

/** Whether the habit asks for attention on [d], ignoring what was logged on [d] itself. */
fun isDue(h: Habit, m: DayMap, d: LocalDate): Boolean = when (h.mode) {
    ScheduleMode.DAILY -> true
    ScheduleMode.DAYS -> d.dayOfWeek.value in h.days
    ScheduleMode.PER_WEEK -> {
        val doneOtherDays = doneInWeek(m, d) - (if (isDone(m[d])) 1 else 0)
        doneOtherDays < h.perWeek
    }
    ScheduleMode.EVERY_N -> {
        val last = lastDone(m, d.minusDays(1))
        last == null || ChronoUnit.DAYS.between(last, d) >= h.everyN
    }
}

fun scheduleLabel(h: Habit): String = when (h.mode) {
    ScheduleMode.DAILY -> "Щодня"
    ScheduleMode.DAYS ->
        if (h.days.size == 7) "Щодня" else h.days.sorted().joinToString(" ") { Dates.dayShort[it - 1] }
    ScheduleMode.PER_WEEK -> "${h.perWeek} ${plural(h.perWeek, "раз", "рази", "разів")} на тиждень"
    ScheduleMode.EVERY_N -> "Кожні ${h.everyN} ${plural(h.everyN, "день", "дні", "днів")}"
}

/**
 * Habit strength from 0 to 1: an exponentially smoothed share of kept days, in the spirit of
 * Loop Habit Tracker. A miss lowers it a little instead of resetting it; a skipped day is neutral.
 */
fun strength(h: Habit, m: DayMap, today: LocalDate): Float {
    val first = m.keys.minByOrNull { it.toEpochDay() }
    val created = runCatching { LocalDate.parse(h.created) }.getOrDefault(today)
    var d: LocalDate = if (first != null && first.isBefore(created)) first else created
    val horizon = today.minusDays(180)
    if (d.isBefore(horizon)) d = horizon
    val freq = when (h.mode) {
        ScheduleMode.DAILY -> 1.0
        ScheduleMode.DAYS -> h.days.size / 7.0
        ScheduleMode.PER_WEEK -> h.perWeek / 7.0
        ScheduleMode.EVERY_N -> 1.0 / h.everyN
    }
    val keep = 0.5.pow(sqrt(freq) / 13.0)
    var score = 0.0
    var last: LocalDate? = lastDone(m, d.minusDays(1))
    while (!d.isAfter(today)) {
        val e = m[d]
        if (isDone(e)) last = d
        val v: Double? = when {
            e?.status == SKIPPED -> null
            h.mode == ScheduleMode.DAILY || h.mode == ScheduleMode.DAYS -> when {
                h.mode == ScheduleMode.DAYS && d.dayOfWeek.value !in h.days -> null
                isDone(e) -> 1.0
                d == today -> null
                else -> 0.0
            }
            h.mode == ScheduleMode.PER_WEEK -> {
                val c = (0..6).count { isDone(m[d.minusDays(it.toLong())]) }
                min(1.0, c.toDouble() / h.perWeek)
            }
            else -> {
                val l = last
                when {
                    l != null && ChronoUnit.DAYS.between(l, d) < h.everyN -> 1.0
                    d == today -> null
                    else -> 0.0
                }
            }
        }
        if (v != null) score = score * keep + v * (1 - keep)
        d = d.plusDays(1)
    }
    return score.toFloat().coerceIn(0f, 1f)
}

/** Current streak with its unit already declined, e.g. "5 тижнів". */
fun streak(h: Habit, m: DayMap, today: LocalDate): String {
    when (h.mode) {
        ScheduleMode.DAILY, ScheduleMode.DAYS -> {
            var n = 0
            var d = today
            for (i in 0 until 2000) {
                val scheduled = h.mode == ScheduleMode.DAILY || d.dayOfWeek.value in h.days
                val e = m[d]
                if (scheduled) {
                    if (isDone(e)) n++
                    else if (e?.status == SKIPPED || d == today) { /* neutral */ }
                    else break
                }
                d = d.minusDays(1)
            }
            return "$n ${plural(n, "день", "дні", "днів")}"
        }
        ScheduleMode.PER_WEEK -> {
            var n = 0
            val thisWeek = Dates.weekStart(today)
            var w = thisWeek
            for (i in 0 until 520) {
                if (doneInWeek(m, w) >= h.perWeek) n++
                else if (w != thisWeek) break
                w = w.minusWeeks(1)
            }
            return "$n ${plural(n, "тиждень", "тижні", "тижнів")}"
        }
        ScheduleMode.EVERY_N -> {
            val dates = m.entries.filter { it.value.status == DONE }.map { it.key }.sortedByDescending { it.toEpochDay() }
            var n = 0
            if (dates.isNotEmpty() && ChronoUnit.DAYS.between(dates[0], today) <= h.everyN) {
                n = 1
                for (i in 1 until dates.size) {
                    if (ChronoUnit.DAYS.between(dates[i], dates[i - 1]) <= h.everyN) n++ else break
                }
            }
            return "$n ${plural(n, "раз", "рази", "разів")}"
        }
    }
}

/** One line describing what an entry holds, e.g. "5 км · 28 хв · 5:36". */
fun entrySummary(h: Habit, e: Entry): String {
    if (e.status == SKIPPED) return "Пропущено"
    val parts = mutableListOf<String>()
    when (h.type) {
        HabitType.DATA -> {
            for (f in h.fields) {
                val v = e.values[f.id] ?: continue
                parts += (fmt(v) + " " + f.unit).trim()
            }
            val dist = h.distField()?.let { e.values[it.id] }
            val time = h.timeField()?.let { e.values[it.id] }
            if (dist != null && time != null) pace(dist, time)?.let { parts += it }
        }
        HabitType.COUNT -> parts += (fmt(e.values[KEY_COUNT] ?: 0.0) + " " + h.unit).trim()
        HabitType.CHECK -> if (e.items.isNotEmpty()) parts += e.items.joinToString(", ")
    }
    if (parts.isEmpty()) parts += "Зроблено"
    return parts.joinToString(" · ")
}
