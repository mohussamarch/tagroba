package app.masroufy.usecase

import app.masroufy.core.transferAskRequestId
import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.DueProgress
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.InstallmentError
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.InstallmentCountPreview
import app.masroufy.core.IsoDate
import app.masroufy.core.DUE_LINK_DAYS
import app.masroufy.core.Transaction
import app.masroufy.core.dueLinkCandidate
import app.masroufy.core.installmentCountPreview
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.planPaidMinor
import app.masroufy.core.shiftDays
import app.masroufy.core.toDayNumber
import app.masroufy.core.totalFromCount
import app.masroufy.core.TextKey
import app.masroufy.core.checkDueSchedule
import app.masroufy.core.checkInstallmentPlan
import app.masroufy.core.debtSchedule
import app.masroufy.core.dueProgress
import app.masroufy.core.formatMoney
import app.masroufy.core.installmentPaymentKind
import app.masroufy.core.installmentSchedule
import app.masroufy.core.subtractMoney
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.core.DuesCategories
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * الأقساط للبنوك والشركات + مواعيد الديون بين الناس (OVERRIDES §50).
 * ربط قسط بيغيّر نوع العملية ويحطها تحت «المستحقات» (§56 — القسط كله مصروف): تقسيط المشتريات ⇒ «شراء»، التمويل ⇒ «قسط تمويل».
 * ومبلغ التمويل نفسه يوم ما ينزل في الحساب بيتربط بخطته (§59) ⇒ «مبلغ تمويل مستلم» (دخل تحت «المستحقات ← تمويل»).
 */
data class InstallmentInput(
    val id: Id? = null,
    val name: String,
    val provider: String,
    val kind: InstallmentKind,
    val currency: Currency,
    val principalMinor: Halalas,
    val totalMinor: Halalas,
    val installmentMinor: Halalas,
    val cycleMonths: Int = 1,
    val firstDueAt: IsoDate,
    val hasInterest: Boolean? = null,
    /** r05 (BNPL): كل كام أسبوع (null = كل [cycleMonths] شهر) · تصنيف المشترى (null = «المستحقات ← تقسيط مشتريات»). */
    val cycleWeeks: Int? = null,
    val categoryId: Id? = null,
    /** r051: «فكّرني قبل كل دفعة بيوم» (`InstallmentPlan.remindDayBefore`). */
    val remindDayBefore: Boolean? = null,
    /** r06-1b4: الدفعات اللي اتدفعت قبل التسجيل («الدفعة دي رقم كام؟» — `InstallmentPlan.paidBeforeCount`). null = زي ما هي. */
    val paidBeforeCount: Int? = null,
)

/** [receivedMinor]: مبلغ عملية «مبلغ تمويل مستلم» المربوطة (null = لسه ما اتربطتش — مش صفر). */
data class InstallmentView(val plan: InstallmentPlan, val progress: DueProgress, val receivedMinor: Halalas? = null)

data class ManageInstallmentsDeps(
    val plans: InstallmentPlanRepository,
    val payments: InstallmentPaymentRepository,
    val roscaEntries: RoscaEntryRepository,
    val terms: DebtTermsRepository,
    val obligations: ObligationRepository,
    val txns: TransactionRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** فرع «المستحقات ← تمويل / تقسيط مشتريات» بيتحط على العملية المربوطة (§56). */
    val categories: CategoryRepository,
    /** عشان عملية دفعة زكاة ما تتربطش بقسط كمان (§62). */
    val zakatPayments: app.masroufy.port.ZakatPaymentRepository? = null,
    /** عشان عملية اتسجلت نقطة في حدث ما تتربطش هنا كمان (§64). */
    val eventLinks: app.masroufy.port.EventLinkRepository? = null,
    /** رجول التحويل لنفسك (§64) — الرجل ما تتربطش هنا. */
    val spaceLegs: app.masroufy.port.SpaceTransferLegs? = null,
)

class ManageInstallments(private val deps: ManageInstallmentsDeps) {
    private val links = DueLinks(deps.txns, deps.roscaEntries, deps.payments, deps.plans, deps.categories, deps.clock, deps.zakatPayments, deps.eventLinks, deps.spaceLegs)

    private suspend fun find(id: Id): InstallmentPlan =
        deps.plans.listAll().firstOrNull { it.id == id } ?: throw InstallmentError(uiText(TextKey.INSTALLMENT_NOT_FOUND))

