package com.g41us.faithhymn.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Gold = Color(0xFFF3C969)
private val Ink = Color(0xFF140A0C)

/**
 * Startup: a hymn book opens, gold light sweeps across its pages, the name writes itself in,
 * then everything gently zooms away into the app (≈1.9s).
 */
@Composable
fun AnimatedIntro(done: () -> Unit) {
    val appear = remember { Animatable(0f) }   // book scale-in
    val open = remember { Animatable(0f) }     // 0 closed -> 1 open
    val shine = remember { Animatable(-0.6f) } // light sweep position
    val glow = remember { Animatable(0f) }
    val letters = remember { Animatable(0f) }  // how many letters are written
    val tagline = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val title = "FaithHymns"

    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
        launch { open.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
        delay(260)
        launch { glow.animateTo(1f, tween(700)) }
        launch { shine.animateTo(1.6f, tween(900, easing = LinearOutSlowInEasing)) }
        launch { letters.animateTo(title.length.toFloat(), tween(560, easing = LinearEasing)) }
        delay(420)
        tagline.animateTo(1f, tween(380))
        delay(420)
        exit.animateTo(1f, tween(420, easing = FastOutLinearInEasing))
        done()
    }

    val e = exit.value
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - e }
            .background(Brush.radialGradient(listOf(Color(0xFF5A1C25), Color(0xFF2C0F14), Ink), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
            val s = 1f + e * 0.25f; scaleX = s; scaleY = s
        }) {
            Box(contentAlignment = Alignment.Center) {
                // warm halo
                Canvas(Modifier.size(340.dp)) {
                    drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.45f * glow.value), Color.Transparent)), radius = size.minDimension / 2)
                }
                Canvas(Modifier.size(width = 250.dp, height = 178.dp).graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    val s = 0.55f + 0.45f * appear.value; scaleX = s; scaleY = s; alpha = appear.value.coerceIn(0f, 1f)
                }.drawWithContent {
                    drawContent()
                    // gold light sweeping across the pages
                    val x = size.width * shine.value
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.55f), Color.White.copy(alpha = 0.7f), Gold.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(x - 90f, 0f), end = Offset(x + 90f, size.height)), blendMode = BlendMode.SrcAtop)
                }) { drawBook(open.value) }
            }
            Spacer(Modifier.height(28.dp))
            Row {
                title.forEachIndexed { i, ch ->
                    val t = (letters.value - i).coerceIn(0f, 1f)
                    Text(ch.toString(), color = Color.White.copy(alpha = t), fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp,
                        letterSpacing = (-0.8).sp, modifier = Modifier.graphicsLayer { translationY = (1f - t) * 18f })
                }
            }
            Text("Hymns of faith, in your hand", color = Gold.copy(alpha = tagline.value), fontFamily = Serif, fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 6.dp).graphicsLayer { translationY = (1f - tagline.value) * 10f })
        }
    }
}

/** Open book: two pages hinged at the spine, with printed lines and a music note. [p] 0 = closed, 1 = open. */
private fun DrawScope.drawBook(p: Float) {
    val w = size.width; val h = size.height; val cx = w / 2
    val pageW = w / 2 - 6f
    val tilt = 1f - p                       // pages fold toward the spine while closed
    val cover = Color(0xFF6E1F2A)
    // cover behind the pages
    drawRoundRect(cover, Offset(cx - pageW * p - 10f, 8f), Size(pageW * 2 * p + 20f, h - 4f), CornerRadius(18f))
    fun page(left: Boolean) {
        val pw = pageW * (0.08f + 0.92f * p)
        val x = if (left) cx - pw else cx
        val lift = 14f * tilt
        val path = Path().apply {
            if (left) {
                moveTo(cx, 10f + lift); cubicTo(cx - pw * 0.5f, -2f + lift, x + 10f, 2f, x, 8f)
                lineTo(x, h - 10f); cubicTo(x + 10f, h - 16f, cx - pw * 0.5f, h - 8f, cx, h - 2f); close()
            } else {
                moveTo(cx, 10f + lift); cubicTo(cx + pw * 0.5f, -2f + lift, x + pw - 10f, 2f, x + pw, 8f)
                lineTo(x + pw, h - 10f); cubicTo(x + pw - 10f, h - 16f, cx + pw * 0.5f, h - 8f, cx, h - 2f); close()
            }
        }
        drawPath(path, Brush.horizontalGradient(if (left) listOf(Color(0xFFFFFBF2), Color(0xFFE9E1D0)) else listOf(Color(0xFFE9E1D0), Color(0xFFFFFBF2)), startX = x, endX = x + pw))
        // printed lines
        if (p > 0.55f) {
            val a = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
            for (k in 0 until 5) {
                val y = 36f + k * 20f
                val len = pw * (if (k == 4) 0.45f else 0.72f)
                val sx = if (left) x + pw * 0.16f else x + pw * 0.12f
                if (!left && k in 1..3) continue
                drawLine(Color(0xFF8A7F6A).copy(alpha = 0.45f * a), Offset(sx, y), Offset(sx + len, y), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
    }
    page(true); page(false)
    // spine shadow
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.Transparent), startX = cx - 14f, endX = cx + 14f), Offset(cx - 14f, 6f), Size(28f, h - 8f))
    // music note on the right page
    if (p > 0.7f) {
        val a = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val nx = cx + pageW * 0.55f; val ny = h * 0.62f
        val col = Color(0xFF8A2A35).copy(alpha = a)
        drawOval(col, Offset(nx - 26f, ny - 8f), Size(26f, 20f))
        drawLine(col, Offset(nx - 2f, ny + 2f), Offset(nx - 2f, ny - 52f), strokeWidth = 6f)
        drawLine(col, Offset(nx - 2f, ny - 52f), Offset(nx + 22f, ny - 40f), strokeWidth = 8f, cap = StrokeCap.Round)
    }
}
