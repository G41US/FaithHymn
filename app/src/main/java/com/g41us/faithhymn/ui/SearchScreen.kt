package com.g41us.faithhymn.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.data.SearchFilter
import com.g41us.faithhymn.data.SearchResults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val suggestions = listOf("Amazing grace", "Blessed assurance", "It is well", "Great is Thy faithfulness", "How great Thou art",
    "Abide with me", "Count your blessings", "What a friend", "Rock of ages", "To God be the glory")

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(app: FaithHymnApp, open: (Int) -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    var res by remember { mutableStateOf(SearchResults(emptyList(), emptyList())) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    val available by app.catalog.available.collectAsStateWithLifecycle()
    val downloaded by app.audio.downloaded.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { history = app.repo.recentSearches(); if (q.isEmpty()) focus.requestFocus() }
    LaunchedEffect(q, filter) { delay(120); res = app.repo.search(q, filter) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { q = it }
    }
    val canVoice = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }
    fun openHymn(n: Int) { kb?.hide(); scope.launch { app.repo.saveSearch(q) }; open(n) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Search")
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.fill).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text(if (filter == SearchFilter.NUMBER) "Hymn number" else "Title, lyrics or number", color = c.secondary, fontSize = 17.sp)
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(fontSize = 17.sp, color = c.label),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = if (filter == SearchFilter.NUMBER) KeyboardType.Number else KeyboardType.Text),
                        keyboardActions = KeyboardActions(onSearch = { kb?.hide(); scope.launch { app.repo.saveSearch(q); history = app.repo.recentSearches() } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                if (q.isNotEmpty()) Icon(Icons.Rounded.Cancel, "Clear", tint = c.tertiary, modifier = Modifier.size(20.dp).clickable { q = "" })
                else if (canVoice) Icon(Icons.Rounded.Mic, "Voice search", tint = c.accent, modifier = Modifier.size(22.dp).clickable {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a line or title"))
                })
            }
        }
        Segmented(listOf("All", "Title", "Lyrics", "Number"), filter.ordinal, { filter = SearchFilter.entries[it] }, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))

        if (q.isBlank()) LazyColumn(contentPadding = PaddingValues(bottom = LocalBarPadding.current + 16.dp)) {
            if (history.isNotEmpty()) {
                item {
                    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.weight(1f))
                        TextButton({ scope.launch { app.repo.clearSearches(); history = emptyList() } }) { Text("Clear", color = c.accent) }
                    }
                }
                itemsIndexed(history) { i, h ->
                    Column(Modifier.padding(horizontal = 16.dp).clip(cornerFor(i, history.size)).background(c.card)) {
                        ListRow(h, icon = { Icon(Icons.Rounded.History, null, tint = c.secondary, modifier = Modifier.size(20.dp)) },
                            trailing = { Icon(Icons.Rounded.NorthWest, null, tint = c.tertiary, modifier = Modifier.size(18.dp)) }, onClick = { q = h })
                        if (i != history.lastIndex) Separator(50)
                    }
                }
            }
            item {
                Text("Try", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s -> Chip(s, icon = Icons.Rounded.MusicNote) { q = s } }
                }
                Text("Tip: type any words you remember from a verse — spelling doesn't have to be perfect.", fontSize = 13.sp, color = c.secondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
            }
        } else if (res.isEmpty) Hint("No results", "Try fewer words, or switch the filter to All.")
        else LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = LocalBarPadding.current + 16.dp)) {
            res.correctedQuery?.let { fix ->
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Text("  Showing results for “$fix”", fontSize = 14.sp, color = c.secondary)
                    }
                }
            }
            listOf("Titles" to res.titleHits, "In the lyrics" to res.lyricHits).filter { it.second.isNotEmpty() }.forEach { (head, items) ->
                stickyHeader {
                    Row(Modifier.fillMaxWidth().background(c.bg).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(head, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.label, modifier = Modifier.weight(1f))
                        Text("${items.size}", fontSize = 15.sp, color = c.secondary)
                    }
                }
                itemsIndexed(items, key = { _, h -> head + h.number }) { i, h ->
                    Column(Modifier.padding(horizontal = 16.dp).clip(cornerFor(i, items.size)).background(c.card)) {
                        HymnRow(h, onClick = { openHymn(h.number) }, audio = h.number in available, offline = h.number in downloaded)
                        if (i != items.lastIndex) Separator(72)
                    }
                }
            }
        }
    }
}
