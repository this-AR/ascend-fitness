package com.ascend.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ascend.fitness.data.muscleLabel
import com.ascend.fitness.ui.theme.LocalAscend

private data class Zone(val muscle: String, val l: Float, val t: Float, val r: Float, val b: Float, val oval: Boolean = false)

private fun mirror(z: Zone) = z.copy(l = 100 - z.r, r = 100 - z.l)
private fun pair(z: Zone) = listOf(z, mirror(z))

private val FRONT = pair(Zone("shoulders", 17f, 29f, 31f, 43f, true)) +
    pair(Zone("chest", 33f, 34f, 49f, 52f)) +
    pair(Zone("biceps", 18f, 45f, 28f, 67f)) +
    pair(Zone("forearms", 14f, 71f, 25f, 100f)) +
    listOf(Zone("abs", 42f, 55f, 58f, 92f)) +
    pair(Zone("obliques", 34f, 58f, 41f, 90f)) +
    pair(Zone("quads", 33f, 113f, 47f, 156f)) +
    listOf(Zone("adductors", 47.5f, 112f, 52.5f, 138f))

private val BACK = pair(Zone("shoulders", 17f, 29f, 31f, 43f, true)) +
    listOf(Zone("traps", 37f, 25f, 63f, 39f)) +
    listOf(Zone("upperback", 34f, 40f, 66f, 57f)) +
    pair(Zone("lats", 31f, 52f, 40f, 82f)) +
    listOf(Zone("lowerback", 42f, 78f, 58f, 95f)) +
    pair(Zone("triceps", 18f, 44f, 28f, 67f)) +
    pair(Zone("forearms", 14f, 71f, 25f, 100f)) +
    pair(Zone("glutes", 33f, 97f, 49.5f, 118f)) +
    pair(Zone("hamstrings", 33f, 121f, 48f, 158f)) +
    pair(Zone("calves", 35f, 165f, 46f, 198f))

fun fatigueColor(f: Float, idle: Color): Color = when {
    f <= 0.02f -> idle
    f < 0.5f -> lerp(Color(0xFF3DDC84), Color(0xFFFFC93C), f / 0.5f)
    else -> lerp(Color(0xFFFFC93C), Color(0xFFFF4D4D), (f - 0.5f) / 0.5f)
}

private fun DrawScope.body(zones: List<Zone>, fatigue: Map<String, Float>, base: Color, idle: Color,
                            colorOf: (Float) -> Color = { fatigueColor(it, idle) }) {
    val sx = size.width / 100f
    val sy = size.height / 210f
    fun rr(l: Float, t: Float, r: Float, b: Float, c: Color, rad: Float = 6f) =
        drawRoundRect(c, Offset(l * sx, t * sy), Size((r - l) * sx, (b - t) * sy), CornerRadius(rad * sx))
    drawCircle(base, 10f * sx, Offset(50f * sx, 13f * sy))
    rr(45f, 20f, 55f, 28f, base, 3f)
    rr(30f, 26f, 70f, 98f, base, 12f)
    rr(31f, 94f, 69f, 116f, base, 8f)
    listOf(15f to 30f, 70f to 85f).forEach { (l, r) -> rr(l, 30f, r, 70f, base, 6f) }
    listOf(12f to 27f, 73f to 88f).forEach { (l, r) -> rr(l, 69f, r, 104f, base, 6f) }
    listOf(31f to 49.5f, 50.5f to 69f).forEach { (l, r) -> rr(l, 110f, r, 162f, base, 8f) }
    listOf(33f to 48f, 52f to 67f).forEach { (l, r) -> rr(l, 160f, r, 206f, base, 7f) }
    zones.forEach { z ->
        val col = colorOf(fatigue[z.muscle] ?: 0f)
        if (z.oval) drawOval(col, Offset(z.l * sx, z.t * sy), Size((z.r - z.l) * sx, (z.b - z.t) * sy))
        else rr(z.l, z.t, z.r, z.b, col, 5f)
    }
}

/** Front and back body diagrams coloured by fatigue (green fresh-ish, red very tired, grey untrained). */
@Composable
fun BodyMap(fatigue: Map<String, Float>, modifier: Modifier = Modifier) {
    val c = LocalAscend.current
    val tired = fatigue.filter { it.value > 0.25f }.entries.sortedByDescending { it.value }
    val desc = if (tired.isEmpty()) "All muscles recovered" else "Fatigued: " + tired.joinToString { muscleLabel(it.key) }
    Row(modifier.fillMaxWidth().semantics { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("Front" to FRONT, "Back" to BACK).forEach { (label, zones) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 210f)) { body(zones, fatigue, Color(0xFF1C1C20), Color(0xFF34343A)) }
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}


private val BACK_MUSCLES = setOf("lats", "upperback", "traps", "lowerback", "triceps", "glutes", "hamstrings", "calves")

private fun workedMap(primary: String, secondary: String): Map<String, Float> =
    secondary.split(",").map { it.trim() }.filter { it.isNotEmpty() }.associateWith { 0.5f } + (primary to 1f)

/** "Muscles worked" figure used when an exercise has no photo: main muscle in the accent colour, helpers lighter. */
@Composable
fun MuscleMap(primary: String, secondary: String, modifier: Modifier = Modifier) {
    val c = LocalAscend.current
    val worked = workedMap(primary, secondary)
    val colorOf: (Float) -> Color = { v -> when { v >= 1f -> c.accent; v > 0f -> c.accent2.copy(alpha = 0.55f); else -> Color(0xFF34343A) } }
    val names = worked.keys.filter { it != "cardio" }.sortedByDescending { worked[it] }.joinToString { muscleLabel(it) }
    Row(modifier.fillMaxWidth().semantics { contentDescription = "Muscles worked: $names" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("Front" to FRONT, "Back" to BACK).forEach { (label, zones) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 210f)) { body(zones, worked, Color(0xFF1C1C20), Color(0xFF34343A), colorOf) }
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** Single small figure (front or back, whichever shows the main muscle) for list thumbnails. */
@Composable
fun MuscleThumbFigure(primary: String, secondary: String, modifier: Modifier = Modifier) {
    val c = LocalAscend.current
    val worked = workedMap(primary, secondary)
    val zones = if (primary in BACK_MUSCLES) BACK else FRONT
    val colorOf: (Float) -> Color = { v -> when { v >= 1f -> c.accent; v > 0f -> c.accent2.copy(alpha = 0.55f); else -> Color(0xFF3A3A40) } }
    Canvas(modifier.aspectRatio(100f / 210f)) { body(zones, worked, Color(0xFF26262A), Color(0xFF3A3A40), colorOf) }
}
