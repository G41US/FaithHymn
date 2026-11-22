package com.g41us.faithhymn.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.g41us.faithhymn.R

/** App UI: Plus Jakarta Sans. */
val Sans = FontFamily(
    Font(R.font.jakarta_400, FontWeight.Normal), Font(R.font.jakarta_500, FontWeight.Medium),
    Font(R.font.jakarta_600, FontWeight.SemiBold), Font(R.font.jakarta_700, FontWeight.Bold),
    Font(R.font.jakarta_800, FontWeight.ExtraBold), Font(R.font.jakarta_800, FontWeight.Black),
)

/** Hymn lyrics: Source Serif 4. */
val Serif = FontFamily(
    Font(R.font.sourceserif_400, FontWeight.Normal), Font(R.font.sourceserif_400, FontWeight.Medium),
    Font(R.font.sourceserif_600, FontWeight.SemiBold), Font(R.font.sourceserif_700, FontWeight.Bold),
    Font(R.font.sourceserif_italic_400, FontWeight.Normal, FontStyle.Italic), Font(R.font.sourceserif_italic_700, FontWeight.Bold, FontStyle.Italic),
)

private val base = TextStyle(fontFamily = Sans, letterSpacing = 0.sp)

val AppTypography = Typography(
    displayLarge = base.copy(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp),
    headlineLarge = base.copy(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.6).sp),
    headlineMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
    headlineSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

/** Same fonts for images drawn on a Canvas (verse cards). */
object CanvasFonts {
    lateinit var sans: Typeface; lateinit var sansBold: Typeface; lateinit var serif: Typeface; lateinit var serifBold: Typeface
    fun init(ctx: Context) {
        sans = ResourcesCompat.getFont(ctx, R.font.jakarta_500) ?: Typeface.DEFAULT
        sansBold = ResourcesCompat.getFont(ctx, R.font.jakarta_800) ?: Typeface.DEFAULT_BOLD
        serif = ResourcesCompat.getFont(ctx, R.font.sourceserif_400) ?: Typeface.SERIF
        serifBold = ResourcesCompat.getFont(ctx, R.font.sourceserif_700) ?: Typeface.SERIF
    }
}
