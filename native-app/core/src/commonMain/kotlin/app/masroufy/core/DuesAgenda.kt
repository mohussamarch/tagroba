package app.masroufy.core

/**
 * «المستحقات» (OVERRIDES §43، §50) — قايمة واحدة بكل ارتباط مفتوح ليه ميعاد: أقساط الجمعية وأدوارها،
 * الأقساط للبنوك والشركات، الديون اللي ليها مواعيد، والاشتراكات والفواتير.
 * **مش كيان متخزن** — بتتحسب من الكيانات كل مرة، فمفيش رقم يقدر يبعد عن مصدره.
 */
enum class DueSource(val wire: String) {
    ROSCA_CONTRIBUTION("rosca_contribution"), ROSCA_PAYOUT("rosca_payout"),
    INSTALLMENT("installment"), DEBT("debt"), RECURRING("recurring"),
}

/** اتجاه الفلوس: هتدفع ولا هتستلم. */
enum class DueFlow(val wire: String) { PAY("pay"), RECEIVE("receive") }

enum class DueStatus(val wire: String) {
    /** ميعاده فات. */
    OVERDUE("overdue"),

    /** خلال [DUE_SOON_DAYS] أيام. */
    SOON("soon"),
    UPCOMING("upcoming"),
}

/** «قرّب» = خلال 3 أيام — نفس نافذة تنبيهات الاشتراكات. (قرار تنفيذ — المالك يقدر يغيّره.) */
const val DUE_SOON_DAYS = 3

data class DueItem(
    val source: DueSource,
    val sourceId: Id,
    val title: String,
    val dueAt: IsoDate,
    val amountMinor: Halalas,
    val currency: Currency,
    val flow: DueFlow,
    val status: DueStatus,
)

fun dueStatusOf(dueAt: IsoDate, today: IsoDate): DueStatus = when {
    dueAt < today -> DueStatus.OVERDUE
    daysBetween(today, dueAt) <= DUE_SOON_DAYS -> DueStatus.SOON
    else -> DueStatus.UPCOMING
}

private fun items(source: DueSource, id: Id, title: String, currency: Currency, flow: DueFlow, today: IsoDate, rows: List<Pair<IsoDate, Halalas>>) =
    rows.map { (date, amount) -> DueItem(source, id, title, date, amount, currency, flow, dueStatusOf(date, today)) }

fun roscaDueItems(r: Rosca, entries: List<RoscaEntry>, today: IsoDate, until: IsoDate): List<DueItem> {
    val status = roscaStatus(r, entries, today)
    val pay = items(DueSource.ROSCA_CONTRIBUTION, r.id, r.name, r.currency, DueFlow.PAY, today, unpaidInstallments(contributionSchedule(r), status.paidMinor, until))
    val receive = status.payouts.filter { it.state != PayoutState.RECEIVED }.map {
        DueItem(DueSource.ROSCA_PAYOUT, r.id, r.name, it.dueAt, it.amountMinor - it.receivedMinor, r.currency, DueFlow.RECEIVE, dueStatusOf(it.dueAt, today))
    }
    return pay + receive
}

fun installmentDueItems(p: InstallmentPlan, payments: List<InstallmentPayment>, today: IsoDate, until: IsoDate): List<DueItem> {
    val paid = sumMoney(payments.filter { it.planId == p.id }.map { it.amountMinor })
    return items(DueSource.INSTALLMENT, p.id, p.name, p.currency, DueFlow.PAY, today, unpaidInstallments(installmentSchedule(p), paid, until))
}

/** دين ليه مواعيد. «لك عنده» = هتستلم؛ القرض والأمانة = هتدفع. */
fun debtDueItems(terms: DebtTerms, obligation: Obligation, settlements: List<Settlement>, title: String, today: IsoDate, until: IsoDate): List<DueItem> {
    val paid = subtractMoney(obligation.originalMinor, remainingOfObligation(obligation, settlements))
    val flow = if (obligation.kind == ObligationKind.RECEIVABLE) DueFlow.RECEIVE else DueFlow.PAY
    return items(DueSource.DEBT, obligation.id, title, obligation.currency, flow, today, unpaidInstallments(debtSchedule(terms, obligation), paid, until))
}

