package com.g41us.faithhymn.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.AudioStore
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn
import com.g41us.faithhymn.data.HymnBlock
import com.g41us.faithhymn.data.HymnFormat
import com.g41us.faithhymn.data.HymnSummary
import com.g41us.faithhymn.data.Playlist
import com.g41us.faithhymn.data.Voice
import kotlinx.coroutines.launch

/** Swipe left/right between hymns. Inside a playlist, swipes go through the playlist only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HymnScreen(app: FaithHymnApp, start: Int, playlistId: Long?, back: () -> Unit, present: (Int, Long?) -> Unit, openPlayer: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val changes by app.repo.changes.collectAsStateWithLifecycle()

    var order by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(playlistId) {
        order = playlistId?.let { id -> app.repo.playlistHymns(id).map { it.number } }?.takeIf { start in it } ?: (1..app.repo.count).toList()
    }
    val ord = order ?: return
    val pager = rememberPagerState(initialPage = ord.indexOf(start).coerceAtLeast(0)) { ord.size }
    val current = ord[pager.currentPage]
    LaunchedEffect(pager.currentPage) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    var hymn by remember { mutableStateOf<Hymn?>(null) }
    var fav by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var shareBlock by remember { mutableStateOf<HymnBlock?>(null) }
    val collapsedPages = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(current) { hymn = app.repo.hymn(current); app.repo.markOpened(current) }
    LaunchedEffect(current, changes) { fav = app.repo.isFavourite(current) }

    if (settings.keepScreenOn) DisposableEffect(Unit) {
        val w = (ctx as Activity).window
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val collapsed = collapsedPages[current] == true

    Column(Modifier.fillMaxSize().background(c.card)) {
        // Collapsing nav bar: title slides in once the big title scrolls away.
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp)) {
            Row(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).clickable(onClick = back).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = c.accent, modifier = Modifier.size(20.dp))
                if (!collapsed) Text(if (playlistId != null) "Playlist" else "Back", color = c.accent, fontSize = 17.sp)
            }
            val a by animateFloatAsState(if (collapsed) 1f else 0f, label = "title")
            Column(Modifier.align(Alignment.Center).widthIn(max = 190.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (collapsed) hymn?.title?.titleCase() ?: "" else "Hymn $current", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a > 0f) Text("Hymn $current", fontSize = 11.sp, color = c.secondary.copy(alpha = c.secondary.alpha * a))
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); scope.launch { app.repo.toggleFavourite(current) } }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = if (fav) c.red else c.accent)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = c.accent) }
                    DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                        MenuItem("Present on screen", Icons.Rounded.Slideshow) { menu = false; present(current, playlistId) }
                        MenuItem("Add to playlist", Icons.Rounded.PlaylistAdd) { menu = false; addTo = true }
                        MenuItem("Share a verse as image", Icons.Rounded.Image) { menu = false; shareBlock = hymn?.parsed?.firstOrNull() }
                        MenuItem("Share text", Icons.Rounded.IosShare) { menu = false; hymn?.let { shareText(ctx, it) } }
                        MenuItem("What the markings mean", Icons.Rounded.HelpOutline) { menu = false; showKey = true }
                    }
                }
            }
            if (collapsed) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp).background(c.separator))
        }
        HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1, key = { ord[it] }) { page ->
            HymnPage(app, ord[page], settings.fontSize, settings.serif, settings.myPart, settings.showMarkings,
                onCollapsed = { collapsedPages[ord[page]] = it }, onShareBlock = { shareBlock = it }, onKey = { showKey = true })
        }
        // Listening controls only when this hymn actually has a recording.
        val available by app.catalog.available.collectAsStateWithLifecycle()
        val saved by app.audio.downloaded.collectAsStateWithLifecycle()
        val playing by app.player.state.collectAsStateWithLifecycle()
        if (current in available || current in saved || playing.number == current) AudioBar(app, current, hymn, ord, playlistId != null, openPlayer)
        else Spacer(Modifier.navigationBarsPadding())
    }

    if (addTo) AddToPlaylist(app, current) { addTo = false }
    if (showKey) ModalBottomSheet({ showKey = false }, containerColor = c.bg) {
        BottomSheetTitle("Reading the markings")
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) { MarkingsKey() }
    }
    shareBlock?.let { b -> hymn?.let { h -> VerseShareSheet(h, b) { shareBlock = null } } }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalIos.current
    DropdownMenuItem({ Text(text, color = c.label) }, onClick, trailingIcon = { Icon(icon, null, tint = c.label) })
}

@Composable
private fun HymnPage(app: FaithHymnApp, n: Int, fontSize: Float, serif: Boolean, myPart: Voice?, showMarkings: Boolean,
                     onCollapsed: (Boolean) -> Unit, onShareBlock: (HymnBlock) -> Unit, onKey: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var hymn by remember { mutableStateOf<Hymn?>(null) }
    LaunchedEffect(n) { hymn = app.repo.hymn(n) }
    val h = hymn ?: return
    var size by remember(fontSize) { mutableFloatStateOf(fontSize) }
    val scroll = rememberScrollState()
    val family = if (serif) Serif else Sans
    val blocks = h.parsed
    val hasParts = remember(blocks) { blocks.any { b -> b.lines.any { it.part != null && !it.part.everyone } } }
    val hasMarks = remember(blocks) { hasParts || blocks.any { b -> b.dynamic != null || b.lines.any { it.dynamic != null } } }
    val available by app.catalog.available.collectAsStateWithLifecycle()

    // Follow along: highlight + scroll to the verse being sung.
    val st by app.player.state.collectAsStateWithLifecycle()
    val playingThis = st.number == n && st.durationMs > 0
    val active = if (playingThis) blockAt(blocks, st.positionMs.toFloat() / st.durationMs) else null
    val blockY = remember { mutableStateMapOf<Int, Float>() }
    var lyricsTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (active != null && st.playing) blockY[active]?.let { y -> scroll.animateScrollTo((lyricsTop + y - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0)) }
    }
    LaunchedEffect(scroll.value) { onCollapsed(scroll.value > with(density) { 90.dp.toPx() }) }

    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.size >= 2) {
                            val a = e.changes[0]; val b = e.changes[1]
                            val prev = (a.previousPosition - b.previousPosition).getDistance()
                            val now = (a.position - b.position).getDistance()
                            if (prev > 0f) { size = (size * now / prev).coerceIn(12f, 44f); e.changes.forEach { it.consume() } }
                        } else if (e.changes.all { !it.pressed } && size != fontSize) app.settings.update { s -> s.copy(fontSize = size) }
                    }
                }
            }
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HYMN ${h.number}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent, letterSpacing = 1.sp)
            if (n in available) Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, null, tint = c.secondary, modifier = Modifier.size(14.dp)); Text(" Recording", fontSize = 12.sp, color = c.secondary)
            }
            Spacer(Modifier.weight(1f))
            if (hasMarks) Text("Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onKey).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(h.title.titleCase(), fontSize = (size * 1.4f).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = c.label, fontFamily = Sans, lineHeight = (size * 1.6f).sp)
        var topics by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(n) { topics = app.repo.topicsFor(n) }
        if (topics.isNotEmpty()) Text(topics.joinToString("  ·  "), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
        if (hasParts) PartFilter(myPart) { v -> app.settings.update { it.copy(myPart = v) } }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.androidxOnPositioned { lyricsTop = it }) {
            HymnLyrics(blocks, size, serif, myPart, showMarkings, activeBlock = active,
                onBlockPositioned = { i, y -> blockY[i] = y },
                onLongPress = onShareBlock,
                onDynamic = { d -> Toast.makeText(ctx, "$d — ${HymnFormat.DYNAMICS[d] ?: ""}", Toast.LENGTH_SHORT).show() })
        }
        Text("Long-press a verse to share it", fontSize = 12.sp, color = c.tertiary, modifier = Modifier.padding(top = 28.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(30.dp))
    }
}

private fun Modifier.androidxOnPositioned(f: (Float) -> Unit) =
    this.then(Modifier.onGloballyPositioned { f(it.positionInParent().y) })

/** "I sing: All · Soprano · Alto · Tenor · Bass" — other parts are dimmed. */
@Composable
private fun PartFilter(my: Voice?, onPick: (Voice?) -> Unit) {
    val c = LocalIos.current
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("I sing", fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(end = 8.dp))
        listOf<Pair<Voice?, String>>(null to "All", Voice.S to "S", Voice.A to "A", Voice.T to "T", Voice.B to "B").forEach { (v, l) ->
            val sel = my == v
            val col = v?.let(::voiceColor) ?: c.accent
            Box(Modifier.padding(end = 6.dp).height(28.dp).widthIn(min = 32.dp).clip(CircleShape).background(if (sel) col else c.fill).clickable { onPick(v) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center) {
                Text(l, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else if (v != null) col else c.label)
            }
        }
    }
}

