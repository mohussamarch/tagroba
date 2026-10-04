package app.masroufy.usecase

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
import app.masroufy.core.IsoDate
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
)

class ManageInstallments(private val deps: ManageInstallmentsDeps) {
    private val links = DueLinks(deps.txns, deps.roscaEntries, deps.payments, deps.plans, deps.categories, deps.clock, deps.zakatPayments)

    private suspend fun find(id: Id): InstallmentPlan =
        deps.plans.listAll().firstOrNull { it.id == id } ?: throw InstallmentError(uiText(TextKey.INSTALLMENT_NOT_FOUND))

    private suspend fun paidOf(planId: Id): Halalas = sumMoney(deps.payments.listByPlan(planId).map { it.amountMinor })

    suspend fun list(today: IsoDate): List<InstallmentView> {
        val plans = deps.plans.listAll()
        val received = deps.txns.findByIds(plans.mapNotNull { it.receivedTransactionId }).associate { it.id to it.amountMinor }
        return plans.map { InstallmentView(it, dueProgress(installmentSchedule(it), paidOf(it.id), today), it.receivedTransactionId?.let(received::get)) }
    }

    suspend fun save(input: InstallmentInput): InstallmentPlan {
        val existing = input.id?.let { find(it) }
        val draft = InstallmentPlan(
            id = existing?.id ?: deps.ids.next("installment"),
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
        )
        val plan = draft.copy(name = checkInstallmentPlan(draft).name)
        if (existing != null) {
            val paid = paidOf(plan.id)
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
        val room = subtractMoney(plan.totalMinor, paidOf(planId))
        if (amount > room) throw InstallmentError(uiText(TextKey.INSTALLMENT_OVER, formatMoney(room, plan.currency), formatMoney(amount - room, plan.currency)))
        val payment = InstallmentPayment(deps.ids.next("installmentpay"), planId, transactionId, amount)
        deps.uow.run {
            deps.payments.saveMany(listOf(payment))
            links.markKind(transactionId, installmentPaymentKind(plan), DuesCategories.forInstallment(plan.kind))
        }
        return payment
    }

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
