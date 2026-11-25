package com.ascend.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ascend.fitness.ai.ChatTurn
import com.ascend.fitness.ai.Coach
import com.ascend.fitness.data.Repository
import com.ascend.fitness.ui.components.Banner
import com.ascend.fitness.ui.components.Card
import com.ascend.fitness.ui.components.Field
import com.ascend.fitness.ui.components.GradientButton
import com.ascend.fitness.ui.components.Muted
import com.ascend.fitness.ui.components.Pill
import com.ascend.fitness.ui.components.Screen
import com.ascend.fitness.ui.theme.LocalAscend
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun NeedsKey(nav: NavHostController) {
    Card {
        Text("Connect Gemini to use the coach", style = MaterialTheme.typography.titleMedium, color = LocalAscend.current.ink)
        Muted("Get a free API key from Google AI Studio (aistudio.google.com), then paste it in Settings. It’s stored encrypted on this phone, and a summary of your training is sent to Google when you use an AI feature and once a day for your daily tip. Without a key, nothing leaves the phone.")
        GradientButton("Open Settings", Modifier.fillMaxWidth()) { nav.navigate("settings") }
    }
}

@Composable
fun CoachScreen(repo: Repository, nav: NavHostController, initial: String) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    val turns = remember { mutableStateListOf<ChatTurn>() }
    val api = remember { mutableStateListOf<ChatTurn>() } // what Gemini sees: the first question carries the data summary
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || busy) return
        error = null
        busy = true
        scope.launch {
            // The full training summary rides along with the first question only.
            val q = if (api.isEmpty()) "TRAINING DATA:\n${repo.trainingContext()}\n\nQUESTION:\n$text" else text
            val request = api.toList() + ChatTurn(true, q)
            turns.add(ChatTurn(true, text))
            runCatching { repo.ask(request) }
                .onSuccess { api.add(ChatTurn(true, q)); api.add(ChatTurn(false, it)); turns.add(ChatTurn(false, it)) }
                .onFailure { error = it.message; turns.removeAt(turns.lastIndex); input = text }
            busy = false
            list.animateScrollToItem(turns.size)
        }
    }
    LaunchedEffect(initial, settings.hasGeminiKey) { if (initial.isNotBlank() && settings.hasGeminiKey && turns.isEmpty()) send(initial) }

    Screen("Coach", onBack = { nav.popBackStack() }) {
        if (!settings.hasGeminiKey) { NeedsKey(nav); return@Screen }
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (turns.isEmpty()) item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = c.accent)
                        Text("  Ask anything about your training", style = MaterialTheme.typography.titleSmall, color = c.ink)
                    }
                    Muted("The coach reads your workouts, ranks, recovery map, weekly plan and measurements before answering.")
                }
            }
            items(turns) { t ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(t.text, style = MaterialTheme.typography.bodyLarge, color = if (t.fromUser) c.onAccent else c.ink,
                        modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (t.fromUser) c.grad else androidx.compose.ui.graphics.SolidColor(c.surface)).padding(14.dp))
                }
            }
            if (busy) item { CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(8.dp)) }
            error?.let { item { Banner(it, c.bad) } }
        }
        if (turns.isEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(listOf("What should I train today?", "Analyse my last 4 weeks", "Which lift is lagging?", "Plan a deload week", "How do I reach the next rank on bench?")) { s ->
                Pill(s, false) { send(s) }
            }
        }
        Row(Modifier.fillMaxWidth().imePadding().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field(input, { input = it.take(1000) }, "Ask the coach", singleLine = false) }
            IconButton(onClick = { val t = input; input = ""; send(t) }, enabled = !busy && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = c.accent)
            }
        }
    }
}

@Composable
fun AiPlanScreen(repo: Repository, nav: NavHostController) {
    val c = LocalAscend.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.state.collectAsState()
    var goal by rememberSaveable { mutableStateOf("Build muscle") }
    var days by rememberSaveable { mutableStateOf(4) }
    var equipment by rememberSaveable { mutableStateOf("Full gym") }
    var minutes by rememberSaveable { mutableStateOf(60) }
    var notes by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<Coach.AiPlan?>(null) }

    Screen("AI plan", onBack = { nav.popBackStack() }) {
        if (!settings.hasGeminiKey) { NeedsKey(nav); return@Screen }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Gemini builds a weekly plan from your goal, your equipment and what your history says about you. You can review it before saving.")
            Text("Goal", style = MaterialTheme.typography.titleSmall, color = c.ink)
            FlowPills(listOf("Build muscle", "Get stronger", "Lose fat", "General fitness", "Athletic"), goal) { goal = it }
            Text("Days per week", style = MaterialTheme.typography.titleSmall, color = c.ink)
            FlowPills((2..6).map { "$it" }, "$days") { days = it.toInt() }
            Text("Equipment", style = MaterialTheme.typography.titleSmall, color = c.ink)
            FlowPills(listOf("Full gym", "Dumbbells only", "Bodyweight only", "Home: dumbbells and bands"), equipment) { equipment = it }
            Text("Session length", style = MaterialTheme.typography.titleSmall, color = c.ink)
            FlowPills(listOf("30", "45", "60", "90"), "$minutes") { minutes = it.toInt() }
            Field(notes, { notes = it.take(400) }, "Anything else? Injuries, weak points, days you can't train", singleLine = false)
            GradientButton(if (busy) "Building your plan…" else "Generate plan", Modifier.fillMaxWidth(), icon = Icons.Default.AutoAwesome, enabled = !busy) {
                busy = true; error = null; plan = null
                scope.launch {
                    runCatching {
                        val names = repo.dao.exercisesOnce().map { it.name }
                        val req = "Goal: $goal. $days days per week. Equipment: $equipment. About $minutes minutes per session. Notes: ${notes.ifBlank { "none" }}"
                        val raw = repo.ask(listOf(ChatTurn(true, Coach.planPrompt(repo.trainingContext(), req, names))), json = true)
                        Coach.parsePlan(raw, names.toSet())
                    }.onSuccess { plan = it }.onFailure { error = it.message ?: "Something went wrong." }
                    busy = false
                }
            }
            error?.let { Banner(it, c.bad) }
            plan?.let { p ->
                Card {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, color = c.ink)
                    if (p.summary.isNotBlank()) Muted(p.summary)
                    p.days.forEach { (wd, d) ->
                        Text("${DayOfWeek.of(wd).getDisplayName(TextStyle.FULL, Locale.getDefault())} · ${d.name}", style = MaterialTheme.typography.labelLarge, color = c.accent)
                        d.exercises.forEach { e -> Muted("${e.name} · ${e.sets} × ${e.repMin}${if (e.repMax != e.repMin) "–${e.repMax}" else ""}") }
                    }
                    GradientButton("Save as my week", Modifier.fillMaxWidth()) {
                        scope.launch { repo.saveAiPlan(p); nav.popBackStack() }
                    }
                    Muted("Saving adds these routines and replaces your weekly plan. Existing routines and history stay.")
                }
            }
        }
    }
}

@Composable
private fun FlowPills(options: List<String>, selected: String, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(options) { o -> Pill(o, o == selected) { onPick(o) } } }
}
