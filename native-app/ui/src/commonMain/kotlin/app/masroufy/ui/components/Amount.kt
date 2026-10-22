package app.masroufy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import app.masroufy.ui.text.NO_BREAK_SPACE
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.MoneyPrivacy
import app.masroufy.core.NOT_AVAILABLE
import app.masroufy.core.UiKey
import app.masroufy.core.absMoney
import app.masroufy.core.currencySymbol
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.core.withCurrency
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.t
import app.masroufy.ui.text.trueMinus
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * المبالغ (DESIGN-SYSTEM «الخط والأرقام»): `dir=ltr` + أرقام جدولية (`tnum`) + علامة الطرح الحقيقية «−» للصرف و«+» للدخل.
 * **الشاشة ما بتحسبش**: المبلغ جاي جاهز من حالة الاستخدام بالهللة، وده بيعرضه بس (`formatMoney` من `core`).
 * `null` ⇒ «غير متاح» (CLAUDE.md #10) — **عمره ما يبقى صفر**.
 */
enum class AmountTone(private val colorLazy: () -> Color, val sign: String) {
    /** صرف: أحمر بـ«−». */
    EXPENSE({ Ink.expense }, "−"),

    /** دخل: أخضر بـ«+». */
    INCOME({ Ink.income }, "+"),

    /** تحويل بين محافظك: لون التحويل المحايد من غير علامة، ورمز «منه فيه» مكانها في [AmountText] (HANDOVER §6.5-10 — `MoveLook.kt`). */
    TRANSFER({ Ink.transfer }, ""),

    /** رقم عادي (رصيد · مجموع): لون النص، والسالب بـ«−». */
    PLAIN({ Ink.text }, ""),
    ;
    val color: Color get() = colorLazy()
}

/** نمط الأرقام الجدولية. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr)

/** نص المبلغ (من غير رسم) — نفس اللي [AmountText] بيعرضه، للجمل والاسم المقروء. */
fun amountLabel(minor: Halalas?, currency: Currency, tone: AmountTone = AmountTone.PLAIN, showCurrency: Boolean = true): String {
    if (minor == null) return NOT_AVAILABLE
    val number = trueMinus(amount(if (tone == AmountTone.EXPENSE || tone == AmountTone.INCOME) absMoney(minor) else minor, currency))
    val signed = (if (MoneyPrivacy.isHidden) "" else tone.sign) + number
    return if (showCurrency) withCurrency(signed, currency) else signed
}

/**
 * مبلغ في سطر: «⃁ −42.00» من الشمال لليمين (علامة الريال الرسمية شمال الرقم). [size] بالـsp من مقاسات النموذج (15 في الصفوف · 16 · 30 · 38 للبطل).
 * [color] = null ⇒ لون النبرة.
 */
@Composable
fun AmountText(
    minor: Halalas?,
    currency: Currency,
    modifier: Modifier = Modifier,
    tone: AmountTone = AmountTone.PLAIN,
    size: Int = 15,
    weight: FontWeight = FontWeight.Bold,
    showCurrency: Boolean = true,
    color: Color? = null,
) {
    if (minor == null) {
        BasicText(NOT_AVAILABLE, modifier, style = Type.of(size, weight).copy(color = color ?: Ink.muted))
        return
    }
    val style = Type.of(size, weight, 1.3).copy(color = color ?: tone.color).tabular()
    if (tone == AmountTone.TRANSFER) return MoveAmountText(amountLabel(minor, currency, tone, showCurrency), modifier, style, size)
    BasicText(amountLabel(minor, currency, tone, showCurrency), modifier, style = style, maxLines = 1, softWrap = false)
}

/**
 * تحويل بين محافظك (HANDOVER §6.5-10): رمز «منه فيه» ([MOVE_SWAP]) **مكان العلامة** على شمال الرقم، بنفس لون المبلغ المحايد.
 * الرمز عليه وصف لقارئ الشاشة («تحويل بين محافظك») عشان العلامة الغايبة ما تتقريش «صرف».
 */
@Composable
private fun MoveAmountText(label: String, modifier: Modifier, style: TextStyle, size: Int) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            LucideIcon(MOVE_SWAP, size = (size * 0.9).dp, tint = style.color, contentDescription = t(UiKey.MOVE_SYMBOL))
            BasicText(label, style = style, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * الرقم الكبير (البطاقة البطلة): العملة صغيرة على الشمال والرقم كبير جنبها، زي النموذج (`dir=ltr` · 18 + 38).
 * [onHero] ⇒ أبيض. `null` ⇒ «غير متاح» 30 عريض.
 */
@Composable
fun HeroAmount(minor: Halalas?, currency: Currency, modifier: Modifier = Modifier, onHero: Boolean = true, size: Int = 38) {
    val ink = if (onHero) Ink.onPrimary else Ink.text
    if (minor == null) {
        BasicText(NOT_AVAILABLE, modifier, style = Type.of(30, FontWeight.Bold).copy(color = ink))
        return
    }
    // نص واحد على سطر واحد (المالك 2026-10-15 — العلامة كانت بتنزل تحت الرقم لوحدها): العلامة والرقم على نفس الخط، ومسافة
    // ما بتنكسرش بينهم، والرقم الطويل بيصغر لحد ما يدخل بدل ما يتقطع أو ينزل سطر
    BasicText(
        heroAmountText(trueMinus(amount(minor, currency)), currencySymbol(currency), size),
        modifier,
        style = Type.of(size, FontWeight.Bold, 1.2).copy(color = ink, letterSpacing = (-0.5).sp, textAlign = TextAlign.Right).tabular(),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = HERO_MIN_SP.sp, maxFontSize = size.sp, stepSize = 1.sp),
    )
}

/** أصغر مقاس للرقم الكبير لما المبلغ طويل والمكان ضيق. */
internal const val HERO_MIN_SP = 18

/** العلامة صغيرة (18 من [size] زي النموذج) على الشمال + مسافة ما بتنكسرش + الرقم — بـ`em` عشان تصغر مع الرقم. */
internal fun heroAmountText(number: String, symbol: String, size: Int): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = (18f / size).em, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)) { append(symbol) }
    append(NO_BREAK_SPACE)
    append(number)
}
