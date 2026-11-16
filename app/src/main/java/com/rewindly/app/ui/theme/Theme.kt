package com.rewindly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.R

/**
 * Identidad de Rewindly: monocromática y editorial.
 * Serif de titulares (Instrument Serif), sans compacta (Inter Tight) y mono para datos (JetBrains Mono).
 */
@Immutable
data class Mono(
    val bg: Color,        // fondo
    val fg: Color,        // tinta principal
    val dim: Color,       // texto secundario
    val faint: Color,     // texto terciario / ejes
    val line: Color,      // filetes de 1 dp
    val raised: Color,    // superficie apenas elevada
    val isDark: Boolean
)

val DarkMono = Mono(
    bg = Color(0xFF000000), fg = Color(0xFFFFFFFF), dim = Color(0xFF9A9A9A), faint = Color(0xFF5C5C5C),
    line = Color(0xFF262626), raised = Color(0xFF0D0D0D), isDark = true
)
val LightMono = Mono(
    bg = Color(0xFFFFFFFF), fg = Color(0xFF000000), dim = Color(0xFF5E5E5E), faint = Color(0xFF9E9E9E),
    line = Color(0xFFE3E3E3), raised = Color(0xFFF6F6F6), isDark = false
)

val LocalMono = staticCompositionLocalOf { DarkMono }

/** Acceso corto a la paleta actual. */
val mono: Mono
    @Composable @ReadOnlyComposable get() = LocalMono.current

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter_tight, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jet(w: Int) = Font(R.font.jetbrains_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)
val Sans = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))
val MonoFont = FontFamily(jet(400), jet(500), jet(700))

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 62.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Serif, fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Serif, fontSize = 42.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontSize = 26.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp)
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp), extraLarge = RoundedCornerShape(6.dp)
)

private fun schemeFor(m: Mono) = if (m.isDark) darkColorScheme(
    primary = m.fg, onPrimary = m.bg, secondary = m.fg, onSecondary = m.bg, tertiary = m.dim,
    background = m.bg, onBackground = m.fg, surface = m.bg, onSurface = m.fg,
    surfaceVariant = m.raised, onSurfaceVariant = m.dim,
    surfaceContainer = Color(0xFF111111), surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceContainerLow = m.raised, surfaceContainerLowest = m.bg, surfaceBright = Color(0xFF1C1C1C),
    outline = Color(0xFF3A3A3A), outlineVariant = m.line, error = Color(0xFFFFFFFF), onError = Color.Black,
    secondaryContainer = Color(0xFF1F1F1F), onSecondaryContainer = m.fg, primaryContainer = Color(0xFF1F1F1F), onPrimaryContainer = m.fg,
    inverseSurface = m.fg, inverseOnSurface = m.bg, scrim = Color.Black
) else lightColorScheme(
    primary = m.fg, onPrimary = m.bg, secondary = m.fg, onSecondary = m.bg, tertiary = m.dim,
    background = m.bg, onBackground = m.fg, surface = m.bg, onSurface = m.fg,
    surfaceVariant = m.raised, onSurfaceVariant = m.dim,
    surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFEDEDED), surfaceContainerHighest = Color(0xFFE6E6E6),
    surfaceContainerLow = m.raised, surfaceContainerLowest = m.bg, surfaceBright = m.bg,
    outline = Color(0xFFBDBDBD), outlineVariant = m.line, error = Color(0xFF000000), onError = Color.White,
    secondaryContainer = Color(0xFFEAEAEA), onSecondaryContainer = m.fg, primaryContainer = Color(0xFFEAEAEA), onPrimaryContainer = m.fg,
    inverseSurface = m.fg, inverseOnSurface = m.bg, scrim = Color.Black
)

@Composable
fun RewindlyTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val m = if (dark) DarkMono else LightMono
    CompositionLocalProvider(LocalMono provides m) {
        MaterialTheme(colorScheme = schemeFor(m), typography = typography, shapes = shapes, content = content)
    }
}

/** Estilos de las historias: alternan tinta y papel para dar ritmo sin color. */
@Immutable
data class StoryStyle(val bg: Color, val fg: Color)

val StoryStyles = listOf(
    StoryStyle(Color(0xFF000000), Color(0xFFFFFFFF)),
    StoryStyle(Color(0xFFFFFFFF), Color(0xFF000000)),
    StoryStyle(Color(0xFF121212), Color(0xFFFFFFFF)),
    StoryStyle(Color(0xFFEDEDE8), Color(0xFF000000))
)
