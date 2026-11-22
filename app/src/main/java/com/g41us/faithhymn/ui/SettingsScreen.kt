package com.g41us.faithhymn.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.R
import com.g41us.faithhymn.data.ThemeMode
import com.g41us.faithhymn.data.Voice
import com.g41us.faithhymn.update.UpdateState
import kotlinx.coroutines.launch

private const val APP_LINK = "https://github.com/G41US/FaithHymn/releases/latest"

/** Launcher icon variants: activity-alias name, label, gradient. */
private val IconVariants = listOf(
    Triple(".IconClassic", "Classic", listOf(Color(0xFF8E6CFF), Color(0xFF4A3AE0))),
    Triple(".IconGold", "Gold", listOf(Color(0xFFF6C25B), Color(0xFFC27A12))),
    Triple(".IconMidnight", "Midnight", listOf(Color(0xFF2C2C3A), Color(0xFF0B0B12))),
)

fun setLauncherIcon(ctx: Context, variant: Int) {
    val pm = ctx.packageManager
    IconVariants.forEachIndexed { i, (alias, _, _) ->
        pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "com.g41us.faithhymn$alias"),
            if (i == variant) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: FaithHymnApp) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    val available by app.catalog.available.collectAsStateWithLifecycle()
    val bulk by app.audio.bulk.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var shareApp by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(s.fontSize) }
    val mb = remember(downloaded) { app.audio.totalBytes() / 1_048_576.0 }
    LaunchedEffect(Unit) { if (app.updater.state.value is UpdateState.Idle) app.updater.check() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = LocalBarPadding.current + 24.dp)) {
        LargeTitle("Settings")

        SectionHeader("Appearance")
        Group {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", fontSize = 17.sp, color = c.label); Spacer(Modifier.height(8.dp))
                Segmented(listOf("Auto", "Light", "Dark", "Night"), s.theme.ordinal, { i -> app.settings.update { it.copy(theme = ThemeMode.entries[i]) } })
                if (s.theme == ThemeMode.NIGHT) Text("Warm sepia on dark — easy on the eyes in evening services.", fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
            }
            Separator()
            Column(Modifier.padding(16.dp)) {
                Text("Accent colour", fontSize = 17.sp, color = c.label); Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accents.forEachIndexed { i, (name, pair) ->
                        val col = if (c.dark) pair.second else pair.first
                        Box(Modifier.size(34.dp).clip(CircleShape).background(col).clickable { app.settings.update { it.copy(accent = i) } }
                            .border(if (s.accent == i) 3.dp else 0.dp, if (s.accent == i) c.label.copy(alpha = 0.35f) else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) { if (s.accent == i) Icon(Icons.Rounded.Check, name, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            Separator()
            ListRow("Classic serif text", icon = { IconTile(Icons.Rounded.TextFields, Color(0xFFFF9500)) },
                trailing = { Switch(s.serif, { v -> app.settings.update { it.copy(serif = v) } }, colors = iosSwitch()) })
        }

        SectionHeader("App icon")
        Group {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconVariants.forEachIndexed { i, (_, label, grad) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        if (s.iconVariant != i) { app.settings.update { it.copy(iconVariant = i) }; setLauncherIcon(ctx, i) }
                    }) {
                        Box(Modifier.size(62.dp).shadow(if (s.iconVariant == i) 8.dp else 2.dp, RoundedCornerShape(15.dp)).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(grad))
                            .border(if (s.iconVariant == i) 3.dp else 0.dp, if (s.iconVariant == i) c.accent else Color.Transparent, RoundedCornerShape(15.dp))) {
                            Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.fillMaxSize().padding(0.dp))
                        }
                        Text(label, fontSize = 13.sp, color = if (s.iconVariant == i) c.accent else c.secondary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        SectionHeader("Text size")
        Group {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp, color = c.secondary)
                    IosSlider(size, { size = it }, 12f..44f, Modifier.weight(1f).padding(horizontal = 12.dp), steps = 1f,
                        onDone = { app.settings.update { it.copy(fontSize = size) } })
                    Text("A", fontSize = 26.sp, color = c.secondary)
                }
                Text("Praise to the Lord, the Almighty,\nthe King of creation!", fontSize = size.sp, lineHeight = (size * 1.45f).sp,
                    color = c.label, fontFamily = if (s.serif) FontFamily.Serif else FontFamily.Default)
                Spacer(Modifier.height(4.dp))
                Text("Tip: pinch on any hymn to resize.", fontSize = 13.sp, color = c.secondary)
            }
        }

        SectionHeader("Reading")
        Group {
            ListRow("Keep screen on", subtitle = "While a hymn is open", icon = { IconTile(Icons.Rounded.LightMode, Color(0xFF34C759)) },
                trailing = { Switch(s.keepScreenOn, { v -> app.settings.update { it.copy(keepScreenOn = v) } }, colors = iosSwitch()) })
            Separator(59)
            ListRow("Show music markings", subtitle = "Parts (Sop, ATB…) and dynamics (p, mf…)", icon = { IconTile(Icons.Rounded.MusicNote, Color(0xFFFF2D55)) },
                trailing = { Switch(s.showMarkings, { v -> app.settings.update { it.copy(showMarkings = v) } }, colors = iosSwitch()) })
            Separator(59)
            Column(Modifier.padding(16.dp)) {
                Text("My voice part", fontSize = 17.sp, color = c.label)
                Text("Other parts are dimmed so yours stands out", fontSize = 13.sp, color = c.secondary)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("All", "Soprano", "Alto", "Tenor", "Bass"), s.myPart?.let { it.ordinal + 1 } ?: 0,
                    { i -> app.settings.update { it.copy(myPart = if (i == 0) null else Voice.entries[i - 1]) } })
            }
        }

        SectionHeader("Offline audio")
        Group {
            ListRow("Saved recordings", icon = { IconTile(Icons.Rounded.DownloadDone, c.accent) }, value = "${downloaded.size} · ${"%.1f".format(mb)} MB")
            Separator(59)
            val b = bulk
            if (b != null) Column(Modifier.padding(16.dp)) {
                Row { Text("Saving ${b.first} of ${b.second}…", color = c.label, modifier = Modifier.weight(1f)); Text("Stop", color = c.red, modifier = Modifier.clickable { app.audio.cancelDownloadAll() }) }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { if (b.second == 0) 0f else b.first.toFloat() / b.second }, color = c.accent, trackColor = c.fill, modifier = Modifier.fillMaxWidth())
            } else ListRow("Save all recordings", subtitle = if (available.isEmpty()) "No recordings uploaded yet" else "${(available - downloaded).size} not saved yet",
                titleColor = if (available.isEmpty()) c.tertiary else c.accent, icon = { IconTile(Icons.Rounded.CloudDownload, Color(0xFF007AFF)) },
                onClick = if (available.isEmpty()) null else ({ scope.launch { app.audio.downloadAll(available) } }))
            if (downloaded.isNotEmpty()) {
                Separator(59)
                ListRow("Remove all downloads", titleColor = c.red, icon = { IconTile(Icons.Rounded.Delete, c.red) }, onClick = { confirm = true })
            }
        }

        SectionHeader("App updates")
        UpdateGroup(app)

        SectionHeader("Share")
        Group {
            ListRow("Share FaithHymns", subtitle = "QR code and link for church members", icon = { IconTile(Icons.Rounded.QrCode2, Color(0xFF5856D6)) }, chevron = true, onClick = { shareApp = true })
        }

        SectionHeader("About")
        Group {
            ListRow("Hymns in the book", icon = { IconTile(Icons.Rounded.MenuBook, Color(0xFF007AFF)) }, value = "1,001")
            Separator(59)
            ListRow("Show the welcome tour again", icon = { IconTile(Icons.Rounded.AutoAwesome, Color(0xFFFF9500)) }, onClick = { app.settings.update { it.copy(onboarded = false) } })
        }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Remove all downloads?") }, text = { Text("You can download them again any time.") },
        confirmButton = { TextButton({ confirm = false; app.audio.deleteAll() }) { Text("Remove", color = c.red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
    if (shareApp) ModalBottomSheet({ shareApp = false }, containerColor = c.bg) {
        val qr = remember { qrImage(APP_LINK) }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BottomSheetTitle("Share FaithHymns")
            Box(Modifier.size(230.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) { Image(qr, "QR code", Modifier.fillMaxSize()) }
            Text("Scan to download the latest version", fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(12.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Send link", Icons.Rounded.Send, Modifier.weight(1f)) {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Get FaithHymns — our hymn book on your phone (Android): $APP_LINK"), "Share FaithHymns"))
                }
                PillButton("Send QR", Icons.Rounded.QrCode2, Modifier.weight(1f)) { shareBitmap(ctx, qrBitmap(APP_LINK, 900), "faithhymns_qr", "Share QR code") }
            }
        }
    }
}

