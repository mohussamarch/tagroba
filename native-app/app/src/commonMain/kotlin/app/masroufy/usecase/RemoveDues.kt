package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.R06Key
import app.masroufy.core.uiText
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository

/** الشيل مرفوض والسبب بلغة المالك (مثلًا الخطة عليها دفعات مربوطة بعمليات). */
class DuesRemovalRefused(message: String) : IllegalStateException(message)

/**
 * r06-links (خريطة الربط — «مفيش مسح ولا أرشفة»): الشيل **اللي ما يكسرش الدفتر بس**، ومعاه «تراجع» زي مسح العملية.
 * - **خطة التقسيط:** بتتمسح **لو مالهاش ولا دفعة مربوطة بعملية**؛ عليها دفعات ⇒ مرفوض وبيقول ليه (مفيش أرشفة للخطط لسه — HANDOVER).
 * - الاشتراك: «وقّف» و«رجّعه» موجودين قبل كده (`ManageRecurring.save` بـ`active`) — أرشفة من غير مسح.
 * - الشخص: أرشفة بس (`ManagePeople.archivePerson` — spec/03 «لا حذف للحساب ذي سجل»).
 * - الجمعية: مش هنا (مستودعها مالوش مسح، ومدخلاتها مربوطة بعمليات — HANDOVER).
 */
class RemoveDues(
    private val plans: InstallmentPlanRepository,
    private val payments: InstallmentPaymentRepository,
) {
    suspend fun deletePlan(id: Id): InstallmentPlan {
        val plan = plans.listAll().firstOrNull { it.id == id } ?: throw DuesRemovalRefused(uiText(R06Key.REMOVE_GONE))
        if (payments.listByPlan(id).isNotEmpty()) throw DuesRemovalRefused(uiText(R06Key.REMOVE_PLAN_LINKED))
        plans.delete(id)
        return plan
    }

    /** «تراجع»: الخطة بترجع زي ما كانت بالظبط (نفس المعرّف). */
    suspend fun restorePlan(plan: InstallmentPlan) = plans.save(plan)
}
