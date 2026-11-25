package com.ascend.fitness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ascend.fitness.data.ExerciseEntity
import com.ascend.fitness.data.MUSCLES
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.muscleLabel
import com.ascend.fitness.ui.theme.LocalAscend

private val EQUIPMENT = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Bodyweight", "Kettlebell", "Band", "EZ bar", "Other")

/** Best matches first: exact name, then name starts with the query, then a word starts with it, then anything else. */
internal fun searchRank(e: ExerciseEntity, q: String): Int {
    val s = q.trim().lowercase()
    if (s.isEmpty()) return 0
    val n = e.name.lowercase()
    return when {
        n == s -> 0
        n.startsWith(s) -> 1
        n.split(' ', '-', '(').any { it.startsWith(s) } -> 2
        else -> 3
    }
}

/** Full-screen exercise search with a muscle filter and a way to add a custom exercise. */
@Composable
fun ExercisePicker(
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, muscle: String, equipment: String, metric: Metric) -> Unit,
    onPick: (ExerciseEntity) -> Unit,
) {
    val c = LocalAscend.current
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            Screen("Add exercise", onBack = onDismiss) {
                if (creating) {
                    CreateExercise(onCancel = { creating = false }) { n, m, e, k -> onCreate(n, m, e, k); creating = false }
                    return@Screen
                }
                Field(q, { q = it.take(60) }, "Search exercises")
                LazyRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pill("All", muscle == null) { muscle = null } }
                    items(MUSCLES) { m -> Pill(muscleLabel(m), muscle == m) { muscle = if (muscle == m) null else m } }
                }
                LazyRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pill("Any equipment", equipment == null) { equipment = null } }
                    items(EQUIPMENT) { e -> Pill(e, equipment == e) { equipment = if (equipment == e) null else e } }
                }
                val list = exercises.filter {
                    (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) || it.equipment.contains(q.trim(), true)) &&
                        (muscle == null || it.primaryMuscle == muscle || it.secondaryMuscles.split(",").contains(muscle)) &&
                        (equipment == null || it.equipment == equipment)
                }.sortedWith(compareBy({ searchRank(it, q) }, { it.estimated }, { it.name.lowercase() }))
                Muted("${list.size} exercises", Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    item { SoftButton("Create custom exercise", Modifier.fillMaxWidth(), icon = Icons.Default.Add) { creating = true } }
                    items(list, key = { it.id }) { ex ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
                                .clickable(role = Role.Button) { onPick(ex) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumb(ex.images.substringBefore(","), Modifier.padding(end = 12.dp), ex.primaryMuscle, ex.secondaryMuscles)
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                                Text("${muscleLabel(ex.primaryMuscle)} · ${ex.equipment}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                        }
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CreateExercise(onCancel: () -> Unit, onSave: (String, String, String, Metric) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("chest") }
    var equipment by remember { mutableStateOf("Dumbbell") }
    var metric by remember { mutableStateOf(Metric.WEIGHT) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(name, { name = it.take(60) }, "Exercise name")
        Muted("Main muscle")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(MUSCLES) { m -> Pill(muscleLabel(m), muscle == m) { muscle = m } } }
        Muted("Equipment")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Barbell", "Dumbbell", "Machine", "Cable", "Kettlebell", "Bodyweight", "Band", "Other")) { e -> Pill(e, equipment == e) { equipment = e } }
        }
        Muted("Track by")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Weight and reps", metric == Metric.WEIGHT) { metric = Metric.WEIGHT }
            Pill("Reps only", metric == Metric.REPS) { metric = Metric.REPS }
        }
        Muted("Custom exercises are logged and charted but not ranked, since they have no strength standard.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Cancel", Modifier.weight(1f), onClick = onCancel)
            GradientButton("Save", Modifier.weight(1f), enabled = name.isNotBlank()) { onSave(name.trim(), muscle, equipment, metric) }
        }
    }
}