    private suspend fun paidOf(plan: InstallmentPlan): Halalas = planPaidMinor(plan, deps.payments.listByPlan(plan.id))

    suspend fun list(today: IsoDate): List<InstallmentView> {
        val plans = deps.plans.listAll()
        val received = deps.txns.findByIds(plans.mapNotNull { it.receivedTransactionId }).associate { it.id to it.amountMinor }
        return plans.map { InstallmentView(it, dueProgress(installmentSchedule(it), paidOf(it), today), it.receivedTransactionId?.let(received::get)) }
    }

    suspend fun save(input: InstallmentInput): InstallmentPlan = save(input, newId = null)

    /** [newId] = معرّف الخطة الجديدة لو ثابت (`saveCounted` بعملية أول قسط)، وإلا معرّف جديد. */
    private suspend fun save(input: InstallmentInput, newId: Id?): InstallmentPlan {
        val existing = input.id?.let { find(it) }
        val draft = InstallmentPlan(
            id = existing?.id ?: newId ?: deps.ids.next("installment"),
            name = input.name,
            provider = input.provider.trim(),
            kind = input.kind,
            currency = input.currency,
            principalMinor = input.principalMinor,
            totalMinor = input.totalMinor,
            installmentMinor = input.installmentMinor,
            cycleMonths = input.cycleMonths,
            firstDueAt = input.firstDueAt,
            hasInterest = input.hasInterest,
            createdAt = existing?.createdAt ?: deps.clock.nowIso(),
            receivedTransactionId = existing?.receivedTransactionId,
            // §75-8: «مش قسط الخطة دي» بيفضل بعد التعديل
            dismissedTxnIds = existing?.dismissedTxnIds.orEmpty(),
            cycleWeeks = input.cycleWeeks,
            categoryId = input.categoryId,
            remindDayBefore = input.remindDayBefore ?: existing?.remindDayBefore,
            paidBeforeCount = input.paidBeforeCount ?: existing?.paidBeforeCount,
        )
        val plan = draft.copy(name = checkInstallmentPlan(draft).name)
        if (existing != null) {
            val paid = paidOf(plan)
            val linked = paid > 0 || existing.receivedTransactionId != null
            if (linked && (existing.currency != plan.currency || existing.kind != plan.kind)) {
                throw InstallmentError(uiText(TextKey.DUE_LOCKED_AFTER_LINK))
            }
            // الإجمالي الجديد أقل من اللي اتدفع ⇒ مرفوض
            dueProgress(installmentSchedule(plan), paid, plan.firstDueAt)
        }
        deps.plans.save(plan)
        return plan
    }

    suspend fun link(planId: Id, transactionId: Id, amountMinor: Halalas? = null): InstallmentPayment {
        val plan = find(planId)
        val (_, amount) = links.check(transactionId, Direction.OUT, plan.currency, plan.name, amountMinor)
        val room = subtractMoney(plan.totalMinor, paidOf(plan))
        if (amount > room) throw InstallmentError(uiText(TextKey.INSTALLMENT_OVER, formatMoney(room, plan.currency), formatMoney(amount - room, plan.currency)))
        val payment = InstallmentPayment(deps.ids.next("installmentpay"), planId, transactionId, amount)
        deps.uow.run {
            deps.payments.saveMany(listOf(payment))
            links.markKind(transactionId, installmentPaymentKind(plan), plan.categoryId ?: DuesCategories.forInstallment(plan.kind))
        }
        return payment
    }

    /** §82-20: معاينة «عدد الأقساط × القسط ⇒ الإجمالي» (و«ادفع بعدين»: المدفوع وقت الشراء والباقي) — الشاشة بتعرضها وما بتحسبش. */
    fun countPreview(installmentMinor: Halalas, count: Int, payLater: Boolean): InstallmentCountPreview =
        installmentCountPreview(installmentMinor, count, payLater)

