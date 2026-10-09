package app.masroufy.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import app.masroufy.core.Language
import app.masroufy.core.Texts

/**
 * الخط (DESIGN-SYSTEM «الخط والأرقام»): **Noto Sans Arabic** (400 · 500 · 700) — على أندرويد من خدمة خطوط جوجل على الجهاز
 * (من غير ملف خط في المستودع)، ولو ما نزلش ⇒ خط الجهاز (المقابل لـ`Tahoma, sans-serif` في النموذج).
 */
@Composable
expect fun rememberMasroufyFont(): FontFamily

val LocalFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** أنماط النص بالأسماء — المقاسات من `tokens.json` بس. */
object Type {
    @Composable fun title() = style(TextSize.title, FontWeight.Bold, 1.5)
    @Composable fun section() = style(TextSize.section, FontWeight.Bold, 1.4)
    @Composable fun body() = style(TextSize.body, FontWeight.Normal, 1.6)
    @Composable fun bodyBold() = style(TextSize.body, FontWeight.Bold, 1.5)
    @Composable fun caption() = style(TextSize.caption, FontWeight.Normal, 1.5)
    @Composable fun captionBold() = style(TextSize.caption, FontWeight.Bold, 1.5)

    /** نص بمقاس معيّن من النموذج (13 · 15 · 16 …) — نفس الخط واللون. */
    @Composable fun of(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Double = 1.5) = style(size, weight, lineHeight)

    @Composable
    private fun style(size: Int, weight: FontWeight, lineHeight: Double) = TextStyle(
        fontFamily = LocalFont.current, fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.em, color = Ink.text,
        textDirection = TextDirection.Content,
    )
}

/**
 * الثيم: الخط · الاتجاه (عربي ⇒ يمين لشمال، إنجليزي ⇒ شمال ليمين) · «تقليل الحركة». مفيش Material — كل حاجة من رموز v0.4.
 * [reduceMotion] من إعدادات الجهاز (التطبيق بيقراها).
 */
@Composable
fun MasroufyTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val direction = if (Texts.language == Language.EN) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(
        LocalFont provides rememberMasroufyFont(),
        LocalLayoutDirection provides direction,
        LocalReduceMotion provides reduceMotion,
        LocalTextSelectionColors provides TextSelectionColors(handleColor = Ink.primary, backgroundColor = Ink.selected),
        content = content,
    )
}
