package app.masroufy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.NOT_AVAILABLE
import app.masroufy.core.absMoney
import app.masroufy.core.currencySymbol
import app.masroufy.ui.text.amount
import app.masroufy.ui.text.trueMinus
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * المبالغ (DESIGN-SYSTEM «الخط والأرقام»): `dir=ltr` + أرقام جدولية (`tnum`) + علامة الطرح الحقيقية «−» للصرف و«+» للدخل.
 * **الشاشة ما بتحسبش**: المبلغ جاي جاهز من حالة الاستخدام بالهللة، وده بيعرضه بس (`formatMoney` من `core`).
 * `null` ⇒ «غير متاح» (CLAUDE.md #10) — **عمره ما يبقى صفر**.
 */
enum class AmountTone(val color: Color, val sign: String) {
    /** صرف: أحمر بـ«−». */
    EXPENSE(Ink.expense, "−"),

    /** دخل: أخضر بـ«+». */
    INCOME(Ink.income, "+"),

    /** تحويل داخلي: أزرق من غير علامة. */
    TRANSFER(Ink.transfer, ""),

    /** رقم عادي (رصيد · مجموع): لون النص، والسالب بـ«−». */
    PLAIN(Ink.text, ""),
}

/** نمط الأرقام الجدولية. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum", textDirection = TextDirection.Ltr)

/** نص المبلغ (من غير رسم) — نفس اللي [AmountText] بيعرضه، للجمل والاسم المقروء. */
fun amountLabel(minor: Halalas?, currency: Currency, tone: AmountTone = AmountTone.PLAIN, showCurrency: Boolean = true): String {
    if (minor == null) return NOT_AVAILABLE
    val number = trueMinus(amount(if (tone == AmountTone.EXPENSE || tone == AmountTone.INCOME) absMoney(minor) else minor, currency))
    val signed = tone.sign + number
    return if (showCurrency) "$signed ${currencySymbol(currency)}" else signed
}

/**
 * مبلغ في سطر: «−42.00 ر.س» من الشمال لليمين. [size] بالـsp من مقاسات النموذج (15 في الصفوف · 16 · 30 · 38 للبطل).
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
    BasicText(
        amountLabel(minor, currency, tone, showCurrency),
        modifier,
        style = Type.of(size, weight, 1.3).copy(color = color ?: tone.color).tabular(),
        maxLines = 1,
    )
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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.Bottom) {
            BasicText(currencySymbol(currency), style = Type.of(18).copy(color = ink, lineHeight = 1.6.em))
            BasicText(
                trueMinus(amount(minor, currency)),
                style = Type.of(size, FontWeight.Bold, 1.2).copy(color = ink, letterSpacing = (-0.5).sp).tabular(),
                maxLines = 1,
            )
        }
    }
}
