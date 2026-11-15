package tech.kulish.habits

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt

private const val WEEKS = 16
private val HeatShades = listOf(Color(0x14FFFFFF), Color(0xFF4E6220), Color(0xFF8FB03A), Lime)

@Composable
fun StatsScreen(
    data: AppData,
    initialHabit: String?,
    onLog: (String, LocalDate) -> Unit,
    onEdit: (String) -> Unit,
) {
    val habits = data.habits.filter { !it.archived }
    val archived = data.habits.filter { it.archived }
    val today = Dates.today()
    var selected by remember { mutableStateOf(initialHabit) }
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Статистика", style = TitleStyle)

        val h = habits.find { it.id == selected } ?: habits.firstOrNull()
        if (h == null) {
            Text("Додай звичку, і тут зʼявиться її історія", color = Muted)
        } else {
            val m = remember(data, h.id) { entriesOf(data, h.id) }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (x in habits) Pill(x.name, x.id == h.id) { selected = x.id }
            }

            Row(
                modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val s = remember(m, h) { strength(h, m, today) }
                Text("${(s * 100).roundToInt()}%", style = TitleStyle, fontSize = 40.sp, color = Lime)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Сила звички", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Пропуск трохи знижує її, а не обнуляє", style = LabelStyle)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, value) in tiles(h, m, today)) {
                    Column(
                        Modifier.weight(1f).card(RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
                        Text(value, style = NumberStyle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().card(RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Останні $WEEKS тижнів · торкнись дня, щоб виправити", style = LabelStyle)
                Heatmap(h, m, today) { sheetDate = it }
            }

            val recent = m.entries.sortedByDescending { it.key.toEpochDay() }.take(8)
            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((d, e) in recent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .card(RoundedCornerShape(18.dp))
                                .clickable { if (h.type == HabitType.DATA && e.status == DONE) onLog(h.id, d) else sheetDate = d }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(Dates.short(d), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                entrySummary(h, e) + (if (e.note.isNotBlank()) " · " + e.note else ""),
                                color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Pill("Редагувати «${h.name}»", selected = false, modifier = Modifier.fillMaxWidth()) { onEdit(h.id) }

            val date = sheetDate
            if (date != null) {
                DaySheet(
                    habit = h,
                    date = date,
                    entry = m[date],
                    onDismiss = { sheetDate = null },
                    onLog = { sheetDate = null; onLog(h.id, date) },
                    onStats = null,
                    onEdit = { sheetDate = null; onEdit(h.id) },
                )
            }
        }

        if (archived.isNotEmpty()) {
            Text("В архіві", style = LabelStyle, modifier = Modifier.padding(top = 8.dp))
            for (a in archived) Pill(a.name, selected = false, modifier = Modifier.fillMaxWidth()) { onEdit(a.id) }
        }

        Backup()
    }
}

private fun tiles(h: Habit, m: DayMap, today: LocalDate): List<Pair<String, String>> {
    val streak = "Серія" to streak(h, m, today)
    val dist = h.distField()
    val time = h.timeField()
    val weekStart = Dates.weekStart(today)
    val week = (0..6).mapNotNull { m[weekStart.plusDays(it.toLong())] }.filter { it.status == DONE }
    if (h.type == HabitType.DATA && dist != null) {
        val km = week.sumOf { it.values[dist.id] ?: 0.0 }
        val month = m.entries.filter { it.value.status == DONE && !it.key.isBefore(today.minusDays(29)) }.map { it.value }
        val avg = if (time == null) null else pace(month.sumOf { it.values[dist.id] ?: 0.0 }, month.sumOf { it.values[time.id] ?: 0.0 })
        return listOf("За тиждень" to "${fmt(km)} км", "Темп, 30 днів" to (avg ?: "–"), streak)
    }
    val monthDone = m.entries.count { it.value.status == DONE && it.key.year == today.year && it.key.month == today.month }
    val total = m.values.count { it.status == DONE }
    return listOf("Цього місяця" to monthDone.toString(), "Усього" to total.toString(), streak)
}

/** GitHub-style grid: one column per week, Monday on top. Brighter means more done that day. */
@Composable
private fun Heatmap(h: Habit, m: DayMap, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val start = Dates.weekStart(today).minusWeeks((WEEKS - 1).toLong())
    val dist = h.distField()
    val maxDist = if (dist == null) 0.0 else m.values.maxOfOrNull { it.values[dist.id] ?: 0.0 } ?: 0.0

    fun level(e: Entry?): Int {
        if (e == null || e.status == SKIPPED) return 0
        return when (h.type) {
            HabitType.CHECK -> 3
            HabitType.COUNT -> {
                val r = (e.values[KEY_COUNT] ?: 0.0) / h.target.coerceAtLeast(0.0001)
                if (r >= 1.0) 3 else if (r >= 0.5) 2 else 1
            }
            HabitType.DATA -> {
                if (dist == null || maxDist <= 0.0) 3
                else {
                    val r = (e.values[dist.id] ?: 0.0) / maxDist
                    if (r >= 0.75) 3 else if (r >= 0.4) 2 else 1
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val cell = (maxWidth - gap * (WEEKS - 1)) / WEEKS
        val density = LocalDensity.current
        val cellPx = with(density) { cell.toPx() }
        val gapPx = with(density) { gap.toPx() }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(cell * 7 + gap * 6)
                .pointerInput(h.id, cellPx) {
                    detectTapGestures { p ->
                        val col = (p.x / (cellPx + gapPx)).toInt().coerceIn(0, WEEKS - 1)
                        val row = (p.y / (cellPx + gapPx)).toInt().coerceIn(0, 6)
                        val d = start.plusDays((col * 7 + row).toLong())
                        if (!d.isAfter(today)) onPick(d)
                    }
                },
        ) {
            val radius = CornerRadius(cellPx * 0.28f)
            for (col in 0 until WEEKS) {
                for (row in 0..6) {
                    val d = start.plusDays((col * 7 + row).toLong())
                    if (d.isAfter(today)) continue
                    val e = m[d]
                    val topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx))
                    drawRoundRect(HeatShades[level(e)], topLeft, Size(cellPx, cellPx), radius)
                    if (e?.status == SKIPPED) {
                        // A skipped day is outlined so it reads as deliberate, not as a miss.
                        drawRoundRect(Muted, topLeft, Size(cellPx, cellPx), radius, style = Stroke(width = gapPx * 0.4f))
                    }
                    if (d == today) {
                        drawRoundRect(Violet, topLeft, Size(cellPx, cellPx), radius, style = Stroke(width = gapPx * 0.6f))
                    }
                }
            }
        }
    }
}

private fun writeText(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/** Manual backup through the system file picker; the app itself has no network access. */
@Composable
private fun Backup() {
    val ctx = LocalContext.current
    var confirmImport by remember { mutableStateOf(false) }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            writeText(ctx, uri, Repo.exportJson())
            Toast.makeText(ctx, "Копію збережено", Toast.LENGTH_SHORT).show()
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            writeText(ctx, uri, Repo.exportCsv())
            Toast.makeText(ctx, "CSV збережено", Toast.LENGTH_SHORT).show()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                Repo.importJson(text ?: error("empty"))
                Reminders.rescheduleAll(ctx)
            }.isSuccess
            Toast.makeText(ctx, if (ok) "Дані відновлено" else "Це не файл копії", Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Резервна копія", style = LabelStyle)
        val stamp = Dates.today().toString()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Зберегти копію", selected = false, modifier = Modifier.weight(1f)) { saveJson.launch("habits-$stamp.json") }
            Pill("Експорт CSV", selected = false, modifier = Modifier.weight(1f)) { saveCsv.launch("habits-$stamp.csv") }
        }
        Pill("Відновити з копії", selected = false, modifier = Modifier.fillMaxWidth()) { confirmImport = true }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Відновити з копії?") },
            text = { Text("Усі поточні звички й записи буде замінено вмістом файлу.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    open.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }) { Text("Вибрати файл") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Скасувати") } },
        )
    }
}
