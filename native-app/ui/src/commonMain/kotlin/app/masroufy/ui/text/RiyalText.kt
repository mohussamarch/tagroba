package app.masroufy.ui.text

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import app.masroufy.core.RIYAL_SIGN
import androidx.compose.foundation.text.BasicText as FoundationBasicText

/**
 * علامة الريال الرسمية (U+20C1 · قرار المالك 2026-10-15): خطوط الجوال لسه ما فيهاش العلامة، فبنرسمها بخط صغير
 * فيه **العلامة بس** (`res/font/riyal_sign.otf` — معمول من ملف ساما الرسمي `Saudi_Riyal_Symbol-2.svg`، ومنقول من فرع
 * التصميم `claude/design-system-v0-4-90a69a` · `Account.dc.html`). الأرقام والكلام بيفضلوا بخط التطبيق.
 * `null` ⇒ مفيش خط للعلامة على المنصة دي (سطح المكتب · الآيفون لسه) والنص بيتعرض زي ما هو.
 */
@Composable
expect fun rememberRiyalFont(): FontFamily?

val LocalRiyalFont = staticCompositionLocalOf<FontFamily?> { null }

/** **المساعد الوحيد**: كل حرف U+20C1 بياخد خط العلامة، وباقي النص (والتنسيقات اللي فيه) زي ما هو. */
fun withRiyalSign(text: AnnotatedString, font: FontFamily): AnnotatedString {
    if (text.text.indexOf(RIYAL_SIGN) < 0) return text
    val sign = SpanStyle(fontFamily = font)
    return buildAnnotatedString {
        append(text)
        text.text.forEachIndexed { i, c -> if (c == RIYAL_SIGN) addStyle(sign, i, i + 1) }
    }
}

/**
 * بديل `BasicText` بتاع Compose بنفس الاسم والمعاملات — كل ملفات `ui/` بتستورده من هنا، فأي نص فيه العلامة
 * (مبلغ · جملة · وحدة حقل) بيترسم صح من غير ما الشاشة تعرف حاجة.
 */
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    color: ColorProducer? = null,
) {
    val riyal = LocalRiyalFont.current
    if (text.indexOf(RIYAL_SIGN) < 0) {
        FoundationBasicText(
            text = text, modifier = modifier, style = style, onTextLayout = onTextLayout, overflow = overflow,
            softWrap = softWrap, maxLines = maxLines, minLines = minLines, color = color,
        )
    } else if (riyal == null) {
        FoundationBasicText(
            text = keepRiyalTogether(text), modifier = modifier, style = style, onTextLayout = onTextLayout, overflow = overflow,
            softWrap = softWrap, maxLines = maxLines, minLines = minLines, color = color,
        )
    } else {
        FoundationBasicText(
            text = withRiyalSign(AnnotatedString(keepRiyalTogether(text)), riyal), modifier = modifier, style = style, onTextLayout = onTextLayout,
            overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, color = color,
        )
    }
}

/** مسافة ما بتنكسرش (U+00A0). */
const val NO_BREAK_SPACE: Char = ' '

/**
 * العلامة والرقم **عمرهم ما يتفرقوا على سطرين** (المالك 2026-10-15 — العلامة كانت بتنزل لوحدها تحت الرقم): المسافة اللي بعد
 * U+20C1 (من `withCurrency` — «⃁ 96.47») بتبقى مسافة ما بتنكسرش وقت الرسم بس. النص في `core` والاختبارات زي ما هو.
 */
fun keepRiyalTogether(text: String): String = text.replace("$RIYAL_SIGN ", "$RIYAL_SIGN$NO_BREAK_SPACE")

/** نفس [keepRiyalTogether] للنص المنسّق: نفس الطول، فالتنسيقات والروابط بترجع في نفس أماكنها. */
fun keepRiyalTogether(text: AnnotatedString): AnnotatedString {
    val plain = keepRiyalTogether(text.text)
    if (plain == text.text) return text
    return buildAnnotatedString {
        append(plain)
        text.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        text.paragraphStyles.forEach { addStyle(it.item, it.start, it.end) }
        text.getStringAnnotations(0, text.length).forEach { addStringAnnotation(it.tag, it.item, it.start, it.end) }
        text.getLinkAnnotations(0, text.length).forEach { r ->
            when (val link = r.item) {
                is LinkAnnotation.Url -> addLink(link, r.start, r.end)
                is LinkAnnotation.Clickable -> addLink(link, r.start, r.end)
            }
        }
    }
}

@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    color: ColorProducer? = null,
    autoSize: TextAutoSize? = null,
) {
    val riyal = LocalRiyalFont.current
    val together = keepRiyalTogether(text)
    FoundationBasicText(
        text = if (riyal == null) together else withRiyalSign(together, riyal), modifier = modifier, style = style, onTextLayout = onTextLayout,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, inlineContent = inlineContent, color = color,
        autoSize = autoSize,
    )
}
