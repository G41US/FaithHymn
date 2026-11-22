package com.g41us.faithhymn.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.AudioStore
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn
import com.g41us.faithhymn.data.HymnSummary
import com.g41us.faithhymn.data.Playlist
import kotlinx.coroutines.launch

/** Swipe left/right between hymns. Inside a playlist, swipes go through the playlist only. */
@Composable
fun HymnScreen(app: FaithHymnApp, start: Int, playlistId: Long?, back: () -> Unit, present: (Int, Long?) -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val changes by app.repo.changes.collectAsStateWithLifecycle()

    var order by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(playlistId) {
        order = playlistId?.let { id -> app.repo.playlistHymns(id).map { it.number } }?.takeIf { start in it } ?: (1..app.repo.count).toList()
    }
    val ord = order ?: return
    val pager = rememberPagerState(initialPage = ord.indexOf(start).coerceAtLeast(0)) { ord.size }
    val current = ord[pager.currentPage]

    var hymn by remember { mutableStateOf<Hymn?>(null) }
    var fav by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    LaunchedEffect(current) { hymn = app.repo.hymn(current); app.repo.markOpened(current) }
    LaunchedEffect(current, changes) { fav = app.repo.isFavourite(current) }

    if (settings.keepScreenOn) DisposableEffect(Unit) {
        val w = (ctx as Activity).window
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(Modifier.fillMaxSize().background(c.card)) {
        NavBar("Hymn $current", back, if (playlistId != null) "Playlist" else "Back") {
            IconButton({ scope.launch { app.repo.toggleFavourite(current) } }) {
                Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (fav) c.red else c.accent)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = c.accent) }
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    MenuItem("Present on screen", Icons.Rounded.Slideshow) { menu = false; present(current, playlistId) }
                    MenuItem("Add to playlist", Icons.Rounded.PlaylistAdd) { menu = false; addTo = true }
                    MenuItem("Share as image", Icons.Rounded.Image) { menu = false; hymn?.let { shareImage(ctx, it, it.blocks.firstOrNull() ?: it.content) } }
                    MenuItem("Share text", Icons.Rounded.IosShare) { menu = false; hymn?.let { shareText(ctx, it) } }
                }
            }
        }
        Separator(0)
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { ord[it] }) { page ->
            HymnPage(app, ord[page], settings.fontSize, settings.serif)
        }
        AudioBar(app, current, hymn, ord, playlistId != null)
    }

    if (addTo) PlaylistPicker(app, onDismiss = { addTo = false }) { p ->
        addTo = false
        scope.launch { app.repo.addToPlaylist(p.id, current) }
        Toast.makeText(ctx, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalIos.current
    DropdownMenuItem({ Text(text, color = c.label) }, onClick, trailingIcon = { Icon(icon, null, tint = c.label) })
}

@Composable
private fun HymnPage(app: FaithHymnApp, n: Int, fontSize: Float, serif: Boolean) {
    val c = LocalIos.current
    var hymn by remember { mutableStateOf<Hymn?>(null) }
    LaunchedEffect(n) { hymn = app.repo.hymn(n) }
    val h = hymn ?: return
    var size by remember(fontSize) { mutableFloatStateOf(fontSize) }
    val family = if (serif) FontFamily.Serif else FontFamily.Default

    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                // Pinch to zoom text (two fingers only, so swiping still pages and scrolling still scrolls).
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (e.changes.size >= 2) {
                            val a = e.changes[0]; val b = e.changes[1]
                            val prev = (a.previousPosition - b.previousPosition).getDistance()
                            val now = (a.position - b.position).getDistance()
                            if (prev > 0f) {
                                size = (size * now / prev).coerceIn(12f, 44f)
                                e.changes.forEach { it.consume() }
                            }
                        } else if (e.changes.all { !it.pressed } && size != fontSize) {
                            app.settings.update { s -> s.copy(fontSize = size) }
                        }
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text("HYMN ${h.number}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(h.title.titleCase(), fontSize = (size * 1.35f).sp, fontWeight = FontWeight.Bold, color = c.label, fontFamily = family, lineHeight = (size * 1.6f).sp)
        Spacer(Modifier.height(18.dp))
        h.blocks.forEachIndexed { i, block ->
            Text(styledBlock(block, c.accent, c.secondary), fontSize = size.sp, lineHeight = (size * 1.5f).sp, color = c.label, fontFamily = family)
            if (i != h.blocks.lastIndex) Spacer(Modifier.height((size * 0.9f).dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Highlights "Chorus:" and part labels like "Sop:", "ATB:", "S/A:". */
fun styledBlock(block: String, accent: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val part = Regex("^(Chorus:?|Refrain:?|[A-Z][A-Za-z/]{0,4}:)\\s*")
    block.lines().forEachIndexed { i, line ->
        if (i > 0) append("\n")
        val m = part.find(line)
        if (m != null) {
            val label = m.value.trim()
            withStyle(SpanStyle(color = if (label.startsWith("Chorus") || label.startsWith("Refrain")) accent else muted,
                fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) { append(label) }
            append(" "); append(line.substring(m.range.last + 1))
        } else append(line)
    }
}

@Composable
private fun AudioBar(app: FaithHymnApp, n: Int, hymn: Hymn?, order: List<Int>, inPlaylist: Boolean) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by app.player.state.collectAsStateWithLifecycle()
    val progress by app.audio.progress.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    val thisPlaying = st.number == n
    val dl = progress[n]

    Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (thisPlaying && st.durationMs > 0) {
            Slider(value = st.positionMs.toFloat(), onValueChange = { app.player.seek(it.toLong()) }, valueRange = 0f..st.durationMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.fill), modifier = Modifier.height(24.dp))
            Row { Text(fmtTime(st.positionMs), fontSize = 12.sp, color = c.secondary); Spacer(Modifier.weight(1f)); Text(fmtTime(st.durationMs), fontSize = 12.sp, color = c.secondary) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when { thisPlaying && st.error != null -> st.error!!; thisPlaying && st.buffering -> "Loading…"; thisPlaying -> "Now playing"
                        n in downloaded -> "Saved for offline"; else -> "Listen to this hymn" },
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (thisPlaying && st.error != null) c.red else c.label,
                )
                if (dl != null) LinearProgressIndicator(progress = { dl }, color = c.accent, trackColor = c.fill, modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(2.dp)))
            }
            // Download / delete
            when {
                dl != null -> CircularProgressIndicator(Modifier.size(28.dp).padding(4.dp), color = c.accent, strokeWidth = 2.dp)
                n in downloaded -> IconButton({ app.audio.delete(n) }) { Icon(Icons.Rounded.DownloadDone, "Remove download", tint = c.accent) }
                else -> IconButton({
                    scope.launch {
                        when (val r = app.audio.download(n)) {
                            AudioStore.Result.Ok -> Toast.makeText(ctx, "Saved for offline", Toast.LENGTH_SHORT).show()
                            AudioStore.Result.NotFound -> Toast.makeText(ctx, "No recording for this hymn yet", Toast.LENGTH_SHORT).show()
                            is AudioStore.Result.Failed -> Toast.makeText(ctx, "Download failed: ${r.msg}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Icon(Icons.Outlined.Download, "Download", tint = c.accent) }
            }
            Spacer(Modifier.width(4.dp))
            CircleButton(if (thisPlaying && st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, {
                if (thisPlaying) app.player.toggle()
                else hymn?.let { h ->
                    val queue = if (inPlaylist) order else listOf(n)
                    scope.launch {
                        val items = queue.mapNotNull { num -> if (num == n) HymnSummary(h.number, h.title, h.firstLine) else app.repo.hymn(num)?.let { HymnSummary(it.number, it.title, it.firstLine) } }
                        app.player.play(items, queue.indexOf(n).coerceAtLeast(0))
                    }
                }
            }, size = 48, tint = androidx.compose.ui.graphics.Color.White, bg = c.accent)
        }
    }
}

@Composable
fun PlaylistPicker(app: FaithHymnApp, onDismiss: () -> Unit, onPick: (Playlist) -> Unit) {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { lists = app.repo.playlists() }
    if (creating) NameDialog("New playlist", "", { creating = false }) { name ->
        creating = false
        scope.launch { val id = app.repo.createPlaylist(name); onPick(Playlist(id, name, 0)) }
    } else AlertDialog(onDismiss, title = { Text("Add to playlist") },
        text = {
            Column {
                lists.forEach { p -> ListRow(p.name, subtitle = "${p.count} hymns", onClick = { onPick(p) }) }
                ListRow("New playlist…", titleColor = LocalIos.current.accent, onClick = { creating = true })
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } })
}

fun shareText(ctx: android.content.Context, h: Hymn) {
    val text = "Hymn ${h.number}: ${h.title.titleCase()}\n\n${h.content}\n\n— FaithHymns"
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share hymn"))
}
