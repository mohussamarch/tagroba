package app.masroufy.usecase

import app.masroufy.core.DueLinkKind
import app.masroufy.core.DueLinkSuggestion
import app.masroufy.core.DueOpening
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.TextKey
import app.masroufy.core.installmentOpenings
import app.masroufy.core.roscaOpenings
import app.masroufy.core.suggestDueLinks
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionRepository
import app.masroufy.port.ZakatPaymentRepository

/**
 * §75-8 (قرار المالك 2026-10-08 — الشريحة S4): **الأقساط والجمعية ⇒ يقترح الربط.** عملية مش مربوطة بنفس مبلغ قسط مفتوح (أو قسط جمعية،
 * أو قبض دورك، أو مبلغ تمويل) وعملته، وجوه أسبوع من ميعاده ⇒ سؤال «نربطه؟» على العملية (`DueLinkAskSource`).
 * - [accept] بيعدّي على الربط العادي (`ManageInstallments.link`/`linkReceived` · `ManageRoscas.link`) بكل فحوصه — والقسط بيبقى مصروف تحت
 *   «المستحقات» (§56) زي الربط بالإيد بالظبط.
 * - [decline] = «مش ده»: العملية بتتحفظ على الخطة أو الجمعية ([app.masroufy.core.InstallmentPlan.dismissedTxnIds]) فما تتقترحش عليها تاني
 *   (**اختيار Claude** — المالك يقدر يغيّره).
 * - المربوطة بأي حاجة ما بتتقترحش: جمعية · قسط · مبلغ تمويل · دفعة زكاة · نقطة في حدث · رجل تحويل لنفسك (نفس قواعد `DueLinks.check`).
 * القراية: العمليات في الفترة المطلوبة بس، والخطط والجمعيات (عددهم صغير بطبيعته).
 */
data class SuggestDueLinksDeps(
    val txns: TransactionRepository,
    val plans: InstallmentPlanRepository,
    val payments: InstallmentPaymentRepository,
    val roscas: RoscaRepository,
    val roscaEntries: RoscaEntryRepository,
    /** الربط نفسه بيعدّي عليهم (الفحص · النوع · فرع «المستحقات»). */
    val installments: ManageInstallments,
    val roscaLinks: ManageRoscas,
    /** نفس الاعتمادات الاختيارية بتاعة `DueLinks` — التشغيل الحقيقي بيدّيها كلها. */
    val zakatPayments: ZakatPaymentRepository? = null,
    val eventLinks: EventLinkRepository? = null,
    val spaceLegs: SpaceTransferLegs? = null,
)

class SuggestDueLinks(private val deps: SuggestDueLinksDeps) {
    /** الاقتراحات على عمليات من [from] لـ[to] (بالتاريخ). فاضية = مفيش حاجة تتسأل. */
    suspend fun list(from: IsoDate, to: IsoDate): List<DueLinkSuggestion> {
        val plans = deps.plans.listAll()
        val openings = mutableListOf<DueOpening>()
        for (p in plans) openings += installmentOpenings(p, sumMoney(deps.payments.listByPlan(p.id).map { it.amountMinor }), to)
        for (r in deps.roscas.listAll()) openings += roscaOpenings(r, deps.roscaEntries.listByRosca(r.id), to)
        if (openings.isEmpty()) return emptyList()
        // فلترة أولية بالمبلغ والاتجاه قبل ما نسأل عن الروابط (القراية على المرشحين بس)
        val wanted = openings.map { it.amountMinor to it.kind.direction }.toSet()
        val candidates = deps.txns.listByDateRange(from, to).filter { (it.amountMinor to it.observedDirection) in wanted }
        if (candidates.isEmpty()) return emptyList()
        return suggestDueLinks(candidates, openings, linkedAmong(candidates.map { it.id }, plans.mapNotNull { it.receivedTransactionId }))
    }

    /** العمليات المربوطة بحاجة خلاص من [ids] — نفس قواعد `DueLinks.check` («الفلوس ما تتعدش مرتين»). */
    private suspend fun linkedAmong(ids: List<Id>, received: List<Id>): Set<Id> = buildSet {
        addAll(deps.roscaEntries.listByTransactionIds(ids).map { it.transactionId })
        addAll(deps.payments.listByTransactionIds(ids).map { it.transactionId })
        addAll(received)
        deps.zakatPayments?.let { z -> addAll(z.listByTransactionIds(ids).mapNotNull { it.transactionId }) }
        deps.eventLinks?.let { e -> addAll(e.listByTransactionIds(ids).filter { it.role.isGift }.map { it.transactionId }) }
        deps.spaceLegs?.let { addAll(it.legsAmong(ids)) }
    }

    /** «أيوه اربطه»: بالمبلغ كله (الاقتراح أصلًا بمبلغ العملية بالظبط). أي فحص بيرفض ⇒ نفس رسالة الربط بالإيد. */
    suspend fun accept(s: DueLinkSuggestion) {
        when (s.kind) {
            DueLinkKind.INSTALLMENT -> deps.installments.link(s.ownerId, s.transactionId)
            DueLinkKind.FINANCING_RECEIVED -> deps.installments.linkReceived(s.ownerId, s.transactionId)
            DueLinkKind.ROSCA_CONTRIBUTION -> deps.roscaLinks.link(s.ownerId, s.transactionId, RoscaEntryKind.CONTRIBUTION)
            DueLinkKind.ROSCA_PAYOUT -> deps.roscaLinks.link(s.ownerId, s.transactionId, RoscaEntryKind.PAYOUT)
        }
    }

    /** «مش ده»: ما تتقترحش على الخطة أو الجمعية دي تاني (ومرة تانية = ولا حاجة بتتغير). */
    suspend fun decline(s: DueLinkSuggestion) {
        when (s.kind) {
            DueLinkKind.INSTALLMENT, DueLinkKind.FINANCING_RECEIVED -> {
                val plan = deps.plans.listAll().firstOrNull { it.id == s.ownerId } ?: throw DueLinkError(uiText(TextKey.INSTALLMENT_NOT_FOUND))
                if (s.transactionId !in plan.dismissedTxnIds) deps.plans.save(plan.copy(dismissedTxnIds = plan.dismissedTxnIds + s.transactionId))
            }
            DueLinkKind.ROSCA_CONTRIBUTION, DueLinkKind.ROSCA_PAYOUT -> {
                val rosca = deps.roscas.listAll().firstOrNull { it.id == s.ownerId } ?: throw DueLinkError(uiText(TextKey.ROSCA_NOT_FOUND))
                if (s.transactionId !in rosca.dismissedTxnIds) deps.roscas.save(rosca.copy(dismissedTxnIds = rosca.dismissedTxnIds + s.transactionId))
            }
        }
    }
}
