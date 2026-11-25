package com.ascend.fitness.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ascend.fitness.R

@Immutable
data class Palette(val name: String, val a: Color, val b: Color)

val PALETTES = listOf(
    Palette("Ember", Color(0xFFFF4D1F), Color(0xFFFFA03D)),
    Palette("Volt", Color(0xFFC2F53D), Color(0xFF3DE896)),
    Palette("Tide", Color(0xFF12D1AE), Color(0xFF2E9BFF)),
)

@Immutable
data class AscendColors(
    val palette: Palette,
    val bg: Color = Color(0xFF0B0B0C),
    val surface: Color = Color(0xFF161618),
    val surface2: Color = Color(0xFF222226),
    val hairline: Color = Color(0xFF26262A),
    val ink: Color = Color(0xFFF4F3F0),
    val muted: Color = Color(0xFFA3A3AA),
    val onAccent: Color = Color(0xFF0B0B0C),
    val good: Color = Color(0xFF3DDC84),
    val warn: Color = Color(0xFFFFC93C),
    val bad: Color = Color(0xFFFF5A5F),
) {
    val accent get() = palette.a
    val accent2 get() = palette.b
    val soft get() = palette.a.copy(alpha = 0.15f)
    val grad get() = Brush.linearGradient(listOf(palette.a, palette.b))
    val hgrad get() = Brush.horizontalGradient(listOf(palette.a, palette.b))
}

val LocalAscend = staticCompositionLocalOf { AscendColors(PALETTES[0]) }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) =
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Display = FontFamily(variable(R.font.bricolage, 500), variable(R.font.bricolage, 600))
val Body = FontFamily(variable(R.font.geist, 400), variable(R.font.geist, 500))

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 32.sp, letterSpacing = (-0.03).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 28.sp, letterSpacing = (-0.03).em),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 24.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 13.sp),
)

@Composable
fun AscendTheme(themeName: String, content: @Composable () -> Unit) {
    val palette = PALETTES.firstOrNull { it.name == themeName } ?: PALETTES[0]
    val c = AscendColors(palette)
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, secondary = c.accent2,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.hairline, error = c.bad,
    )
    CompositionLocalProvider(LocalAscend provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