@Composable
private fun UpdateGroup(app: FaithHymnApp) {
    val c = LocalIos.current
    val scope = rememberCoroutineScope()
    val st by app.updater.state.collectAsStateWithLifecycle()
    // Re-check install permission when the user comes back from system settings.
    var canInstall by remember { mutableStateOf(app.updater.canInstall()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) canInstall = app.updater.canInstall() }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    Group {
        ListRow("Current version", icon = { IconTile(Icons.Rounded.Info, Color(0xFF8E8E93)) }, value = app.updater.currentVersionName)
        Separator(59)
        when (val s = st) {
            UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Error -> {
                ListRow("Check for updates", titleColor = c.accent, icon = { IconTile(Icons.Rounded.SystemUpdate, c.accent) },
                    subtitle = when (s) { UpdateState.UpToDate -> "You're on the latest version"; is UpdateState.Error -> s.message; else -> null },
                    onClick = { scope.launch { app.updater.check() } })
            }
            UpdateState.Checking -> ListRow("Checking…", icon = { IconTile(Icons.Rounded.SystemUpdate, c.accent) },
                trailing = { CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp) })
            is UpdateState.Available -> Column(Modifier.padding(16.dp)) {
                Text("Version ${s.release.versionName} is available", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                if (s.release.notes.isNotBlank()) Text(s.release.notes.take(400), fontSize = 14.sp, color = c.secondary, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Button({ scope.launch { app.updater.download(s.release) } }, Modifier.fillMaxWidth().height(46.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent)) {
                    Text("Update now" + if (s.release.sizeBytes > 0) " (${"%.1f".format(s.release.sizeBytes / 1_048_576.0)} MB)" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            is UpdateState.Downloading -> Column(Modifier.padding(16.dp)) {
                Text("Downloading ${s.release.versionName}… ${(s.progress * 100).toInt()}%", fontSize = 15.sp, color = c.label)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.progress }, color = c.accent, trackColor = c.fill, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.ReadyToInstall -> Column(Modifier.padding(16.dp)) {
                Text(if (canInstall) "Version ${s.release.versionName} is ready" else "Allow FaithHymns to install updates",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
                if (!canInstall) Text("One-time step: turn on \"Allow from this source\", then come back.", fontSize = 14.sp, color = c.secondary, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Button({ if (canInstall) app.updater.install(s.file) else app.updater.openInstallPermission() }, Modifier.fillMaxWidth().height(46.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent)) {
                    Text(if (canInstall) "Install update" else "Open settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
