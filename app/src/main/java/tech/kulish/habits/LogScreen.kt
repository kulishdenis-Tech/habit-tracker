@file:OptIn(ExperimentalMaterial3Api::class)

package tech.kulish.habits

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun CircleButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.size(48.dp).clip(PillShape).background(Color(0x17FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, closeIcon: ImageVector = AppIcons.Back, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(48.dp).glass(PillShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(closeIcon, contentDescription = "Назад", tint = Ink, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = TitleStyle, fontSize = 24.sp, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = LabelStyle)
        }
    }
}

/** Entry form for a habit with data fields, such as a run. */
@Composable
fun LogScreen(data: AppData, habit: Habit, date: LocalDate, onClose: () -> Unit) {
    val m = remember(data, habit.id) { entriesOf(data, habit.id) }
    val existing = m[date]
    // A new entry starts from the previous one: most runs repeat the last distance.
    val template = existing ?: lastDone(m, date)?.let { m[it] }

    val values = remember(habit.id, date) {
        mutableStateMapOf<String, Double>().apply {
            for (f in habit.fields) put(f.id, template?.values?.get(f.id) ?: 0.0)
        }
    }
    var feel by remember(habit.id, date) { mutableStateOf((existing?.values?.get(KEY_FEEL) ?: 0.0).toInt()) }
    var note by remember(habit.id, date) { mutableStateOf(existing?.note ?: "") }
    var typing by remember { mutableStateOf<Field?>(null) }

    val dist = habit.distField()
    val time = habit.timeField()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(habit.name, Dates.long(date), onClose = onClose)

        if (dist != null && time != null) {
            val p = pace(values[dist.id] ?: 0.0, values[time.id] ?: 0.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(GlassFill)
                    .border(1.dp, Violet.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Темп, рахується сам", color = Color(0xFFC9C1FF), fontSize = 13.sp)
                    Text(p ?: "–:––", style = TitleStyle, fontSize = 44.sp)
                }
                Text("хв/км", color = Color(0xFFC9C1FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        for (f in habit.fields) {
            val v = values[f.id] ?: 0.0
            val isKm = f.unit.trim().lowercase() == "км"
            val step = if (isKm) 0.5 else 1.0
            Column(
                modifier = Modifier.fillMaxWidth().card(RoundedCornerShape(24.dp)).padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { typing = f },
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(if (f.unit.isBlank()) f.name else "${f.name}, ${f.unit}", style = LabelStyle)
                        Text(fmt(v), style = NumberStyle)
                    }
                    CircleButton(AppIcons.Minus, "Менше") { values[f.id] = (v - step).coerceAtLeast(0.0) }
                    CircleButton(AppIcons.Plus, "Більше") { values[f.id] = v + step }
                }
                if (isKm) {
                    Row(Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (km in listOf(3.0, 5.0, 8.0, 10.0)) {
                            Pill("${fmt(km)} км", v == km, Modifier.weight(1f)) { values[f.id] = km }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Самопочуття", style = LabelStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 1..5) {
                    val on = feel == n
                    // The chosen mark rounds off into a circle; the rest stay squarish.
                    val corner by animateDpAsState(if (on) 24.dp else 14.dp, spring(dampingRatio = 0.45f, stiffness = 300f), label = "feel")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(if (on) Lime else ChipFill)
                            .border(1.dp, if (on) Lime else CardLine, RoundedCornerShape(corner))
                            .clickable { feel = if (on) 0 else n },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(n.toString(), color = if (on) Bg else Ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Нотатка") },
            placeholder = { Text("Маршрут, погода, взуття") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        )

        Pill("Зберегти", selected = true, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            val all = values.toMap().toMutableMap()
            if (feel > 0) all[KEY_FEEL] = feel.toDouble()
            Repo.setEntry(Entry(habit.id, date.toString(), DONE, all, note.trim()))
            onClose()
        }
        if (existing != null) {
            Pill("Видалити запис", selected = false, modifier = Modifier.fillMaxWidth()) {
                Repo.clearEntry(habit.id, date)
                onClose()
            }
        }
    }

    val field = typing
    if (field != null) {
        var text by remember(field.id) { mutableStateOf(fmt(values[field.id] ?: 0.0)) }
        AlertDialog(
            onDismissRequest = { typing = null },
            title = { Text(if (field.unit.isBlank()) field.name else "${field.name}, ${field.unit}") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = text.replace(',', '.').trim().toDoubleOrNull()
                    if (parsed != null && parsed >= 0.0) values[field.id] = parsed
                    typing = null
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { typing = null }) { Text("Скасувати") } },
        )
    }
}
