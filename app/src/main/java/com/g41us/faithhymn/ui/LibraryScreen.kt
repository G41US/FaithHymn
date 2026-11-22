package com.g41us.faithhymn.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.HymnSummary
import com.g41us.faithhymn.data.Playlist
import com.g41us.faithhymn.data.PlaylistItem
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val ServiceSlots = listOf("Opening", "Praise", "Worship", "Offertory", "Before sermon", "After sermon", "Communion", "Closing")

@Composable
fun LibraryScreen(app: FaithHymnApp, open: (Int) -> Unit, openPlaylist: (Long) -> Unit) {
    val c = LocalIos.current
    val scope = rememberCoroutineScope()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var lists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var recent by remember { mutableStateOf<List<HymnSummary>>(emptyList()) }
    var newList by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(changes) { favs = app.repo.favourites(); lists = app.repo.playlists(); recent = app.repo.recent(50) }
    val bottom = LocalBarPadding.current + 16.dp

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Library") {
            when (tab) {
                0 -> if (favs.isNotEmpty()) CircleButton(if (settings.favGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView, { app.settings.update { it.copy(favGrid = !it.favGrid) } })
                1 -> CircleButton(Icons.Rounded.Add, { newList = true })
            }
        }
        Segmented(listOf("Favourites", "Playlists", "Recent"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> when {
                favs.isEmpty() -> Hint("No favourites yet", "Tap the heart on any hymn to keep it here.")
                settings.favGrid -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favs, key = { it.number }) { h -> FavTile(h) { open(h.number) } }
                }
                else -> SwipeList(favs, open, bottom, onAdd = { addTo = it }, onRemove = { n -> scope.launch { app.repo.removeFavourite(n) } })
            }
            1 -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(lists, key = { it.id }) { p ->
                    Column(Modifier.clickable { openPlaylist(p.id) }) {
                        PlaylistCover(p.name, p.color, Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(14.dp)), numbers = p.preview)
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(listOfNotNull(prettyDate(p.serviceDate), "${p.count} hymn${if (p.count == 1) "" else "s"}").joinToString(" · "), fontSize = 13.sp, color = c.secondary, maxLines = 1)
                    }
                }
                item(key = "new") {
                    Column(Modifier.clickable { newList = true }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(c.fill), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(40.dp))
                                Text("New playlist", color = c.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("Plan a service", fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            else -> if (recent.isEmpty()) Hint("Nothing here yet", "Hymns you open will show up here.")
                    else Column {
                        SwipeList(recent, open, 8.dp, Modifier.weight(1f, fill = false), onAdd = { addTo = it }, onRemove = { n -> scope.launch { app.repo.removeRecent(n) } })
                        TextButton({ scope.launch { app.repo.clearHistory() } }, Modifier.align(Alignment.CenterHorizontally).padding(bottom = LocalBarPadding.current)) { Text("Clear history", color = c.red) }
                    }
        }
    }
    if (newList) PlaylistEditSheet(null, onDismiss = { newList = false }) { name, date, color ->
        newList = false; scope.launch { val id = app.repo.createPlaylist(name, date); app.repo.updatePlaylist(id, name, date, color); openPlaylist(id) }
    }
    addTo?.let { n -> AddToPlaylist(app, n) { addTo = null } }
}

@Composable
private fun FavTile(h: HymnSummary, onClick: () -> Unit) {
    val c = LocalIos.current
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.card).clickable(onClick = onClick).padding(14.dp).height(110.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${h.number}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Favorite, null, tint = c.red, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(h.title.titleCase(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
    }
}

/** List with iOS swipe actions: → add to playlist, ← remove. */
@Composable
private fun SwipeList(items: List<HymnSummary>, open: (Int) -> Unit, bottom: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onAdd: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val c = LocalIos.current
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = bottom)) {
        itemsIndexed(items, key = { _, h -> h.number }) { i, h ->
            Column(Modifier.animateItem().padding(horizontal = 16.dp).clip(cornerFor(i, items.size)).background(c.card)) {
                SwipeRow(Icons.Rounded.PlaylistAdd to { onAdd(h.number) }, Icons.Rounded.Delete to { onRemove(h.number) }) {
                    Box(Modifier.background(c.card)) { HymnRow(h, onClick = { open(h.number) }) }
                }
                if (i != items.lastIndex) Separator(72)
            }
        }
    }
}

@Composable
fun AddToPlaylist(app: FaithHymnApp, n: Int, done: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    PlaylistPicker(app, onDismiss = done) { p ->
        scope.launch { app.repo.addToPlaylist(p.id, n) }
        Toast.makeText(ctx, "Added to ${p.name}", Toast.LENGTH_SHORT).show(); done()
    }
}

