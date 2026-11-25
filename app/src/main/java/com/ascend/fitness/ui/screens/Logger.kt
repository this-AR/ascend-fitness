package com.ascend.fitness.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.AscendApp
import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.NOT_STARTED
import com.ascend.fitness.domain.Progression
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.SetEntity
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.domain.Suggestion
import com.ascend.fitness.ui.components.Banner
import com.ascend.fitness.ui.components.ExercisePicker
import com.ascend.fitness.ui.components.GhostButton
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.ProgressBar
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SoftButton
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Prior(val bestValue: Double, val lastSession: List<SetEntityLite>)
private data class SetEntityLite(val weightKg: Double, val reps: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoggerScreen(repo: Repository, nav: NavHostController, workoutId: Long, app: AscendApp) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    val workout by remember { repo.dao.workoutFlow(workoutId) }.collectAsState(null)
    val sets by remember { repo.dao.setsFor(workoutId) }.collectAsState(emptyList())
    val exercises by remember { repo.dao.exercises() }.collectAsState(emptyList())
    val exById = exercises.associateBy { it.id }
    val order = sets.sortedBy { it.position }.map { it.exerciseId }.distinct()

    val priors = remember { mutableStateMapOf<Long, Prior>() }
    val suggestions = remember { mutableStateMapOf<Long, Suggestion?>() }
    LaunchedEffect(order, exercises.size, workout?.routineId) {
        val ranges = workout?.routineId?.let { rid -> repo.dao.routineItemsOnce(rid).associate { it.exerciseId to (it.repMin to it.repMax) } }.orEmpty()
        for (exId in order) {
            if (priors.containsKey(exId)) continue
            val ex = exById[exId] ?: continue
            val hist = repo.dao.historyForOnce(exId).filter { it.workoutId != workoutId }
            val lastWorkout = hist.lastOrNull()?.workoutId
            priors[exId] = Prior(Strength.best(ex, hist), hist.filter { it.workoutId == lastWorkout && !it.warmup }.map { SetEntityLite(it.weightKg, it.reps) })
            val (lo, hi) = ranges[exId] ?: (8 to 12)
            suggestions[exId] = repo.suggestionFor(ex, lo, hi)
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var restEnd by remember { mutableStateOf<Long?>(null) }
    var restTotal by remember { mutableLongStateOf(1L) }
    var prBanner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(prBanner) { if (prBanner != null) { delay(5000); prBanner = null } }
    var picker by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun toggleDone(s: SetEntity, ex: ExerciseEntity) = scope.launch {
        val nowDone = !s.done
        repo.dao.updateSet(s.copy(done = nowDone, completedAt = if (nowDone) System.currentTimeMillis() else null))
        if (nowDone && !s.warmup) {
            val prior = priors[ex.id]
            val value = if (ex.metric == Metric.REPS) s.reps.toDouble() else Strength.e1rm(s.weightKg, s.reps)
            if (prior != null && prior.lastSession.isNotEmpty() && value > prior.bestValue + 0.01) {
                prBanner = if (ex.metric == Metric.REPS) "New record! ${ex.name}: ${s.reps} reps"
                else "New record! ${ex.name}: ${Strength.format(value)} kg estimated max"
                priors[ex.id] = prior.copy(bestValue = value)
            }
            restTotal = settings.restSeconds * 1000L
            restEnd = System.currentTimeMillis() + restTotal
        }
    }

    val w = workout
    val planning = w?.startedAt == NOT_STARTED
    Box(Modifier.fillMaxSize().imePadding()) {
        Screen(w?.name ?: "Workout", onBack = { nav.popBackStack() }, actions = {
            IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Default.DeleteOutline, "Discard workout", tint = c.muted) }
            if (!planning) {
                Spacer(Modifier.width(4.dp))
                GradientButton("Finish", Modifier.height(44.dp)) { confirmFinish = true }
            }
        }) {
            if (planning) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.soft).padding(14.dp)) {
                    Text("Step 1 of 2 · Set up", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text("Check sets, weight and reps, then press Start. The timer starts then.", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
            } else {
                val elapsed = ((now - (w?.startedAt ?: now)) / 1000).coerceAtLeast(0)
                val doneSets = sets.filter { it.done && !it.warmup }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Duration", "%d:%02d".format(elapsed / 60, elapsed % 60), Modifier.weight(1f))
                    MiniStat("Volume", "${Strength.format(doneSets.sumOf { it.weightKg * it.reps })} kg", Modifier.weight(1f))
                    MiniStat("Sets", "${doneSets.size}", Modifier.weight(1f))
                }
            }
            prBanner?.let { Banner(it, c.accent, Modifier.padding(bottom = 8.dp)) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(order, key = { it }) { exId ->
                    val ex = exById[exId] ?: return@items
                    val exSets = sets.filter { it.exerciseId == exId }.sortedBy { it.position }
                    val prior = priors[exId]
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.titleMedium, color = c.accent,
                                    modifier = Modifier.combinedClickable(role = Role.Button, onClick = { nav.navigate("exercise/${ex.id}") }))
                                if (planning) Text(
                                    prior?.lastSession?.size?.takeIf { it > 0 }?.let { "Last time: $it set${if (it == 1) "" else "s"}" } ?: "First time: no previous sets",
                                    style = MaterialTheme.typography.bodySmall, color = c.muted)
                                suggestions[exId]?.let { Text(it.note, style = MaterialTheme.typography.bodySmall, color = c.muted) }
                                if (prior != null && prior.bestValue > 0) Text(
                                    if (ex.metric == Metric.REPS) "Best: ${Strength.format(prior.bestValue)} reps"
                                    else "Best estimated max: ${Strength.format(prior.bestValue)} kg",
                                    style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            IconButton(onClick = { scope.launch { repo.dao.deleteExerciseFromWorkout(workoutId, exId) } }) {
                                Icon(Icons.Default.Close, "Remove ${ex.name}", tint = c.muted)
                            }
                        }
                        if (planning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sets", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                                RoundStep(Icons.Default.Remove, "One set fewer for ${ex.name}", enabled = exSets.size > 1) {
                                    exSets.lastOrNull()?.let { scope.launch { repo.dao.deleteSet(it) } }
                                }
                                Text("${exSets.size}", style = MaterialTheme.typography.titleLarge, color = c.ink,
                                    textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                                RoundStep(Icons.Default.Add, "One more set for ${ex.name}", enabled = exSets.size < 20) {
                                    scope.launch { repo.addSet(workoutId, exId) }
                                }
                            }
                            var k = 0
                            exSets.forEach { s ->
                                val label = if (s.warmup) "Warm-up" else "Set ${++k}"
                                val prev = prior?.lastSession?.getOrNull(k - 1)?.takeIf { !s.warmup }
                                PlanSetRow(label, prev?.let { if (ex.metric == Metric.REPS) "${it.reps} reps" else "${Strength.format(it.weightKg)} kg × ${it.reps}" } ?: "–",
                                    ex, s) { scope.launch { repo.dao.updateSet(it) } }
                            }
                            return@Column
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Set", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(34.dp))
                            Text("Previous", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
                            if (ex.metric == Metric.WEIGHT) Text("kg", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(68.dp), textAlign = TextAlign.Center)
                            Text("Reps", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                            Spacer(Modifier.width(44.dp))
                        }
                        var n = 0
                        exSets.forEach { s ->
                            val label = if (s.warmup) "W" else (++n).toString()
                            val prev = prior?.lastSession?.getOrNull(n - 1)?.takeIf { !s.warmup }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (s.done) c.accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(
                                        role = Role.Button,
                                        onClickLabel = "Toggle warm-up", onLongClickLabel = "Delete set",
                                        onClick = { scope.launch { repo.dao.updateSet(s.copy(warmup = !s.warmup)) } },
                                        onLongClick = { scope.launch { repo.dao.deleteSet(s) } },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, style = MaterialTheme.typography.titleSmall, color = if (s.warmup) c.warn else c.ink) }
                                Text(prev?.let { if (ex.metric == Metric.REPS) "${it.reps} reps" else "${Strength.format(it.weightKg)} × ${it.reps}" } ?: "–",
                                    style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                                if (ex.metric == Metric.WEIGHT) NumberCell(s.id, Strength.format(s.weightKg), true, "Weight in kg, set $label", Modifier.width(68.dp)) { v ->
                                    v.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }?.let { scope.launch { repo.dao.updateSet(s.copy(weightKg = it)) } }
                                }
                                NumberCell(s.id, s.reps.toString(), false, "Reps, set $label", Modifier.width(56.dp)) { v ->
                                    v.toIntOrNull()?.takeIf { it in 0..1000 }?.let { scope.launch { repo.dao.updateSet(s.copy(reps = it)) } }
                                }
                                Box(
                                    Modifier.size(44.dp, 36.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (s.done) c.grad else SolidColor(c.surface2))
                                        .combinedClickable(role = Role.Checkbox, onClick = { toggleDone(s, ex) })
                                        .semantics { contentDescription = if (s.done) "Set $label done" else "Mark set $label done" },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.Check, null, tint = if (s.done) c.onAccent else c.muted, modifier = Modifier.size(20.dp)) }
                            }
                        }
                        Muted("Tap a set number for warm-up, long-press to delete it.")
                        GhostButton("Add set", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { scope.launch { repo.addSet(workoutId, exId) } }
                    }
                }
                item {
                    SoftButton("Add exercise", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { picker = true }
                    Spacer(Modifier.height(if (restEnd != null) 110.dp else 24.dp))
                }
            }
            if (planning) {
                val ready = sets.isNotEmpty() && sets.all { it.reps > 0 }
                if (!ready) Muted(if (sets.isEmpty()) "Add at least one exercise to start." else "Every set needs reps before you start.",
                    Modifier.padding(top = 8.dp), center = true)
                GradientButton("Start workout", Modifier.fillMaxWidth().padding(vertical = 12.dp), icon = Icons.Default.PlayArrow, enabled = ready) {
                    scope.launch { repo.beginWorkout(workoutId) }
                }
            }
        }

        val end = restEnd
        if (end != null) {
            val left = ((end - now) / 1000).coerceAtLeast(0)
            if (left <= 0) LaunchedEffect(end) { delay(1500); restEnd = null }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
                    .clip(RoundedCornerShape(20.dp)).background(c.surface2).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (left > 0) "Rest  %d:%02d".format(left / 60, left % 60) else "Rest over. Next set!",
                        style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = { restEnd = end - 15_000; restTotal = (restTotal - 15_000).coerceAtLeast(1) }) { Text("−15s", color = c.ink) }
                    TextButton(onClick = { restEnd = end + 15_000; restTotal += 15_000 }) { Text("+15s", color = c.ink) }
                    TextButton(onClick = { restEnd = null }) { Text("Skip", color = c.accent) }
                }
                ProgressBar(1f - (end - now).toFloat() / restTotal)
            }
        }
    }

    if (picker) ExercisePicker(
        exercises, onDismiss = { picker = false },
        onCreate = { name, muscle, equipment, metric ->
            scope.launch {
                runCatching {
                    repo.dao.insertExercise(ExerciseEntity(name = name, primaryMuscle = muscle, equipment = equipment, metric = metric, isCustom = true))
                }.onSuccess { repo.addExercise(workoutId, it) }
                picker = false
            }
        },
    ) { ex -> scope.launch { repo.addExercise(workoutId, ex.id); picker = false } }

    if (confirmFinish) {
        val undone = sets.count { !it.done }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = { Text(if (undone > 0) "$undone unticked sets will be removed. Ticked sets are saved." else "Great session. Your sets will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    scope.launch {
                        if (sets.none { it.done }) { repo.discardWorkout(workoutId); nav.popBackStack(); return@launch }
                        app.lastFinish = repo.finishWorkout(workoutId)
                        nav.navigate("summary/$workoutId") { popUpTo("logger/{id}") { inclusive = true } }
                    }
                }) { Text("Finish", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going", color = c.ink) } },
            containerColor = c.surface,
        )
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard workout?") },
        text = { Text("All sets from this workout will be deleted. This can’t be undone.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; scope.launch { repo.discardWorkout(workoutId); nav.popBackStack() } }) { Text("Discard", color = c.bad) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel", color = c.ink) } },
        containerColor = c.surface,
    )
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalAscend.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2)) {
        Icon(icon, label, tint = if (enabled) c.ink else c.muted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

/** One set in set-up mode: what was done last time, then weight and reps with − / + or typed values. */
@Composable
private fun PlanSetRow(label: String, last: String, ex: ExerciseEntity, s: SetEntity, onChange: (SetEntity) -> Unit) {
    val c = LocalAscend.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg.copy(alpha = 0.5f)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (s.warmup) c.warn else c.ink, modifier = Modifier.weight(1f))
            Text("Last time: $last", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ex.metric == Metric.WEIGHT) {
                val step = Progression.increment(ex)
                StepField("kg", s.id, Strength.format(s.weightKg), true, Modifier.weight(1f),
                    onMinus = { onChange(s.copy(weightKg = (s.weightKg - step).coerceAtLeast(0.0))) },
                    onPlus = { onChange(s.copy(weightKg = (s.weightKg + step).coerceAtMost(1000.0))) },
                ) { v -> v.toDoubleOrNull()?.takeIf { it in 0.0..1000.0 }?.let { onChange(s.copy(weightKg = it)) } }
            }
            StepField("reps", s.id, s.reps.toString(), false, Modifier.weight(1f),
                onMinus = { onChange(s.copy(reps = (s.reps - 1).coerceAtLeast(1))) },
                onPlus = { onChange(s.copy(reps = (s.reps + 1).coerceAtMost(1000))) },
            ) { v -> v.toIntOrNull()?.takeIf { it in 0..1000 }?.let { onChange(s.copy(reps = it)) } }
        }
    }
}

