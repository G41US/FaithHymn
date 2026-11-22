package com.g41us.faithhymn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.HymnSummary
import com.g41us.faithhymn.data.Playlist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HymnsScreen(app: FaithHymnApp, open: (Int) -> Unit) {
    val c = LocalIos.current
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    var all by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var today by remember { mutableStateOf<HymnSummary?>(null) }
    var recent by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var pad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { all = app.repo.all(); today = app.repo.hymnOfTheDay() }
    LaunchedEffect(changes) { recent = app.repo.recent(12) }
    val list = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
        item { LargeTitle("Hymns") { CircleButton(Icons.Rounded.Dialpad, { pad = true }) } }
        today?.let { h ->
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF8E6CFF), Color(0xFF4A3AE0)))).clickable { open(h.number) }.padding(18.dp)
                ) {
                    Column {
                        Text("HYMN OF THE DAY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(6.dp))
                        Text("${h.number}. ${h.title.titleCase()}", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(h.firstLine, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recently opened") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent, key = { it.number }) { h ->
                        Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(c.card).clickable { open(h.number) }.padding(12.dp)) {
                            Text("${h.number}", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(h.title.titleCase(), color = c.label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(38.dp))
                        }
                    }
                }
            }
        }
        item { SectionHeader("All ${all.size} hymns") }
        itemsIndexed(all, key = { _, h -> h.number }) { i, h ->
            val shape = when { all.size == 1 -> RoundedCornerShape(12.dp); i == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                i == all.lastIndex -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp); else -> RoundedCornerShape(0.dp) }
            Column(Modifier.padding(horizontal = 16.dp).clip(shape).background(c.card)) {
                HymnRow(h, onClick = { open(h.number) })
                if (i != all.lastIndex) Separator(72)
            }
        }
    }
    if (pad) NumberPad(app.repo.count, onDismiss = { pad = false }, onGo = { pad = false; open(it) })
}

@Composable
fun NumberPad(max: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val c = LocalIos.current
    var v by remember { mutableStateOf("") }
    val n = v.toIntOrNull()
    val valid = n != null && n in 1..max
    androidx.compose.ui.window.Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.card).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Go to hymn", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
            Text(v.ifEmpty { "—" }, fontSize = 44.sp, fontWeight = FontWeight.Light, color = if (v.isEmpty() || valid) c.label else c.red, modifier = Modifier.padding(vertical = 8.dp))
            Text(if (v.isNotEmpty() && !valid) "Hymns go from 1 to $max" else " ", fontSize = 13.sp, color = c.red)
            Spacer(Modifier.height(8.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "Go")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { k ->
                        val isGo = k == "Go"
                        Box(
                            Modifier.size(68.dp).clip(RoundedCornerShape(34.dp))
                                .background(if (isGo) (if (valid) c.accent else c.fill) else c.fill)
                                .clickable {
                                    when (k) {
                                        "⌫" -> v = v.dropLast(1)
                                        "Go" -> if (valid) onGo(n!!)
                                        else -> if (v.length < 4) v = (v + k).trimStart('0')
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, fontSize = if (isGo) 18.sp else 28.sp, fontWeight = if (isGo) FontWeight.SemiBold else FontWeight.Normal, color = if (isGo && valid) Color.White else c.label) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(app: FaithHymnApp, open: (Int) -> Unit) {
    val c = LocalIos.current
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (q.isEmpty()) focus.requestFocus() }
    LaunchedEffect(q) { delay(120); results = app.repo.search(q) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Search")
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.fill).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Title, lyrics or number", color = c.secondary, fontSize = 17.sp)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = c.label),
                    cursorBrush = SolidColor(c.accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { kb?.hide() }), modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            if (q.isNotEmpty()) Icon(Icons.Rounded.Cancel, "Clear", tint = c.tertiary, modifier = Modifier.size(20.dp).clickable { q = "" })
        }
        when {
            q.isBlank() -> Hint("Search all 1,001 hymns", "Type any words you remember from a verse, the title, or the hymn number.")
            results.isEmpty() -> Hint("No results", "Try fewer or different words.")
            else -> LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
                item { Text("${results.size} result${if (results.size == 1) "" else "s"}", color = c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 36.dp, bottom = 6.dp)) }
                itemsIndexed(results, key = { _, h -> h.number }) { i, h ->
                    Column(Modifier.padding(horizontal = 16.dp).clip(cornerFor(i, results.size)).background(c.card)) {
                        HymnRow(h, onClick = { kb?.hide(); open(h.number) })
                        if (i != results.lastIndex) Separator(72)
                    }
                }
            }
        }
    }
}

