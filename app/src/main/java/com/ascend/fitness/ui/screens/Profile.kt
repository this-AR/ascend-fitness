package com.ascend.fitness.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.data.MeasurementEntity
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.Sex
import com.ascend.fitness.domain.Achievements
import com.ascend.fitness.domain.Streaks
import com.ascend.fitness.domain.Strength
import com.ascend.fitness.ui.components.Banner
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.Field
import com.ascend.fitness.ui.components.GhostButton
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.IconTile
import com.ascend.fitness.ui.components.LineChart
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Pill
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.components.SectionTitle
import com.ascend.fitness.ui.components.SoftButton
import com.ascend.fitness.ui.components.Stat
import com.ascend.fitness.ui.theme.LocalAscend
import com.ascend.fitness.ui.theme.PALETTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val settings by repo.settings.state.collectAsState()
    val summaries by remember { repo.dao.workoutSummaries() }.collectAsState(emptyList())
    val workouts by remember { repo.dao.finishedWorkouts() }.collectAsState(emptyList())
    val unlocked by remember { repo.dao.achievements() }.collectAsState(emptyList())
    val keys = unlocked.map { it.key }.toSet()
    val streak = Streaks.compute(workouts, settings.weeklyGoal)

    Screen("Profile", actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings", tint = c.ink) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Workouts", "${summaries.size}", Modifier.weight(1f))
                    Stat("Volume", "${Strength.format(summaries.sumOf { it.volume } / 1000)} t", Modifier.weight(1f))
                    Stat("Best streak", "${streak.bestWeekStreak} wk", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.AutoMirrored.Filled.List, "History", Modifier.weight(1f)) { nav.navigate("history") }
                IconTile(Icons.Default.Straighten, "Measures", Modifier.weight(1f)) { nav.navigate("measurements") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.Default.AutoAwesome, "Coach", Modifier.weight(1f)) { nav.navigate("coach?q=") }
                IconTile(Icons.Default.Settings, "Settings", Modifier.weight(1f)) { nav.navigate("settings") }
            }
            SectionTitle("Achievements · ${keys.size}/${Achievements.ALL.size}")
            Achievements.ALL.sortedByDescending { it.key in keys }.forEach { a ->
                val got = a.key in keys
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (got) c.grad else androidx.compose.ui.graphics.SolidColor(c.surface2)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (got) Icons.Default.EmojiEvents else Icons.Default.Lock, null, tint = if (got) c.onAccent else c.muted)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleSmall, color = if (got) c.ink else c.muted)
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val summaries by remember { repo.dao.workoutSummaries() }.collectAsState(emptyList())
    val df = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    Screen("History", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (summaries.isEmpty()) Muted("No finished workouts yet.")
            summaries.forEach { s ->
                Card(onClick = { nav.navigate("summary/${s.id}") }, padding = PaddingValues(16.dp)) {
                    Row {
                        Text(s.name, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                        Text(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).format(df), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Muted("${((s.endedAt ?: s.startedAt) - s.startedAt) / 60_000} min · ${s.exerciseCount} exercise${if (s.exerciseCount == 1) "" else "s"} · ${s.setCount} set${if (s.setCount == 1) "" else "s"} · ${Strength.format(s.volume)} kg")
                }
            }
        }
    }
}

private val MEASURE_TYPES = listOf(
    "bodyweight" to "Bodyweight (kg)", "bodyfat" to "Body fat (%)", "waist" to "Waist (cm)", "chest" to "Chest (cm)",
    "hips" to "Hips (cm)", "arm" to "Arm (cm)", "thigh" to "Thigh (cm)", "calf" to "Calf (cm)", "neck" to "Neck (cm)", "shoulders" to "Shoulders (cm)",
)

