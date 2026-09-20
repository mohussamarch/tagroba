package app.masroufy.core

/**
 * الدفتر — نقل `src/domain/ledger.ts` (قواعد spec/02 كدوال نقية).
 * **عملية واحدة، مبلغ واحد، طرق عرض كتير**: الوسوم ما بتضاعفش المجموع.
 */
class LedgerError(message: String) : IllegalStateException(message)

/** نصيب المستخدم = المبلغ − اللي اتخصص للآخرين كـ**دين**. الهدية ما بتخصمش (spec/06). */
fun personalShareOf(transaction: Transaction, allocations: List<PersonAllocation>): Halalas {
    if (!countsAsPersonalExpense(transaction.economicKind)) return 0
    val mine = allocations.filter { it.transactionId == transaction.id }
    val allocatedTotal = sumMoney(mine.map { it.amountMinor })
    if (allocatedTotal > transaction.amountMinor) {
        throw LedgerError(
            uiText(TextKey.LEDGER_ALLOCATIONS_EXCEED, formatMoney(allocatedTotal), formatMoney(transaction.amountMinor)),
        )
    }
    val receivable = sumMoney(mine.filter { it.allocationKind == AllocationKind.RECEIVABLE }.map { it.amountMinor })
    return subtractMoney(transaction.amountMinor, receivable)
}

data class PeriodTotals(
    val incomeMinor: Halalas,
    /** المصروف الشخصي غير المستبعد، بعد فصل اللي على الآخرين. */
    val personalExpenseMinor: Halalas,
    /** الدخل − المصروف. مش السيولة ولا رصيد البنك (spec/02). */
    val remainingMinor: Halalas,
    /** المستبعد من الميزانية — بيتعرض لوحده وما بيختفيش. */
    val excludedExpenseMinor: Halalas,
)

/** مجاميع الفترة. المدخل عمليات الفترة بس — الدالة ما بتفلترش بالتاريخ. */
fun computePeriodTotals(transactions: List<Transaction>, allocations: List<PersonAllocation>): PeriodTotals {
    var income = 0L
    var expense = 0L
    var excluded = 0L
    for (t in transactions) {
        if (countsAsIncome(t.economicKind)) {
            income = addMoney(income, t.amountMinor)
            continue
        }
        if (!countsAsPersonalExpense(t.economicKind)) continue
        val share = personalShareOf(t, allocations)
        if (t.excludedFromBudget) excluded = addMoney(excluded, share) else expense = addMoney(expense, share)
    }
    return PeriodTotals(income, expense, subtractMoney(income, expense), excluded)
}

/** مجموع العمليات الموسومة — العمليات **المميزة** بمعرّفها، فوسمين على عملية واحدة = مبلغها مرة واحدة. */
fun sumByTag(transactions: List<Transaction>, taggedTransactionIds: List<String>, allocations: List<PersonAllocation> = emptyList()): Halalas {
    val wanted = taggedTransactionIds.toSet()
    val seen = mutableSetOf<String>()
    var total = 0L
    for (t in transactions) {
        if (t.id !in wanted || !seen.add(t.id)) continue
        total = addMoney(total, personalShareOf(t, allocations))
    }
    return total
}

data class PersonBalance(
    val personId: String,
    /** «لك عنده». */
    val receivableMinor: Halalas,
    /** «له عندك» — قروض. */
    val payableLoanMinor: Halalas,
    /** «له عندك» — أمانات. ما بتتقاصش مع القرض. */
    val payableCustodyMinor: Halalas,
)

/** المتبقي من التزام = الأصل − تسوياته. السالب ممنوع ومش بيتسكت عليه. */
fun remainingOfObligation(obligation: Obligation, settlements: List<Settlement>): Halalas {
    val paid = sumMoney(settlements.filter { it.obligationId == obligation.id }.map { it.amountMinor })
    val remaining = subtractMoney(obligation.originalMinor, paid)
    if (remaining < 0) throw LedgerError(uiText(TextKey.LEDGER_SETTLEMENTS_EXCEED, obligation.id))
    return remaining
}

/** أرصدة شخص — **مفيش تقاص تلقائي** بين «لك» و«عليك»، والقرض والأمانة منفصلين (spec/02). */
fun computePersonBalance(personId: String, obligations: List<Obligation>, settlements: List<Settlement>): PersonBalance {
    val mine = obligations.filter { it.personId == personId }
    fun totalOf(kind: ObligationKind) = sumMoney(mine.filter { it.kind == kind }.map { remainingOfObligation(it, settlements) })
    return PersonBalance(personId, totalOf(ObligationKind.RECEIVABLE), totalOf(ObligationKind.LOAN_PAYABLE), totalOf(ObligationKind.CUSTODY_PAYABLE))
}

data class SettlementCheck(
    val allowed: Boolean,
    /** سبب الرفض بلغة المستخدم (spec/06). */
    val reason: String?,
    /** اللي بيتسوّى فعلًا. */
    val settledMinor: Halalas,
    /** الزيادة اللي محتاجة إجراء صريح كأمانة مستقلة. */
    val surplusMinor: Halalas,
)

/** فحص تسوية قبل تطبيقها — الزيادة ما بتتبلعش ولا بتتسحب من التزام تاني (spec/06). */
fun checkSettlement(obligation: Obligation, settlements: List<Settlement>, amountMinor: Halalas): SettlementCheck {
    if (amountMinor <= 0) return SettlementCheck(false, uiText(TextKey.SETTLEMENT_AMOUNT_POSITIVE), 0, 0)
    val remaining = remainingOfObligation(obligation, settlements)
    if (remaining == 0L) return SettlementCheck(false, uiText(TextKey.SETTLEMENT_FULLY_PAID), 0, amountMinor)
    if (amountMinor <= remaining) return SettlementCheck(true, null, amountMinor, 0)
    val kindLabel = when (obligation.kind) {
        ObligationKind.CUSTODY_PAYABLE -> uiText(TextKey.OBLIGATION_CUSTODY)
        ObligationKind.LOAN_PAYABLE -> uiText(TextKey.OBLIGATION_LOAN)
        ObligationKind.RECEIVABLE -> uiText(TextKey.OBLIGATION_RECEIVABLE)
    }
    val surplus = subtractMoney(amountMinor, remaining)
    return SettlementCheck(
        allowed = false,
        // بالريال مش بالهللة: الوحدة الداخلية ما تظهرش للمستخدم أبدًا
        reason = uiText(TextKey.SETTLEMENT_OVER_REMAINING, kindLabel, formatMoney(remaining), formatMoney(amountMinor), formatMoney(surplus)),
        settledMinor = remaining,
        surplusMinor = surplus,
    )
}

data class GrossSplit(val principalMinor: Halalas, val feeMinor: Halalas)

/** «رسوم 20 ضمن خصم 1020 ⇒ أصل 1000 + رسوم 20؛ مش 1040» (spec/06). */
fun splitGrossIntoPrincipalAndFee(grossMinor: Halalas, feeMinor: Halalas): GrossSplit {
    if (feeMinor < 0) throw LedgerError(uiText(TextKey.LEDGER_FEE_NEGATIVE))
    if (feeMinor > grossMinor) throw LedgerError(uiText(TextKey.LEDGER_FEE_OVER_GROSS, feeMinor.toString(), grossMinor.toString()))
    return GrossSplit(subtractMoney(grossMinor, feeMinor), feeMinor)
}
