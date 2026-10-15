package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.asConfirmedSalary
import app.masroufy.core.salaryRetroPlan
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.RepairBackupPort
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * «طبّق على اللي فات» بعد إضافة جهة شغل أو حساب ليها أو تغيير فترتها (قايمة المالك 2026-10-15، البند 7): **معاينة بالعدد** ⇒ المالك
 * يأكد ⇒ **نسخة متأكدة قبل أي كتابة** (`saveVerifiedBackup` — من غير جهاز يقدر يحفظ ⇒ ولا كتابة) ⇒ الإيداعات بتبقى «راتب» مؤكد ⇒ «تراجع»
 * بيرجّع كل واحدة زي ما كانت بالظبط (بنسخة تانية قبله). اللي المالك حدد نوعه بإيده **ما بيتلمسش** إلا لو اختار «غيّر دول كمان» صراحة.
 * رجول «التحويل لنفسك» (§64) برّه دايمًا. التصنيف ما بيتغيرش (زي «ده مرتب من …؟» — §64-ج).
 */
data class EmployerRetroDeps(
    val sources: IncomeSourceRepository,
    val txns: TransactionRepository,
    val uow: UnitOfWork,
    val clock: Clock,
    /** ملف النسخة قبل التغيير على الجهاز. null (الآيفون لسه · المعاينة) ⇒ التطبيق بيترفض برسالة. */
    val backup: RepairBackupPort?,
    val spaceLegs: SpaceTransferLegs? = null,
)

/** المعاينة: [employerId] = null ⇒ كل الجهات. [skip] = عمليات اتجاوبت في نفس الخطوة. */
data class RetroPreview(val employerId: Id?, val toSalaryIds: List<Id>, val handSetIds: List<Id>, val skip: Set<Id> = emptySet()) {
    val isEmpty: Boolean get() = toSalaryIds.isEmpty() && handSetIds.isEmpty()
}

/** حالة النوع والتصنيف قبل التغيير — للتراجع وللنسخة. */
data class KindSnapshot(
    val id: Id,
    val kind: EconomicKind,
    val kindConfirmed: Boolean,
    val review: ReviewState,
    val categoryId: Id?,
    val categoryConfirmed: Boolean,
    val suggestedKind: EconomicKind?,
) {
    fun toDocument(): Map<String, Any?> = linkedMapOf(
        "id" to id, "economicKind" to kind.wire, "economicKindConfirmed" to kindConfirmed, "reviewState" to review.wire,
        "categoryId" to categoryId, "categoryConfirmed" to categoryConfirmed, "suggestedKind" to suggestedKind?.wire,
    )

    /** الرجوع للحالة دي بالظبط. */
    fun restorePatch(nowIso: String): TransactionPatch = TransactionPatch(
        economicKind = kind, economicKindConfirmed = kindConfirmed, reviewState = review,
        categoryId = categoryId, clearCategoryId = categoryId == null, categoryConfirmed = categoryConfirmed,
        suggestedKind = suggestedKind, clearSuggestedKind = suggestedKind == null, updatedAt = nowIso,
    )

    companion object {
        fun of(t: Transaction) = KindSnapshot(t.id, t.economicKind, t.economicKindConfirmed, t.reviewState, t.categoryId, t.categoryConfirmed, t.suggestedKind)
    }
}

/** اللي اتغير ونسخته — [undo] بيرجّعهم. */
data class RetroUndo(val before: List<KindSnapshot>)

data class RetroResult(val changed: Int, val backup: VerifiedBackup, val undo: RetroUndo)

class EmployerRetroApply(private val deps: EmployerRetroDeps) {
    private suspend fun everything() = deps.txns.listByDateRange(ManageEmployers.EARLIEST, ManageEmployers.LATEST)

    /** العدد قبل أي كتابة — ما بيكتبش حاجة. */
    suspend fun preview(employerId: Id?, skip: Set<Id> = emptySet()): RetroPreview {
        val plan = salaryRetroPlan(everything(), deps.sources.listAll(), employerId, skip)
        val legs = deps.spaceLegs?.legsAmong((plan.toSalary + plan.handSet).map { it.id }).orEmpty()
        return RetroPreview(employerId, plan.toSalary.map { it.id } - legs, plan.handSet.map { it.id } - legs, skip)
    }

    /**
     * التطبيق بعد التأكيد: بيرفض لو اللي هيتغير مش نفس المعاينة (حاجة اتغيرت من ساعتها) أو لو النسخة ما اتحفظتش. [includeHandSet] = «غيّر
     * دول كمان». بيرجّع العدد ونسخة التراجع.
     */
    suspend fun apply(preview: RetroPreview, includeHandSet: Boolean = false): RetroResult {
        val backup = deps.backup ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_RETRO_NO_BACKUP))
        val fresh = preview(preview.employerId, preview.skip)
        if (fresh.toSalaryIds != preview.toSalaryIds || (includeHandSet && fresh.handSetIds != preview.handSetIds)) {
            throw IncomeSourceError(uiText(UiKey.EMPLOYER_RETRO_CHANGED))
        }
        val ids = preview.toSalaryIds + if (includeHandSet) preview.handSetIds else emptyList()
        val targets = deps.txns.findByIds(ids)
        if (targets.isEmpty()) return RetroResult(0, saveVerifiedBackup(backup, deps.clock, KIND, emptyList()), RetroUndo(emptyList()))
        val before = targets.map(KindSnapshot::of)
        val verified = saveVerifiedBackup(backup, deps.clock, KIND, before.map { it.toDocument() })
        val now = deps.clock.nowIso()
        deps.uow.run { deps.txns.saveMany(targets.map { asConfirmedSalary(it, now) }) }
        return RetroResult(targets.size, verified, RetroUndo(before))
    }

    /** «تراجع»: كل عملية بترجع لحالتها قبل التطبيق — بنسخة للحالة الحالية الأول. بيرجّع العدد. */
    suspend fun undo(undo: RetroUndo): Int {
        if (undo.before.isEmpty()) return 0
        val backup = deps.backup ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_RETRO_NO_BACKUP))
        val current = deps.txns.findByIds(undo.before.map { it.id }).map(KindSnapshot::of)
        saveVerifiedBackup(backup, deps.clock, "$KIND-undo", current.map { it.toDocument() })
        val now = deps.clock.nowIso()
        deps.uow.run { for (s in undo.before) deps.txns.update(s.id, s.restorePatch(now)) }
        return undo.before.size
    }

    private companion object {
        const val KIND = "employer-salary"
    }
}
