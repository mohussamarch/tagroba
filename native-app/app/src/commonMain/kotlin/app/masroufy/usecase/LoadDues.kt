package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DuesMonthLine
import app.masroufy.core.DuesTotals
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.PersonBalance
import app.masroufy.core.addMoney
import app.masroufy.core.computePersonBalance
import app.masroufy.core.debtDueItems
import app.masroufy.core.dueProgress
import app.masroufy.core.duesMonthLine
import app.masroufy.core.duesTotals
import app.masroufy.core.financingCostInPeriod
import app.masroufy.core.installmentDueItems
import app.masroufy.core.installmentSchedule
import app.masroufy.core.recurringDueItems
import app.masroufy.core.roscaDueItems
import app.masroufy.core.roscaStatus
import app.masroufy.core.shiftMonths
import app.masroufy.core.sortDues
import app.masroufy.core.sumMoney
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository

/**
 * شاشة «المستحقات» (OVERRIDES §43، §50): الأرصدة فوق، وتحتها كل اللي ليه ميعاد بالترتيب،
 * وسطر الشهر اللي بيتعرض تحت «المتبقي» في الرئيسية.
 * القراية محدودة بعدد الجمعيات والخطط والأشخاص والاشتراكات — مش بعدد العمليات.
 */
data class DuesView(
    val totals: DuesTotals,
    /** بالميعاد: المتأخر الأول، وبعده لحد شهر قدام أو آخر الفترة (الأبعد). */
    val agenda: List<DueItem>,
    val month: DuesMonthLine,
    /**
     * أرباح التمويل اللي اتحققت في الفترة — **مصروف** مش ظاهر في العمليات نفسها (القسط نفسه مش مصروف).
     * الميزانية لما تتبني في كوتلن بتضيفه على المصروف. ⚠️ لسه ما اتربطش بـ`computePeriodTotals`.
     */
    val financingCostMinor: Halalas,
)

data class LoadDuesDeps(
    val roscas: RoscaRepository,
    val roscaEntries: RoscaEntryRepository,
    val plans: InstallmentPlanRepository,
    val payments: InstallmentPaymentRepository,
    val terms: DebtTermsRepository,
    val people: PersonRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val recurring: RecurringRepository,
    val txns: TransactionRepository,
)

class LoadDues(private val deps: LoadDuesDeps) {
    /**
     * المواعيد بس (من غير أرصدة) لحد [until] — للتقويم (§65). نفس بُناة `DuesAgenda` اللي `load` بيستعملهم، فمفيش حساب جدول تاني.
     * الدين اللي صاحبه مش في قايمة الأشخاص ما بيظهرش (زي `load`).
     */
    suspend fun dueItems(today: IsoDate, until: IsoDate): List<DueItem> {
        val items = mutableListOf<DueItem>()
        for (r in deps.roscas.listAll()) items += roscaDueItems(r, deps.roscaEntries.listByRosca(r.id), today, until)
        for (p in deps.plans.listAll()) items += installmentDueItems(p, deps.payments.listByPlan(p.id), today, until)
        val names = deps.people.listAll().associate { it.id to it.name }
        val obligations = names.keys.flatMap { deps.obligations.listByPerson(it) }.associateBy { it.id }
        for (terms in deps.terms.listAll()) {
            val o = obligations[terms.obligationId] ?: continue
            items += debtDueItems(terms, o, deps.settlements.listByObligations(listOf(o.id)), names.getValue(o.personId), today, until)
        }
        for (r in deps.recurring.listAll()) items += recurringDueItems(r, today, until)
        return sortDues(items)
    }

    /** [remainingMinor] = «المتبقي» من الرئيسية (null لو غير متاح) — سطر الشهر بيطرح منه اللي عليك. */
    suspend fun load(today: IsoDate, period: Period, currency: Currency, remainingMinor: Halalas?): DuesView {
        val nextMonth = shiftMonths(today, 1)
        val until = if (nextMonth > period.end) nextMonth else period.end
        val items = mutableListOf<DueItem>()

        val roscaStatuses = deps.roscas.listAll().map { r ->
            val entries = deps.roscaEntries.listByRosca(r.id)
            items += roscaDueItems(r, entries, today, until)
            roscaStatus(r, entries, today)
        }

        var financingCost = 0L
        val installmentsLeft = deps.plans.listAll().map { p ->
            val payments = deps.payments.listByPlan(p.id)
            items += installmentDueItems(p, payments, today, until)
            val dates = deps.txns.findByIds(payments.map { it.transactionId }).associate { it.id to it.occurredAt }
            financingCost = addMoney(financingCost, financingCostInPeriod(p, payments, payments.mapNotNull { pay -> dates[pay.transactionId]?.let { pay.id to it } }.toMap(), period))
            dueProgress(installmentSchedule(p), sumMoney(payments.map { it.amountMinor }), today).remainingMinor
        }

        val names = deps.people.listAll().associate { it.id to it.name }
        val balances = mutableListOf<PersonBalance>()
        val termsByObligation = deps.terms.listAll().associateBy { it.obligationId }
        for (personId in names.keys) {
            val obligations = deps.obligations.listByPerson(personId)
            val settlements = deps.settlements.listByObligations(obligations.map { it.id })
            balances += computePersonBalance(personId, obligations, settlements)
            for (o in obligations) {
                val terms = termsByObligation[o.id] ?: continue
                items += debtDueItems(terms, o, settlements, names.getValue(personId), today, until)
            }
        }

        for (r in deps.recurring.listAll()) items += recurringDueItems(r, today, until)

        return DuesView(
            totals = duesTotals(balances, roscaStatuses, installmentsLeft),
            agenda = sortDues(items),
            month = duesMonthLine(items, period, currency, remainingMinor),
            financingCostMinor = financingCost,
        )
    }
}
