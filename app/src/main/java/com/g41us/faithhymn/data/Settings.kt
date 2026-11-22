package com.g41us.faithhymn.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** NIGHT = warm sepia dark for evening services. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, NIGHT }
enum class PresentTheme { BLACK, GRADIENT, PHOTO }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Float = 19f,
    val keepScreenOn: Boolean = true,
    val serif: Boolean = true,
    val accent: Int = 0,
    val onboarded: Boolean = false,
    val myPart: Voice? = null,
    val showMarkings: Boolean = true,
    val favGrid: Boolean = false,
    val presentTheme: PresentTheme = PresentTheme.BLACK,
    val presentBg: String? = null,
    val presentLeader: Boolean = false,
    val presentAuto: Boolean = false,
    val iconVariant: Int = 0,
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
        accent = p.getInt("accent", 0),
        onboarded = p.getBoolean("onboarded", false),
        myPart = p.getString("my_part", null)?.let { runCatching { Voice.valueOf(it) }.getOrNull() },
        showMarkings = p.getBoolean("markings", true),
        favGrid = p.getBoolean("fav_grid", false),
        presentTheme = PresentTheme.entries.getOrElse(p.getInt("present_theme", 0)) { PresentTheme.BLACK },
        presentBg = p.getString("present_bg", null),
        presentLeader = p.getBoolean("present_leader", false),
        presentAuto = p.getBoolean("present_auto", false),
        iconVariant = p.getInt("icon", 0),
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_s.value).let { it.copy(fontSize = it.fontSize.coerceIn(12f, 44f)) }
        p.edit().putInt("theme", n.theme.ordinal).putFloat("font", n.fontSize)
            .putBoolean("screen_on", n.keepScreenOn).putBoolean("serif", n.serif).putInt("accent", n.accent)
            .putBoolean("onboarded", n.onboarded).putString("my_part", n.myPart?.name).putBoolean("markings", n.showMarkings)
            .putBoolean("fav_grid", n.favGrid).putInt("present_theme", n.presentTheme.ordinal).putString("present_bg", n.presentBg)
            .putBoolean("present_leader", n.presentLeader).putBoolean("present_auto", n.presentAuto).putInt("icon", n.iconVariant)
            .apply()
        _s.value = n
    }
}
