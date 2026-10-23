package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.FxSettleError
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.Settlement
import app.masroufy.core.SettlementFx
import app.masroufy.core.TextKey
import app.masroufy.core.fxDebtMinor
import app.masroufy.core.uiText

/**
 * §111-4 سداد دين بعملة البلد التانية: المالك بيكتب المبلغ بالعملة اللي اتدفع بيها والسعر، والدين بينزل بالمبلغ بعد التحويل (عدد صحيح بالهللة —
 * `core/FxSettle.kt`)، والسعر والمدفوع بيتسجلوا على التسوية. **السعر إجباري**: مفيش سعر للجنيه في ملف الأسعار ([prefillRate4] = null).
 */
class SettleOtherCurrency internal constructor(private val deps: ManagePeopleDeps, private val people: ManagePeople) {
    /** سعر جاهز من مصدر أسعار البرنامج — مفيش مصدر للجنيه دلوقتي (cty-11) ⇒ null والخانة فاضية. */
    fun prefillRate4(@Suppress("UNUSED_PARAMETER") paid: Currency, @Suppress("UNUSED_PARAMETER") debt: Currency): Long? = null

    /** اللي هينزل من الدين بعملته — null = المبلغ أو السعر ناقص/غلط (مش صفر). */
    fun quote(paidMinor: Halalas?, rate4: Long?): Halalas? = fxDebtMinor(paidMinor, rate4)

    suspend fun settle(obligationId: Id, personId: Id, paidMinor: Halalas, paidCurrency: Currency, rate4: Long?, requestId: String? = null): Settlement {
        val obligation = deps.obligations.listByPerson(personId).firstOrNull { it.id == obligationId }
            ?: throw IllegalStateException(uiText(TextKey.DEBT_NOT_FOUND))
        if (paidCurrency == obligation.currency) throw FxSettleError(uiText(R06Key.CTY_FX_SAME_CURRENCY))
        val debtMinor = fxDebtMinor(paidMinor, rate4) ?: throw FxSettleError(uiText(R06Key.CTY_FX_RATE_NEEDED))
        return people.settle(obligationId, personId, debtMinor, requestId = requestId, fx = SettlementFx(paidMinor, paidCurrency, rate4!!))
    }
}
