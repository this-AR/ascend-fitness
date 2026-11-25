package com.ascend.fitness.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.data.CoachTipEntity
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.RoutineItem
import com.ascend.fitness.domain.Recovery
import com.ascend.fitness.domain.Streaks
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.ui.components.BodyMap
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Ring
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SectionTitle
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    val workouts by remember { repo.dao.finishedWorkouts() }.collectAsState(emptyList())
    val active by remember { repo.dao.activeWorkout() }.collectAsState(null)
    val schedule by remember { repo.dao.schedule() }.collectAsState(emptyList())
    val prs by remember { repo.dao.recentPrs(3) }.collectAsState(emptyList())
    val exercises by remember { repo.dao.exercises() }.collectAsState(emptyList())
    val since = remember { System.currentTimeMillis() - 4L * 86_400_000 }
    val muscleSets by remember { repo.dao.muscleSetsSince(since) }.collectAsState(emptyList())
    var tip by remember { mutableStateOf<CoachTipEntity?>(null) }
    LaunchedEffect(settings.hasGeminiKey) { tip = repo.dailyTip() }

    val today = LocalDate.now().dayOfWeek.value
    val todayRoutineId = schedule.firstOrNull { it.dayOfWeek == today }?.routineId
    val todayItems by remember(todayRoutineId) { todayRoutineId?.let { repo.dao.routineItems(it) } ?: flowOf(emptyList<RoutineItem>()) }.collectAsState(emptyList())
    var todayName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(todayRoutineId) { todayName = todayRoutineId?.let { repo.dao.routine(it)?.name } }
    val streak = Streaks.compute(workouts, settings.weeklyGoal)
    val hour = LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }

    Screen(null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                }
                IconButton(onClick = { nav.navigate("settings") }, modifier = Modifier.size(44.dp).clip(CircleShape).background(c.surface)) {
                    Icon(Icons.Default.Settings, "Settings", tint = c.ink)
                }
            }

            tip?.let { t ->
                Card(onClick = { nav.navigate("coach?q=" + Uri.encode("Today's tip: \"${t.text}\" Expand on this tip and tell me how to apply it in my next session.")) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (t.fromAi) "Coach tip for today" else "Tip of the day", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    }
                    Text(t.text, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                    Muted(if (settings.hasGeminiKey) "Tap to ask the coach about it" else "Add a Gemini key in Settings for tips based on your training")
                }
            }

            active?.let { w ->
                Card(onClick = { nav.navigate("logger/${w.id}") }) {
                    val ready = w.startedAt == com.ascend.fitness.data.NOT_STARTED
                    Text(if (ready) "Workout set up, not started" else "Workout in progress", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text(w.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
                    Muted(if (ready) "Tap to finish setting it up and start" else "Tap to continue")
                }
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Ring(streak.thisWeek.toFloat() / settings.weeklyGoal, 104.dp, 10.dp, "${streak.thisWeek}/${settings.weeklyGoal}", "this week")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Weekly goal", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        val left = settings.weeklyGoal - streak.thisWeek
                        Text(if (left > 0) "$left more to hit your goal" else "Goal hit. Nice work.", style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("${streak.weekStreak} week streak · best ${streak.bestWeekStreak}", style = MaterialTheme.typography.bodyMedium, color = c.accent)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                        val d = i + 1
                        val done = d in streak.doneDays
                        val planned = schedule.firstOrNull { it.dayOfWeek == d }?.routineId != null
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).then(
                                when {
                                    done -> Modifier.background(c.grad)
                                    planned -> Modifier.border(2.dp, c.accent.copy(alpha = 0.5f), CircleShape)
                                    else -> Modifier.background(c.surface2)
                                }))
                            Text(l, style = MaterialTheme.typography.bodySmall, color = if (d == today) c.ink else c.muted)
                        }
                    }
                }
            }

            if (active == null) {
                if (todayRoutineId != null && todayName != null) {
                    Card {
                        Text("Today’s plan", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        Text(todayName!!, style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Muted(todayItems.joinToString(" · ") { it.exerciseName }.ifEmpty { "No exercises in this routine yet." })
                        GradientButton("Start $todayName", Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow) {
                            scope.launch { val id = repo.startWorkout(todayRoutineId); nav.navigate("logger/$id") }
                        }
                    }
                } else {
                    GradientButton(if (schedule.any { it.routineId != null }) "Rest day · start a workout anyway" else "Start a workout",
                        Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow) {
                        scope.launch { val id = repo.startWorkout(null); nav.navigate("logger/$id") }
                    }
                }
            }

            SectionTitle("Recovery", action = "Details", onAction = { nav.navigate("progress") })
            Card(onClick = { nav.navigate("progress") }) {
                BodyMap(Recovery.fatigue(muscleSets), Modifier.padding(horizontal = 40.dp))
                Muted("Green is lightly worked, red needs rest. Grey hasn’t been trained in 4 days.")
            }

            if (prs.isNotEmpty()) {
                SectionTitle("Recent records")
                prs.forEach { p ->
                    val ex = exercises.firstOrNull { it.id == p.exerciseId }
                    Card(onClick = { nav.navigate("exercise/${p.exerciseId}") }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        Text(ex?.name ?: "Exercise", style = MaterialTheme.typography.titleSmall, color = c.ink)
                        Muted(when (p.kind) {
                            "e1rm" -> "New estimated max: ${Strength.format(p.value)} kg"
                            "weight" -> "Heaviest weight: ${Strength.format(p.value)} kg"
                            else -> "Most reps: ${Strength.format(p.value)}"
                        })
                    }
                }
            }
            if (workouts.isEmpty()) Muted("Log your first workout to unlock ranks, charts and the recovery map.")
            Spacer(Modifier.height(8.dp))
        }
    }
}
