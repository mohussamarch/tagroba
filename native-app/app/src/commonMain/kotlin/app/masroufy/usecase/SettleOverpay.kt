package app.masroufy.usecase

import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Overpay
import app.masroufy.core.OverpayChoice
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.overpayOf
import app.masroufy.core.overpayable
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.uiText

/**
 * §82-19 «دفعت أكتر من اللي عليك» من لوحة السداد (تسوية **من غير عملية** — `SettleForm`): المبلغ أكبر من الباقي على دين **عليك** ⇒ بدل
 * الرفض، سؤال بأربع اختيارات ([OverpayChoice]). الدين نفسه بيتسدّد بالباقي بالظبط (نفس `ManagePeople.settle` — نفس الفحص الذرّي)، والزيادة:
 * - «أمانة ليك عنده» ⇒ دين **ليك** عليه بالزيادة (`RECEIVABLE` + `custodyHeld`) · «سلفة جديدة ليه» ⇒ دين ليك عادي — الاتنين من غير
 *   عملية (زي الدين القديم §27) و**بنفس عملة الدين** (مش الريال دايمًا).
 * - «دعم» ⇒ الدين بيتسدّد بس. §88 «الدعم مصروف» بيتطبق على العملية اللي الفلوس خرجت فيها (`AnswerTransferAsks.answerOverpay`)؛ هنا مفيش
 *   عملية أصلًا (التسوية نفسها برّه الحسابات) ⇒ مفيش رقم يتحسب مصروف (قاعدة 10 — ما بنخترعش عملية).
 * - «غلطة في المبلغ» ⇒ **ولا كتابة** والشاشة بترجع للمبلغ.
 * الدين **ليك** (هو اللي دفعلك أكتر) مالوش السؤال ده ⇒ الرفض زي ما هو. الأرصدة لكل شخص وعملة من `computePersonBalance` (الأمانة جزء من «لك»).
 * المسار اللي فيه عملية (حوالة «ده سداد دين عليك؟») في `AnswerTransferAsks.answerOverpay`.
 */
data class OverpayOutcome(
    /** تسوية الدين بالباقي (فاضية لـ«غلطة في المبلغ»). */
    val settled: List<Settlement>,
    /** الدين الجديد ليك (أمانة أو سلفة) — null لـ«دعم» و«غلطة». */
    val created: Obligation?,
    val overpay: Overpay,
    /** «غلطة في المبلغ» ⇒ ارجع عدّل المبلغ. */
    val editAmount: Boolean = false,
)

class SettleOverpay internal constructor(private val deps: ManagePeopleDeps, private val people: ManagePeople) {
    private suspend fun open(obligationId: Id, personId: Id): Pair<Obligation, Halalas> {
        val o = deps.obligations.listByPerson(personId).firstOrNull { it.id == obligationId }
            ?: throw IllegalStateException(uiText(TextKey.DEBT_NOT_FOUND))
        return o to remainingOfObligation(o, deps.settlements.listByObligations(listOf(o.id)))
    }

    /** الزيادة لو [amountMinor] أكبر من الباقي على دين **عليك** — null = مش «أكتر من اللي عليك» (التسوية العادية أو رفضها). */
    suspend fun check(obligationId: Id, personId: Id, amountMinor: Halalas): Overpay? {
        val (o, remaining) = open(obligationId, personId)
        return if (overpayable(o)) overpayOf(amountMinor, remaining) else null
    }

    /** رد المالك على «دفعت أكتر من اللي عليك» — كل الكتابة في وحدة عمل واحدة، وقبلها الفحص كله. */
    suspend fun resolve(obligationId: Id, personId: Id, amountMinor: Halalas, choice: OverpayChoice): OverpayOutcome {
        val (o, remaining) = open(obligationId, personId)
        val over = (if (overpayable(o)) overpayOf(amountMinor, remaining) else null)
            ?: throw IllegalStateException(uiText(UiKey.OVERPAY_NOT_OVER))
        if (choice == OverpayChoice.WRONG_AMOUNT) return OverpayOutcome(emptyList(), null, over, editAmount = true)
        return deps.uow.run {
            val settled = people.settle(o.id, personId, over.openMinor)
            val created = when (choice) {
                OverpayChoice.HELD_FOR_YOU, OverpayChoice.NEW_LOAN -> Obligation(
                    id = deps.ids.next("obl"),
                    personId = personId,
                    originTransactionId = null,
                    kind = ObligationKind.RECEIVABLE,
                    originalMinor = over.surplusMinor,
                    currency = o.currency,
                    custodyHeld = choice == OverpayChoice.HELD_FOR_YOU,
                ).also { deps.obligations.saveMany(listOf(it)) }
                else -> null
            }
            OverpayOutcome(listOf(settled), created, over)
        }
    }
}
