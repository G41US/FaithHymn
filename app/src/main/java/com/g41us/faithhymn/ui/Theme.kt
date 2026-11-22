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

/** Accent choices (Settings → Accent colour). */
val Accents = listOf(
    "Indigo" to (Color(0xFF5856D6) to Color(0xFF7D7AFF)),
    "Blue" to (Color(0xFF007AFF) to Color(0xFF0A84FF)),
    "Teal" to (Color(0xFF0F9DA8) to Color(0xFF40C8E0)),
    "Green" to (Color(0xFF248A3D) to Color(0xFF30D158)),
    "Gold" to (Color(0xFFB8860B) to Color(0xFFE5B64A)),
    "Orange" to (Color(0xFFE5701A) to Color(0xFFFF9F0A)),
    "Rose" to (Color(0xFFD12A5B) to Color(0xFFFF375F)),
    "Purple" to (Color(0xFF9B30D9) to Color(0xFFBF5AF2)),
)

private val LightBase = IosColors(
    bg = Color(0xFFF2F2F7), card = Color.White, label = Color(0xFF000000), secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43), separator = Color(0x4A3C3C43), accent = Color(0xFF5856D6),
    fill = Color(0x1F787880), red = Color(0xFFFF3B30), dark = false, bar = Color(0xCCF9F9F9),
)
private val DarkBase = IosColors(
    bg = Color(0xFF000000), card = Color(0xFF1C1C1E), label = Color.White, secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5), separator = Color(0xA6545458), accent = Color(0xFF7D7AFF),
    fill = Color(0x3D787880), red = Color(0xFFFF453A), dark = true, bar = Color(0xB31C1C1E),
)
/** Warm, low-glare dark for evening services: sepia text on brown-black. */
private val NightBase = IosColors(
    bg = Color(0xFF120E0A), card = Color(0xFF1E1812), label = Color(0xFFF1DFC2), secondary = Color(0x99F1DFC2),
    tertiary = Color(0x55F1DFC2), separator = Color(0x553A2E22), accent = Color(0xFFE0A45A),
    fill = Color(0x33A08060), red = Color(0xFFFF6B4A), dark = true, night = true, bar = Color(0xB31E1812),
)

/** Colours for choir parts: Soprano, Alto, Tenor, Bass. */
fun voiceColor(v: Voice): Color = when (v) {
    Voice.S -> Color(0xFFFF2D55); Voice.A -> Color(0xFFFF9500); Voice.T -> Color(0xFF34C759); Voice.B -> Color(0xFF007AFF)
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
