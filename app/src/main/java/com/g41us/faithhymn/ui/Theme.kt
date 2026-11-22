package com.g41us.faithhymn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.g41us.faithhymn.data.ThemeMode
import com.g41us.faithhymn.data.Voice

/** iOS system palette. */
@Immutable
data class IosColors(
    val bg: Color, val card: Color, val label: Color, val secondary: Color, val tertiary: Color,
    val separator: Color, val accent: Color, val fill: Color, val red: Color, val dark: Boolean, val night: Boolean = false,
    val bar: Color = card,
)

/** Accent choices (Settings → Accent colour): (light, dark). Muted, ink-like tones from printed hymnals. */
val Accents = listOf(
    "Oxblood" to (Color(0xFF8A2A35) to Color(0xFFD6AE6B)),
    "Forest" to (Color(0xFF2E5B45) to Color(0xFF93C6A8)),
    "Navy" to (Color(0xFF244A75) to Color(0xFF9BBCE6)),
    "Ochre" to (Color(0xFF946318) to Color(0xFFE2B66C)),
    "Plum" to (Color(0xFF5C3560) to Color(0xFFCCA2CF)),
    "Ink" to (Color(0xFF2A2724) to Color(0xFFE8E2D8)),
)

/** Warm paper and ink. */
private val LightBase = IosColors(
    bg = Color(0xFFF5F1E9), card = Color(0xFFFFFDF8), label = Color(0xFF1E1B17), secondary = Color(0x991E1B17),
    tertiary = Color(0x4D1E1B17), separator = Color(0x1F1E1B17), accent = Color(0xFF8A2A35),
    fill = Color(0x121E1B17), red = Color(0xFFB3261E), dark = false, bar = Color(0xE6F5F1E9),
)
/** Warm charcoal, never blue-black. */
private val DarkBase = IosColors(
    bg = Color(0xFF131211), card = Color(0xFF1D1B19), label = Color(0xFFF1ECE3), secondary = Color(0x99F1ECE3),
    tertiary = Color(0x4DF1ECE3), separator = Color(0x26F1ECE3), accent = Color(0xFFD6AE6B),
    fill = Color(0x1AF1ECE3), red = Color(0xFFFF8A80), dark = true, bar = Color(0xE6131211),
)
/** Sepia for evening services: candle-lit text on brown-black. */
private val NightBase = IosColors(
    bg = Color(0xFF120E0A), card = Color(0xFF1C1712), label = Color(0xFFF0DFC3), secondary = Color(0x99F0DFC3),
    tertiary = Color(0x55F0DFC3), separator = Color(0x333A2E22), accent = Color(0xFFD9A75E),
    fill = Color(0x26A08060), red = Color(0xFFFF8A65), dark = true, night = true, bar = Color(0xE6120E0A),
)

/** Colours for choir parts: Soprano, Alto, Tenor, Bass. */
fun voiceColor(v: Voice): Color = when (v) {
    Voice.S -> Color(0xFFB54A5E); Voice.A -> Color(0xFFB07C2C); Voice.T -> Color(0xFF4E7D5B); Voice.B -> Color(0xFF41658F)
}

val LocalIos = staticCompositionLocalOf { LightBase }

fun iosColors(dark: Boolean, night: Boolean, accent: Int): IosColors {
    val base = when { night -> NightBase; dark -> DarkBase; else -> LightBase }
    if (night && accent == 0) return base       // night keeps its warm gold by default
    val (l, d) = Accents.getOrElse(accent) { Accents[0] }.second
    return base.copy(accent = if (dark) d else l)
}

@Composable
fun resolveDark(mode: ThemeMode) = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK, ThemeMode.NIGHT -> true }

@Composable
fun FaithHymnTheme(mode: ThemeMode, accent: Int, content: @Composable () -> Unit) {
    val ios = iosColors(resolveDark(mode), mode == ThemeMode.NIGHT, accent)
    val scheme = if (ios.dark) darkColorScheme(primary = ios.accent, background = ios.bg, surface = ios.card, onSurface = ios.label, onBackground = ios.label, surfaceContainer = ios.card, surfaceContainerHigh = ios.card, surfaceContainerLow = ios.card)
                 else lightColorScheme(primary = ios.accent, background = ios.bg, surface = ios.card, onSurface = ios.label, onBackground = ios.label, surfaceContainer = ios.card, surfaceContainerHigh = ios.card, surfaceContainerLow = ios.card)
    CompositionLocalProvider(LocalIos provides ios) { MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            androidx.compose.material3.ProvideTextStyle(AppTypography.bodyLarge, content)
        } }
}
