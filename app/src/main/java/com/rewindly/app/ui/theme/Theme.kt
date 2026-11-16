package com.rewindly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Identidad propia de Rewindly: tinta profunda, coral y violeta. Sin colores ni recursos de Spotify. */
object RColors {
    val Ink = Color(0xFF0E0B1A)
    val InkSoft = Color(0xFF171328)
    val Card = Color(0xFF1F1A35)
    val CardHigh = Color(0xFF2A2347)
    val Coral = Color(0xFFFF6B57)
    val Violet = Color(0xFF8B5CF6)
    val Teal = Color(0xFF2DD4BF)
    val Amber = Color(0xFFFBBF24)
    val Rose = Color(0xFFF472B6)
    val Sky = Color(0xFF60A5FA)
    val Text = Color(0xFFF4F1FF)
    val TextDim = Color(0xFFA9A3C7)
    val Warning = Color(0xFFFFB86B)
}

/** Paletas de las historias, una por personalidad musical. */
val StoryPalettes = listOf(
    listOf(Color(0xFF1B1036), Color(0xFF5B2A86), Color(0xFFFF6B57)),
    listOf(Color(0xFF2B0F2E), Color(0xFFB0306A), Color(0xFFFFB347)),
    listOf(Color(0xFF071C2C), Color(0xFF0F766E), Color(0xFF2DD4BF)),
    listOf(Color(0xFF2A1A0B), Color(0xFF9A3412), Color(0xFFFBBF24)),
    listOf(Color(0xFF05061A), Color(0xFF1E1B6B), Color(0xFF8B5CF6)),
    listOf(Color(0xFF1A0A0A), Color(0xFF7F1D1D), Color(0xFFFF6B57)),
    listOf(Color(0xFF0B1A2A), Color(0xFF1D4ED8), Color(0xFFF472B6)),
    listOf(Color(0xFF0A1F12), Color(0xFF15803D), Color(0xFFA3E635)),
    listOf(Color(0xFF1F0B2B), Color(0xFF6D28D9), Color(0xFF60A5FA))
)

private val scheme = darkColorScheme(
    primary = RColors.Coral,
    onPrimary = Color(0xFF1A0E0B),
    secondary = RColors.Violet,
    onSecondary = Color.White,
    tertiary = RColors.Teal,
    background = RColors.Ink,
    onBackground = RColors.Text,
    surface = RColors.InkSoft,
    onSurface = RColors.Text,
    surfaceVariant = RColors.Card,
    onSurfaceVariant = RColors.TextDim,
    surfaceContainer = RColors.Card,
    surfaceContainerHigh = RColors.CardHigh,
    surfaceContainerHighest = RColors.CardHigh,
    surfaceContainerLow = RColors.InkSoft,
    outline = Color(0xFF3B335E),
    outlineVariant = Color(0xFF2E2850),
    error = Color(0xFFFF7A90)
)

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.2.sp)
)

@Composable
fun RewindlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