@Composable
private fun AudioBar(app: FaithHymnApp, n: Int, hymn: Hymn?, order: List<Int>, inPlaylist: Boolean, openPlayer: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by app.player.state.collectAsStateWithLifecycle()
    val progress by app.audio.progress.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    val thisPlaying = st.number == n
    val dl = progress[n]

    Column(Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding()) {
        if (thisPlaying && st.durationMs > 0) LinearProgressIndicator(progress = { st.positionMs.toFloat() / st.durationMs }, color = c.accent, trackColor = c.fill,
            modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {}, gapSize = 0.dp)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(enabled = thisPlaying, onClick = openPlayer)) {
                Text(
                    when { thisPlaying && st.error != null -> st.error!!; thisPlaying && st.buffering -> "Loading…"; thisPlaying -> "Now playing · ${fmtTime(st.positionMs)}"
                        n in downloaded -> "Saved for offline"; else -> "Listen to this hymn" },
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (thisPlaying && st.error != null) c.red else c.label,
                )
                if (thisPlaying && st.error == null) Text("Tap for player · verses follow along", fontSize = 12.sp, color = c.secondary)
                if (dl != null) LinearProgressIndicator(progress = { dl }, color = c.accent, trackColor = c.fill, modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(2.dp)))
            }
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
            }, size = 48, tint = Color.White, bg = c.accent)
        }
    }
}

