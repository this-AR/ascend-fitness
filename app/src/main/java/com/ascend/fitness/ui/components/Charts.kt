package com.ascend.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.fitness.ui.theme.LocalAscend

data class ChartLine(val value: Double, val label: String, val color: Color)

/** Line chart over time with optional dashed reference lines (tier thresholds). */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    description: String,
    modifier: Modifier = Modifier,
    lines: List<ChartLine> = emptyList(),
    height: Dp = 200.dp,
    emptyText: String = "Log this exercise in two or more workouts to see the chart.",
) {
    val c = LocalAscend.current
    val tm = rememberTextMeasurer()
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Muted(emptyText, center = true)
        }
        return
    }
    val ys = points.map { it.second }
    val nextLines = lines
    val lo = (ys.min() * 0.9)
    val hiCandidate = (nextLines.map { it.value } + ys).max()
    val hi = maxOf(hiCandidate, ys.max()) * 1.05
    val visibleLines = lines.filter { it.value in lo..hi }
    val x0 = points.first().first.toDouble()
    val x1 = points.last().first.toDouble().let { if (it == x0) x0 + 1 else it }
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val labelW = if (visibleLines.isEmpty()) 0f else 72.dp.toPx()
        val w = size.width - labelW
        val h = size.height - 8.dp.toPx()
        fun px(t: Long) = ((t - x0) / (x1 - x0) * (w - 12.dp.toPx()) + 6.dp.toPx()).toFloat()
        fun py(v: Double) = (h - (v - lo) / (hi - lo) * (h - 12.dp.toPx())).toFloat()
        visibleLines.forEach { l ->
            val y = py(l.value)
            drawLine(l.color.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            val text = tm.measure(l.label, TextStyle(color = l.color, fontSize = 12.sp))
            drawText(text, topLeft = Offset(w + 6.dp.toPx(), y - text.size.height / 2f))
        }
        val path = Path()
        points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(px(t), py(v)) else path.lineTo(px(t), py(v)) }
        val area = Path().apply {
            addPath(path)
            lineTo(px(points.last().first), h); lineTo(px(points.first().first), h); close()
        }
        drawPath(area, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.3f), Color.Transparent), 0f, h))
        drawPath(path, Brush.horizontalGradient(listOf(c.accent, c.accent2)),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(c.bg, 6.dp.toPx(), Offset(px(last.first), py(last.second)))
        drawCircle(c.accent2, 6.dp.toPx(), Offset(px(last.first), py(last.second)), style = Stroke(3.dp.toPx()))
    }
}

/** Simple bar chart; the last bar is drawn in the accent gradient. */
@Composable
fun BarChart(values: List<Double>, description: String, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val c = LocalAscend.current
    if (values.isEmpty()) return
    val max = values.max().takeIf { it > 0 } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val n = values.size
        val gap = 6.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val bh = (v / max * size.height).toFloat().coerceAtLeast(3.dp.toPx())
            val x = i * (bw + gap)
            if (i == n - 1) drawRoundRect(Brush.verticalGradient(listOf(c.accent2, c.accent)), Offset(x, size.height - bh), Size(bw, bh), CornerRadius(6.dp.toPx()))
            else drawRoundRect(c.surface2, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(6.dp.toPx()))
        }
    }
}

@Composable
fun Ring(fraction: Float, size: Dp, stroke: Dp, centerTop: String, centerBottom: String) {
    val c = LocalAscend.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            drawArc(c.hairline, 0f, 360f, false, topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(Brush.linearGradient(listOf(c.accent, c.accent2)), -90f, 360f * fraction.coerceIn(0f, 1f), false,
                topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text(centerBottom, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
