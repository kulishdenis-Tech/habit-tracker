@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package tech.kulish.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = LabelStyle)
        content()
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().card(PillShape).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        CircleButton(AppIcons.Minus, "Менше", Modifier.size(44.dp), onMinus)
        CircleButton(AppIcons.Plus, "Більше", Modifier.size(44.dp), onPlus)
    }
}

/** Create or edit a habit. [habit] is null for a new one. */
@Composable
fun EditScreen(habit: Habit?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val id = remember { habit?.id ?: newId() }
    val isNew = remember { habit == null }

    var name by remember { mutableStateOf(habit?.name ?: "") }
    var type by remember { mutableStateOf(habit?.type ?: HabitType.CHECK) }
    var icon by remember { mutableStateOf(habit?.icon ?: 0) }
    var mode by remember { mutableStateOf(habit?.mode ?: ScheduleMode.DAILY) }
    var days by remember { mutableStateOf(habit?.days ?: setOf(1, 2, 3, 4, 5, 6, 7)) }
    var perWeek by remember { mutableStateOf(habit?.perWeek ?: 3) }
    var everyN by remember { mutableStateOf(habit?.everyN ?: 2) }
    var part by remember { mutableStateOf(habit?.part ?: DayPart.MORNING) }
    var unit by remember { mutableStateOf(habit?.unit ?: "") }
    var target by remember { mutableStateOf(fmt(habit?.target ?: 1.0)) }
    var sticky by remember { mutableStateOf(habit?.sticky ?: false) }
    var items by remember { mutableStateOf(habit?.items?.joinToString(", ") ?: "") }
    val fields = remember { mutableStateListOf<Field>().apply { addAll(habit?.fields ?: emptyList()) } }
    val reminders = remember { mutableStateListOf<Int>().apply { addAll(habit?.reminders ?: emptyList()) } }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val save: (Boolean) -> Unit = { archived ->
        val h = Habit(
            id = id,
            name = name.trim().ifBlank { "Без назви" },
            type = type,
            icon = icon,
            mode = mode,
            days = if (days.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else days,
            perWeek = perWeek,
            everyN = everyN,
            part = part,
            fields = if (type == HabitType.DATA) fields.filter { it.name.isNotBlank() } else emptyList(),
            unit = unit.trim(),
            target = (target.replace(',', '.').toDoubleOrNull() ?: 1.0).coerceAtLeast(1.0),
            reminders = reminders.sorted(),
            sticky = sticky,
            items = items.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            archived = archived,
            created = habit?.created ?: Dates.today().toString(),
        )
        Repo.upsertHabit(h)
        Reminders.rescheduleAll(ctx)
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(if (isNew) "Нова звичка" else "Звичка", null, AppIcons.Close, onClose)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Назва") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )

        Section("Значок") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcons.habit.forEachIndexed { i, vector ->
                    val on = icon == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Lime else ChipFill)
                            .border(1.dp, if (on) Lime else CardLine, RoundedCornerShape(14.dp))
                            .clickable { icon = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(vector, contentDescription = "Значок ${i + 1}", tint = if (on) Bg else Violet, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Section("Тип") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Відмітка", type == HabitType.CHECK, Modifier.weight(1f)) { type = HabitType.CHECK }
                Pill("Число", type == HabitType.COUNT, Modifier.weight(1f)) { type = HabitType.COUNT }
                Pill("З даними", type == HabitType.DATA, Modifier.weight(1f)) { type = HabitType.DATA }
            }
            Text(
                when (type) {
                    HabitType.CHECK -> "Один дотик: зробив чи ні."
                    HabitType.COUNT -> "Кількість за день; кожен дотик додає одиницю."
                    HabitType.DATA -> "Свої поля з одиницями. Якщо є «км» і «хв», темп рахується сам."
                },
                style = LabelStyle,
            )
        }

        if (type == HabitType.COUNT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Ціль на день") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Одиниця") },
                    placeholder = { Text("склянок") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }

        if (type == HabitType.DATA) {
            Section("Поля даних") {
                fields.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = f.name,
                            onValueChange = { fields[i] = f.copy(name = it) },
                            label = { Text("Назва") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        OutlinedTextField(
                            value = f.unit,
                            onValueChange = { fields[i] = f.copy(unit = it) },
                            label = { Text("Од.") },
                            singleLine = true,
                            modifier = Modifier.width(92.dp),
                            shape = RoundedCornerShape(18.dp),
                        )
                        CircleButton(AppIcons.Close, "Прибрати поле", Modifier.size(44.dp)) { fields.removeAt(i) }
                    }
                }
                Pill("Додати поле", selected = false, modifier = Modifier.fillMaxWidth()) { fields.add(Field(newId(), "", "")) }
            }
        }

        if (type == HabitType.CHECK) {
            OutlinedTextField(
                value = items,
                onValueChange = { items = it },
                label = { Text("Засоби, через кому (необовʼязково)") },
                placeholder = { Text("сироватка, крем, маска") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            )
        }

        Section("Розклад") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Щодня", mode == ScheduleMode.DAILY, Modifier.weight(1f)) { mode = ScheduleMode.DAILY }
                Pill("Дні тижня", mode == ScheduleMode.DAYS, Modifier.weight(1f)) { mode = ScheduleMode.DAYS }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("N разів на тиждень", mode == ScheduleMode.PER_WEEK, Modifier.weight(1f)) { mode = ScheduleMode.PER_WEEK }
                Pill("Кожні N днів", mode == ScheduleMode.EVERY_N, Modifier.weight(1f)) { mode = ScheduleMode.EVERY_N }
            }
            when (mode) {
                ScheduleMode.DAILY -> {}
                ScheduleMode.DAYS -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..7) {
                        val on = d in days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(PillShape)
                                .background(if (on) Lime else ChipFill)
                                .border(1.dp, if (on) Lime else CardLine, PillShape)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(Dates.dayShort[d - 1], color = if (on) Bg else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                ScheduleMode.PER_WEEK -> Stepper(
                    "$perWeek ${plural(perWeek, "раз", "рази", "разів")} на тиждень",
                    onMinus = { perWeek = (perWeek - 1).coerceAtLeast(1) },
                    onPlus = { perWeek = (perWeek + 1).coerceAtMost(7) },
                )
                ScheduleMode.EVERY_N -> Stepper(
                    "Кожні $everyN ${plural(everyN, "день", "дні", "днів")}",
                    onMinus = { everyN = (everyN - 1).coerceAtLeast(2) },
                    onPlus = { everyN = (everyN + 1).coerceAtMost(60) },
                )
            }
        }

        Section("Час доби") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Ранок", part == DayPart.MORNING, Modifier.weight(1f)) { part = DayPart.MORNING }
                Pill("День", part == DayPart.DAY, Modifier.weight(1f)) { part = DayPart.DAY }
                Pill("Вечір", part == DayPart.EVENING, Modifier.weight(1f)) { part = DayPart.EVENING }
            }
        }

        Section("Нагадування") {
            Column(
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (minute in reminders.sorted()) {
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(PillShape)
                                .background(Color(0x17FFFFFF))
                                .clickable { reminders.remove(minute) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(fmtTime(minute), color = Ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Icon(AppIcons.Close, contentDescription = "Прибрати", tint = Muted, modifier = Modifier.size(14.dp))
                        }
                    }
                    Pill("Додати час", selected = false) { pickTime = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Не змахувати, доки не відмічу", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sticky, onCheckedChange = { sticky = it })
                }
            }
        }

        Pill(if (isNew) "Створити" else "Зберегти", selected = true, modifier = Modifier.fillMaxWidth().height(60.dp)) { save(false) }

        if (!isNew) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (habit?.archived == true) "Повернути з архіву" else "В архів", selected = false, modifier = Modifier.weight(1f)) {
                    save(habit?.archived != true)
                }
                Pill("Видалити", selected = false, modifier = Modifier.weight(1f)) { confirmDelete = true }
            }
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Час нагадування") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val minute = state.hour * 60 + state.minute
                    if (minute !in reminders) reminders.add(minute)
                    pickTime = false
                }) { Text("Додати") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Скасувати") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Видалити звичку?") },
            text = { Text("Разом із нею зникне вся її історія. Якщо історія потрібна, краще відправити звичку в архів.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteHabit(id)
                    onClose()
                }) { Text("Видалити") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
        )
    }
}
