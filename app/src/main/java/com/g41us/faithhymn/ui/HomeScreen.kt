package com.g41us.faithhymn.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn
import com.g41us.faithhymn.data.HymnSummary
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val HEADER_ITEMS = 4 // greeting, hymn of the day, recent, sort/chips header

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HymnsScreen(app: FaithHymnApp, open: (Int) -> Unit) {
    val c = LocalIos.current
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val available by app.catalog.available.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    var all by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var today by remember { mutableStateOf<Hymn?>(null) }
    var recent by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var pad by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 List, 1 A–Z, 2 Numbers
    val az = mode == 1
    LaunchedEffect(Unit) { all = app.repo.all(); today = app.repo.hymnOfTheDay()?.let { app.repo.hymn(it.number) } }
    LaunchedEffect(changes) { recent = app.repo.recent(12) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sorted = remember(all, az) { if (az) all.sortedBy { sortKey(it.title) } else all }
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }

    val cols = 5
    fun jump(i: Int) = scope.launch { list.scrollToItem(HEADER_ITEMS + if (mode == 2) i / cols else i) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = LocalBarPadding.current + 16.dp)) {
            item(key = "greet") { Greeting(onPad = { pad = true }) }
            item(key = "today") { today?.let { TodayCard(app, it, available, open) } ?: Spacer(Modifier.height(1.dp)) }
            item(key = "recent") {
                if (recent.isNotEmpty()) Column {
                    SectionHeader("Recently opened")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recent, key = { it.number }) { h ->
                            Column(Modifier.width(150.dp).bounceClick { open(h.number) }.clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.separator, RoundedCornerShape(14.dp)).padding(12.dp)) {
                                Text("${h.number}", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(h.title.titleCase(), color = c.label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(38.dp))
                            }
                        }
                    }
                } else Spacer(Modifier.height(1.dp))
            }
            stickyHeader(key = "chips") {
                Column(Modifier.fillMaxWidth().background(c.bg).padding(top = if (collapsed) 44.dp else 0.dp).statusBarsPaddingIf(collapsed)) {
                    Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("All ${all.size} hymns", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.weight(1f))
                        Segmented(listOf("List", "A–Z", "Numbers"), mode, { mode = it; scope.launch { list.scrollToItem(3) } }, Modifier.width(210.dp))
                    }
                    if (!az) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        items((0 until 11).toList()) { k ->
                            val from = k * 100 + 1; val to = minOf(from + 99, 1001)
                            val cur = (list.firstVisibleItemIndex - HEADER_ITEMS) * (if (mode == 2) cols else 1) + 1
                            Chip(if (to == from) "$from" else "$from–$to", selected = cur in from..to && list.firstVisibleItemIndex >= HEADER_ITEMS) { jump(from - 1) }
                        }
                    }
                }
            }
            if (mode == 2) {
                // Number boxes, like the original hymn book app: tap a number to open it.
                val rows = sorted.chunked(cols)
                itemsIndexed(rows, key = { i, _ -> "row$i" }) { _, row ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { h ->
                            Box(Modifier.weight(1f).aspectRatio(1f).bounceClick { open(h.number) }.clip(RoundedCornerShape(14.dp)).background(c.card)
                                .border(1.dp, c.separator, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Text("${h.number}", fontSize = if (h.number >= 1000) 16.sp else 19.sp, fontWeight = FontWeight.Bold, color = c.label,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                                if (h.number in available) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(5.dp).clip(CircleShape).background(c.accent))
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else itemsIndexed(sorted, key = { _, h -> h.number }) { i, h ->
                val shape = cornerFor(i, sorted.size)
                Column(Modifier.padding(horizontal = 16.dp).clip(shape).background(c.card)) {
                    HymnRow(h, onClick = { open(h.number) }, audio = h.number in available, offline = h.number in downloaded)
                    if (i != sorted.lastIndex) Separator(72)
                }
            }
        }
        CompactBar("Hymns", collapsed, Modifier.align(Alignment.TopCenter)) {
            CircleButton(Icons.Rounded.Dialpad, { pad = true }, size = 32)
        }
    }
    if (pad) NumberPad(app.repo.count, onDismiss = { pad = false }, onGo = { pad = false; open(it) })
}

private fun Modifier.statusBarsPaddingIf(b: Boolean) = if (b) this.statusBarsPadding() else this

/** "The Lord's my Shepherd" sorts under L, not under "The". */
private fun sortKey(t: String) = t.lowercase().trimStart('“', '"', '\'', '(').removePrefix("the ").removePrefix("o ").removePrefix("a ").ifEmpty { t.lowercase() }

@Composable
private fun Greeting(onPad: () -> Unit) {
    val c = LocalIos.current
    val now = remember { LocalDateTime.now() }
    val hello = when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Peace be with you" }
    Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondary,
            modifier = Modifier.padding(start = 20.dp))
        LargeTitle(hello) { CircleButton(Icons.Rounded.Dialpad, onPad) }
    }
}

/** Hymn of the day: a printed-page card — ivory, ink and one accent, no gradients. */
@Composable
private fun TodayCard(app: FaithHymnApp, h: Hymn, available: Set<Int>, open: (Int) -> Unit) {
    val c = LocalIos.current
    val first = h.parsed.firstOrNull()?.lines?.take(2)?.joinToString("\n") { it.plain.trim() } ?: h.firstLine
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().bounceClick { open(h.number) }
            .clip(RoundedCornerShape(18.dp)).background(c.card).border(1.dp, c.separator, RoundedCornerShape(18.dp)).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HYMN OF THE DAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accent, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            Text("No. ${h.number}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.secondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(h.title.titleCase(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, color = c.label, lineHeight = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(com.g41us.faithhymn.data.HymnFormat.softenFirstWord(first), fontSize = 17.sp, color = c.secondary, lineHeight = 24.sp,
            fontFamily = Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.bounceClick { open(h.number) }.clip(RoundedCornerShape(12.dp)).background(c.accent).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Read", color = if (c.dark) c.bg else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (h.number in available) Row(Modifier.bounceClick { app.player.play(listOf(HymnSummary(h.number, h.title, h.firstLine))) }
                .clip(RoundedCornerShape(12.dp)).border(1.dp, c.separator, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayArrow, null, tint = c.label, modifier = Modifier.size(18.dp))
                Text(" Listen", color = c.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