fun cornerFor(i: Int, size: Int) = when {
    size == 1 -> RoundedCornerShape(12.dp)
    i == 0 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    i == size - 1 -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
fun Hint(title: String, body: String) {
    val c = LocalIos.current
    Column(Modifier.fillMaxWidth().padding(top = 80.dp, start = 40.dp, end = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.label)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 15.sp, color = c.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun LibraryScreen(app: FaithHymnApp, open: (Int) -> Unit, openPlaylist: (Long) -> Unit) {
    val c = LocalIos.current
    val scope = rememberCoroutineScope()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var lists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recent by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var newList by remember { mutableStateOf(false) }
    LaunchedEffect(changes) { favs = app.repo.favourites(); lists = app.repo.playlists(); recent = app.repo.recent(50) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Library") { if (tab == 1) CircleButton(Icons.Rounded.Add, { newList = true }) }
        Segmented(listOf("Favourites", "Playlists", "Recent"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> if (favs.isEmpty()) Hint("No favourites yet", "Tap the heart on any hymn to keep it here.")
                 else HymnList(favs, open)
            1 -> if (lists.isEmpty()) Hint("No playlists yet", "Make a list for Sunday service, choir practice or your quiet time.")
                 else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                     itemsIndexed(lists, key = { _, p -> p.id }) { i, p ->
                         Column(Modifier.padding(horizontal = 16.dp).clip(cornerFor(i, lists.size)).background(c.card)) {
                             ListRow(p.name, subtitle = "${p.count} hymn${if (p.count == 1) "" else "s"}", chevron = true,
                                 icon = { IconTile(Icons.Rounded.QueueMusic, c.accent, 34) }, onClick = { openPlaylist(p.id) })
                             if (i != lists.lastIndex) Separator(64)
                         }
                     }
                 }
            else -> if (recent.isEmpty()) Hint("Nothing here yet", "Hymns you open will show up here.")
                    else Column {
                        HymnList(recent, open, Modifier.weight(1f, fill = false))
                        TextButton({ scope.launch { app.repo.clearHistory() } }, Modifier.align(Alignment.CenterHorizontally)) { Text("Clear history", color = c.red) }
                    }
        }
    }
    if (newList) NameDialog("New playlist", "", onDismiss = { newList = false }) { name ->
        newList = false; scope.launch { app.repo.createPlaylist(name) }
    }
}

@Composable
fun HymnList(items: List<HymnSummary>, open: (Int) -> Unit, modifier: Modifier = Modifier, trailing: (@Composable (HymnSummary) -> Unit)? = null) {
    val c = LocalIos.current
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(items, key = { _, h -> h.number }) { i, h ->
            Column(Modifier.padding(horizontal = 16.dp).clip(cornerFor(i, items.size)).background(c.card)) {
                HymnRow(h, onClick = { open(h.number) }, trailing = trailing?.let { t -> { t(h) } })
                if (i != items.lastIndex) Separator(72)
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("e.g. Sunday Service") }) },
        confirmButton = { TextButton({ if (name.isNotBlank()) onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistScreen(app: FaithHymnApp, id: Long, back: () -> Unit, open: (Int) -> Unit, present: (Int) -> Unit) {
    val c = LocalIos.current
    val scope = rememberCoroutineScope()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(changes) { name = app.repo.playlistName(id) ?: ""; items = app.repo.playlistHymns(id) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        NavBar(if (editing) "" else name, back, "Library") {
            TextButton({ editing = !editing }) { Text(if (editing) "Done" else "Edit", color = c.accent, fontSize = 17.sp, fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Normal) }
        }
        LargeTitle(name)
        if (items.isNotEmpty() && !editing) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Play all", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { app.player.play(items) }
            PillButton("Present", Icons.Rounded.Slideshow, Modifier.weight(1f)) { present(items.first().number) }
        }
        if (editing) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Rename", Icons.Rounded.Edit, Modifier.weight(1f)) { rename = true }
            PillButton("Delete list", Icons.Rounded.Delete, Modifier.weight(1f), tint = c.red) { confirmDelete = true }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) Hint("Empty playlist", "Open any hymn and tap ••• → Add to playlist.")
        else HymnList(items, if (editing) { _ -> } else open, Modifier.navigationBarsPadding(), trailing = if (!editing) null else { h ->
            Row {
                IconButton({ scope.launch { app.repo.moveInPlaylist(id, h.number, up = true) } }) { Icon(Icons.Rounded.KeyboardArrowUp, "Up", tint = c.secondary) }
                IconButton({ scope.launch { app.repo.moveInPlaylist(id, h.number, up = false) } }) { Icon(Icons.Rounded.KeyboardArrowDown, "Down", tint = c.secondary) }
                IconButton({ scope.launch { app.repo.removeFromPlaylist(id, h.number) } }) { Icon(Icons.Rounded.RemoveCircle, "Remove", tint = c.red) }
            }
        })
    }
    if (rename) NameDialog("Rename playlist", name, { rename = false }) { rename = false; scope.launch { app.repo.renamePlaylist(id, it) } }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete \"$name\"?") }, text = { Text("The hymns themselves stay in the book.") },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { app.repo.deletePlaylist(id); back() } }) { Text("Delete", color = c.red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@Composable
fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, tint: Color? = null, onClick: () -> Unit) {
    val c = LocalIos.current
    Row(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.card).clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint ?: c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
        Text(text, color = tint ?: c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