@Composable
fun MeasurementsScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val all by remember { repo.dao.measurements() }.collectAsState(emptyList())
    var type by rememberSaveable { mutableStateOf("bodyweight") }
    var value by rememberSaveable { mutableStateOf("") }
    val label = MEASURE_TYPES.first { it.first == type }.second
    val list = all.filter { it.type == type }
    val df = DateTimeFormatter.ofPattern("d MMM yyyy")

    Screen("Measurements", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MEASURE_TYPES.size) { i -> val (k, l) = MEASURE_TYPES[i]; Pill(l.substringBefore(" ("), type == k) { type = k } }
            }
            Card {
                Field(value, { value = it.replace(',', '.').filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, "$label today",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                GradientButton("Add", Modifier.fillMaxWidth(), enabled = value.toDoubleOrNull()?.let { it > 0 && it < 1000 } == true) {
                    val v = value.toDouble()
                    scope.launch {
                        repo.dao.insertMeasurement(MeasurementEntity(date = System.currentTimeMillis(), type = type, value = v))
                        if (type == "bodyweight" && v in 25.0..350.0) repo.settings.update { it.copy(bodyweightKg = v) }
                        repo.evaluateAchievements()
                    }
                    value = ""
                }
                if (type == "bodyweight") Muted("Your latest bodyweight also updates your rank ladders.")
            }
            Card { LineChart(list.reversed().map { it.date to it.value }, "$label over time", emptyText = "Add two or more entries to see the chart.") }
            list.forEach { m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault()).format(df), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                    Text(Strength.format(m.value), style = MaterialTheme.typography.titleSmall, color = c.ink)
                    IconButton(onClick = { scope.launch { repo.dao.deleteMeasurement(m) } }) { Icon(Icons.Default.Delete, "Delete entry", tint = c.muted) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by repo.settings.state.collectAsState()
    var key by remember { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(s.geminiModel) }
    var bw by rememberSaveable { mutableStateOf(Strength.format(s.bodyweightKg)) }
    var year by rememberSaveable { mutableStateOf(s.birthYear.toString()) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var keyMsg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var testing by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
            }.fold({ "Backup saved." to true }, { "Could not save backup: ${it.message}" to false })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    Screen("Settings", onBack = { nav.popBackStack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { (m, ok) -> Banner(m, if (ok) c.good else c.bad) }
            SectionTitle("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PALETTES.forEach { p ->
                    val on = p.name == s.theme
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(c.surface)
                            .border(2.dp, if (on) p.a else c.surface, RoundedCornerShape(18.dp))
                            .clickable(role = Role.RadioButton) { repo.settings.update { it.copy(theme = p.name) } }.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.a, p.b))))
                        Text(p.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                    }
                }
            }

            SectionTitle("You")
            Card {
                Field(bw, { bw = it.replace(',', '.').filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "Bodyweight (kg)",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Field(year, { year = it.filter(Char::isDigit).take(4) }, "Birth year", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Male standards", s.sex == Sex.MALE) { repo.settings.update { it.copy(sex = Sex.MALE) } }
                    Pill("Female standards", s.sex == Sex.FEMALE) { repo.settings.update { it.copy(sex = Sex.FEMALE) } }
                }
                Text("Weekly goal: ${s.weeklyGoal} workouts", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (2..6).forEach { g -> Pill("$g", s.weeklyGoal == g) { repo.settings.update { it.copy(weeklyGoal = g) } } } }
                Text("Rest timer: ${s.restSeconds}s", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(60, 90, 120, 180).forEach { r -> Pill("${r}s", s.restSeconds == r) { repo.settings.update { it.copy(restSeconds = r) } } } }
                GhostButton("Save profile", Modifier.fillMaxWidth()) {
                    val b = bw.toDoubleOrNull(); val y = year.toIntOrNull()
                    val thisYear = LocalDate.now().year
                    if (b != null && b in 25.0..350.0 && y != null && y in 1920..(thisYear - 10)) {
                        repo.settings.update { it.copy(bodyweightKg = b, birthYear = y) }
                        message = "Profile saved." to true
                    } else message = "Enter a bodyweight of 25–350 kg and a real birth year." to false
                }
            }

            SectionTitle("AI coach (Gemini)")
            Card {
                Muted(if (s.hasGeminiKey) "A Gemini key is saved (encrypted on this phone)." else "Paste a Gemini API key from aistudio.google.com to turn on daily AI tips, the coach chat and AI plans. With a key, a summary of your training (profile, recent workouts, ranks, recovery, measurements) is sent to Google when you use these and once a day for the tip. Without a key, the app is fully offline.")
                Field(key, { key = it.trim().take(200) }, if (s.hasGeminiKey) "Replace API key" else "Gemini API key", secret = true)
                Field(model, { model = it.trim().lowercase().take(64) }, "Model")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(if (testing) "Testing…" else "Test and save", Modifier.weight(1f),
                        enabled = !testing && (key.isNotBlank() || model != s.geminiModel)) {
                        val m = model.ifBlank { "gemini-3.5-flash-lite" }
                        val candidate = key.ifBlank { repo.settings.geminiKey() }
                        testing = true; keyMsg = null
                        scope.launch {
                            keyMsg = if (candidate == null) {
                                repo.settings.update { it.copy(geminiModel = m) }; "Model saved." to true
                            } else runCatching { repo.testGeminiKey(candidate, m) }.fold(
                                // Only a key that works is stored.
                                { repo.settings.setGeminiKey(candidate); repo.settings.update { it.copy(geminiModel = m) }; key = ""; "Gemini connected and saved." to true },
                                { ((it.message ?: "Gemini test failed.") + " Nothing was saved.") to false })
                            testing = false
                        }
                    }
                    if (s.hasGeminiKey) SoftButton("Remove key", Modifier.weight(1f), danger = true) { repo.settings.setGeminiKey(null); keyMsg = "Key removed." to true }
                }
                keyMsg?.let { (m, ok) -> Banner(m, if (ok) c.good else c.bad) }
            }

            SectionTitle("Your data")
            Card {
                Muted("Everything is stored only on this phone. Save a backup file regularly: uninstalling the app deletes its data. Backups never include your API key.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Save backup", Modifier.weight(1f)) { exportLauncher.launch("ascend-backup-${LocalDate.now()}.json") }
                    GhostButton("Restore", Modifier.weight(1f)) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                }
            }
            Muted("Ascend 0.4.1 · offline-first · fonts: Bricolage Grotesque and Geist (SIL Open Font License)")
            Muted(com.ascend.fitness.data.Catalog.ATTRIBUTION)
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in the app is replaced by the backup. Save a backup of your current data first if you need it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        message = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                ctx.contentResolver.openInputStream(uri)!!.use { s ->
                                    val bytes = s.readNBytesCompat(50 * 1024 * 1024)
                                    String(bytes)
                                }
                            }
                            repo.importJson(text)
                        }.fold({ "Backup restored." to true }, { "Restore failed, nothing changed: ${it.message}" to false })
                    }
                }) { Text("Restore", color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel", color = c.ink) } },
            containerColor = c.surface,
        )
    }
}

