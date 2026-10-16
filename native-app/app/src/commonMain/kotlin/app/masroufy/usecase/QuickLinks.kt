package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.Transaction
import app.masroufy.core.employerOf
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.TransactionRepository

/**
 * «ممكن تكون مربوطة بـ» أول ما العملية تتفتح (طلب المالك 2026-10-16 — «لما ادوس علي العملية من برا المفروض يظهرلي ربط بعمليات تابعة بسرعة»):
 * لحد [LIMIT] مرشحين **من منطق الربط الموجود بس** (مفيش قاعدة مطابقة جديدة)، وكل واحد بـ«اربط» بضغطة:
 * - **استرداد ⇄ شراء**: مرشحين «عمليات تابعة» (`LinkInflows.load` ⇒ `Expense.candidates` — نافذة تاريخ المصروف بس، مش الجدول كله) ⇒ `LinkInflows.link`.
 * - **راتب ⇄ جهة الشغل**: الجهة اللي حسابها المحوِّل متربط بيها وفي فترتها (`employerOf` — نفس قاعدة التسجيل) والتحويل لسه مش «راتب» مؤكد ⇒
 *   `IncomeAnswers.markSalary`.
 * مالهمش مصدر مرشحين في الكود دلوقتي (HANDOVER): الرِّجل التانية للتحويل بين محافظك · الرسوم ⇄ التحويل · القسط/التقسيط ⇄ الشراء.
 * قراية واحدة للعملية + نافذة تاريخها + مصادر الدخل (عددها صغير) — مرة لكل فتحة.
 */
data class QuickLinksDeps(
    val txns: TransactionRepository,
    val inflows: LinkInflows?,
    val incomeSources: IncomeSourceRepository?,
    val answers: IncomeAnswers?,
)

sealed interface QuickLink {
    /** فلوس داخلة ممكن تكون استرداد للمصروف ده. */
    data class Refund(val inflow: Transaction) : QuickLink

    /** التحويل ده من حساب جهة شغلك في فترتها. */
    data class Salary(val employer: IncomeSource) : QuickLink
}

class QuickLinks(private val deps: QuickLinksDeps) {
    suspend fun candidates(transactionId: Id): List<QuickLink> {
        val t = deps.txns.findByIds(listOf(transactionId)).singleOrNull() ?: return emptyList()
        val out = mutableListOf<QuickLink>()
        if (t.observedDirection == Direction.IN && !(t.economicKind == EconomicKind.SALARY && t.economicKindConfirmed) && deps.answers != null) {
            val sources = deps.incomeSources?.listAll().orEmpty().filter { it.payerKeys.isNotEmpty() }
            if (sources.isNotEmpty()) employerOf(t, sources)?.let { out += QuickLink.Salary(it) }
        }
        if (t.observedDirection == Direction.OUT) {
            val view = deps.inflows?.load(transactionId)
            if (view is LinkedInflowsView.Expense) out += view.candidates.take(LIMIT).map { QuickLink.Refund(it) }
        }
        return out.take(LIMIT)
    }

    /** «اربط» — null = اتربطت · غير كده رسالة الرفض (ولا حاجة اتكتبت). */
    suspend fun link(transactionId: Id, link: QuickLink): app.masroufy.core.LinkRefusal? = when (link) {
        is QuickLink.Refund -> deps.inflows?.link(transactionId, listOf(link.inflow.id))
        is QuickLink.Salary -> { deps.answers?.markSalary(transactionId, link.employer.id); null }
    }

    companion object {
        const val LIMIT = 3
    }
}
