package com.g41us.faithhymn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.HymnSummary

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val c = LocalIos.current
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, color = c.label, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = LocalIos.current.secondary,
        modifier = Modifier.padding(start = 36.dp, end = 20.dp, top = 22.dp, bottom = 7.dp))
}

/** iOS inset-grouped container. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LocalIos.current.card), content = content)
}

@Composable
fun Separator(start: Int = 16) {
    Box(Modifier.padding(start = start.dp).fillMaxWidth().height(0.5.dp).background(LocalIos.current.separator))
}

/** Square rounded "settings" style icon, like iOS. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Int = 29) {
    // Calm, single-colour tiles (tint kept for API compatibility).
    val c = LocalIos.current
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.27f).dp)).background(c.fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size((size * 0.6f).dp))
    }
}

@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: (@Composable () -> Unit)? = null,
    value: String? = null, chevron: Boolean = false, titleColor: Color? = null, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalIos.current
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = titleColor ?: c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, fontSize = 17.sp, color = c.secondary)
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = c.tertiary, modifier = Modifier.padding(start = 8.dp).size(14.dp))
    }
}

/** List row for a hymn: number badge, title, first line or search snippet. */
@Composable
fun HymnRow(h: HymnSummary, onClick: () -> Unit, audio: Boolean = false, offline: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalIos.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${h.number}", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.width(38.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.title.titleCase(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (offline) Icon(androidx.compose.material.icons.Icons.Rounded.DownloadDone, "Saved offline", tint = c.accent, modifier = Modifier.padding(start = 6.dp).size(15.dp))
                else if (audio) Icon(androidx.compose.material.icons.Icons.Rounded.Headphones, "Has recording", tint = c.accent, modifier = Modifier.padding(start = 6.dp).size(15.dp))
            }
            val sub = h.snippet?.replace('\n', ' ')
            if (sub != null) Text(highlight(sub, c.accent), fontSize = 14.sp, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            else Text(h.firstLine, fontSize = 14.sp, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Turns FTS snippet "[match]" markers into bold accent text. */
fun highlight(s: String, color: Color) = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf('[', i); val b = if (a >= 0) s.indexOf(']', a) else -1
        if (a < 0 || b < 0) { append(s.substring(i)); break }
        append(s.substring(i, a))
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(s.substring(a + 1, b)) }
        i = b + 1
    }
}

@Composable
fun NavBar(title: String, onBack: () -> Unit, backLabel: String = "Back", actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalIos.current
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Text(backLabel, color = c.accent, fontSize = 17.sp)
        }
        Text(title, Modifier.align(Alignment.Center).widthIn(max = 200.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.foundation.layout.Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun CircleButton(icon: ImageVector, onClick: () -> Unit, size: Int = 36, tint: Color? = null, bg: Color? = null) {
    val c = LocalIos.current
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg ?: c.fill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint ?: c.accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** iOS segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalIos.current
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(c.fill).padding(2.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                    .background(if (i == selected) (if (c.dark) Color(0xFF3A3530) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, color = c.label) }
        }
    }
}

fun fmtTime(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
