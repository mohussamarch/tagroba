package app.masroufy.usecase

import app.masroufy.core.BnplProvider
import app.masroufy.core.BnplStep
import app.masroufy.core.DueProgress
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.IsoDate
import app.masroufy.core.bnplProviderOf
import app.masroufy.core.bnplSteps
import app.masroufy.core.dueProgress
import app.masroufy.core.installmentSchedule
import app.masroufy.core.sumMoney
import app.masroufy.port.CategoryRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository

/**
 * كارت خطة في قسم «مشتريات بالتقسيط» (r051 — `Installments.dc.html`): المزوّد · اسم التصنيف ([categoryName] null = «تقسيط مشتريات») ·
 * [progress] (دفعت N من M · الباقي · الجاية) · [steps] لـ«المواعيد» وشريط الدفعات · التذكير ([InstallmentPlan.remindDayBefore]).
 */
data class BnplPlanView(
    val plan: InstallmentPlan,
    val provider: BnplProvider,
    val categoryName: String?,
    val progress: DueProgress,
    val steps: List<BnplStep>,
)

/**
 * خطط «اشتري دلوقتي وادفع بعدين» (تقسيط مشتريات عند مزوّد معروف) للقسم بتاعها في «الأقساط» — المفتوحة الأول بالأقرب ميعادًا، وبعدها
 * اللي خلصت. نفس جدول الأقساط ونفس «الباقي عليك في الأقساط» (`DuesTotals` بتعدّها مع باقي الخطط).
 */
class LoadBnplPlans(
    private val plans: InstallmentPlanRepository,
    private val payments: InstallmentPaymentRepository,
    private val categories: CategoryRepository?,
) {
    suspend fun load(today: IsoDate): List<BnplPlanView> {
        val mine = plans.listAll().mapNotNull { p -> bnplProviderOf(p.provider)?.takeIf { p.kind == InstallmentKind.PURCHASE_PLAN }?.let { p to it } }
        if (mine.isEmpty()) return emptyList()
        val names = categories?.listAll().orEmpty().associate { it.id to it.name }
        return mine.map { (p, provider) ->
            val paid = sumMoney(payments.listByPlan(p.id).map { it.amountMinor })
            val s = installmentSchedule(p)
            BnplPlanView(p, provider, p.categoryId?.let { names[it] }, dueProgress(s, paid, today), bnplSteps(s, paid))
        }.sortedWith(compareBy({ it.progress.nextDueAt == null }, { it.progress.nextDueAt ?: "" }, { it.plan.createdAt }, { it.plan.id }))
    }

    /** معرّفات الخطط دي — الأقسام التانية في «الأقساط» بتسيبها لقسمها. */
    fun isBnpl(p: InstallmentPlan): Boolean = p.kind == InstallmentKind.PURCHASE_PLAN && bnplProviderOf(p.provider) != null
}
