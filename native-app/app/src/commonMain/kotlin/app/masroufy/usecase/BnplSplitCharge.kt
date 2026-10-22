package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InstallmentKind
import app.masroufy.core.Transaction
import app.masroufy.core.bnplFirstDueFor
import app.masroufy.core.bnplProviderOf
import app.masroufy.core.bnplSplitReady
import app.masroufy.core.bnplSumSuggestion
import app.masroufy.core.dueProgress
import app.masroufy.core.installmentSchedule
import app.masroufy.core.planPaidMinor
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.port.TransactionPatch

/** جزء من الخصم المجمّع: خطة موجودة ([planId]) أو «+ مشترى جديد» ([newPurchase]) — و[amountMinor] = القسط اللي الخصم ده دفعه منها. */
data class BnplSplitPart(val amountMinor: Halalas, val planId: Id? = null, val newPurchase: BnplPurchase? = null)

/** «تراجع» عن التوزيع: الدفعات بتتفك، والعملية بترجع زي ما كانت، والخطط الجديدة اللي اتعملت فيه بتتمسح. */
data class BnplSplitUndo(val transactionId: Id, val newPlanIds: List<Id>, val before: BnplBefore)

/** صفحة التوزيع: الخصم والخطط المفتوحة (القسط مقترح للمبلغ) — كله محسوب هنا. */
data class BnplSplitView(val charge: Transaction, val options: List<BnplOption>, val suggested: List<Id> = emptyList())

/**
 * PLAN-0.6 §8 (قرار المالك 2026-10-17) — «مجموع أكتر من قسط»: خصم واحد من تابي/تمارا بيدفع كذا قسط. المالك بيختار الخطط (أو «+ مشترى جديد»)
 * وبيكتب قسط كل واحدة، و**المجموع لازم يساوي الخصم بالهللة** — غير كده [diff] بيرجع الفرق والحفظ مرفوض. الحفظ = `ManageInstallments.splitCharge`
 * (نفس دفعات الأقساط — §56: القسط مصروف بعمليته، فمفيش عدّ مرتين). [undo] بيرجّع كل حاجة زي ما كانت.
 */
class BnplSplitCharge(private val deps: BnplChargesDeps) {
    /**
     * الخصم + خطط تقسيط المشتريات المفتوحة **لنفس المزوّد** (r06-1b4 — التصميم; مزوّد مش معروف ⇒ كل الخطط) + [BnplSplitView.suggested]:
     * الأقساط المستحقة اللي مجموعها = الخصم بالظبط (`bnplSumSuggestion`) ⇒ بتتعلّم من الأول.
     */
    suspend fun view(transactionId: Id): BnplSplitView {
        val t = charge(transactionId)
        val today = deps.today()
        val provider = bnplProviderOf(t.rawMerchantName ?: t.rawDescription)
        val mine = deps.plans.listAll().filter {
            it.kind == InstallmentKind.PURCHASE_PLAN && it.currency == t.currency && (provider == null || bnplProviderOf(it.provider) == provider)
        }
        val options = mine.mapNotNull { plan ->
            val paid = planPaidMinor(plan, deps.payments.listByPlan(plan.id))
            val left = subtractMoney(plan.totalMinor, paid)
            if (left <= 0) return@mapNotNull null
            val p = dueProgress(installmentSchedule(plan), paid, today)
            BnplOption(plan.id, plan.name, minOf(plan.installmentMinor, left), plan.currency, p.count - p.paidCount, p.count, p.nextDueAt, 0)
        }
        return BnplSplitView(t, options, bnplSumSuggestion(t.amountMinor, options.map { it.planId to it.installmentMinor }))
    }

    /** الخصم − مجموع الأجزاء (صفر = يتحفظ · موجب = ناقص · سالب = زيادة). */
    fun diff(chargeMinor: Halalas, parts: List<BnplSplitPart>): Halalas = subtractMoney(chargeMinor, sumMoney(parts.map { it.amountMinor }))

    /** «وزّع على الأقساط» شغال؟ [amounts] = مبالغ الأجزاء مقرية (null = مش مكتوبة صح) · [newNamed] = كل مشترى جديد ليه اسم. */
    fun ready(chargeMinor: Halalas, amounts: List<Halalas?>, newNamed: Boolean): Boolean = bnplSplitReady(chargeMinor, amounts, newNamed)

    /** الحفظ — الغلط (المجموع مش مساوي · أقل من جزئين · خطة مقفولة) بيطلع رسالة للشاشة ومفيش حاجة بتتكتب. */
    suspend fun save(transactionId: Id, parts: List<BnplSplitPart>): BnplSplitUndo {
        val t = charge(transactionId)
        val provider = bnplProviderOf(t.rawMerchantName ?: t.rawDescription)?.brand ?: ""
        val existing = parts.mapNotNull { p -> p.planId?.let { it to p.amountMinor } }
        val fresh = parts.mapNotNull { p ->
            val n = p.newPurchase ?: return@mapNotNull null
            InstallmentInput(
                name = n.what.trim().ifEmpty { provider.ifEmpty { t.rawMerchantName.orEmpty() } }, provider = provider,
                kind = InstallmentKind.PURCHASE_PLAN, currency = t.currency, principalMinor = 0, totalMinor = 0, installmentMinor = p.amountMinor,
                firstDueAt = bnplFirstDueFor(t.occurredAt.take(10), n.cycle, n.at.coerceIn(1, n.count)), cycleWeeks = n.cycle.weeks,
                categoryId = n.categoryId, remindDayBefore = n.remind, paidBeforeCount = (n.at.coerceIn(1, n.count) - 1).takeIf { it > 0 },
            ) to n.count
        }
        val made = deps.installments.splitCharge(transactionId, existing, fresh)
        return BnplSplitUndo(transactionId, made, t.bnplBefore())
    }

    suspend fun undo(u: BnplSplitUndo) {
        deps.installments.unlink(u.transactionId)
        val b = u.before
        deps.txns.update(
            u.transactionId,
            TransactionPatch(
                economicKind = b.kind, economicKindConfirmed = b.kindConfirmed, categoryId = b.categoryId, clearCategoryId = b.categoryId == null,
                categoryConfirmed = b.categoryConfirmed, reviewState = b.reviewState, suggestedKind = b.suggestedKind,
                clearSuggestedKind = b.suggestedKind == null, updatedAt = deps.clock.nowIso(),
            ),
        )
        for (id in u.newPlanIds) deps.plans.delete(id)
    }

    private suspend fun charge(transactionId: Id): Transaction {
        val t = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IllegalArgumentException("transaction $transactionId")
        require(t.observedDirection == Direction.OUT) { "not a charge" }
        return t
    }
}
