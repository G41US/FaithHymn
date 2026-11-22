package com.g41us.faithhymn.ui

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.AppSettings
import com.g41us.faithhymn.data.Hymn
import com.g41us.faithhymn.data.HymnBlock
import com.g41us.faithhymn.data.HymnFormat
import com.g41us.faithhymn.data.HymnSummary
import com.g41us.faithhymn.data.PresentTheme
import kotlinx.coroutines.delay

private data class Slide(val hymn: Hymn, val index: Int, val block: HymnBlock) {
    /** Congregation text: words only (parts and dynamics are for the choir). */
    val text: String get() = block.lines.joinToString("\n") { l -> l.segments.filter { it.dynamic == null }.joinToString("") { it.text }.trim() }
        .let { if (index == 0) HymnFormat.softenFirstWord(it) else it }
}

/**
 * One verse at a time — for a projector or TV. Tap right = next, left = previous.
 * With a TV connected as a second screen (Miracast / HDMI / wireless display), slides go to the TV and the phone becomes the remote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentScreen(app: FaithHymnApp, start: Int, playlistId: Long?, close: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val player by app.player.state.collectAsStateWithLifecycle()
    var slides by remember { mutableStateOf<List<Slide>>(emptyList()) }
    var hymns by remember { mutableStateOf<List<Hymn>>(emptyList()) }
    var i by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(false) }
    val bg = rememberBackground(settings.presentBg)

    LaunchedEffect(Unit) {
        val numbers = playlistId?.let { id -> app.repo.playlistHymns(id).map { it.number } }?.let { l -> l.drop(l.indexOf(start).coerceAtLeast(0)) } ?: listOf(start)
        hymns = numbers.mapNotNull { app.repo.hymn(it) }
        slides = hymns.flatMap { h -> h.parsed.mapIndexed { idx, b -> Slide(h, idx, b) } }
    }

    // Auto-advance: follow the recording.
    LaunchedEffect(settings.presentAuto, hymns) {
        if (settings.presentAuto && hymns.isNotEmpty() && player.number !in hymns.map { it.number })
            app.player.play(hymns.map { HymnSummary(it.number, it.title, it.firstLine) })
    }
    if (settings.presentAuto && player.durationMs > 0) {
        val h = hymns.firstOrNull { it.number == player.number }
        if (h != null) {
            val target = slides.indexOfFirst { it.hymn.number == h.number } + blockAt(h.parsed, player.positionMs.toFloat() / player.durationMs)
            LaunchedEffect(target) { if (target >= 0) i = target }
        }
    }

    // External display (TV)
    val tv = rememberPresentationDisplay()
    val current = slides.getOrNull(i); val next = slides.getOrNull(i + 1)
    val slideState = rememberUpdatedState(Triple(current, next, settings))
    DisposableEffect(tv, bg) {
        val p = tv?.let { d -> SlidePresentation(activity, d) { val (cur, _, s) = slideState.value; SlideView(cur, null, s, bg, leader = false, big = true) }.also { it.show() } }
        onDispose { p?.dismiss() }
    }

    DisposableEffect(Unit) {
        val w = (ctx as Activity).window
        val ctl = WindowCompat.getInsetsController(w, w.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { ctl.show(WindowInsetsCompat.Type.systemBars()); w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(onBack = close)
    LaunchedEffect(showUi, i) { if (showUi && tv == null) { delay(3000); showUi = false } }

    fun go(d: Int) { i = (i + d).coerceIn(0, (slides.size - 1).coerceAtLeast(0)); showUi = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (tv != null) RemoteView(current, next, i, slides.size, tv.name, ::go)
        else Box(Modifier.fillMaxSize().pointerInput(slides.size) {
            detectTapGestures { o -> if (o.x > size.width / 2) go(1) else go(-1) }
        }) {
            AnimatedContent(i, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "slide", modifier = Modifier.fillMaxSize()) { idx ->
                SlideView(slides.getOrNull(idx), slides.getOrNull(idx + 1), settings, bg, leader = settings.presentLeader, big = false)
            }
        }
        if (showUi || tv != null) Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
            if (tv == null) IconButton({ ctx.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Icon(Icons.Rounded.Cast, "Show on TV", tint = Color.White) }
            IconButton({ sheet = true }) { Icon(Icons.Rounded.Tune, "Present settings", tint = Color.White) }
            IconButton(close) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
        }
    }
    if (sheet) PresentSettingsSheet(app, settings, tvName = tv?.name) { sheet = false }
}

@Composable
private fun SlideView(s: Slide?, next: Slide?, settings: AppSettings, bg: Bitmap?, leader: Boolean, big: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (settings.presentTheme) {
            PresentTheme.BLACK -> {}
            PresentTheme.GRADIENT -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B1C6B), Color(0xFF0B0820), Color(0xFF3A1340)))))
            PresentTheme.PHOTO -> if (bg != null) {
                Image(bg.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }
        if (s == null) return@Box
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = if (big) 40.dp else 48.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val lines = s.text.lines()
                val byHeight = maxHeight.value / (lines.size.coerceAtLeast(1) * 1.45f)
                val byWidth = maxWidth.value / (lines.maxOf { it.length }.coerceAtLeast(12) * 0.5f)
                val fs = minOf(byHeight, byWidth, if (big) 80f else 56f).coerceAtLeast(16f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s.block.kind == com.g41us.faithhymn.data.BlockKind.CHORUS)
                        Text("CHORUS", color = Color.White.copy(alpha = 0.5f), fontSize = (fs * 0.35f).coerceAtLeast(11f).sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(s.text, color = Color.White, fontSize = fs.sp, lineHeight = (fs * 1.35f).sp, textAlign = TextAlign.Center, fontFamily = Serif)
                }
            }
            if (leader && next != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp)) {
                Text(if (next.hymn.number != s.hymn.number) "NEXT · HYMN ${next.hymn.number}" else "NEXT", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(next.text.lines().take(2).joinToString("\n"), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, fontFamily = Serif, maxLines = 2)
            }
            Text("${s.hymn.number} · ${s.hymn.title.titleCase()}  —  ${s.index + 1}/${s.hymn.parsed.size}",
                color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
        }
    }
}

/** Phone becomes a remote while the TV shows the slides. */
@Composable
private fun RemoteView(cur: Slide?, next: Slide?, i: Int, total: Int, tvName: String, go: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF111114)).safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tv, null, tint = Color(0xFF34C759)); Text("  On $tvName", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text("NOW · ${i + 1}/$total", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(cur?.text ?: "", color = Color.White, fontSize = 22.sp, fontFamily = Serif, lineHeight = 30.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (next != null) {
            Text("NEXT", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(next.text.lines().take(3).joinToString("\n"), color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontFamily = Serif, maxLines = 3)
        }
        Row(Modifier.padding(top = 16.dp).height(96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { go(-1) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronLeft, "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Box(Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0xFF5856D6)).clickable { go(1) }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Next", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Icon(Icons.Rounded.ChevronRight, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresentSettingsSheet(app: FaithHymnApp, s: AppSettings, tvName: String?, onDismiss: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            app.settings.update { it.copy(presentBg = uri.toString(), presentTheme = PresentTheme.PHOTO) }
        }
    }
    ModalBottomSheet(onDismiss, containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
            BottomSheetTitle("Presentation")
            SectionHeader("Background")
            Group {
                Column(Modifier.padding(16.dp)) {
                    Segmented(listOf("Black", "Gradient", "Photo"), s.presentTheme.ordinal, { t ->
                        if (t == 2 && s.presentBg == null) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        else app.settings.update { it.copy(presentTheme = PresentTheme.entries[t]) }
                    })
                }
                if (s.presentTheme == PresentTheme.PHOTO) { Separator(); ListRow("Choose photo…", titleColor = c.accent, onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) }
            }
            SectionHeader("Display")
            Group {
                ListRow("Leader view", subtitle = "Show the next verse at the bottom", icon = { IconTile(Icons.Rounded.Visibility, Color(0xFF34C759)) },
                    trailing = { Switch(s.presentLeader, { v -> app.settings.update { it.copy(presentLeader = v) } }, colors = iosSwitch()) })
                Separator(59)
                ListRow("Follow the recording", subtitle = "Plays the audio and moves verses along", icon = { IconTile(Icons.Rounded.Headphones, c.accent) },
                    trailing = { Switch(s.presentAuto, { v -> app.settings.update { it.copy(presentAuto = v) } }, colors = iosSwitch()) })
            }
            SectionHeader("TV")
            Group {
                ListRow(tvName?.let { "Connected: $it" } ?: "Show on a TV", subtitle = if (tvName == null) "Connect with Smart View / Cast screen or HDMI. Slides go to the TV, your phone becomes the remote." else null,
                    icon = { IconTile(Icons.Rounded.Cast, Color(0xFF007AFF)) }, chevron = tvName == null,
                    onClick = if (tvName == null) ({ ctx.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) else null)
            }
        }
    }
}

@Composable
fun iosSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759), checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White, uncheckedTrackColor = LocalIos.current.fill, uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun rememberBackground(uri: String?): Bitmap? {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = uri?.let { u -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it, null, o) }
                var sample = 1; while (o.outWidth / sample > 2400) sample *= 2
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        } }
    }
    return bmp
}

/** Current presentation display (TV / projector), updates when one connects or disconnects. */
@Composable
private fun rememberPresentationDisplay(): Display? {
    val ctx = LocalContext.current
    val dm = remember { ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    fun find() = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
    var d by remember { mutableStateOf(find()) }
    DisposableEffect(Unit) {
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { d = find() }
            override fun onDisplayRemoved(id: Int) { d = find() }
            override fun onDisplayChanged(id: Int) { d = find() }
        }
        dm.registerDisplayListener(l, null)
        onDispose { dm.unregisterDisplayListener(l) }
    }
    return d
}

private class SlidePresentation(private val owner: ComponentActivity, display: Display, private val content: @Composable () -> Unit) : Presentation(owner, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.decorView?.let { it.setViewTreeLifecycleOwner(owner); it.setViewTreeSavedStateRegistryOwner(owner) }
        setContentView(ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
            setContent { content() }
        })
    }
}
