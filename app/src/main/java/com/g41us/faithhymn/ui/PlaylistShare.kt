package com.g41us.faithhymn.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.PlaylistItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SharedPlaylist(val name: String, val date: String?, val items: List<Pair<Int, String?>>)

object PlaylistLink {
    /** faithhymns://playlist?n=Sunday%20Service&d=2026-11-29&h=1~Opening,23,45~Closing */
    fun build(name: String, date: String?, items: List<PlaylistItem>): String = Uri.Builder().scheme("faithhymns").authority("playlist")
        .appendQueryParameter("n", name)
        .apply { if (date != null) appendQueryParameter("d", date) }
        .appendQueryParameter("h", items.joinToString(",") { i -> i.hymn.number.toString() + (i.slot?.let { "~$it" } ?: "") })
        .build().toString()

    fun parse(uri: Uri): SharedPlaylist? = runCatching {
        if (uri.scheme != "faithhymns" || uri.host != "playlist") return null
        val items = uri.getQueryParameter("h").orEmpty().split(",").filter { it.isNotBlank() }.mapNotNull { p ->
            val n = p.substringBefore("~").toIntOrNull()?.takeIf { it in 1..1001 } ?: return@mapNotNull null
            n to p.substringAfter("~", "").ifBlank { null }
        }
        SharedPlaylist(uri.getQueryParameter("n")?.take(60)?.ifBlank { null } ?: "Shared playlist", uri.getQueryParameter("d"), items)
    }.getOrNull()
}

fun qrBitmap(text: String, size: Int = 720, dark: Int = 0xFF000000.toInt()): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m[i % size, i / size]) dark else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
fun qrImage(text: String): ImageBitmap = qrBitmap(text).asImageBitmap()

fun prettyDate(iso: String?): String? = iso?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")) }.getOrNull() }

fun sharePlaylistText(ctx: Context, name: String, date: String?, items: List<PlaylistItem>) {
    val link = PlaylistLink.build(name, date, items)
    val body = buildString {
        append("🎵 $name"); prettyDate(date)?.let { append(" — $it") }; append("\n\n")
        items.forEachIndexed { i, it -> append("${i + 1}. ${it.slot?.let { s -> "[$s] " } ?: ""}Hymn ${it.hymn.number} — ${it.hymn.title.titleCase()}\n") }
        append("\nOpen in FaithHymns: $link")
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Share playlist"))
}

fun shareBitmap(ctx: Context, bmp: Bitmap, name: String, title: String) {
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "$name.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), title))
}
