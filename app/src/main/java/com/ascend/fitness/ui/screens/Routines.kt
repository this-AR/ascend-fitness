package com.ascend.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.RoutineEntity
import com.ascend.fitness.data.RoutineExerciseEntity
import com.ascend.fitness.data.ScheduleEntity
import com.ascend.fitness.domain.SPLITS
import com.ascend.fitness.domain.SplitTemplate
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.ExercisePicker
import com.ascend.fitness.ui.components.Field
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Pill
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SectionTitle
import com.ascend.fitness.ui.components.SoftButton
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private data class EditItem(val exerciseId: Long, val name: String, val sets: Int, val repMin: Int, val repMax: Int)

@Composable
fun RoutineEditScreen(repo: Repository, nav: NavHostController, routineId: Long) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val exercises by remember { repo.dao.exercises() }.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    var routine by remember { mutableStateOf<RoutineEntity?>(null) }
    val items = remember { mutableStateListOf<EditItem>() }
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(routineId) {
        if (routineId != 0L) {
            routine = repo.dao.routine(routineId)
            name = routine?.name.orEmpty()
            items.clear()
            items.addAll(repo.dao.routineItemsOnce(routineId).map { EditItem(it.exerciseId, it.exerciseName, it.targetSets, it.repMin, it.repMax) })
        }
    }

    Screen(if (routineId == 0L) "New routine" else "Edit routine", onBack = { nav.popBackStack() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(name, { name = it.take(60) }, "Routine name, e.g. Push day")
            items.forEachIndexed { i, it ->
                Card(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.name, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (i > 0) items.add(i - 1, items.removeAt(i)) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = c.muted) }
                        IconButton(onClick = { if (i < items.lastIndex) items.add(i + 1, items.removeAt(i)) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down", tint = c.muted) }
                        IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Close, "Remove", tint = c.muted) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper("Sets", it.sets, 1..10, Modifier.weight(1f)) { v -> items[i] = items[i].copy(sets = v) }
                        Stepper("Min reps", it.repMin, 1..50, Modifier.weight(1f)) { v -> items[i] = items[i].copy(repMin = v, repMax = maxOf(v, items[i].repMax)) }
                        Stepper("Max reps", it.repMax, 1..60, Modifier.weight(1f)) { v -> items[i] = items[i].copy(repMax = v, repMin = minOf(v, items[i].repMin)) }
                    }
                }
            }
            SoftButton("Add exercise", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { picker = true }
            Muted("The app suggests weights from your last session: when you hit max reps on every set, it adds weight and drops you back to min reps.")
            if (routine != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete routine", color = c.bad) }
            Spacer(Modifier.height(8.dp))
        }
        GradientButton("Save routine", Modifier.fillMaxWidth().padding(vertical = 12.dp), enabled = name.isNotBlank() && items.isNotEmpty()) {
            scope.launch {
                val list = items.map { RoutineExerciseEntity(routineId = 0, exerciseId = it.exerciseId, position = 0, targetSets = it.sets, repMin = it.repMin, repMax = it.repMax) }
                val r = routine
                if (r == null) repo.createRoutine(name, "", list)
                else { repo.dao.updateRoutine(r.copy(name = name.trim())); repo.dao.replaceRoutineExercises(r.id, list) }
                repo.evaluateAchievements()
                nav.popBackStack()
            }
        }
    }

    if (picker) ExercisePicker(exercises, onDismiss = { picker = false },
        onCreate = { n, m, e, k ->
            scope.launch {
                runCatching { repo.dao.insertExercise(ExerciseEntity(name = n, primaryMuscle = m, equipment = e, metric = k, isCustom = true)) }
                    .onSuccess { id -> items.add(EditItem(id, n, 3, 8, 12)) }
                picker = false
            }
        }) { ex -> items.add(EditItem(ex.id, ex.name, 3, 8, 12)); picker = false }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete routine?") },
        text = { Text("Past workouts stay in your history. Days using this routine become rest days.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    routine?.let { r ->
                        repo.dao.allSchedule().filter { it.routineId == r.id }.let { s -> repo.dao.upsertSchedule(s.map { it.copy(routineId = null) }) }
                        repo.dao.deleteRoutine(r)
                    }
                    nav.popBackStack()
                }
            }) { Text("Delete", color = c.bad) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.ink) } },
        containerColor = c.surface,
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, modifier: Modifier, onChange: (Int) -> Unit) {
    val c = LocalAscend.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, "Less $label", tint = c.ink) }
            Text("$value", style = MaterialTheme.typography.titleMedium, color = c.ink)
            IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "More $label", tint = c.ink) }
        }
    }
}

