package com.g41us.faithhymn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g41us.faithhymn.data.Part
import com.g41us.faithhymn.data.Voice

/** Height of the floating tab bar + mini player, so lists can scroll underneath the frosted bar. */
val LocalBarPadding = compositionLocalOf { 0.dp }

/** iOS-style slider: thin track, white round thumb with shadow. */
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, steps: Float? = null, onDone: () -> Unit = {}) {
    val c = LocalIos.current
    var width by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val thumb = 28.dp
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    fun at(x: Float): Float {
        val tPx = with(density) { thumb.toPx() }
        val f = ((x - tPx / 2) / (width - tPx)).coerceIn(0f, 1f)
        var v = range.start + f * (range.endInclusive - range.start)
        if (steps != null) v = (Math.round(v / steps) * steps)
        return v.coerceIn(range.start, range.endInclusive)
    }
    Box(
        modifier.height(36.dp).fillMaxWidth().onSizeChanged { width = it.width }
            .pointerInput(range) { detectTapGestures { onChange(at(it.x)); onDone() } }
            .pointerInput(range) { detectDragGestures(onDragEnd = onDone) { ch, _ -> onChange(at(ch.position.x)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackW = with(density) { (width.toFloat()).toDp() } - thumb
        Box(Modifier.padding(horizontal = thumb / 2).fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill))
        Box(Modifier.padding(start = thumb / 2).width(trackW * frac).height(4.dp).clip(CircleShape).background(c.accent))
        Box(Modifier.offset(x = trackW * frac).size(thumb).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** Coloured pill for a choir part ("Sop", "ATB", "Gen"…). */
@Composable
fun PartPill(part: Part, dim: Boolean = false) {
    val c = LocalIos.current
    val colors = if (part.everyone) listOf(c.secondary, c.secondary) else part.voices.sortedBy { it.ordinal }.map(::voiceColor).let { if (it.size == 1) it + it else it }
    Row(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
            .background(colors.first().copy(alpha = if (part.everyone) 0f else if (c.dark) 0.22f else 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!part.everyone) part.voices.sortedBy { it.ordinal }.forEach { v ->
            Box(Modifier.padding(end = 2.dp).size(6.dp).clip(CircleShape).background(voiceColor(v)))
        }
        Text(if (part.everyone) part.label.trimEnd(':').uppercase() else part.label.trimEnd(':'), fontSize = if (part.everyone) 11.sp else 12.sp,
            letterSpacing = if (part.everyone) 1.sp else 0.sp, fontWeight = FontWeight.SemiBold,
            color = if (part.everyone) c.secondary else colors.first().copy(alpha = if (dim) 0.5f else 1f),
            modifier = Modifier.padding(start = if (part.everyone) 0.dp else 2.dp))
    }
}

/** Musical dynamic ("mf", "pp", "rit.") in the classic bold-italic serif look. */
@Composable
fun DynamicBadge(d: String, onClick: (() -> Unit)? = null) {
    val c = LocalIos.current
    Text(d, fontFamily = Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.accent,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(5.dp)).background(c.accent.copy(alpha = 0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 5.dp))
}

/** Playlist / artwork tones: flat cloth-binding colours (two near-identical stops = solid). */
private fun flat(c: Long) = Color(c).let { listOf(it, it) }
val CoverGradients = listOf(
    flat(0xFF7A2630), flat(0xFF2E5B45), flat(0xFF244A75), flat(0xFF8E6420),
    flat(0xFF4F3053), flat(0xFF3A3632), flat(0xFF6B4A2E), flat(0xFF2F4F55),
)

/** Gradient tile for a playlist, with a short title (or hymn numbers) on it. */
@Composable
fun PlaylistCover(name: String, color: Int, modifier: Modifier = Modifier, numbers: List<Int> = emptyList(), big: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(if (big) 22.dp else 14.dp)).background(Brush.linearGradient(CoverGradients[color % CoverGradients.size])).padding(if (big) 18.dp else 12.dp)) {
        Box(Modifier.align(Alignment.BottomStart).width(if (big) 36.dp else 22.dp).height(2.dp).background(Color(0xFFD9B46A)))
        Column {
            Text(name, color = Color(0xFFF7F0E3), fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = if (big) 28.sp else 18.sp, maxLines = 3, lineHeight = if (big) 32.sp else 22.sp)
            if (numbers.isNotEmpty()) Text(numbers.joinToString(" · "), color = Color(0xFFF7F0E3).copy(alpha = 0.7f), fontSize = if (big) 15.sp else 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Row with swipe actions: swipe right = [startAction], swipe left = [endAction] (destructive). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(
    startAction: Pair<ImageVector, () -> Unit>?, endAction: Pair<ImageVector, () -> Unit>?,
    content: @Composable () -> Unit,
) {
    val c = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { startAction?.second?.invoke(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); false }
            SwipeToDismissBoxValue.EndToStart -> { endAction?.second?.invoke(); haptic.performHapticFeedback(HapticFeedbackType.LongPress); endAction != null }
            else -> false
        }
    }, positionalThreshold = { it * 0.35f })
    SwipeToDismissBox(
        state, enableDismissFromStartToEnd = startAction != null, enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            val dir = state.dismissDirection
            val (bg, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(c.accent, startAction?.first, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(c.red, endAction?.first, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp), contentAlignment = align) {
                if (icon != null) Icon(icon, null, tint = Color.White)
            }
        },
    ) { content() }
}

@Composable
fun Chip(text: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalIos.current
    Row(
        Modifier.bounceClick(onClick).clip(RoundedCornerShape(50)).background(if (selected) c.accent else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.separator, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Color.White else c.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) (if (c.dark) c.bg else Color.White) else c.secondary)
    }
}

@Composable
fun BottomSheetTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalIos.current.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
}

/** Collapsing iOS nav bar: shows [title] centred once the big title has scrolled away. */
@Composable
fun CompactBar(title: String, visible: Boolean, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalIos.current
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, label = "bar")
    // Status-bar strip is always solid so scrolled content never shows under the clock/battery.
    Box(modifier.fillMaxWidth().background(c.bg.copy(alpha = maxOf(alpha, 1f))).background(c.bar.copy(alpha = c.bar.alpha * alpha)).statusBarsPadding().height(if (visible || alpha > 0f) 44.dp else 0.dp)) {
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = 80.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = c.label.copy(alpha = alpha), maxLines = 1)
        if (visible) Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        if (alpha > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp).background(c.separator.copy(alpha = c.separator.alpha * alpha)))
    }
}

fun Dp.orZero() = if (this < 0.dp) 0.dp else this

/** Premium press feel: the element sinks slightly while pressed, with a light haptic. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = this.then(Modifier.composed {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val s by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.965f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    Modifier.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
})
