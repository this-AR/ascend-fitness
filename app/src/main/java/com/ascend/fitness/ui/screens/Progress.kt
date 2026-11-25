package com.ascend.fitness.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.data.Metric
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.muscleLabel
import com.ascend.fitness.domain.Recovery
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.domain.Suggestion
import com.ascend.fitness.domain.TIERS
import com.ascend.fitness.ui.components.BarChart
import com.ascend.fitness.ui.components.BodyMap
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.ExercisePhotos
import com.ascend.fitness.ui.components.GhostButton
import com.ascend.fitness.ui.components.ChartLine
import com.ascend.fitness.ui.components.IconTile
import com.ascend.fitness.ui.components.LineChart
import com.ascend.fitness.ui.components.Medal
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Pill
import com.ascend.fitness.ui.components.ProgressBar
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SectionTitle
import com.ascend.fitness.ui.components.SoftButton
import com.ascend.fitness.ui.components.fatigueColor
import com.ascend.fitness.ui.theme.LocalAscend
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val settings by repo.settings.state.collectAsState()
    val exercises by remember { repo.dao.exercises() }.collectAsState(emptyList())
    val sets by remember { repo.dao.allDoneSets() }.collectAsState(emptyList())
    val since = remember { System.currentTimeMillis() - 4L * 86_400_000 }
    val muscleSets by remember { repo.dao.muscleSetsSince(since) }.collectAsState(emptyList())
    val ranks = remember(exercises, sets, settings) { repo.ranks(exercises, sets) }
    val fatigue = Recovery.fatigue(muscleSets)

    Screen("Progress") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Recovery map")
            Card {
                BodyMap(fatigue, Modifier.padding(horizontal = 24.dp))
                val tired = fatigue.filter { it.value > 0.15f }.entries.sortedByDescending { it.value }
                if (tired.isEmpty()) Muted("Everything is fresh. Good day for a heavy session.")
                tired.forEach { (m, f) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(muscleLabel(m), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                        Text(when { f > 0.66f -> "Rest it"; f > 0.33f -> "Recovering"; else -> "Light work ok" },
                            style = MaterialTheme.typography.labelMedium, color = fatigueColor(f, c.muted))
                    }
                }
            }
            SectionTitle("Medals", action = if (ranks.isNotEmpty()) "See all" else null, onAction = { nav.navigate("ranks") })
            if (ranks.isEmpty()) Card { Muted("Finish a workout with ranked exercises (bench, squat, curls, pull-ups and ~950 more) to earn your first medal.") }
            else {
                val overall = ranks.map { it.tier.coerceAtLeast(0) }.average().roundToInt()
                Card(onClick = { nav.navigate("ranks") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Medal(overall, 76.dp)
                        Column {
                            Text("Overall rank", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            Text(TIERS[overall].name, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                            Muted("Average of ${ranks.size} ranked exercise${if (ranks.size == 1) "" else "s"} at ${Strength.format(settings.bodyweightKg)} kg")
                        }
                    }
                    if (ranks.size > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ranks.take(5).forEach { r -> Medal(r.tier.coerceAtLeast(0), 44.dp) }
                        Text(if (ranks.size > 5) "+${ranks.size - 5} more" else "", style = MaterialTheme.typography.labelMedium, color = c.muted,
                            modifier = Modifier.weight(1f))
                    }
                    Text("Tap to see every medal and how close the next rank is", style = MaterialTheme.typography.labelMedium, color = c.accent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.Default.Straighten, "Measurements", Modifier.weight(1f)) { nav.navigate("measurements") }
                IconTile(Icons.AutoMirrored.Filled.List, "History", Modifier.weight(1f)) { nav.navigate("history") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Every ranked exercise with its medal and the gap to the next rank. */
@Composable
fun RanksScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val settings by repo.settings.state.collectAsState()
    val exercises by remember { repo.dao.exercises() }.collectAsState(emptyList())
    val sets by remember { repo.dao.allDoneSets() }.collectAsState(emptyList())
    val ranks = remember(exercises, sets, settings) { repo.ranks(exercises, sets) }
    Screen("All medals", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("${ranks.size} ranked exercise${if (ranks.size == 1) "" else "s"}, best first.")
            ranks.forEach { r ->
                Card(onClick = { nav.navigate("exercise/${r.exercise.id}") }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Medal(r.tier.coerceAtLeast(0), 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row {
                                Text(r.exercise.name, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                                Text(if (r.exercise.metric == Metric.REPS) "${Strength.format(r.best)} reps" else "${Strength.format(r.best)} kg",
                                    style = MaterialTheme.typography.bodyMedium, color = c.muted)
                            }
                            ProgressBar(r.progress, height = 6.dp)
                            val next = r.ladder.getOrNull(r.tier + 1)
                            Text(
                                (if (r.tier >= 0) TIERS[r.tier].name else "Unranked") + (next?.let {
                                    " · ${Strength.format(it - r.best)} ${if (r.exercise.metric == Metric.REPS) "reps" else "kg"} to ${TIERS[r.tier + 1].name}"
                                } ?: " · top rank"),
                                style = MaterialTheme.typography.bodySmall, color = c.muted,
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun ExerciseDetailScreen(repo: Repository, nav: NavHostController, exerciseId: Long) {
    val c = LocalAscend.current
    val settings by repo.settings.state.collectAsState()
    val ex by remember { repo.dao.exerciseFlow(exerciseId) }.collectAsState(null)
    val hist by remember { repo.dao.historyFor(exerciseId) }.collectAsState(emptyList())
    var suggestion by remember { mutableStateOf<Suggestion?>(null) }
    val e = ex ?: return
    LaunchedEffect(e, hist.size) {
        val range = repo.dao.anyRoutineItemFor(e.id)
        suggestion = repo.suggestionFor(e, range?.repMin ?: 8, range?.repMax ?: 12)
    }
    val isReps = e.metric == Metric.REPS
    val ladder = remember(e, settings) { repo.ladder(e) }
    val work = hist.filter { !it.warmup }
    val best = Strength.best(e, work)
    val tier = Strength.tierIndex(best, ladder)
    val sessions = work.groupBy { it.workoutId }.values.map { xs -> xs.first().startedAt to xs }
    var chart by remember { mutableStateOf(if (isReps) "reps" else "e1rm") }

    Screen(e.name, onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val photos = e.images.split(",").filter { it.isNotBlank() }
            if (photos.isNotEmpty()) ExercisePhotos(photos, e.name, e.imagesFrom)
            else Card {
                Text("Muscles worked", style = MaterialTheme.typography.labelMedium, color = c.accent)
                com.ascend.fitness.ui.components.MuscleMap(e.primaryMuscle, e.secondaryMuscles, Modifier.padding(horizontal = 40.dp))
                Muted("Main muscle in colour, helpers lighter. No photo for this exercise yet; the demo button shows it in motion.")
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            GhostButton("Watch a demo (opens YouTube)", Modifier.fillMaxWidth(), icon = Icons.Default.PlayCircle) {
                val q = Uri.encode("${e.name} exercise proper form")
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))) }
            }
            Muted("${muscleLabel(e.primaryMuscle)} · ${e.equipment}${if (e.perHand) " · weight per hand" else ""}" +
                (if (e.level.isNotBlank()) " · ${e.level}" else "") + (if (e.category != "strength") " · ${e.category}" else ""))
            if (ladder.isNotEmpty()) Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Medal(tier.coerceAtLeast(0), 84.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your rank · tier ${tier + 1} of 10", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Text(if (tier >= 0) TIERS[tier].name else "Not ranked yet", style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        Muted(if (isReps) "Best: ${Strength.format(best)} clean reps"
                        else "Estimated max ${Strength.format(best)} kg · ${"%.2f".format(best / settings.bodyweightKg)}× bodyweight")
                    }
                }
                ladder.getOrNull(tier + 1)?.let { next ->
                    Row { Text("Next: ${TIERS[tier + 1].name}", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        Text("${Strength.format(next - best)} ${if (isReps) "reps" else "kg"} to go", style = MaterialTheme.typography.bodyMedium, color = c.accent) }
                    ProgressBar(Strength.progressToNext(best, ladder))
                }
            }

            suggestion?.let { s ->
                Card {
                    Text("Next session", style = MaterialTheme.typography.labelMedium, color = c.accent)
                    Text(if (isReps) "${s.reps} reps" else "${Strength.format(s.weightKg)} kg × ${s.reps}", style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    Muted(s.note)
                }
            }

            SectionTitle("Progress")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isReps) Pill("Estimated max", chart == "e1rm") { chart = "e1rm" }
                Pill("Reps", chart == "reps") { chart = "reps" }
                if (!isReps) Pill("Volume", chart == "volume") { chart = "volume" }
            }
            Card {
                when (chart) {
                    "e1rm" -> {
                        val pts = sessions.map { (t, xs) -> t to Strength.best(e, xs) }.filter { it.second > 0 }
                        val lines = ladder.mapIndexed { i, v -> ChartLine(v, TIERS[i].name, androidx.compose.ui.graphics.Color(TIERS[i].color)) }
                            .filterIndexed { i, _ -> i in (tier - 1)..(tier + 1) }
                        LineChart(pts, "Estimated max per workout for ${e.name}", lines = lines)
                        Muted("Best estimated one-rep max in each workout. Dashed lines are your nearby rank tiers.")
                    }
                    "reps" -> {
                        val pts = sessions.map { (t, xs) ->
                            val top = xs.maxOf { it.weightKg }
                            t to xs.filter { it.weightKg == top }.maxOf { it.reps }.toDouble()
                        }
                        LineChart(pts, "Best reps per workout for ${e.name}")
                        Muted(if (isReps) "Most clean reps in a set, each workout." else "Most reps at your heaviest weight in each workout. Climbing reps at the same weight mean it’s nearly time to add weight.")
                    }
                    else -> {
                        val vols = sessions.takeLast(12).map { (_, xs) -> xs.sumOf { it.weightKg * it.reps } }
                        BarChart(vols, "Volume per workout for ${e.name}", height = 160.dp)
                        Muted("Total kg lifted (weight × reps) in your last ${vols.size} workouts with this exercise.")
                    }
                }
            }

            if (ladder.isNotEmpty()) {
                SectionTitle(if (isReps) "Rank ladder" else "Rank ladder at ${Strength.format(settings.bodyweightKg)} kg")
                ladder.forEachIndexed { i, th ->
                    val here = i == tier
                    Card(padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), radius = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Medal(i, 32.dp)
                            Text(TIERS[i].name, style = MaterialTheme.typography.titleSmall, color = if (i <= tier) c.ink else c.muted, modifier = Modifier.weight(1f))
                            Text(if (isReps) "${Strength.format(th)} reps" else "${Strength.format(th)} kg", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                            Text(if (here) "You" else if (i < tier) "Done" else if (i == tier + 1) "Next" else "",
                                style = MaterialTheme.typography.labelMedium, color = if (here) c.accent else c.muted)
                        }
                    }
                }
                Muted(if (isReps) "Ranked by your best set of clean reps." else "Estimated max = weight × (1 + reps ÷ 30), from sets of 12 reps or fewer. The ladder adjusts to your bodyweight, sex and age in Settings.")
            }
            if (e.estimated && ladder.isNotEmpty()) Muted("Rank standards for this exercise are estimated from similar lifts (equipment, muscle and movement), not hand-tuned like the main lifts.")
            if (e.instructions.isNotBlank()) {
                SectionTitle("How to do it")
                Card {
                    e.instructions.lines().filter { it.isNotBlank() }.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.accent)
                            Text(step, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        }
                    }
                }
            }
            if (repo.hasAi()) SoftButton("Ask the coach about this lift", Modifier.fillMaxWidth(), icon = Icons.Default.AutoAwesome) {
                nav.navigate("coach?q=" + Uri.encode("Look at my ${e.name} history. Am I progressing or stuck? Give me a concrete 4-week plan to reach the next rank."))
            }
        }
    }
}
