package tech.kulish.habits

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.ZoneId

const val ACT_DONE = "tech.kulish.habits.DONE"
const val ACT_SNOOZE = "tech.kulish.habits.SNOOZE"
const val ACT_SKIP = "tech.kulish.habits.SKIP"
const val EXTRA_HABIT = "habit"
private const val EXTRA_MINUTE = "minute"

object Reminders {
    private const val CHANNEL = "reminders"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "Нагадування", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Нагадування про звички"
        nm.createNotificationChannel(ch)
    }

    /** Sets the next alarm for every reminder of every active habit. Safe to call repeatedly. */
    fun rescheduleAll(ctx: Context) {
        for (h in Repo.data.value.habits) {
            if (h.archived) continue
            for (minute in h.reminders) schedule(ctx, h.id, minute)
        }
    }

    fun schedule(ctx: Context, habitId: String, minute: Int) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(ctx, millis, alarmIntent(ctx, habitId, minute))
    }

    fun snooze(ctx: Context, habitId: String) {
        setAlarm(ctx, System.currentTimeMillis() + 60 * 60 * 1000L, alarmIntent(ctx, habitId, -1))
    }

    private fun alarmIntent(ctx: Context, habitId: String, minute: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction("tech.kulish.habits.ALARM")
            .putExtra(EXTRA_HABIT, habitId)
            .putExtra(EXTRA_MINUTE, minute)
        return PendingIntent.getBroadcast(ctx, "$habitId:$minute".hashCode(), i, FLAGS)
    }

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelNotification(ctx: Context, habitId: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(habitId.hashCode())
    }

    fun show(ctx: Context, h: Habit, text: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, h.id.hashCode(),
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_HABIT, h.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            FLAGS,
        )
        fun action(a: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, "${h.id}:$a".hashCode(),
            Intent(ctx, ActionReceiver::class.java).setAction(a).putExtra(EXTRA_HABIT, h.id),
            FLAGS,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFC9F45C.toInt())
            .setContentTitle(h.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(!h.sticky)
            .setOngoing(h.sticky)
        // A habit with data fields cannot be completed blind, so its main action opens the form.
        if (h.type == HabitType.DATA) b.addAction(0, "Записати", open)
        else b.addAction(0, "Зроблено", action(ACT_DONE))
        b.addAction(0, "Через годину", action(ACT_SNOOZE))
        b.addAction(0, "Пропустити", action(ACT_SKIP))
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(h.id.hashCode(), b.build())
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val id = intent.getStringExtra(EXTRA_HABIT) ?: return
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        val data = Repo.data.value
        val h = data.habits.find { it.id == id && !it.archived } ?: return
        if (minute >= 0) {
            // The reminder was removed since this alarm was set.
            if (minute !in h.reminders) return
            Reminders.schedule(ctx, id, minute)
        }
        val today = Dates.today()
        val m = entriesOf(data, id)
        val e = m[today]
        if (e != null && e.status != PARTIAL) return
        if (!isDue(h, m, today)) return
        val text = when (h.mode) {
            ScheduleMode.EVERY_N -> {
                val last = lastDone(m, today)
                if (last == null) scheduleLabel(h)
                else {
                    val n = java.time.temporal.ChronoUnit.DAYS.between(last, today).toInt()
                    "Востаннє $n ${plural(n, "день", "дні", "днів")} тому"
                }
            }
            ScheduleMode.PER_WEEK -> "Цього тижня зроблено ${doneInWeek(m, today)} з ${h.perWeek}"
            else -> scheduleLabel(h)
        }
        Reminders.show(ctx, h, text)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        val id = intent.getStringExtra(EXTRA_HABIT) ?: return
        val h = Repo.data.value.habits.find { it.id == id } ?: return
        val today = Dates.today().toString()
        when (intent.action) {
            ACT_DONE -> Repo.setEntry(
                Entry(
                    habitId = id, date = today, status = DONE,
                    values = if (h.type == HabitType.COUNT) mapOf(KEY_COUNT to h.target) else emptyMap(),
                    items = h.items,
                )
            )
            ACT_SKIP -> Repo.setEntry(Entry(habitId = id, date = today, status = SKIPPED))
            ACT_SNOOZE -> Reminders.snooze(ctx, id)
        }
        Reminders.cancelNotification(ctx, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Reminders.rescheduleAll(ctx)
    }
}