/** الاشتراك أو الفاتورة: من الميعاد الجاي، وكل دورة لحد [until]، وعلى الأقل مرة. الموقوف ما بيظهرش. */
fun recurringDueItems(item: RecurringItem, today: IsoDate, until: IsoDate): List<DueItem> {
    if (!item.active) return emptyList()
    val rows = mutableListOf<Pair<IsoDate, Halalas>>()
    var date = item.nextDueAt
    while (rows.isEmpty() || date <= until) {
        rows += date to item.expectedMinor
        if (date > until || rows.size >= DUE_MAX_INSTALLMENTS) break
        date = shiftMonths(date, item.cycleMonths)
    }
    return items(DueSource.RECURRING, item.id, item.name, item.currency, DueFlow.PAY, today, rows)
}

/** بالميعاد، وفي نفس اليوم اللي هتدفعه قبل اللي هتستلمه. */
fun sortDues(items: List<DueItem>): List<DueItem> =
    items.sortedWith(compareBy<DueItem>({ it.dueAt }, { it.flow != DueFlow.PAY }, { it.source.wire }, { it.sourceId }))

/**
 * سطر الشهر (قرار المالك: «سطر لوحده»): «المتبقي» يفضل الدخل − المصروف زي spec/02،
 * وتحته «عليك لسه الشهر ده» و«المتبقي بعدها». اللي ميعاده فات **بيتحسب** — لسه عليك.
 * اللي **هتستلمه** بيتعرض لوحده وما بيتضافش: فلوس لسه ما جتش (القاعدة 10).
 */
data class DuesMonthLine(
    val toPayMinor: Halalas,
    val toReceiveMinor: Halalas,
    /** null لو «المتبقي» نفسه غير متاح — ما بنطلعش رقم من رقم مجهول. */
    val remainingAfterMinor: Halalas?,
    val payCount: Int,
)

fun duesMonthLine(items: List<DueItem>, period: Period, currency: Currency, remainingMinor: Halalas?): DuesMonthLine {
    val inMonth = items.filter { it.currency == currency && it.dueAt <= period.end }
    val pay = inMonth.filter { it.flow == DueFlow.PAY }
    val toPay = sumMoney(pay.map { it.amountMinor })
    val toReceive = sumMoney(inMonth.filter { it.flow == DueFlow.RECEIVE }.map { it.amountMinor })
    return DuesMonthLine(toPay, toReceive, remainingMinor?.let { subtractMoney(it, toPay) }, pay.size)
}

/**
 * الأرصدة فوق الشاشة: ليك وعليك. **مفيش تقاص** بين الاتنين (spec/02)، والأمانة لوحدها.
 * موقف الجمعية الموجب ادخار (ليك)، والسالب دين (عليك).
 */
data class DuesTotals(
    val receivableMinor: Halalas,
    val roscaSavedMinor: Halalas,
    val payableLoanMinor: Halalas,
    val payableCustodyMinor: Halalas,
    val installmentsLeftMinor: Halalas,
    val roscaOwedMinor: Halalas,
)

fun duesTotals(balances: List<PersonBalance>, roscas: List<RoscaStatus>, installmentsLeft: List<Halalas>): DuesTotals =
    DuesTotals(
        receivableMinor = sumMoney(balances.map { it.receivableMinor }),
        roscaSavedMinor = sumMoney(roscas.map { maxOf(it.positionMinor, 0) }),
        payableLoanMinor = sumMoney(balances.map { it.payableLoanMinor }),
        payableCustodyMinor = sumMoney(balances.map { it.payableCustodyMinor }),
        installmentsLeftMinor = sumMoney(installmentsLeft),
        roscaOwedMinor = sumMoney(roscas.map { maxOf(-it.positionMinor, 0) }),
    )
