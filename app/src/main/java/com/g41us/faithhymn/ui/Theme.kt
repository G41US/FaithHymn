package com.g41us.faithhymn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.g41us.faithhymn.data.ThemeMode

/** iOS system palette. */
@Immutable
data class IosColors(
    val bg: Color, val card: Color, val label: Color, val secondary: Color, val tertiary: Color,
    val separator: Color, val accent: Color, val fill: Color, val red: Color, val dark: Boolean,
)

val LightIos = IosColors(
    bg = Color(0xFFF2F2F7), card = Color.White, label = Color(0xFF000000), secondary = Color(0x993C3C43),
    tertiary = Color(0x4D3C3C43), separator = Color(0x4A3C3C43), accent = Color(0xFF5856D6),
    fill = Color(0x1F787880), red = Color(0xFFFF3B30), dark = false,
)
val DarkIos = IosColors(
    bg = Color(0xFF000000), card = Color(0xFF1C1C1E), label = Color.White, secondary = Color(0x99EBEBF5),
    tertiary = Color(0x4DEBEBF5), separator = Color(0xA6545458), accent = Color(0xFF7D7AFF),
    fill = Color(0x3D787880), red = Color(0xFFFF453A), dark = true,
)

val LocalIos = staticCompositionLocalOf { LightIos }

@Composable
fun FaithHymnTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val ios = if (dark) DarkIos else LightIos
    val scheme = if (dark) darkColorScheme(primary = ios.accent, background = ios.bg, surface = ios.card, onSurface = ios.label, onBackground = ios.label)
                 else lightColorScheme(primary = ios.accent, background = ios.bg, surface = ios.card, onSurface = ios.label, onBackground = ios.label)
    androidx.compose.runtime.CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
