package com.g41us.faithhymn.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn

private data class Slide(val hymn: Hymn, val index: Int, val text: String)

/**
 * Full-screen, one verse at a time — for a projector, TV (cast/screen-mirror) or the choir.
 * Tap right half = next, left half = previous. Moves on to the next hymn of the playlist automatically.
 */
@Composable
fun PresentScreen(app: FaithHymnApp, start: Int, playlistId: Long?, close: () -> Unit) {
    val ctx = LocalContext.current
    var slides by remember { mutableStateOf<List<Slide>>(emptyList()) }
    var i by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val numbers = playlistId?.let { id -> app.repo.playlistHymns(id).map { it.number } }?.let { l -> l.drop(l.indexOf(start).coerceAtLeast(0)) } ?: listOf(start)
        slides = numbers.mapNotNull { app.repo.hymn(it) }.flatMap { h -> h.blocks.mapIndexed { idx, b -> Slide(h, idx, b) } }
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
    LaunchedEffect(showUi, i) { if (showUi) { kotlinx.coroutines.delay(2500); showUi = false } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(slides.size) {
            detectTapGestures { o ->
                showUi = true
                if (o.x > size.width / 2) { if (i < slides.lastIndex) i++ } else if (i > 0) i--
            }
        }
    ) {
        val s = slides.getOrNull(i)
        if (s != null) {
            AnimatedContent(i, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "slide", modifier = Modifier.fillMaxSize()) { idx ->
                val sl = slides[idx]
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
                    // Scale text to the screen and to the verse length.
                    val lines = sl.text.lines().size.coerceAtLeast(1)
                    val byHeight = maxHeight.value / (lines * 1.45f)
                    val byWidth = maxWidth.value / (sl.text.lines().maxOf { it.length }.coerceAtLeast(12) * 0.5f)
                    val fs = minOf(byHeight, byWidth, 56f).coerceAtLeast(16f)
                    Text(sl.text, color = Color.White, fontSize = fs.sp, lineHeight = (fs * 1.35f).sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif)
                }
            }
            // Small caption so the leader knows where they are.
            Text("${s.hymn.number} · ${s.hymn.title.titleCase()}  —  ${s.index + 1}/${s.hymn.blocks.size}",
                color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 12.dp))
        }
        if (showUi) IconButton(close, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
            Icon(Icons.Rounded.Close, "Close", tint = Color.White)
        }
    }
}
