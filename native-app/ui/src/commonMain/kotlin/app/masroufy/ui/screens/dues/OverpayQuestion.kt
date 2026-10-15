package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Overpay
import app.masroufy.core.OverpayChoice
import app.masroufy.core.UiKey
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.OverpayOutcome

/** الاختيارات بترتيب المالك (§82-19): أمانة ليك عنده · دعم · سلفة جديدة ليه · غلطة في المبلغ. */
val OVERPAY_CHOICES: List<OverpayChoice> = listOf(OverpayChoice.HELD_FOR_YOU, OverpayChoice.SUPPORT, OverpayChoice.NEW_LOAN, OverpayChoice.WRONG_AMOUNT)

fun overpayLabel(c: OverpayChoice): String = t(
    when (c) {
        OverpayChoice.HELD_FOR_YOU -> UiKey.OVERPAY_HELD
        OverpayChoice.SUPPORT -> UiKey.OVERPAY_SUPPORT
        OverpayChoice.NEW_LOAN -> UiKey.OVERPAY_LOAN
        OverpayChoice.WRONG_AMOUNT -> UiKey.OVERPAY_WRONG
    },
)

/** «عليك 500 بس ودفعت 700 — الزيادة 200» — الأرقام التلاتة من حالة الاستخدام ([Overpay]) زي ما هي (عرض بس، من غير حساب). */
fun overpayBody(o: Overpay, currency: Currency): String =
    t(UiKey.OVERPAY_BODY, amountLabel(o.openMinor, currency), amountLabel(o.paidMinor, currency), amountLabel(o.surplusMinor, currency))

/** رسالة بعد الرد (null لـ«غلطة في المبلغ» — الشاشة بترجع للمبلغ). */
fun overpaySavedToast(outcome: OverpayOutcome, choice: OverpayChoice, personName: String, currency: Currency): String? {
    val extra = amountLabel(outcome.overpay.surplusMinor, currency)
    return when (choice) {
        OverpayChoice.HELD_FOR_YOU -> t(UiKey.OVERPAY_SAVED_HELD, extra, personName)
        OverpayChoice.SUPPORT -> t(UiKey.OVERPAY_SAVED_SUPPORT, extra, personName)
        OverpayChoice.NEW_LOAN -> t(UiKey.OVERPAY_SAVED_LOAN, extra, personName)
        OverpayChoice.WRONG_AMOUNT -> null
    }
}

/**
 * سؤال «دفعت أكتر من اللي عليك» جوه لوحة السداد (§82-19 — رأي المالك: «يظهرله اوبشنز يختار ما بينهم»): الجملة بالأرقام + أربع زراير.
 * [busy] = الحفظ شغال ⇒ الزراير مقفولة.
 */
@Composable
internal fun OverpayQuestion(overpay: Overpay, currency: Currency, busy: Boolean, onChoose: (OverpayChoice) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(UiKey.OVERPAY_TITLE), style = Type.bodyBold())
        BasicText(overpayBody(overpay, currency), style = Type.caption().copy(color = Ink.muted))
        for (c in OVERPAY_CHOICES) {
            TonalButton(overpayLabel(c), { onChoose(c) }, Modifier.fillMaxWidth(), enabled = !busy, height = 44.dp, muted = c == OverpayChoice.WRONG_AMOUNT)
        }
    }
}