/** Reads at most [limit] bytes; refuses bigger files so a wrong pick can't exhaust memory. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        require(out.size() <= limit) { "File is too large to be a backup." }
    }
    return out.toByteArray()
}

@Composable
fun OnboardingScreen(repo: Repository) {
    val c = LocalAscend.current
    val s by repo.settings.state.collectAsState()
    var bw by rememberSaveable { mutableStateOf("75") }
    var year by rememberSaveable { mutableStateOf("2000") }
    val valid = bw.toDoubleOrNull()?.let { it in 25.0..350.0 } == true && year.toIntOrNull()?.let { it in 1920..(LocalDate.now().year - 10) } == true
    Screen(null) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Welcome to Ascend", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Muted("Log workouts, climb 10 strength ranks from Rookie to Galactic demon, and watch every lift improve. Everything stays on your phone.")
            Text("Pick your theme", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PALETTES.forEach { p ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.surface)
                        .border(2.dp, if (p.name == s.theme) p.a else c.surface, RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { repo.settings.update { it.copy(theme = p.name) } }.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.a, p.b))))
                        Text(p.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                    }
                }
            }
            Text("Your ranks are scaled to you", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Field(bw, { bw = it.replace(',', '.').filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "Bodyweight (kg)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Field(year, { year = it.filter(Char::isDigit).take(4) }, "Birth year", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Male standards", s.sex == Sex.MALE) { repo.settings.update { it.copy(sex = Sex.MALE) } }
                Pill("Female standards", s.sex == Sex.FEMALE) { repo.settings.update { it.copy(sex = Sex.FEMALE) } }
            }
            Text("Workouts per week goal", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (2..6).forEach { g -> Pill("$g", s.weeklyGoal == g) { repo.settings.update { it.copy(weeklyGoal = g) } } } }
            Spacer(Modifier.height(8.dp))
            GradientButton("Let’s go", Modifier.fillMaxWidth(), enabled = valid) {
                repo.settings.update { it.copy(bodyweightKg = bw.toDouble(), birthYear = year.toInt(), onboarded = true) }
            }
        }
    }
}
