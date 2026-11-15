package tech.kulish.habits

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class HabitType { CHECK, COUNT, DATA }
enum class ScheduleMode { DAILY, DAYS, PER_WEEK, EVERY_N }
enum class DayPart { MORNING, DAY, EVENING }

/** One measured value of a DATA habit, e.g. distance in km. */
data class Field(val id: String, val name: String, val unit: String)

data class Habit(
    val id: String,
    val name: String,
    val type: HabitType = HabitType.CHECK,
    val icon: Int = 0,
    val mode: ScheduleMode = ScheduleMode.DAILY,
    /** ISO weekdays, 1 = Monday. Used when mode is DAYS. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val perWeek: Int = 3,
    val everyN: Int = 2,
    val part: DayPart = DayPart.MORNING,
    val fields: List<Field> = emptyList(),
    /** Unit and daily target of a COUNT habit. */
    val unit: String = "",
    val target: Double = 1.0,
    /** Reminder times as minutes from midnight. */
    val reminders: List<Int> = emptyList(),
    val sticky: Boolean = false,
    /** Things used with the habit (care products and the like), picked per entry. */
    val items: List<String> = emptyList(),
    val archived: Boolean = false,
    val created: String = LocalDate.now().toString(),
)

const val PARTIAL = 0
const val DONE = 1
const val SKIPPED = 2

/** Reserved keys in [Entry.values]. */
const val KEY_COUNT = "n"
const val KEY_FEEL = "feel"

data class Entry(
    val habitId: String,
    val date: String,
    val status: Int = DONE,
    val values: Map<String, Double> = emptyMap(),
    val note: String = "",
    val items: List<String> = emptyList(),
)

data class AppData(
    val habits: List<Habit> = emptyList(),
    val entries: List<Entry> = emptyList(),
)

// ---- JSON ----

private fun <T> JSONArray.map(f: (Int) -> T): List<T> = (0 until length()).map(f)

private fun strings(a: JSONArray?): List<String> = a?.map { a.getString(it) } ?: emptyList()

fun AppData.toJson(): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("habits", JSONArray(habits.map { h ->
        JSONObject().apply {
            put("id", h.id)
            put("name", h.name)
            put("type", h.type.name)
            put("icon", h.icon)
            put("mode", h.mode.name)
            put("days", JSONArray(h.days.sorted()))
            put("perWeek", h.perWeek)
            put("everyN", h.everyN)
            put("part", h.part.name)
            put("fields", JSONArray(h.fields.map { f ->
                JSONObject().put("id", f.id).put("name", f.name).put("unit", f.unit)
            }))
            put("unit", h.unit)
            put("target", h.target)
            put("reminders", JSONArray(h.reminders))
            put("sticky", h.sticky)
            put("items", JSONArray(h.items))
            put("archived", h.archived)
            put("created", h.created)
        }
    }))
    root.put("entries", JSONArray(entries.map { e ->
        JSONObject().apply {
            put("habit", e.habitId)
            put("date", e.date)
            put("status", e.status)
            put("values", JSONObject().apply { e.values.forEach { (k, v) -> put(k, v) } })
            put("note", e.note)
            put("items", JSONArray(e.items))
        }
    }))
    return root.toString()
}

fun parseAppData(text: String): AppData {
    val root = JSONObject(text)
    val hs = root.optJSONArray("habits") ?: JSONArray()
    val es = root.optJSONArray("entries") ?: JSONArray()
    val habits = hs.map { i ->
        val o = hs.getJSONObject(i)
        val days = o.optJSONArray("days")
        val fields = o.optJSONArray("fields")
        val rems = o.optJSONArray("reminders")
        Habit(
            id = o.getString("id"),
            name = o.optString("name"),
            type = runCatching { HabitType.valueOf(o.optString("type")) }.getOrDefault(HabitType.CHECK),
            icon = o.optInt("icon", 0),
            mode = runCatching { ScheduleMode.valueOf(o.optString("mode")) }.getOrDefault(ScheduleMode.DAILY),
            days = days?.map { days.getInt(it) }?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7),
            perWeek = o.optInt("perWeek", 3),
            everyN = o.optInt("everyN", 2),
            part = runCatching { DayPart.valueOf(o.optString("part")) }.getOrDefault(DayPart.MORNING),
            fields = fields?.map {
                val f = fields.getJSONObject(it)
                Field(f.getString("id"), f.optString("name"), f.optString("unit"))
            } ?: emptyList(),
            unit = o.optString("unit"),
            target = o.optDouble("target", 1.0),
            reminders = rems?.map { rems.getInt(it) } ?: emptyList(),
            sticky = o.optBoolean("sticky", false),
            items = strings(o.optJSONArray("items")),
            archived = o.optBoolean("archived", false),
            created = o.optString("created", LocalDate.now().toString()),
        )
    }
    val entries = es.map { i ->
        val o = es.getJSONObject(i)
        val vals = o.optJSONObject("values")
        val values = mutableMapOf<String, Double>()
        if (vals != null) for (k in vals.keys()) values[k] = vals.getDouble(k)
        Entry(
            habitId = o.getString("habit"),
            date = o.getString("date"),
            status = o.optInt("status", DONE),
            values = values,
            note = o.optString("note"),
            items = strings(o.optJSONArray("items")),
        )
    }
    return AppData(habits, entries)
}