@Composable
private fun StepField(unit: String, key: Long, value: String, decimal: Boolean, modifier: Modifier,
                      onMinus: () -> Unit, onPlus: () -> Unit, onValue: (String) -> Unit) {
    val c = LocalAscend.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundStep(Icons.Default.Remove, "Less $unit", onClick = onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            NumberCell(key, value, decimal, unit, Modifier.fillMaxWidth(), onValue)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        RoundStep(Icons.Default.Add, "More $unit", onClick = onPlus)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val c = LocalAscend.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.ink)
    }
}

/**
 * Numeric input cell. Focusing it selects the whole number, so typing replaces a pre-filled suggestion
 * instead of inserting into it; values above 1000 or malformed numbers are refused rather than shown.
 */
@Composable
private fun NumberCell(key: Long, value: String, decimal: Boolean, label: String, modifier: Modifier, onValue: (String) -> Unit) {
    val c = LocalAscend.current
    var field by remember(key) { mutableStateOf(TextFieldValue(if (value == "0") "" else value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (field.text.toDoubleOrNull() != value.toDoubleOrNull()) field = TextFieldValue(if (value == "0") "" else value)
    }
    LaunchedEffect(focused) {
        // Runs after the tap has placed the cursor, so the select-all wins.
        if (focused) { delay(40); field = field.copy(selection = TextRange(0, field.text.length)) }
    }
    BasicTextField(
        value = field,
        onValueChange = { v ->
            val clean = v.text.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }
            val num = clean.toDoubleOrNull()
            if (clean.isEmpty() || (num != null && num <= 1000 && clean.count { it == '.' } <= 1)) {
                field = if (clean == v.text) v else TextFieldValue(clean, TextRange(clean.length))
                onValue(clean.ifEmpty { "0" })
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleSmall.copy(color = c.ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.accent),
        modifier = modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
    )
}
