package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.IncomeCategories
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.Liquidity
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.UiKey
import app.masroufy.core.ruleFor
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * الردود الجديدة على الفلوس اللي دخلت (قايمة المالك 2026-10-15، البند 6 — «ضيف تصنيفات انه يكون عمل اضافي او بيعة او احدد هو ده تحويل
 * راتب الدوام لاني معرفتش احوله»): جنب «شخص أعرفه» · «حسابي الآخر» في «مَن هذا؟»، **وكمان على أي تحويل داخل اتسجل قبل كده** (المالك
 * ما عرفش يحوّل تحويل لراتب):
 * - **«راتب الدوام»** ⇒ جهة شغل (موجودة أو جديدة) والحساب المحوِّل بيتربط بيها ⇒ التحويل ده «راتب» مؤكد في تصنيف «الدخل › راتب»، والجاي من
 *   نفس الحساب جوه فترة الجهة بيتعرف لوحده (`employerOf`). وبيرجّع معاينة «طبّق على اللي فات» للباقي (المالك يأكد — `EmployerRetroApply`).
 * - **«عمل إضافي»** (عمل حر) · **«بيعة»** (بيع شخصي) ⇒ دخل للتحويل ده بس، في فرعه من «الدخل».
 * - **نوع تاني** من قايمة قصيرة (مكافأة · هدية · دعم · استرداد · …) ⇒ النوع بس.
 * تصنيفات «الدخل» بتتعمل لو مش موجودة بس (في البلد اللي العملية فيها). كل رد ليه **تراجع** بيرجّع العملية زي ما كانت بالظبط، ويشيل الحساب
 * لو الرد ده هو اللي ربطه. رجل «التحويل لنفسك» (§64) ما يتغيرش نوعها.
 */
data class IncomeAnswersDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val employers: ManageEmployers,
    val retro: EmployerRetroApply,
    val uow: UnitOfWork,
    val clock: Clock,
    val spaceLegs: SpaceTransferLegs? = null,
    /** R4/N4: المربوطة بشخص نوعها بيتغير بعد «فك الربط» بس. null ⇒ مفيش فحص. */
    val personLinks: app.masroufy.port.PersonLinkRepository? = null,
)

/** تراجع عن رد واحد: العملية قبل الرد + الحساب اللي الرد ده ربطه (لو ربط). */
data class AnswerUndo(
    val before: KindSnapshot,
    val employerId: Id? = null,
    val attachedKey: String? = null,
    val rule: PayerRuleUndo? = null,
    /** r06-1b3: «حاجة تانية» كتبت اسمها ملاحظة ⇒ التراجع بيرجّع الملاحظة اللي كانت ([noteBefore] null = مكانش فيه). */
    val restoreNote: Boolean = false,
    val noteBefore: String? = null,
)

/** نتيجة «راتب الدوام»: الجهة (بعد الربط) · الطرف (null = الرسالة من غير اسم ولا رقم) · معاينة الباقي (null = مفيش حاجة تانية). */
data class SalaryAnswer(val employer: IncomeSource?, val party: TransferPartyRef?, val retro: RetroPreview?, val undo: AnswerUndo)

class IncomeAnswers(private val deps: IncomeAnswersDeps) {
    /** الطرف التاني في التحويل الداخل (null = مش تحويل أو من غير اسم ولا رقم). */
    suspend fun partyOf(transactionId: Id): TransferPartyRef? = transferPartyOf(load(transactionId))