    /**
     * §82-20: خطة بالعدد — الإجمالي = [InstallmentInput.installmentMinor] × [count] (اللي في [InstallmentInput.totalMinor] بيتجاهل). تقسيط
     * مشتريات من غير سعر (صفر) ⇒ السعر = الإجمالي (تابي وتمارا من غير زيادة). [firstPaymentTxnId] = عملية أول قسط («اشتري دلوقتي وادفع
     * بعدين» — أول ميعاد = يوم الشراء) ⇒ بتتفحص **قبل** الحفظ وبتتربط بالقسط بعده (القسط مصروف بعمليته — §56).
     */
    suspend fun saveCounted(input: InstallmentInput, count: Int, firstPaymentTxnId: Id? = null): InstallmentPlan {
        val total = totalFromCount(input.installmentMinor, count)
        val principal = if (input.kind == InstallmentKind.PURCHASE_PLAN && input.principalMinor <= 0) total else input.principalMinor
        val counted = input.copy(totalMinor = total, principalMinor = principal)
        if (firstPaymentTxnId == null) return save(counted)
        // مراجعة 0.3: الخطة وربط أول قسط وحدة واحدة (لو الربط وقع ما تفضلش خطة لوحدها)، ومعرّف الخطة ثابت من العملية ⇒ الإعادة بعد انقطاع
        // بتكمّل نفس الخطة ومش بتعمل خطة تانية ولا قسط مكرر
        val fixed = input.id ?: firstPaymentPlanId(firstPaymentTxnId)
        val retry = input.id == null && deps.plans.listAll().any { it.id == fixed }
        val done = retry && deps.payments.listByTransactionIds(listOf(firstPaymentTxnId)).any { it.planId == fixed }
        if (!done) links.check(firstPaymentTxnId, Direction.OUT, input.currency, input.name, input.installmentMinor)
        return deps.uow.run {
            val plan = save(if (retry) counted.copy(id = fixed) else counted, newId = fixed)
            if (!done) link(plan.id, firstPaymentTxnId, input.installmentMinor)
            plan
        }
    }

    /**
     * PLAN-0.6 §8 «مجموع أكتر من قسط»: خصم واحد من تابي/تمارا بيدفع أقساط [existing] (خطة ⇒ القسط) + خطط جديدة [fresh] (أول قسط = الخصم ده،
     * العدد). **المجموع لازم يساوي الخصم بالهللة** وإلا مرفوض. الخطط الجديدة والدفعات والنوع في وحدة عمل واحدة. بيرجّع معرّفات الخطط الجديدة.
     */
    suspend fun splitCharge(transactionId: Id, existing: List<Pair<Id, Halalas>>, fresh: List<Pair<InstallmentInput, Int>>): List<Id> {
        if (existing.size + fresh.size < 2 || existing.any { it.second <= 0 } || existing.map { it.first }.distinct().size != existing.size ||
            fresh.any { it.first.installmentMinor <= 0 || it.second < 1 }
        ) throw InstallmentError(uiText(app.masroufy.core.R06Key.BNPL_MULTI_ERR_PARTS))
        val plans = existing.map { find(it.first) }
        val currency = plans.firstOrNull()?.currency ?: fresh.first().first.currency
        val (txn, _) = links.check(transactionId, Direction.OUT, currency, plans.firstOrNull()?.name ?: fresh.first().first.name, null)
        val total = sumMoney(existing.map { it.second } + fresh.map { it.first.installmentMinor })
        if (total != txn.amountMinor) {
            throw InstallmentError(uiText(app.masroufy.core.R06Key.BNPL_MULTI_ERR_SUM, formatMoney(total, txn.currency), formatMoney(txn.amountMinor, txn.currency)))
        }
        for ((plan, part) in plans.zip(existing)) {
            if (plan.currency != txn.currency) throw InstallmentError(uiText(TextKey.DUE_TXN_CURRENCY, plan.name))
            val room = subtractMoney(plan.totalMinor, paidOf(plan))
            if (part.second > room) throw InstallmentError(uiText(TextKey.INSTALLMENT_OVER, formatMoney(room, plan.currency), formatMoney(part.second - room, plan.currency)))
        }
        return deps.uow.run {
            val made = fresh.map { (input, count) -> saveCounted(input.copy(currency = txn.currency), count) }
            val all = existing + made.map { it.id to it.installmentMinor }
            deps.payments.saveMany(all.map { (planId, amount) -> InstallmentPayment(deps.ids.next("installmentpay"), planId, transactionId, amount) })
            val first = plans.firstOrNull() ?: made.first()
            links.markKind(transactionId, installmentPaymentKind(first), DuesCategories.forInstallment(InstallmentKind.PURCHASE_PLAN))
            made.map { it.id }
        }
    }

