package com.g41us.faithhymn.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Float = 19f,
    val keepScreenOn: Boolean = true,
    val serif: Boolean = true,
)

class SettingsStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _s

    private fun load() = AppSettings(
        theme = ThemeMode.entries.getOrElse(p.getInt("theme", 0)) { ThemeMode.SYSTEM },
        fontSize = p.getFloat("font", 19f),
        keepScreenOn = p.getBoolean("screen_on", true),
        serif = p.getBoolean("serif", true),
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_s.value)
        p.edit().putInt("theme", n.theme.ordinal).putFloat("font", n.fontSize.coerceIn(12f, 44f))
            .putBoolean("screen_on", n.keepScreenOn).putBoolean("serif", n.serif).apply()
        _s.value = n.copy(fontSize = n.fontSize.coerceIn(12f, 44f))
    }
}