/** Create / edit a playlist: name, service date, cover colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditSheet(existing: Playlist?, onDismiss: () -> Unit, onSave: (String, String?, Int) -> Unit) {
    val c = LocalIos.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var date by remember { mutableStateOf(existing?.serviceDate) }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    var picking by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss, containerColor = c.bg, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss) { Text("Cancel", color = c.accent, fontSize = 17.sp) }
                Text(if (existing == null) "New playlist" else "Edit playlist", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.label,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton({ if (name.isNotBlank()) onSave(name.trim(), date, color) }, enabled = name.isNotBlank()) { Text(if (existing == null) "Create" else "Save", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
            }
            PlaylistCover(name.ifBlank { "Sunday Service" }, color, Modifier.padding(16.dp).size(150.dp).align(Alignment.CenterHorizontally))
            Group {
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name, e.g. Sunday Service") },
                    modifier = Modifier.fillMaxWidth().padding(8.dp))
                Separator()
                ListRow("Service date", icon = { IconTile(Icons.Rounded.Event, c.red) }, value = prettyDate(date) ?: "None", chevron = true, onClick = { picking = true })
                if (date != null) { Separator(59); ListRow("Remove date", titleColor = c.red, onClick = { date = null }) }
            }
            SectionHeader("Cover")
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverGradients.indices.forEach { i ->
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(CoverGradients[i])).clickable { color = i },
                        contentAlignment = Alignment.Center) { if (i == color) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                }
            }
        }
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date?.let { LocalDate.parse(it) } ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog({ picking = false }, confirmButton = {
            TextButton({ st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; picking = false }) { Text("Done") }
        }, dismissButton = { TextButton({ picking = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(app: FaithHymnApp, id: Long, back: () -> Unit, open: (Int) -> Unit, present: (Int) -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by app.repo.changes.collectAsStateWithLifecycle()
    var pl by remember { mutableStateOf<Playlist?>(null) }
    var items by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var edit by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(changes) { pl = app.repo.playlist(id); items = app.repo.playlistItems(id) }
    val p = pl ?: return
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = items.indexOfFirst { it.hymn.number == from.key }; val t = items.indexOfFirst { it.hymn.number == to.key }
        if (f >= 0 && t >= 0) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = 0.dp, bottom = 32.dp), modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            item(key = "head") {
                Column(Modifier.statusBarsPadding().padding(top = 44.dp)) {
                    PlaylistCover(p.name, p.color, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(180.dp).shadow(10.dp, RoundedCornerShape(22.dp)),
                        numbers = items.take(6).map { it.hymn.number }, big = true)
                    Text(listOfNotNull(prettyDate(p.serviceDate), "${items.size} hymn${if (items.size == 1) "" else "s"}").joinToString(" · "),
                        color = c.secondary, fontSize = 14.sp, modifier = Modifier.padding(start = 20.dp, top = 10.dp))
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Play all", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { if (items.isNotEmpty()) app.player.play(items.map { it.hymn }) }
                        PillButton("Present", Icons.Rounded.Slideshow, Modifier.weight(1f)) { items.firstOrNull()?.let { present(it.hymn.number) } }
                        PillButton("Share", Icons.Rounded.IosShare, Modifier.weight(1f)) { if (items.isNotEmpty()) share = true }
                    }
                    if (items.isNotEmpty()) Text("Hold ≡ to reorder · swipe left to remove · tap the label to set the service slot", fontSize = 12.sp, color = c.secondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            if (items.isEmpty()) item { Hint("Empty playlist", "Open any hymn and tap ••• → Add to playlist.") }
            itemsIndexed(items, key = { _, it -> it.hymn.number }) { i, it ->
                ReorderableItem(reorder, key = it.hymn.number) { dragging ->
                    val elev by androidx.compose.animation.core.animateDpAsState(if (dragging) 12.dp else 0.dp, label = "drag")
                    Column(Modifier.padding(horizontal = 16.dp).shadow(elev, RoundedCornerShape(12.dp)).clip(if (dragging) RoundedCornerShape(12.dp) else cornerFor(i, items.size)).background(c.card)) {
                        SwipeRow(null, Icons.Rounded.Delete to { scope.launch { app.repo.removeFromPlaylist(id, it.hymn.number) } }) {
                            Row(Modifier.background(c.card), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    Column {
                                        HymnRow(it.hymn, onClick = { open(it.hymn.number) })
                                        SlotChip(it.slot, Modifier.padding(start = 72.dp, bottom = 10.dp, top = 0.dp)) { s -> scope.launch { app.repo.setSlot(id, it.hymn.number, s) } }
                                    }
                                }
                                Icon(Icons.Rounded.DragHandle, "Reorder", tint = c.tertiary, modifier = Modifier.draggableHandle(
                                    onDragStopped = { scope.launch { app.repo.reorder(id, items.map { x -> x.hymn.number }) } }).padding(16.dp))
                            }
                        }
                        if (i != items.lastIndex && !dragging) Separator(72)
                    }
                }
            }
        }
        // Nav bar (frosts in once scrolled)
        Box(Modifier.fillMaxWidth().background(if (collapsed) c.bar else Color.Transparent).statusBarsPadding().height(44.dp)) {
            Row(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).clickable(onClick = back).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = c.accent, modifier = Modifier.size(20.dp)); Text("Library", color = c.accent, fontSize = 17.sp)
            }
            if (collapsed) Text(p.name, Modifier.align(Alignment.Center).widthIn(max = 200.dp), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.label, maxLines = 1)
            Box(Modifier.align(Alignment.CenterEnd)) {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = c.accent) }
                DropdownMenu(menu, { menu = false }, containerColor = c.card) {
                    DropdownMenuItem({ Text("Edit name, date & cover") }, { menu = false; edit = true }, trailingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text("Share") }, { menu = false; if (items.isNotEmpty()) share = true }, trailingIcon = { Icon(Icons.Rounded.IosShare, null) })
                    DropdownMenuItem({ Text("Delete playlist", color = c.red) }, { menu = false; confirmDelete = true }, trailingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.red) })
                }
            }
        }
    }
    if (edit) PlaylistEditSheet(p, { edit = false }) { name, date, color -> edit = false; scope.launch { app.repo.updatePlaylist(id, name, date, color) } }
    if (share) ShareSheet(p, items) { share = false }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Delete \"${p.name}\"?") }, text = { Text("The hymns themselves stay in the book.") },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { app.repo.deletePlaylist(id); back() } }) { Text("Delete", color = c.red) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun SlotChip(slot: String?, modifier: Modifier, onSet: (String?) -> Unit) {
    val c = LocalIos.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(slot ?: "+ Service slot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (slot != null) c.accent else c.secondary,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (slot != null) c.accent.copy(alpha = 0.12f) else c.fill).clickable { open = true }.padding(horizontal = 8.dp, vertical = 3.dp))
        DropdownMenu(open, { open = false }, containerColor = c.card) {
            ServiceSlots.forEach { s -> DropdownMenuItem({ Text(s) }, { open = false; onSet(s) }, trailingIcon = { if (s == slot) Icon(Icons.Rounded.Check, null, tint = c.accent) }) }
            if (slot != null) DropdownMenuItem({ Text("No slot", color = c.red) }, { open = false; onSet(null) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(p: Playlist, items: List<PlaylistItem>, onDismiss: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val link = remember(p, items) { PlaylistLink.build(p.name, p.serviceDate, items) }
    val qr = remember(link) { qrImage(link) }
    ModalBottomSheet(onDismiss, containerColor = c.bg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BottomSheetTitle("Share “${p.name}”")
            Box(Modifier.size(230.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
                Image(qr, "QR code", Modifier.fillMaxSize())
            }
            Text("Scan with a phone camera to open it in FaithHymns", fontSize = 13.sp, color = c.secondary, modifier = Modifier.padding(12.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Send list", Icons.Rounded.Send, Modifier.weight(1f)) { sharePlaylistText(ctx, p.name, p.serviceDate, items) }
                PillButton("Send QR", Icons.Rounded.QrCode2, Modifier.weight(1f)) { shareBitmap(ctx, qrBitmap(link, 900), "playlist_qr", "Share QR code") }
            }
        }
    }
}

/** Confirms adding a playlist received by link / QR. */
@Composable
fun PlaylistImportDialog(app: FaithHymnApp, uri: android.net.Uri, onDone: (Long?) -> Unit) {
    val scope = rememberCoroutineScope()
    val shared = remember(uri) { PlaylistLink.parse(uri) }
    if (shared == null || shared.items.isEmpty()) { LaunchedEffect(uri) { onDone(null) }; return }
    AlertDialog({ onDone(null) }, title = { Text("Add “${shared.name}”?") },
        text = { Text("${shared.items.size} hymns${prettyDate(shared.date)?.let { " · $it" } ?: ""}\n\n" + shared.items.take(8).joinToString("\n") { (n, s) -> "• ${s?.let { "$it: " } ?: ""}Hymn $n" } + if (shared.items.size > 8) "\n…" else "") },
        confirmButton = { TextButton({
            scope.launch {
                val id = app.repo.createPlaylist(shared.name, shared.date, shared.items.map { it.first })
                shared.items.forEach { (n, s) -> if (s != null) app.repo.setSlot(id, n, s) }
                onDone(id)
            }
        }) { Text("Add playlist") } },
        dismissButton = { TextButton({ onDone(null) }) { Text("Cancel") } })
}
