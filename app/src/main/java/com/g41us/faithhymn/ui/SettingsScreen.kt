package com.g41us.faithhymn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.BuildConfig
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.data.ThemeMode

@Composable
fun SettingsScreen(app: FaithHymnApp) {
    val c = LocalIos.current
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val mb = remember(downloaded) { app.audio.totalBytes() / 1_048_576.0 }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        LargeTitle("Settings")

        SectionHeader("Appearance")
        Group {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", fontSize = 17.sp, color = c.label); Spacer(Modifier.height(8.dp))
                Segmented(listOf("Automatic", "Light", "Dark"), s.theme.ordinal, { i -> app.settings.update { it.copy(theme = ThemeMode.entries[i]) } })
            }
            Separator()
            ListRow("Classic serif text", icon = { IconTile(Icons.Rounded.TextFields, Color(0xFFFF9500)) },
                trailing = { Switch(s.serif, { v -> app.settings.update { it.copy(serif = v) } }, colors = iosSwitch()) })
        }

        SectionHeader("Text size")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp, color = c.secondary)
                    Slider(s.fontSize, { v -> app.settings.update { it.copy(fontSize = v) } }, valueRange = 12f..44f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.fill))
                    Text("A", fontSize = 26.sp, color = c.secondary)
                }
                Text("Praise to the Lord, the Almighty,\nthe King of creation!", fontSize = s.fontSize.sp, lineHeight = (s.fontSize * 1.45f).sp,
                    color = c.label, fontFamily = if (s.serif) FontFamily.Serif else FontFamily.Default)
                Spacer(Modifier.height(4.dp))
                Text("Tip: pinch on any hymn to resize.", fontSize = 13.sp, color = c.secondary)
            }
        }

        SectionHeader("Reading")
        Group {
            ListRow("Keep screen on", subtitle = "While a hymn is open", icon = { IconTile(Icons.Rounded.LightMode, Color(0xFF34C759)) },
                trailing = { Switch(s.keepScreenOn, { v -> app.settings.update { it.copy(keepScreenOn = v) } }, colors = iosSwitch()) })
        }

        SectionHeader("Offline audio")
        Group {
            ListRow("Saved recordings", icon = { IconTile(Icons.Rounded.Download, c.accent) }, value = "${downloaded.size} · ${"%.1f".format(mb)} MB")
            if (downloaded.isNotEmpty()) {
                Separator(59)
                ListRow("Remove all downloads", titleColor = c.red, icon = { IconTile(Icons.Rounded.Delete, c.red) }, onClick = { confirm = true })
            }
        }

        SectionHeader("About")
        Group {
            ListRow("Version", icon = { IconTile(Icons.Rounded.Info, Color(0xFF8E8E93)) }, value = BuildConfig.VERSION_NAME)
            Separator(59)
            ListRow("Hymns in the book", icon = { IconTile(Icons.Rounded.MenuBook, Color(0xFF007AFF)) }, value = "1,001")
        }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Remove all downloads?") }, text = { Text("You can download them again any time.") },
        confirmButton = { TextButton({ confirm = false; app.audio.deleteAll() }) { Text("Remove", color = c.red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

@Composable
private fun iosSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759), checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = LocalIos.current.fill, uncheckedBorderColor = Color.Transparent,
)
