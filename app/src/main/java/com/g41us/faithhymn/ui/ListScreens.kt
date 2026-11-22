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
fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, tint: Color? = null, onClick: () -> Unit) {
    val c = LocalIos.current
    Row(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.card).clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint ?: c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
        Text(text, color = tint ?: c.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
