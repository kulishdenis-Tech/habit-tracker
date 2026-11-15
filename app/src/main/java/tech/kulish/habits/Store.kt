package tech.kulish.habits

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

/** All data lives in one JSON file in the app's private storage. No network, no account. */
object Repo {
    private lateinit var file: File
    private val state = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = state

    @Volatile
    private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        file = File(ctx.applicationContext.filesDir, "data.json")
        if (file.exists()) {
            val parsed = runCatching { parseAppData(file.readText()) }.getOrNull()
            if (parsed != null) {
                state.value = parsed
            } else {
                // Keep the unreadable file for recovery instead of overwriting it.
                file.copyTo(File(file.parentFile, "data.bad.json"), overwrite = true)
                state.value = seed()
                save()
            }
        } else {
            state.value = seed()
            save()
        }
        ready = true
    }

    @Synchronized
    fun update(fn: (AppData) -> AppData) {
        state.value = fn(state.value)
        save()
    }

    private fun save() {
        val tmp = File(file.parentFile, "data.tmp")
        tmp.writeText(state.value.toJson())
        if (!tmp.renameTo(file)) {
            file.writeText(state.value.toJson())
            tmp.delete()
        }
    }

    fun setEntry(e: Entry) = update { d ->
        d.copy(entries = d.entries.filterNot { it.habitId == e.habitId && it.date == e.date } + e)
    }

    fun clearEntry(habitId: String, date: LocalDate) = update { d ->
        val key = date.toString()
        d.copy(entries = d.entries.filterNot { it.habitId == habitId && it.date == key })
    }

    fun upsertHabit(h: Habit) = update { d ->
        if (d.habits.any { it.id == h.id }) d.copy(habits = d.habits.map { if (it.id == h.id) h else it })
        else d.copy(habits = d.habits + h)
    }

    fun deleteHabit(id: String) = update { d ->
        d.copy(habits = d.habits.filterNot { it.id == id }, entries = d.entries.filterNot { it.habitId == id })
    }

    fun exportJson(): String = state.value.toJson()

    /** Replaces everything with the backup. Throws if the text is not a valid backup. */
    fun importJson(text: String) {
        val parsed = parseAppData(text)
        update { parsed }
    }

    fun exportCsv(): String {
        val d = state.value
        val names = d.habits.associate { it.id to it }
        val sb = StringBuilder("date,habit,status,values,items,note\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        for (e in d.entries.sortedBy { it.date }) {
            val h = names[e.habitId] ?: continue
            val status = when (e.status) { DONE -> "done"; SKIPPED -> "skipped"; else -> "partial" }
            val values = e.values.entries.joinToString("; ") { (k, v) ->
                val f = h.fields.find { it.id == k }
                val label = when {
                    f != null -> f.name + (if (f.unit.isNotBlank()) " (${f.unit})" else "")
                    k == KEY_COUNT -> h.unit.ifBlank { "count" }
                    k == KEY_FEEL -> "feel"
                    else -> k
                }
                "$label=$v"
            }
            sb.append(e.date).append(',').append(q(h.name)).append(',').append(status).append(',')
                .append(q(values)).append(',').append(q(e.items.joinToString("; "))).append(',')
                .append(q(e.note)).append('\n')
        }
        return sb.toString()
    }

    /** Starter habits for the first launch. Everything here can be edited or deleted. */
    private fun seed(): AppData = AppData(
        habits = listOf(
            Habit(
                id = newId(), name = "Вітаміни", type = HabitType.CHECK, icon = 1,
                mode = ScheduleMode.DAILY, part = DayPart.MORNING,
            ),
            Habit(
                id = newId(), name = "Пробіжка", type = HabitType.DATA, icon = 0,
                mode = ScheduleMode.PER_WEEK, perWeek = 3, part = DayPart.MORNING,
                fields = listOf(Field("dist", "Дистанція", "км"), Field("time", "Час", "хв")),
            ),
            Habit(
                id = newId(), name = "Миття волосся", type = HabitType.CHECK, icon = 2,
                mode = ScheduleMode.EVERY_N, everyN = 2, part = DayPart.EVENING,
            ),
            Habit(
                id = newId(), name = "Догляд", type = HabitType.CHECK, icon = 3,
                mode = ScheduleMode.DAILY, part = DayPart.EVENING,
            ),
        )
    )
}
