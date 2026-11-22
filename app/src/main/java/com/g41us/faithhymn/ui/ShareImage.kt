package com.g41us.faithhymn.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn
import java.io.File

/** Renders a verse onto a 1080x1350 gradient card (Instagram/WhatsApp-friendly) and opens the share sheet. */
fun shareImage(ctx: Context, h: Hymn, verse: String) {
    val w = 1080; val hgt = 1350; val pad = 110
    val bmp = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), hgt.toFloat(), 0xFF8E6CFF.toInt(), 0xFF3A2BC0.toInt(), Shader.TileMode.CLAMP) })

    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f }
    cv.drawText("HYMN ${h.number}", pad.toFloat(), (pad + 40).toFloat(), small)

    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 60f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    val tl = StaticLayout.Builder.obtain(h.title.titleCase(), 0, h.title.length, title, w - pad * 2).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    cv.save(); cv.translate(pad.toFloat(), (pad + 70).toFloat()); tl.draw(cv); cv.restore()

    // Fit the verse: shrink font until it fits the space.
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.SERIF }
    val top = pad + 90 + tl.height + 50; val bottom = hgt - pad - 70
    var size = 54f; var layout: StaticLayout
    do {
        body.textSize = size
        layout = StaticLayout.Builder.obtain(verse, 0, verse.length, body, w - pad * 2).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
        size -= 2f
    } while (layout.height > bottom - top && size > 22f)
    cv.save(); cv.translate(pad.toFloat(), (top + (bottom - top - layout.height).coerceAtLeast(0) / 2f)); layout.draw(cv); cv.restore()

    cv.drawText("FaithHymns", pad.toFloat(), (hgt - pad).toFloat(), small)

    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "hymn_${h.number}.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share verse"))
}
