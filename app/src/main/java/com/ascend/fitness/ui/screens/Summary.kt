package com.ascend.fitness.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.ascend.fitness.AscendApp
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.SetEntity
import com.ascend.fitness.data.WorkoutEntity
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.domain.TIERS
import com.ascend.fitness.ui.components.Banner
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.GhostButton
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.Medal
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SoftButton
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SummaryScreen(repo: Repository, nav: NavHostController, workoutId: Long, app: AscendApp) {
    val c = LocalAscend.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val result = remember { app.lastFinish?.takeIf { it.workoutId == workoutId } }
    var workout by remember { mutableStateOf<WorkoutEntity?>(null) }
    var sets by remember { mutableStateOf<List<SetEntity>>(emptyList()) }
    var names by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(workoutId) {
        workout = repo.dao.workout(workoutId)
        sets = repo.dao.setsForOnce(workoutId).filter { it.done }
        names = repo.dao.exercisesOnce().associate { it.id to it.name }
    }
    val layer = rememberGraphicsLayer()
    val w = workout ?: return

    Screen("Workout summary", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            result?.rankUps?.maxByOrNull { it.tier }?.let { up ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Medal(up.tier, 88.dp)
                        Column {
                            Text("Rank up!", style = MaterialTheme.typography.labelMedium, color = c.accent)
                            Text(TIERS[up.tier].name, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                            Muted(up.exerciseName)
                        }
                    }
                    result.rankUps.filter { it != up }.forEach { Muted("${it.exerciseName}: ${TIERS[it.tier].name}") }
                }
            }
            result?.prs?.forEach { p ->
                Banner("New record · ${p.exerciseName}: " + when (p.kind) {
                    "e1rm" -> "${Strength.format(p.value)} kg estimated max"
                    "weight" -> "${Strength.format(p.value)} kg heaviest"
                    else -> "${p.value.toInt()} reps"
                }, c.accent)
            }
            result?.achievements?.forEach { Banner("Achievement unlocked: $it", c.good) }

            // The share card: drawn into a graphics layer so it can be saved as an image.
            Column(
                Modifier.fillMaxWidth().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                    .clip(RoundedCornerShape(24.dp)).background(c.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ASCEND", style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.weight(1f))
                    Text(Instant.ofEpochMilli(w.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")),
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text(w.name, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                val mins = ((w.endedAt ?: w.startedAt) - w.startedAt) / 60_000
                val work = sets.filter { !it.warmup }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SummaryStat("Duration", "$mins min")
                    SummaryStat("Volume", "${Strength.format(work.sumOf { it.weightKg * it.reps })} kg")
                    SummaryStat("Sets", "${work.size}")
                }
                Spacer(Modifier.height(2.dp).fillMaxWidth().background(c.grad))
                work.groupBy { it.exerciseId }.forEach { (exId, xs) ->
                    val best = xs.maxByOrNull { Strength.e1rm(it.weightKg, it.reps) + it.reps / 1000.0 }!!
                    Row {
                        Text(names[exId] ?: "Exercise", style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                        Text("${xs.size} × · best ${if (best.weightKg > 0) Strength.format(best.weightKg) + " kg × " else ""}${best.reps}",
                            style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
                if (!result?.prs.isNullOrEmpty()) Text("${result!!.prs.size} new personal record${if (result.prs.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall, color = c.accent)
            }

            GradientButton("Share as image", Modifier.fillMaxWidth(), icon = Icons.Default.Share) {
                scope.launch {
                    val bmp = layer.toImageBitmap().asAndroidBitmap()
                    shareBitmap(ctx, bmp)
                }
            }
            if (repo.hasAi()) SoftButton("Ask the coach for a debrief", Modifier.fillMaxWidth(), icon = Icons.Default.AutoAwesome) {
                nav.navigate("coach?q=" + Uri.encode("Give me a short debrief of my most recent workout (${w.name}): what went well, what to change next time, and what to train next."))
            }
            GhostButton("Done", Modifier.fillMaxWidth()) { nav.popBackStack("home", inclusive = false) }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    val c = LocalAscend.current
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.ink)
    }
}

private suspend fun shareBitmap(ctx: Context, bmp: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "workout-${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)
    }
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri("Workout", uri) // gives the share sheet an image preview
    ctx.startActivity(Intent.createChooser(send, "Share workout"))
}