/** Long-press a verse → pick a card style → share as image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseShareSheet(h: Hymn, block: HymnBlock, onDismiss: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    var style by remember { mutableIntStateOf(0) }
    val text = remember(block) { HymnFormat.softenFirstWord(block.plainText.lines().joinToString("\n") { it.trim() }) }
    val previews = remember(block) { CardStyles.map { renderVerseCard(h, text, it, 0.32f).asImageBitmap() } }
    ModalBottomSheet(onDismiss, containerColor = c.bg) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 20.dp)) {
            BottomSheetTitle(if (block.number != null) "Share verse ${block.number}" else "Share chorus")
            Image(previews[style], null, Modifier.align(Alignment.CenterHorizontally).height(300.dp).clip(RoundedCornerShape(14.dp)))
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(CardStyles) { i, s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { style = i }) {
                        Image(previews[i], s.name, Modifier.height(86.dp).clip(RoundedCornerShape(8.dp))
                            .border(if (i == style) 3.dp else 0.dp, if (i == style) c.accent else Color.Transparent, RoundedCornerShape(8.dp)))
                        Text(s.name, fontSize = 12.sp, color = if (i == style) c.accent else c.secondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Button({ shareVerse(ctx, h, text, CardStyles[style]); onDismiss() }, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent)) {
                Icon(Icons.Rounded.IosShare, null); Text("  Share image", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
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
                lists.forEach { p -> ListRow(p.name, subtitle = "${p.count} hymns", icon = { PlaylistCover("", p.color, Modifier.size(34.dp)) }, onClick = { onPick(p) }) }
                ListRow("New playlist…", titleColor = LocalIos.current.accent, onClick = { creating = true })
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } })
}

fun shareText(ctx: android.content.Context, h: Hymn) {
    val text = "Hymn ${h.number}: ${h.title.titleCase()}\n\n${h.content}\n\n— FaithHymns"
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share hymn"))
}
