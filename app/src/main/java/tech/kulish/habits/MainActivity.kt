package tech.kulish.habits

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Habit to open, set when the app is launched from a reminder. */
    private val openHabit = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        Repo.init(this)
        Reminders.ensureChannel(this)
        Reminders.rescheduleAll(this)
        openHabit.value = intent?.getStringExtra(EXTRA_HABIT)
        setContent { AppTheme { App(openHabit) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openHabit.value = intent.getStringExtra(EXTRA_HABIT)
    }
}

sealed interface Screen {
    data object Today : Screen
    data class Stats(val habitId: String?) : Screen
    data class Edit(val habitId: String?) : Screen
    data class Log(val habitId: String, val date: LocalDate) : Screen
}

@Composable
fun App(openHabit: MutableState<String?>) {
    val data by Repo.data.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Today)) }
    val push: (Screen) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    val ctx = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val pending = openHabit.value
    LaunchedEffect(pending) {
        if (pending != null) {
            val h = data.habits.find { it.id == pending }
            if (h != null && h.type == HabitType.DATA) {
                stack = listOf(Screen.Today, Screen.Log(h.id, Dates.today()))
            }
            Reminders.cancelNotification(ctx, pending)
            openHabit.value = null
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    val top = stack.last()
    Backdrop {
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                is Screen.Today -> TodayScreen(
                    data = data,
                    onLog = { id, date -> push(Screen.Log(id, date)) },
                    onEdit = { push(Screen.Edit(it)) },
                    onStats = { stack = listOf(Screen.Today, Screen.Stats(it)) },
                )
                is Screen.Stats -> StatsScreen(
                    data = data,
                    initialHabit = s.habitId,
                    onLog = { id, date -> push(Screen.Log(id, date)) },
                    onEdit = { push(Screen.Edit(it)) },
                )
                is Screen.Edit -> EditScreen(
                    habit = data.habits.find { it.id == s.habitId },
                    onClose = pop,
                )
                is Screen.Log -> {
                    val h = data.habits.find { it.id == s.habitId }
                    if (h == null) LaunchedEffect(Unit) { pop() } else LogScreen(data, h, s.date, pop)
                }
            }
        }

        if (top is Screen.Today || top is Screen.Stats) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .glass(PillShape),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem("Сьогодні", AppIcons.Today, top is Screen.Today) { stack = listOf(Screen.Today) }
                NavItem("Статистика", AppIcons.Stats, top is Screen.Stats) {
                    stack = listOf(Screen.Today, Screen.Stats(null))
                }
                NavItem("Нова", AppIcons.Plus, false) { push(Screen.Edit(null)) }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Lime else Muted
    Column(
        modifier = Modifier
            .clip(PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(22.dp)) { Icon(icon, contentDescription = null, tint = color) }
        Text(label, color = color, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
    }
}
