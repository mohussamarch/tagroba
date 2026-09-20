package app.masroufy.core

/**
 * عرض العمليات — نقل `cashSummary.ts` + `dayGroups.ts` + `search.ts` + `expenseClassification.ts`
 * + `settlementSuggestion.ts` + `settlementCommand.ts`. الشاشة ما بتحسبش مبلغ (CLAUDE.md #4).
 */
data class Wallet(
    val id: Id,
    val name: String,
    val currency: Currency,
    /** "bank" / "cash" / "own_abroad" / "digital_wallet". */
    val kind: String,
    val openingBalanceMinor: Halalas,
    val openingAt: IsoDate,
    /** آخر أربعة أرقام بس (CLAUDE.md #11). */
    val accountLast4: String? = null,
)

data class CashSummary(
    val wallet: Wallet,
    /** الرصيد التقريبي — الكاش اللي اتصرف ومااتسجلش مش هيبان (OVERRIDES §32). */
    val balanceMinor: Halalas,
    val inSinceOpeningMinor: Halalas,
    val outSinceOpeningMinor: Halalas,
    /** المصروف الشخصي اللي اتدفع كاش في الفترة. */
    val spentInPeriodMinor: Halalas,
    /** عمليات الكاش في الفترة — الأحدث الأول. */
    val periodTransactions: List<Transaction>,
)

/** العملية كاش؟ على محفظة الكاش، أو المستخدم علّم إنها اتدفعت كاش. */
fun isCashTransaction(t: Transaction, cashWalletId: String): Boolean =
    t.walletId == cashWalletId || t.transferToWalletId == cashWalletId || t.isCashTagged

fun summarizeCash(wallet: Wallet, transactions: List<Transaction>, allocations: List<PersonAllocation>, periodStart: IsoDate, periodEnd: IsoDate): CashSummary {
    var inMinor = 0L
    var outMinor = 0L
    var spent = 0L
    val inPeriod = mutableListOf<Transaction>()
    for (t in transactions) {
        // الرصيد: حركات محفظة الكاش نفسها من يوم البداية (اللي قبله داخل في رصيد البداية)
        if (t.occurredAt >= wallet.openingAt) {
            if (t.transferToWalletId == wallet.id) inMinor = addMoney(inMinor, t.amountMinor)
            else if (t.walletId == wallet.id) {
                if (t.observedDirection == Direction.IN) inMinor = addMoney(inMinor, t.amountMinor) else outMinor = addMoney(outMinor, t.amountMinor)
            }
        }
        if (t.occurredAt >= periodStart && t.occurredAt <= periodEnd && isCashTransaction(t, wallet.id)) {
            inPeriod += t
            if (t.observedDirection == Direction.OUT && t.transferToWalletId != wallet.id && countsAsPersonalExpense(t.economicKind) && !t.excludedFromBudget) {
                spent = addMoney(spent, personalShareOf(t, allocations))
            }
        }
    }
    val sorted = inPeriod.sortedWith { a, b -> if (a.occurredAt == b.occurredAt) b.sourceOrder - a.sourceOrder else if (a.occurredAt < b.occurredAt) 1 else -1 }
    return CashSummary(wallet, subtractMoney(addMoney(wallet.openingBalanceMinor, inMinor), outMinor), inMinor, outMinor, spent, sorted)
}

data class DayGroup(
    val date: IsoDate,
    /** اللي خرج — موجب. */
    val outgoingMinor: Halalas,
    /** اللي دخل — موجب. مفيش «صافي اليوم» عشان ما يخلطش اتنين مختلفين. */
    val incomingMinor: Halalas,
    val transactions: List<Transaction>,
)

/** الأحدث الأول، وجوه اليوم ترتيب الكشف معكوس (آخر سطر فوق). */
fun groupByDay(transactions: List<Transaction>): List<DayGroup> {
    val byDate = LinkedHashMap<IsoDate, Triple<Long, Long, MutableList<Transaction>>>()
    for (t in transactions) {
        val (out, inc, list) = byDate[t.occurredAt] ?: Triple(0L, 0L, mutableListOf())
        list += t
        byDate[t.occurredAt] = if (t.observedDirection == Direction.OUT) Triple(addMoney(out, t.amountMinor), inc, list) else Triple(out, addMoney(inc, t.amountMinor), list)
    }
    return byDate.map { (date, g) -> DayGroup(date, g.first, g.second, g.third.sortedByDescending { it.sourceOrder }) }.sortedByDescending { it.date }
}

/* ───────────────────────── البحث (محلي بالكامل — spec/01) ───────────────────────── */

/** الهامش المعلن للمبلغ: ٥٪. */
const val AMOUNT_TOLERANCE_PER_THOUSAND = 50L

data class SearchableTransaction(
    val transaction: Transaction,
    val categoryName: String? = null,
    val tagNames: List<String>? = null,
    val merchantNames: List<String>? = null,
)

data class SearchHit(val transaction: Transaction, /** «merchant» / «description» / «note» / «category» / «tag» / «cash» / «amount». */ val matchedFields: List<String>)
data class AmountQuery(val targetMinor: Halalas, val tolerancePerThousand: Long)
data class ParsedQuery(val text: String, val amount: AmountQuery?)

/** الرقم بيتعامل كنص وكمبلغ مع بعض (spec/06: بحث المبلغ مستقل). */
fun parseQuery(raw: String, tolerancePerThousand: Long = AMOUNT_TOLERANCE_PER_THOUSAND): ParsedQuery {
    val text = JsText.trim(raw)
    val latin = latinizeDigits(text).replace('٫', '.')
    val looksNumeric = latin.isNotEmpty() && latin.all { it in '0'..'9' || it == '.' || it == ',' || JsText.isWhitespace(it) } && latin.any { it in '0'..'9' }
    val amount = if (looksNumeric) tryParseMoney(latin) else null
    return ParsedQuery(text, if (amount != null && amount > 0) AmountQuery(amount, tolerancePerThousand) else null)
}

