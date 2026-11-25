package com.ascend.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.data.NOT_STARTED
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.RoutineEntity
import com.ascend.fitness.data.RoutineItem
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.GhostButton
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.IconTile
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SectionTitle
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Where a routine came from, used to label and group routines. */
fun routineSource(r: RoutineEntity): String = when {
    r.notes.startsWith("From split:") -> "Famous split"
    r.notes.startsWith("AI plan:") -> "AI plan"
    else -> "My routine"
}

@Composable
fun WorkoutScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val routines by remember { repo.dao.routines() }.collectAsState(emptyList())
    val schedule by remember { repo.dao.schedule() }.collectAsState(emptyList())
    val active by remember { repo.dao.activeWorkout() }.collectAsState(null)
    val today = LocalDate.now().dayOfWeek.value
    val todayRoutine = routines.firstOrNull { it.id == schedule.firstOrNull { s -> s.dayOfWeek == today }?.routineId }
    val todayItems by remember(todayRoutine?.id) {
        todayRoutine?.let { repo.dao.routineItems(it.id) } ?: flowOf(emptyList<RoutineItem>())
    }.collectAsState(emptyList())

    fun open(routineId: Long?) = scope.launch { nav.navigate("logger/${repo.startWorkout(routineId)}") }

    Screen("Workout") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 1. The two ways to get a plan, always first.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction(Icons.Default.Add, "New routine", "Build your own day", primary = true, modifier = Modifier.weight(1f)) {
                    nav.navigate("routine/0")
                }
                BigAction(Icons.Default.Explore, "Explore splits", "20 proven weekly plans", primary = false, modifier = Modifier.weight(1f)) {
                    nav.navigate("splits")
                }
            }

            // 2. Today.
            val a = active
            if (a != null) {
                val ready = a.startedAt == NOT_STARTED
                Card {
                    Text(if (ready) "Set up, not started yet" else "Workout in progress", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text(a.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
                    GradientButton(if (ready) "Finish set-up" else "Continue workout", Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow) {
                        nav.navigate("logger/${a.id}")
                    }
                }
            } else {
                Card {
                    Text("Today · " + DayOfWeek.of(today).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium, color = c.muted)
                    if (todayRoutine != null) {
                        Text(todayRoutine.name, style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Muted(todayItems.joinToString(" · ") { it.exerciseName }.ifEmpty { "No exercises in this routine yet." })
                        GradientButton("Set up and start", Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow) { open(todayRoutine.id) }
                    } else {
                        Text(if (schedule.any { it.routineId != null }) "Rest day" else "No plan for today",
                            style = MaterialTheme.typography.titleLarge, color = c.ink)
                        Muted("Start any routine below, or an empty workout.")
                    }
                    GhostButton("Start an empty workout", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { open(null) }
                }
            }

            // 3. The week.
            SectionTitle("This week", action = "Edit", onAction = { nav.navigate("splits") })
            Card(onClick = { nav.navigate("splits") }, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                (1..7).forEach { d ->
                    val r = routines.firstOrNull { it.id == schedule.firstOrNull { s -> s.dayOfWeek == d }?.routineId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium, color = if (d == today) c.accent else c.muted,
                            modifier = Modifier.width(48.dp).heightIn(min = 22.dp))
                        Text(r?.name ?: "Rest", style = MaterialTheme.typography.bodyMedium, color = if (r == null) c.muted else c.ink)
                    }
                }
                if (schedule.none { it.routineId != null }) Muted("No weekly plan yet. Tap Explore splits above, or set each day here.")
            }
            IconTile(Icons.Default.AutoAwesome, "Make a plan with AI", Modifier.fillMaxWidth()) { nav.navigate("aiplan") }

            // 4. Routines, each listed once.
            SectionTitle("My routines")
            if (routines.isEmpty()) Muted("Routines are reusable day plans, like “Push day”. Tap New routine, or add a famous split.")
            routines.forEach { r ->
                val days = schedule.filter { it.routineId == r.id }.joinToString { DayOfWeek.of(it.dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(c.surface)
                        .clickable(role = Role.Button) { nav.navigate("routine/${r.id}") }.padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                        Text(routineSource(r) + if (days.isNotEmpty()) " · $days" else "", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    IconButton(onClick = { nav.navigate("routine/${r.id}") }) { Icon(Icons.Default.Edit, "Edit ${r.name}", tint = c.muted) }
                    IconButton(onClick = { open(r.id) }, modifier = Modifier.size(44.dp).clip(CircleShape).background(c.soft)) {
                        Icon(Icons.Default.PlayArrow, "Start ${r.name}", tint = c.accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BigAction(icon: ImageVector, title: String, subtitle: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAscend.current
    val ink = if (primary) c.onAccent else c.ink
    Column(
        modifier.heightIn(min = 132.dp).clip(RoundedCornerShape(24.dp))
            .then(if (primary) Modifier.background(c.grad) else Modifier.background(c.surface).border(1.5.dp, c.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp)))
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (primary) c.bg else c.soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f))
    }
}