    /** معرّف الخطة من عملية أول قسط (آمن من قص الأرقام زي `transferAskRequestId`). */
    private fun firstPaymentPlanId(txnId: Id): Id = "installment-" + transferAskRequestId(txnId).removePrefix("ask-")

    /**
     * عمليات ممكن تكون أول قسط («ادفع بعدين»): صادرة بنفس العملة وبالقسط بالظبط، ±[DUE_LINK_DAYS] من يوم الشراء، مش مربوطة بحاجة — الأقرب
     * لليوم الأول (نفس قاعدة اقتراح الربط §75-8).
     */
    suspend fun firstPaymentCandidates(currency: Currency, installmentMinor: Halalas, purchaseDate: IsoDate): List<Transaction> {
        if (installmentMinor <= 0 || !isValidIsoDate(purchaseDate)) return emptyList()
        val near = deps.txns.listByDateRange(shiftDays(purchaseDate, -DUE_LINK_DAYS), shiftDays(purchaseDate, DUE_LINK_DAYS)).filter {
            it.observedDirection == Direction.OUT && it.currency == currency && it.amountMinor == installmentMinor && dueLinkCandidate(it)
        }
        val ids = near.map { it.id }
        val taken = (deps.payments.listByTransactionIds(ids).map { it.transactionId } + deps.roscaEntries.listByTransactionIds(ids).map { it.transactionId }).toSet()
        val day = toDayNumber(parseIsoDate(purchaseDate))
        return near.filter { it.id !in taken }.sortedWith(compareBy({ kotlin.math.abs(toDayNumber(parseIsoDate(it.occurredAt.take(10))) - day) }, { it.occurredAt }, { it.id }))
    }

    /** r051: العمليات من [ids] المربوطة بأي حاجة (جمعية · قسط · تمويل مستلم · زكاة · نقطة · رجل تحويل) ⇒ ما تتعرضش للربط تاني. */
    suspend fun linkedElsewhere(ids: List<Id>): Set<Id> = links.taken(ids)

    /** مبلغ التمويل اللي نزل في الحساب (§59) — مرة واحدة لكل خطة تمويل، وبالعملية كلها. */
    suspend fun linkReceived(planId: Id, transactionId: Id): InstallmentPlan {
        val plan = find(planId)
        if (plan.kind != InstallmentKind.FINANCING) throw InstallmentError(uiText(TextKey.INSTALLMENT_RECEIVED_NEEDS_FINANCING))
        if (plan.receivedTransactionId != null) throw InstallmentError(uiText(TextKey.INSTALLMENT_ALREADY_RECEIVED))
        links.check(transactionId, Direction.IN, plan.currency, plan.name, null, TextKey.DUE_RECEIVED_NEEDS_IN)
        val linked = plan.copy(receivedTransactionId = transactionId)
        deps.uow.run {
            deps.plans.save(linked)
            links.markKind(transactionId, EconomicKind.FINANCING_RECEIVED, DuesCategories.FINANCING)
        }
        return linked
    }

    /** فك أي ربط على العملية (قسط أو مبلغ مستلم) — النوع والتصنيف بيرجعوا يتسألوا. */
    suspend fun unlink(transactionId: Id) {
        val found = deps.payments.listByTransactionIds(listOf(transactionId))
        val receivedBy = deps.plans.listAll().filter { it.receivedTransactionId == transactionId }
        if (found.isEmpty() && receivedBy.isEmpty()) return
        deps.uow.run {
            if (found.isNotEmpty()) deps.payments.deleteMany(found.map { it.id })
            for (plan in receivedBy) deps.plans.save(plan.copy(receivedTransactionId = null))
            links.clearKind(transactionId)
        }
    }

    /** مواعيد دين موجود — الدين نفسه ما بيتغيرش (بيفضل مطابق للتطبيق الحالي). */
    suspend fun setDebtTerms(terms: DebtTerms): DebtTerms {
        val obligation = deps.obligations.listByPerson(terms.personId).firstOrNull { it.id == terms.obligationId }
            ?: throw InstallmentError(uiText(TextKey.DEBT_NOT_FOUND))
        checkDueSchedule(debtSchedule(terms, obligation))
        deps.terms.save(terms)
        return terms
    }

    suspend fun clearDebtTerms(obligationId: Id) = deps.terms.remove(obligationId)
}