private val CASH_WORDS = setOf(normalizeText("كاش"), normalizeText("نقدي"), "CASH")

private fun textMatches(haystack: String?, needle: String, compact: String): Boolean =
    !haystack.isNullOrEmpty() && (normalizeText(haystack).contains(needle) || normalizeCompact(haystack).contains(compact))

fun searchTransactions(items: List<SearchableTransaction>, query: ParsedQuery): List<SearchHit> {
    val needle = normalizeText(query.text)
    val compact = normalizeCompact(query.text)
    val isCash = needle in CASH_WORDS
    if (needle.isEmpty() && query.amount == null) return emptyList()
    return items.mapNotNull { item ->
        val t = item.transaction
        val matched = buildList {
            if (needle.isNotEmpty()) {
                if (textMatches(t.rawMerchantName, needle, compact) || item.merchantNames.orEmpty().any { textMatches(it, needle, compact) }) add("merchant")
                if (textMatches(t.rawDescription, needle, compact)) add("description")
                if (textMatches(t.note, needle, compact)) add("note")
                if (textMatches(item.categoryName, needle, compact)) add("category")
                if (item.tagNames.orEmpty().any { textMatches(it, needle, compact) }) add("tag")
                if (isCash && t.isCashTagged) add("cash")
            }
            if (query.amount != null && withinRelativeTolerance(t.amountMinor, query.amount.targetMinor, query.amount.tolerancePerThousand)) add("amount")
        }
        if (matched.isEmpty()) null else SearchHit(t, matched)
    }
}

/* ───────────────────────── فصل المصروف عن حركة الفلوس (OVERRIDES §7-أ) ───────────────────────── */

/** تصنيفات المصدر اللي معناها فلوس «انتقلت» مش «اتصرفت» — اتشتقت من البيانات (معيار القبول). */
val NON_EXPENSE_SOURCE_CATEGORIES = listOf("تحويلات", "محافظ رقمية", "تقسيط", "استثمار", "سحب نقدي", "ذهب")
private val NON_EXPENSE_NORMALIZED = NON_EXPENSE_SOURCE_CATEGORIES.map(::normalizeText).toSet()

fun isNonExpenseSourceCategory(category: String?): Boolean = !category.isNullOrEmpty() && normalizeText(category) in NON_EXPENSE_NORMALIZED

data class ClassifiableRow(val debitMinor: Halalas, val creditMinor: Halalas, val sourceCategory: String? = null)
data class ExpenseBreakdown(
    val totalDebitMinor: Halalas,
    val totalCreditMinor: Halalas,
    val nonExpenseMinor: Halalas,
    /** المدين − اللي مش مصروف. */
    val realExpenseMinor: Halalas,
    val nonExpenseByCategory: Map<String, Halalas>,
)

fun computeExpenseBreakdown(rows: List<ClassifiableRow>): ExpenseBreakdown {
    var debit = 0L
    var credit = 0L
    var nonExpense = 0L
    val byCategory = LinkedHashMap<String, Long>()
    for (row in rows) {
        debit = addMoney(debit, row.debitMinor)
        credit = addMoney(credit, row.creditMinor)
        if (row.debitMinor > 0 && isNonExpenseSourceCategory(row.sourceCategory)) {
            nonExpense = addMoney(nonExpense, row.debitMinor)
            byCategory[row.sourceCategory!!] = addMoney(byCategory[row.sourceCategory] ?: 0L, row.debitMinor)
        }
    }
    return ExpenseBreakdown(debit, credit, nonExpense, debit - nonExpense, byCategory)
}

/* ───────────────────────── ربط عملية بدين (OVERRIDES §30) ───────────────────────── */

/** الداخلة بتحصّل دين ليك، والخارجة بتسدّد دين عليك أو بترجّع أمانة. */
fun settleableKinds(direction: Direction): List<ObligationKind> =
    if (direction == Direction.IN) listOf(ObligationKind.RECEIVABLE) else listOf(ObligationKind.LOAN_PAYABLE, ObligationKind.CUSTODY_PAYABLE)

/** الأقل بين مبلغ العملية والمتبقي من الدين. */
fun suggestedSettlementMinor(transactionMinor: Halalas, remainingMinor: Halalas): Halalas = minOf(transactionMinor, remainingMinor)

class SettlementError(message: String) : IllegalStateException(message)

/** تجهيز تسوية على لقطة متسقة من التخزين (مش رصيد شاشة متخزن). نفس الطلب مرتين = نفس التسوية. */
fun prepareSettlement(input: Settlement, personId: String, obligation: Obligation?, rows: List<Settlement>): Settlement {
    if (obligation == null || obligation.personId != personId) throw SettlementError(uiText(TextKey.SETTLEMENT_OBLIGATION_MISSING))
    if (input.amountMinor > MAX_SAFE_HALALAS || input.amountMinor <= 0) throw SettlementError(uiText(TextKey.SETTLEMENT_AMOUNT_HALALAS))
    rows.firstOrNull { it.id == input.id }?.let { existing ->
        if (existing.obligationId != input.obligationId || existing.amountMinor != input.amountMinor || existing.transactionId != input.transactionId) {
            throw SettlementError(uiText(TextKey.SETTLEMENT_REQUEST_CONFLICT))
        }
        return existing
    }
    val check = checkSettlement(obligation, rows, input.amountMinor)
    if (!check.allowed) throw SettlementError(check.reason ?: uiText(TextKey.SETTLEMENT_REJECTED))
    return input
}