/** A boxed option; the selected one is outlined in the theme colour. */
@Composable
private fun ChoiceBox(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAscend.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) c.soft else c.surface2)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.hairline, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) c.accent else c.ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
    }
}

@Composable
fun SplitsScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val routines by remember { repo.dao.routines() }.collectAsState(emptyList())
    val schedule by remember { repo.dao.schedule() }.collectAsState(emptyList())
    var pickDay by remember { mutableStateOf<Int?>(null) }
    var applying by remember { mutableStateOf<SplitTemplate?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf<String?>(null) }

    Screen("Weekly split", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Pick which routine you do on each day. Mon/Wed/Fri can be one plan and Tue/Thu another: any mix works.")
            Card(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                (1..7).forEach { d ->
                    val r = routines.firstOrNull { it.id == schedule.firstOrNull { s -> s.dayOfWeek == d }?.routineId }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { pickDay = d }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.titleSmall,
                            color = c.ink, modifier = Modifier.width(110.dp))
                        Text(r?.name ?: "Rest", style = MaterialTheme.typography.bodyMedium, color = if (r == null) c.muted else c.accent, modifier = Modifier.weight(1f))
                        Text("Change", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                }
            }
            SectionTitle("Famous splits")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, "Beginner", "Intermediate", "Advanced").forEach { l -> Pill(l ?: "All", level == l) { level = l } }
            }
            SPLITS.filter { level == null || it.level == level }.forEach { s ->
                Card(onClick = { expanded = if (expanded == s.name) null else s.name }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                            Text("${s.level} · ${s.week.count { it != null }} days a week", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        IconButton(onClick = { applying = s }, modifier = Modifier.size(44.dp).clip(CircleShape).background(c.grad)) {
                            Icon(Icons.Default.Add, "Add ${s.name} to my week", tint = c.onAccent)
                        }
                    }
                    Muted(s.summary)
                    if (expanded == s.name) {
                        s.week.forEachIndexed { i, dn ->
                            Text("${DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())}: ${dn ?: "Rest"}",
                                style = MaterialTheme.typography.bodyMedium, color = if (dn == null) c.muted else c.ink)
                        }
                        s.days.forEach { d ->
                            Text(d.name, style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.padding(top = 6.dp))
                            d.exercises.forEach { e -> Muted("${e.name} · ${e.sets} × ${e.repMin}${if (e.repMax != e.repMin) "–${e.repMax}" else ""}") }
                        }
                        GradientButton("Use this split", Modifier.fillMaxWidth()) { applying = s }
                    }
                }
            }
        }
    }

    pickDay?.let { d ->
        AlertDialog(
            onDismissRequest = { pickDay = null },
            title = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.getDefault())) },
            text = {
                val current = schedule.firstOrNull { it.dayOfWeek == d }?.routineId
                fun choose(r: RoutineEntity?) {
                    scope.launch { repo.dao.upsertSchedule(listOf(ScheduleEntity(d, r?.id))) }
                    pickDay = null
                }
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceBox("Rest day", null, current == null) { choose(null) }
                    listOf("My routine" to "My routines", "Famous split" to "From famous splits", "AI plan" to "From AI plans").forEach { (src, title) ->
                        val group = routines.filter { routineSource(it) == src }
                        if (group.isNotEmpty()) {
                            Text(title, style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.padding(top = 8.dp))
                            group.forEach { r -> ChoiceBox(r.name, null, current == r.id) { choose(r) } }
                        }
                    }
                    if (routines.isEmpty()) Muted("No routines yet. Add one of the famous splits below, or create your own.")
                    SoftButton("Create a new routine", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { pickDay = null; nav.navigate("routine/0") }
                }
            },
            confirmButton = { TextButton(onClick = { pickDay = null }) { Text("Close", color = c.ink) } },
            containerColor = c.surface,
        )
    }
    applying?.let { s ->
        AlertDialog(
            onDismissRequest = { applying = null },
            title = { Text("Use ${s.name}?") },
            text = { Text("This sets up ${s.days.size} routines and fills your week with them. Your own routines and history are kept; adding the same split again updates it instead of making copies.") },
            confirmButton = { TextButton(onClick = { applying = null; scope.launch { repo.applySplit(s) } }) { Text("Add to my week", color = c.accent) } },
            dismissButton = { TextButton(onClick = { applying = null }) { Text("Cancel", color = c.ink) } },
            containerColor = c.surface,
        )
    }
}
