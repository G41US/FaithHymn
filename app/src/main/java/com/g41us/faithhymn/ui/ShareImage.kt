package com.g41us.faithhymn.ui

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.g41us.faithhymn.audio.titleCase
import com.g41us.faithhymn.data.Hymn

data class CardStyle(val name: String, val colors: IntArray, val text: Int, val sub: Int, val serif: Boolean = true)

val CardStyles = listOf(
    CardStyle("Classic", intArrayOf(0xFF8E6CFF.toInt(), 0xFF3A2BC0.toInt()), Color.WHITE, 0xCCFFFFFF.toInt()),
    CardStyle("Sunrise", intArrayOf(0xFFFFB36B.toInt(), 0xFFE0406A.toInt()), Color.WHITE, 0xD9FFFFFF.toInt()),
    CardStyle("Paper", intArrayOf(0xFFFBF6EC.toInt(), 0xFFEFE3CC.toInt()), 0xFF3B2F22.toInt(), 0xAA3B2F22.toInt()),
    CardStyle("Night", intArrayOf(0xFF1E1812.toInt(), 0xFF0C0906.toInt()), 0xFFF1DFC2.toInt(), 0x99F1DFC2.toInt()),
    CardStyle("Ocean", intArrayOf(0xFF43C6AC.toInt(), 0xFF145E7A.toInt()), Color.WHITE, 0xCCFFFFFF.toInt()),
    CardStyle("Modern", intArrayOf(0xFFFFFFFF.toInt(), 0xFFF2F2F7.toInt()), 0xFF111111.toInt(), 0x99111111.toInt(), serif = false),
)

/** Verse card, 1080x1350 (Instagram/WhatsApp-friendly). [scale] < 1 renders a smaller preview. */
fun renderVerseCard(h: Hymn, verse: String, style: CardStyle, scale: Float = 1f): Bitmap {
    val w = (1080 * scale).toInt(); val hgt = (1350 * scale).toInt(); val pad = 110 * scale
    val bmp = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), hgt.toFloat(), style.colors, null, Shader.TileMode.CLAMP) })
    // Big faint quote mark
    cv.drawText("“", w - pad * 2.2f, pad * 2.6f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.text; alpha = 28; textSize = 420f * scale; typeface = CanvasFonts.serifBold })

    val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.sub; textSize = 34f * scale; typeface = CanvasFonts.sansBold; letterSpacing = 0.12f }
    cv.drawText("HYMN ${h.number}", pad, pad + 40 * scale, small)
    val face = if (style.serif) CanvasFonts.serif else CanvasFonts.sans
    val faceBold = if (style.serif) CanvasFonts.serifBold else CanvasFonts.sansBold
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.text; textSize = 58f * scale; typeface = faceBold }
    val t = h.title.titleCase()
    val tl = StaticLayout.Builder.obtain(t, 0, t.length, title, (w - pad * 2).toInt()).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    cv.save(); cv.translate(pad, pad + 70 * scale); tl.draw(cv); cv.restore()

    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = style.text; typeface = face }
    val top = pad + 90 * scale + tl.height + 50 * scale; val bottom = hgt - pad - 70 * scale
    var size = 56f * scale; var layout: StaticLayout
    do {
        body.textSize = size
        layout = StaticLayout.Builder.obtain(verse, 0, verse.length, body, (w - pad * 2).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.28f).build()
        size -= 2f * scale
    } while (layout.height > bottom - top && size > 20f * scale)
    cv.save(); cv.translate(pad, top + ((bottom - top - layout.height).coerceAtLeast(0f)) / 2f); layout.draw(cv); cv.restore()
    cv.drawText("FaithHymns", pad, hgt - pad, small)
    return bmp
}

fun shareVerse(ctx: Context, h: Hymn, verse: String, style: CardStyle) {
    shareBitmap(ctx, renderVerseCard(h, verse, style), "hymn_${h.number}", "Share verse")
}
