package com.g41us.faithhymn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g41us.faithhymn.data.BlockKind
import com.g41us.faithhymn.data.HymnBlock
import com.g41us.faithhymn.data.HymnFormat
import com.g41us.faithhymn.data.HymnLine
import com.g41us.faithhymn.data.Voice

/** Which block is being sung at [fraction] (0..1) of the recording, weighting blocks by their line count. */
fun blockAt(blocks: List<HymnBlock>, fraction: Float): Int {
    if (blocks.isEmpty()) return 0
    val weights = blocks.map { it.lines.size.coerceAtLeast(1) }
    val total = weights.sum().toFloat(); var acc = 0f
    weights.forEachIndexed { i, w -> acc += w / total; if (fraction < acc) return i }
    return blocks.lastIndex
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HymnLyrics(
    blocks: List<HymnBlock>,
    fontSize: Float,
    serif: Boolean,
    myPart: Voice?,
    showMarkings: Boolean,
    activeBlock: Int? = null,
    onBlockPositioned: ((Int, Float) -> Unit)? = null,
    onLongPress: ((HymnBlock) -> Unit)? = null,
    onDynamic: (String) -> Unit = {},
) {
    val c = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val family = if (serif) FontFamily.Serif else FontFamily.Default
    Column {
        blocks.forEachIndexed { i, b ->
            val active = activeBlock == i
            val chorus = b.kind == BlockKind.CHORUS
            val bg by animateColorAsState(
                when { active -> c.accent.copy(alpha = if (c.dark) 0.22f else 0.12f); chorus -> c.accent.copy(alpha = if (c.dark) 0.10f else 0.05f); else -> Color.Transparent }, label = "blockBg")
            Row(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { onBlockPositioned?.invoke(i, it.positionInParent().y) }
                    .clip(RoundedCornerShape(14.dp)).background(bg)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress?.let { f -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); f(b) } })
                    .padding(horizontal = if (chorus || active) 12.dp else 0.dp, vertical = if (chorus || active) 12.dp else 4.dp),
            ) {
                // Gutter: verse number
                Box(Modifier.width(30.dp).padding(top = 2.dp)) {
                    if (b.kind == BlockKind.VERSE && b.number != null) Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (active) c.accent else c.fill), contentAlignment = Alignment.Center,
                    ) { Text("${b.number}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else c.secondary) }
                }
                Column(Modifier.weight(1f)) {
                    if (chorus) Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CHORUS", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = c.accent)
                        if (showMarkings) {
                            b.timeSignature?.let { MarkChip(it.replace("/", " / ")) }
                            b.tempo?.let { MarkChip(it, italic = true) }
                            if (b.segno) Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Repeat, null, tint = c.secondary, modifier = Modifier.size(14.dp))
                                Text(" repeat", fontSize = 12.sp, color = c.secondary)
                            }
                            b.dynamic?.let { Spacer(Modifier.width(8.dp)); DynamicBadge(it) { onDynamic(it) } }
                        }
                    }
                    if (!chorus && showMarkings && b.tempo != null) Row(Modifier.padding(bottom = 4.dp)) { MarkChip(b.tempo, italic = true) }
                    b.lines.forEachIndexed { li, line ->
                        LyricLine(line, i == 0 && li == 0, fontSize, family, myPart, showMarkings, onDynamic)
                    }
                }
            }
            if (i != blocks.lastIndex) Spacer(Modifier.height((fontSize * 0.7f).dp))
        }
    }
}

@Composable
private fun MarkChip(text: String, italic: Boolean = false) {
    val c = LocalIos.current
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = c.secondary,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(5.dp)).background(c.fill).padding(horizontal = 6.dp, vertical = 1.dp))
}

@Composable
private fun LyricLine(line: HymnLine, first: Boolean, fontSize: Float, family: FontFamily, myPart: Voice?, showMarkings: Boolean, onDynamic: (String) -> Unit) {
    val c = LocalIos.current
    val mine = myPart == null || line.part == null || myPart in line.part.voices
    Row(Modifier.alpha(if (mine) 1f else 0.35f).padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        if (showMarkings && (line.showPart && line.part != null || line.dynamic != null)) Row(Modifier.padding(top = (fontSize * 0.18f).dp), verticalAlignment = Alignment.CenterVertically) {
            if (line.showPart && line.part != null) PartPill(line.part)
            line.dynamic?.let { DynamicBadge(it) { onDynamic(it) } }
        }
        Text(
            buildAnnotatedString {
                line.segments.forEachIndexed { si, s ->
                    when {
                        s.dynamic != null -> if (showMarkings) withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = (fontSize * 0.8f).sp)) { append(" ${s.dynamic} ") }
                        s.echo -> withStyle(SpanStyle(color = c.secondary, fontStyle = FontStyle.Italic)) { append(s.text) }
                        first && si == 0 -> {
                            val t = HymnFormat.softenFirstWord(s.text.trimStart())
                            withStyle(SpanStyle(fontSize = (fontSize * 1.55f).sp, fontWeight = FontWeight.Bold, color = c.accent)) { append(t.take(1)) }
                            append(t.drop(1))
                        }
                        else -> append(s.text)
                    }
                }
            },
            fontSize = fontSize.sp, lineHeight = (fontSize * 1.45f).sp, color = c.label, fontFamily = family,
        )
    }
}

/** "Key" explaining the markings. */
@Composable
fun MarkingsKey() {
    val c = LocalIos.current
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Who sings", fontWeight = FontWeight.SemiBold, color = c.label, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        listOf(Voice.S to "Soprano (Sop, S)", Voice.A to "Alto (A)", Voice.T to "Tenor (Ten, T)", Voice.B to "Bass (B)").forEach { (v, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(voiceColor(v))); Spacer(Modifier.width(10.dp)); Text(t, color = c.label, fontSize = 15.sp)
            }
        }
        Text("Combined labels like ATB or S/A mean those voices together. Gen, All, Unison, Parts = everyone.", color = c.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(14.dp))
        Text("How to sing", fontWeight = FontWeight.SemiBold, color = c.label, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        listOf("pp", "p", "mf", "f", "ff", "rit.", "dim.").forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.width(48.dp)) { DynamicBadge(d) }
                Text(HymnFormat.DYNAMICS[d] ?: "", color = c.label, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Other marks", fontWeight = FontWeight.SemiBold, color = c.label, fontSize = 15.sp)
        Text("(words in brackets) — echo / answering part\nrepeat — go back to the repeat sign\n12 / 8 — time signature of the chorus", color = c.secondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