    /**
     * «راتب الدوام»: [employerId] جهة موجودة، أو [newEmployer] جهة جديدة (لو الاتنين null ⇒ «راتب» من غير جهة). الربط + النوع + الجهة
     * الجديدة في وحدة عمل واحدة.
     */
    suspend fun markSalary(transactionId: Id, employerId: Id? = null, newEmployer: EmployerInput? = null): SalaryAnswer {
        val t = incoming(transactionId, EconomicKind.SALARY)
        val party = transferPartyOf(t)
        var employer: IncomeSource? = null
        var attached: String? = null
        deps.uow.run {
            val target = when {
                employerId != null -> deps.employers.list().firstOrNull { it.source.id == employerId }?.source
                    ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_NOT_FOUND))
                newEmployer != null -> deps.employers.save(null, newEmployer)
                else -> null
            }
            employer = target
            if (target != null && party != null && party.key !in target.payerKeys) {
                employer = deps.employers.attach(target.id, party)
                attached = party.key
            }
            write(t, EconomicKind.SALARY)
        }
        val retro = employer?.let { e -> deps.retro.preview(e.id, skip = setOf(t.id)).takeIf { !it.isEmpty } }
        return SalaryAnswer(employer, party, retro, AnswerUndo(KindSnapshot.of(t), employer?.id, attached))
    }

    /** «عمل إضافي» · «بيعة» · أو نوع داخل تاني من [OTHER_KINDS] — للتحويل ده بس. */
    suspend fun markIncome(transactionId: Id, kind: EconomicKind): AnswerUndo {
        if (kind != EconomicKind.FREELANCE && kind != EconomicKind.PERSONAL_SALE && kind !in OTHER_KINDS && kind !in app.masroufy.core.PAYER_ASK_KINDS) {
            throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_KIND))
        }
        val t = incoming(transactionId, kind)
        deps.uow.run { write(t, kind) }
        return AnswerUndo(KindSnapshot.of(t))
    }

    /** «تراجع»: العملية زي ما كانت، والحساب اللي الرد ربطه بيتشال (الجهة الجديدة بتفضل — مفيش مسح للجهات). */
    suspend fun undo(undo: AnswerUndo) {
        deps.uow.run {
            deps.txns.update(undo.before.id, undo.before.restorePatch(deps.clock.nowIso()))
            val employerId = undo.employerId
            val key = undo.attachedKey
            if (employerId != null && key != null) deps.employers.detach(employerId, key)
            // PLAN-0.6 §7: قاعدة «الفلوس دي إيه؟» بترجع زي ما كانت
            undo.rule?.let { deps.employers.setPayerRule(it.employerId, it.partyKey, it.previous?.kind, it.previous?.label) }
            if (undo.restoreNote) {
                val note = undo.noteBefore
                deps.txns.update(undo.before.id, if (note == null) TransactionPatch(clearNote = true) else TransactionPatch(note = note))
            }
        }
    }

    private suspend fun load(transactionId: Id): Transaction =
        deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_TXN))

    private suspend fun incoming(transactionId: Id, kind: EconomicKind): Transaction {
        val t = load(transactionId)
        if (t.observedDirection != Direction.IN || ruleFor(kind).liquidity != Liquidity.IN) throw IncomeSourceError(uiText(UiKey.EMPLOYER_ERR_NOT_IN))
        if (kind != t.economicKind && deps.spaceLegs?.isLeg(transactionId) == true) throw IncomeSourceError(uiText(TextKey.SPACE_TRANSFER_LEG_LOCKED))
        if (kind != t.economicKind && deps.personLinks?.listByTransactionIds(listOf(transactionId))?.isNotEmpty() == true) {
            throw IncomeSourceError(uiText(UiKey.PLINK_ERR_UNLINK_FIRST))
        }
        return t
    }

    /** النوع مؤكد · خارج المراجعة · وفرعه من «الدخل» لو ليه فرع (التصنيف التاني بيفضل زي ما هو). */
    private suspend fun write(t: Transaction, kind: EconomicKind) {
        val category = IncomeCategories.forKind(kind)
        if (category != null) deps.categories.ensureAll(IncomeCategories.defaults())
        deps.txns.update(
            t.id,
            TransactionPatch(
                economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, clearSuggestedKind = true,
                categoryId = category, categoryConfirmed = if (category != null) true else null, updatedAt = deps.clock.nowIso(),
            ),
        )
    }

    companion object {
        /** «نوع آخر» في لوحة تغيير النوع — أنواع داخلة مالهاش أثر على شخص (السلفة والسداد ليهم «شخص أعرفه»). */
        val OTHER_KINDS: List<EconomicKind> = listOf(
            EconomicKind.BONUS, EconomicKind.GIFT_RECEIVED, EconomicKind.SUPPORT_RECEIVED, EconomicKind.REFUND_RECEIVED,
            EconomicKind.BENEFIT_RECEIVED, EconomicKind.INVESTMENT_INCOME, EconomicKind.OTHER_INCOME,
        )
    }
}
