package com.g41us.faithhymn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Intro(val icon: ImageVector, val title: String, val body: String, val colors: List<Color>)

private val pages = listOf(
    Intro(Icons.Rounded.Search, "Find any hymn", "Search all 1,001 hymns by number, title, or any line you remember — even with a typo.", CoverGradients[0]),
    Intro(Icons.Rounded.Slideshow, "Present to the church", "Show one verse at a time in big text on a projector or TV, straight from your phone.", CoverGradients[1]),
    Intro(Icons.Rounded.Headphones, "Listen offline", "Play recordings with lock-screen controls and save them for when there's no data.", CoverGradients[2]),
)

@Composable
fun Onboarding(done: () -> Unit) {
    val c = LocalIos.current
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(done) { Text("Skip", color = c.secondary, fontSize = 16.sp) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            val off = (pager.currentPage - i + pager.currentPageOffsetFraction).absoluteValue
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).graphicsLayer { alpha = 1f - off.coerceIn(0f, 1f) * 0.6f },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(200.dp).graphicsLayer { val s = 1f - off * 0.2f; scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(48.dp)).background(Brush.linearGradient(p.colors)), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(96.dp))
                }
                Spacer(Modifier.height(40.dp))
                Text(p.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.label, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, fontSize = 17.sp, color = c.secondary, textAlign = TextAlign.Center, lineHeight = 24.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val sel = pager.currentPage == i
                val w by animateDpAsState(if (sel) 22.dp else 8.dp, label = "dot")
                val col by animateColorAsState(if (sel) c.accent else c.fill, label = "dotc")
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(col))
            }
        }
        val last = pager.currentPage == pages.lastIndex
        Button({ if (last) done() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = c.accent)) {
            Text(if (last) "Get started" else "Continue", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
