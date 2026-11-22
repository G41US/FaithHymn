package com.g41us.faithhymn.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.AudioStore
import com.g41us.faithhymn.audio.RepeatMode
import com.g41us.faithhymn.data.Hymn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(app: FaithHymnApp, close: () -> Unit, openHymn: (Int) -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by app.player.state.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    val progress by app.audio.progress.collectAsStateWithLifecycle()
    val bulk by app.audio.bulk.collectAsStateWithLifecycle()
    val available by app.catalog.available.collectAsStateWithLifecycle()
    var hymn by remember { mutableStateOf<Hymn?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var tempoSheet by remember { mutableStateOf(false) }
    var sleepSheet by remember { mutableStateOf(false) }
    var dlMenu by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(st.number) { hymn = st.number?.let { app.repo.hymn(it) } }
    LaunchedEffect(st.number) { if (st.number == null) close() }
    val n = st.number ?: return
    val grad = CoverGradients[n % CoverGradients.size]
    val blocks = hymn?.parsed.orEmpty()
    val active = if (st.durationMs > 0) blockAt(blocks, st.positionMs.toFloat() / st.durationMs) else 0

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(close) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = c.label, modifier = Modifier.size(32.dp)) }
            Text(if (st.queueSize > 1) "Playing ${st.queueIndex + 1} of ${st.queueSize}" else "Now playing", Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
            IconButton({ close(); openHymn(n) }) { Icon(Icons.Rounded.MenuBook, "Open hymn", tint = c.label) }
        }

        // Artwork ⇄ lyrics
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(showLyrics, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art") { lyr ->
                if (lyr) {
                    val scroll = rememberScrollState()
                    val pos = remember { mutableStateMapOf<Int, Float>() }
                    val density = LocalDensity.current
                    LaunchedEffect(active) { pos[active]?.let { scroll.animateScrollTo((it - with(density) { 80.dp.toPx() }).toInt().coerceAtLeast(0)) } }
                    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.card).verticalScroll(scroll).padding(16.dp)) {
                        HymnLyrics(blocks, settings.fontSize * 0.85f, settings.serif, settings.myPart, settings.showMarkings, activeBlock = active,
                            onBlockPositioned = { i, y -> pos[i] = y })
                    }
                } else {
                    val scale by animateFloatAsState(if (st.playing) 1f else 0.86f, spring(dampingRatio = 0.6f), label = "art")
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).scale(scale).shadow(16.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp)).background(grad[0]).clickable { showLyrics = true }) {
                        Box(Modifier.align(Alignment.BottomStart).padding(24.dp).width(48.dp).height(3.dp).background(Color(0xFFD9B46A)))
                        Column(Modifier.padding(24.dp)) {
                            Text("FAITHHYMNS · NO.", color = Color(0xFFF7F0E3).copy(alpha = 0.7f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                            Text("$n", color = Color(0xFFF7F0E3), fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 104.sp, lineHeight = 108.sp)
                        }
                        if (n in downloaded) Row(Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DownloadDone, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" Offline", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(st.title.substringAfter(". "), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(st.error ?: "FaithHymns · Hymn $n", fontSize = 15.sp, color = if (st.error != null) c.red else c.secondary, maxLines = 1)
                }
                Chip("Lyrics", selected = showLyrics, icon = Icons.Rounded.Lyrics) { showLyrics = !showLyrics }
            }
            Spacer(Modifier.height(14.dp))
            val dur = st.durationMs.coerceAtLeast(1)
            IosSlider(seeking ?: st.positionMs.toFloat(), { seeking = it }, 0f..dur.toFloat(), onDone = { seeking?.let { app.player.seek(it.toLong()) }; seeking = null })
            Row {
                Text(fmtTime((seeking ?: st.positionMs.toFloat()).toLong()), fontSize = 12.sp, color = c.secondary); Spacer(Modifier.weight(1f))
                Text("-" + fmtTime((st.durationMs - (seeking ?: st.positionMs.toFloat()).toLong()).coerceAtLeast(0)), fontSize = 12.sp, color = c.secondary)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Ctl(Icons.Rounded.Replay10) { app.player.skip(-10_000) }
                Ctl(Icons.Rounded.SkipPrevious, 40) { app.player.prev() }
                Box(Modifier.size(74.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(c.accent).clickable { app.player.toggle() }, contentAlignment = Alignment.Center) {
                    if (st.buffering) CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 3.dp)
                    else Icon(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(42.dp))
                }
                Ctl(Icons.Rounded.SkipNext, 40, enabled = st.hasNext) { app.player.next() }
                Ctl(Icons.Rounded.Forward10) { app.player.skip(10_000) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool(when (st.repeat) { RepeatMode.ONE -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat },
                    when (st.repeat) { RepeatMode.OFF -> "Repeat"; RepeatMode.ALL -> "Repeat all"; RepeatMode.ONE -> "Repeat one" }, st.repeat != RepeatMode.OFF) { app.player.cycleRepeat() }
                Tool(Icons.Rounded.Tune, if (st.speed != 1f || st.pitch != 0f) "${"%.2f".format(st.speed).trimEnd('0').trimEnd('.')}x ${if (st.pitch > 0) "+" else ""}${if (st.pitch != 0f) st.pitch.toInt() else ""}" else "Tempo & key",
                    st.speed != 1f || st.pitch != 0f) { tempoSheet = true }
                Tool(Icons.Rounded.Bedtime, when { st.sleepEndOfHymn -> "End of hymn"; st.sleepEndsAt != null -> fmtTime(st.sleepEndsAt!! - System.currentTimeMillis()); else -> "Sleep" },
                    st.sleepEndsAt != null || st.sleepEndOfHymn) { sleepSheet = true }
                Box {
                    Tool(when { progress[n] != null || bulk != null -> Icons.Rounded.Downloading; n in downloaded -> Icons.Rounded.DownloadDone; else -> Icons.Rounded.Download },
                        bulk?.let { "${it.first}/${it.second}" } ?: if (n in downloaded) "Saved" else "Save", n in downloaded) { dlMenu = true }
                    DropdownMenu(dlMenu, { dlMenu = false }, containerColor = c.card) {
                        if (n !in downloaded) DropdownMenuItem({ Text("Save this hymn offline") }, {
                            dlMenu = false
                            scope.launch { if (app.audio.download(n) == AudioStore.Result.NotFound) Toast.makeText(ctx, "No recording for this hymn yet", Toast.LENGTH_SHORT).show() }
                        }, trailingIcon = { Icon(Icons.Rounded.Download, null) })
                        else DropdownMenuItem({ Text("Remove offline copy") }, { dlMenu = false; app.audio.delete(n) }, trailingIcon = { Icon(Icons.Rounded.Delete, null) })
                        if (bulk == null) DropdownMenuItem({ Text("Save all recordings (${available.size})") }, {
                            dlMenu = false
                            scope.launch { val ok = app.audio.downloadAll(available); Toast.makeText(ctx, "Saved $ok recordings", Toast.LENGTH_SHORT).show() }
                        }, enabled = available.isNotEmpty(), trailingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                        else DropdownMenuItem({ Text("Stop saving all") }, { dlMenu = false; app.audio.cancelDownloadAll() }, trailingIcon = { Icon(Icons.Rounded.Stop, null) })
                    }
                }
            }
        }
    }

    if (tempoSheet) ModalBottomSheet({ tempoSheet = false }, containerColor = c.bg) {
        var speed by remember { mutableFloatStateOf(st.speed) }
        var pitch by remember { mutableFloatStateOf(st.pitch) }
        Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
            BottomSheetTitle("Tempo & key")
            Text("For choir practice: slow a hymn down to learn it, or move it into your key.", fontSize = 14.sp, color = c.secondary, modifier = Modifier.padding(horizontal = 20.dp))
            SectionHeader("Tempo · ${"%.2f".format(speed)}x")
            Group { Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SlowMotionVideo, null, tint = c.secondary)
                IosSlider(speed, { speed = it }, 0.5f..1.5f, Modifier.weight(1f).padding(horizontal = 10.dp), steps = 0.05f, onDone = { app.player.setTempoPitch(speed, pitch) })
                Icon(Icons.Rounded.Speed, null, tint = c.secondary)
            } }
            SectionHeader("Key · ${if (pitch > 0) "+" else ""}${pitch.toInt()} semitone${if (kotlin.math.abs(pitch.toInt()) == 1) "" else "s"}")
            Group { Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♭", fontSize = 22.sp, color = c.secondary)
                IosSlider(pitch, { pitch = it }, -6f..6f, Modifier.weight(1f).padding(horizontal = 10.dp), steps = 1f, onDone = { app.player.setTempoPitch(speed, pitch) })
                Text("♯", fontSize = 22.sp, color = c.secondary)
            } }
            TextButton({ speed = 1f; pitch = 0f; app.player.setTempoPitch(1f, 0f) }, Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)) { Text("Reset", color = c.accent, fontSize = 16.sp) }
        }
    }
    if (sleepSheet) ModalBottomSheet({ sleepSheet = false }, containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
            BottomSheetTitle("Sleep timer")
            Group {
                listOf(5, 10, 15, 30, 45, 60).forEachIndexed { i, m ->
                    ListRow("$m minutes", onClick = { app.player.sleepIn(m); sleepSheet = false })
                    Separator()
                }
                ListRow("End of this hymn", onClick = { app.player.sleepAtEndOfHymn(); sleepSheet = false })
                if (st.sleepEndsAt != null || st.sleepEndOfHymn) { Separator(); ListRow("Turn off timer", titleColor = c.red, onClick = { app.player.cancelSleep(); sleepSheet = false }) }
            }
        }
    }
}

@Composable
private fun Ctl(icon: ImageVector, size: Int = 30, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalIos.current
    IconButton(onClick, enabled = enabled, modifier = Modifier.size((size + 16).dp)) {
        Icon(icon, null, tint = if (enabled) c.label else c.tertiary, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalIos.current
    Column(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (on) c.accent else c.secondary, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = if (on) c.accent else c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
