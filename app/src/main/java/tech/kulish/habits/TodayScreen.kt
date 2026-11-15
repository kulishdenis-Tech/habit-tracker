@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tech.kulish.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    data: AppData,
    onLog: (String, LocalDate) -> Unit,
    onEdit: (String?) -> Unit,
    onStats: (String) -> Unit,
) {
    val today = Dates.today()
    var date by remember { mutableStateOf(today) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    val maps = remember(data) { data.habits.associate { it.id to entriesOf(data, it.id) } }
    val active = data.habits.filter { !it.archived }
    fun dayMap(h: Habit): DayMap = maps[h.id] ?: emptyMap()
    val visible = active.filter { isDue(it, dayMap(it), date) || dayMap(it)[date] != null }
    val counted = visible.filter { dayMap(it)[date]?.status != SKIPPED }
    val doneCount = counted.count { isDone(dayMap(it)[date]) }
    val progress by animateFloatAsState(
        targetValue = if (counted.isEmpty()) 0f else doneCount.toFloat() / counted.size,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "progress",
    )

    val tap: (Habit) -> Unit = { h ->
        val e = dayMap(h)[date]
        when (h.type) {
            HabitType.DATA -> onLog(h.id, date)
            HabitType.CHECK -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (e != null) Repo.clearEntry(h.id, date)
                else Repo.setEntry(Entry(h.id, date.toString(), DONE, items = h.items))
            }
            HabitType.COUNT -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (e?.status == DONE || e?.status == SKIPPED) {
                    Repo.clearEntry(h.id, date)
                } else {
                    val n = (e?.values?.get(KEY_COUNT) ?: 0.0) + 1.0
                    Repo.setEntry(
                        Entry(h.id, date.toString(), if (n >= h.target) DONE else PARTIAL, mapOf(KEY_COUNT to n))
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Dates.long(date), style = LabelStyle)
                Text(if (date == today) "Сьогодні" else Dates.short(date).substringAfter(", "), style = TitleStyle)
            }
            Box(Modifier.height(44.dp).glass(PillShape).padding(horizontal = 16.dp), Alignment.Center) {
                Text("$doneCount з ${counted.size}", style = NumberStyle, fontSize = 15.sp)
            }
        }

        Box(Modifier.fillMaxWidth().height(6.dp).clip(PillShape).background(Color(0x17FFFFFF))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).clip(PillShape).background(Lime))
        }

        // The current week; tap a day to see and fix it.
        val weekStart = Dates.weekStart(today)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..6) {
                val d = weekStart.plusDays(i.toLong())
                val selected = d == date
                val future = d.isAfter(today)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Lime else CardFill)
                        .border(1.dp, if (selected) Lime else CardLine, RoundedCornerShape(16.dp))
                        .clickable(enabled = !future) { date = d },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val fg = if (selected) Bg else if (future) Color(0xFF6B7383) else Muted
                    Text(Dates.dayShort[i], color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(d.dayOfMonth.toString(), color = fg, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }

        if (active.isEmpty()) {
            Text("Ще немає жодної звички", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Pill("Додати звичку", selected = true, modifier = Modifier.fillMaxWidth()) { onEdit(null) }
        } else if (visible.isEmpty()) {
            Text("На цей день нічого не заплановано", color = Muted, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = TextAlign.Center)
        }

        val titles = mapOf(DayPart.MORNING to "Ранок", DayPart.DAY to "День", DayPart.EVENING to "Вечір")
        for (part in DayPart.values()) {
            val list = visible.filter { it.part == part }
            if (list.isEmpty()) continue
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(titles[part] ?: "", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                for (h in list) {
                    HabitCard(
                        habit = h,
                        entry = dayMap(h)[date],
                        sub = cardSub(h, dayMap(h), date),
                        onClick = { tap(h) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            sheetFor = h.id
                        },
                    )
                }
            }
        }

        if (visible.isNotEmpty()) {
            Text(
                "Утримуй картку: пропуск без втрати серії, нотатка, статистика",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val sheetHabit = active.find { it.id == sheetFor }
    if (sheetHabit != null) {
        DaySheet(
            habit = sheetHabit,
            date = date,
            entry = dayMap(sheetHabit)[date],
            onDismiss = { sheetFor = null },
            onLog = { sheetFor = null; onLog(sheetHabit.id, date) },
            onStats = { sheetFor = null; onStats(sheetHabit.id) },
            onEdit = { sheetFor = null; onEdit(sheetHabit.id) },
        )
    }
}

/** Second line of a habit card: schedule plus what matters right now. */
fun cardSub(h: Habit, m: DayMap, date: LocalDate): String {
    val e = m[date]
    if (e != null && (e.status == SKIPPED || e.status == DONE && h.type != HabitType.CHECK)) return entrySummary(h, e)
    val base = scheduleLabel(h)
    val extra = when {
        h.type == HabitType.COUNT ->
            "${fmt(e?.values?.get(KEY_COUNT) ?: 0.0)} з ${fmt(h.target)} ${h.unit}".trim()
        h.mode == ScheduleMode.PER_WEEK -> "зроблено ${doneInWeek(m, date)}"
        h.mode == ScheduleMode.EVERY_N -> {
            val last = lastDone(m, date.minusDays(1))
            if (last == null) null
            else {
                val n = ChronoUnit.DAYS.between(last, date).toInt()
                if (n == 1) "востаннє вчора" else "востаннє $n ${plural(n, "день", "дні", "днів")} тому"
            }
        }
        e != null && e.items.isNotEmpty() -> e.items.joinToString(", ")
        else -> null
    }
    return if (extra == null) base else "$base · $extra"
}

@Composable
fun HabitCard(habit: Habit, entry: Entry?, sub: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val done = isDone(entry)
    val skipped = entry?.status == SKIPPED
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pressing tips the card back slightly, like a physical tile.
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press",
    )
    val bg by animateColorAsState(if (done) Lime.copy(alpha = 0.13f) else CardFill, label = "cardBg")
    val line by animateColorAsState(if (done) Lime.copy(alpha = 0.45f) else CardLine, label = "cardLine")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = 1f - 0.03f * press
                scaleX = s
                scaleY = s
                rotationX = 7f * press
                cameraDistance = 14f * density
                alpha = if (skipped) 0.55f else 1f
            }
            .clip(CardShape)
            .background(bg)
            .border(1.dp, line, CardShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x12FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.of(habit.icon), contentDescription = null, tint = Violet, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(habit.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (habit.type == HabitType.DATA && !done) {
            Icon(AppIcons.Forward, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
        } else {
            CheckFlip(done)
        }
    }
}

/** The check mark: a rounded square that flips over in 3D and lands as a filled circle. */
@Composable
fun CheckFlip(done: Boolean) {
    val t by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 260f),
        label = "check",
    )
    val angle = t * 180f
    val corner = (11f + 6f * t).coerceIn(6f, 17f).dp
    Box(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 10f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        if (angle <= 90f) {
            Box(Modifier.fillMaxSize().border(2.dp, Color(0xFF6B7383), RoundedCornerShape(corner)))
        } else {
            Box(
                // Counter-rotate so the tick is not mirrored on the back face.
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(corner)).background(Lime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = Bg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SheetAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(PillShape)
            .background(if (primary) Lime else ChipFill)
            .border(1.dp, if (primary) Lime else CardLine, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (primary) Bg else Ink
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
    }
}

/** Everything that can be done with one habit on one day. Opened by a long press or from the heatmap. */
@Composable
fun DaySheet(
    habit: Habit,
    date: LocalDate,
    entry: Entry?,
    onDismiss: () -> Unit,
    onLog: () -> Unit,
    onStats: (() -> Unit)?,
    onEdit: () -> Unit,
) {
    val key = date.toString()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF161920)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(habit.name, style = TitleStyle, fontSize = 22.sp)
                Text(
                    Dates.long(date) + (if (entry != null) " · " + entrySummary(habit, entry) else ""),
                    style = LabelStyle,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (habit.type == HabitType.DATA) {
                    SheetAction(if (isDone(entry)) "Змінити" else "Записати", AppIcons.Plus, Modifier.weight(1f), primary = true, onClick = onLog)
                } else if (!isDone(entry)) {
                    SheetAction("Зроблено", AppIcons.Check, Modifier.weight(1f), primary = true) {
                        Repo.setEntry(
                            Entry(
                                habit.id, key, DONE,
                                values = if (habit.type == HabitType.COUNT) mapOf(KEY_COUNT to habit.target) else emptyMap(),
                                note = entry?.note ?: "",
                                items = habit.items,
                            )
                        )
                    }
                }
                if (entry?.status != SKIPPED) {
                    SheetAction("Пропустити", AppIcons.Skip, Modifier.weight(1f)) {
                        Repo.setEntry(Entry(habit.id, key, SKIPPED, note = entry?.note ?: ""))
                    }
                }
                if (entry != null) {
                    SheetAction("Очистити", AppIcons.Close, Modifier.weight(1f)) { Repo.clearEntry(habit.id, date) }
                }
            }

            if (habit.items.isNotEmpty() && habit.type == HabitType.CHECK) {
                Text("Що використав", style = LabelStyle)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (item in habit.items) {
                        val on = entry != null && entry.status == DONE && item in entry.items
                        Pill(item, on) {
                            val current = if (entry != null && entry.status == DONE) entry.items else emptyList()
                            val next = if (on) current - item else current + item
                            Repo.setEntry(Entry(habit.id, key, DONE, note = entry?.note ?: "", items = next))
                        }
                    }
                }
            }

            if (entry != null) {
                var note by remember(habit.id, key) { mutableStateOf(entry.note) }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        Repo.setEntry(entry.copy(note = it))
                    },
                    label = { Text("Нотатка") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onStats != null) SheetAction("Статистика", AppIcons.Stats, Modifier.weight(1f), onClick = onStats)
                SheetAction("Редагувати", AppIcons.Today, Modifier.weight(1f), onClick = onEdit)
            }
        }
    }
}
