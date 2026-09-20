package app.masroufy.core

/**
 * مطابقة الرصيد الجاري — نقل `src/domain/reconcile.ts` (spec/02 و OVERRIDES §7-ب).
 * افتتاحي + الوارد − الصادر **بترتيب المصدر** — مفيش إعادة ترتيب جوه اليوم.
 */
data class LedgerMovement(
    val date: IsoDate,
    /** ترتيب السطر في الملف الأصلي. */
    val sourceOrder: Int,
    val debitMinor: Halalas,
    val creditMinor: Halalas,
    /** الرصيد المعلن بعد الحركة، لو موجود. */
    val statedBalanceMinor: Halalas? = null,
    val reference: String? = null,
    val label: String? = null,
)

data class BalanceMismatch(
    val index: Int,
    val date: IsoDate,
    val computedMinor: Halalas,
    val statedMinor: Halalas,
    val differenceMinor: Halalas,
    val reference: String?,
    val label: String?,
    /** اليوم فيه كام حركة؟ أكتر من واحدة = الترتيب ممكن يكون هو السبب. */
    val sameDayCount: Int,
)

data class ReconcileResult(
    val openingMinor: Halalas,
    val openingAt: IsoDate,
    val closingMinor: Halalas,
    val closingAt: IsoDate?,
    val totalDebitMinor: Halalas,
    val totalCreditMinor: Halalas,
    val movementCount: Int,
    /** فاضية = مطابقة كاملة. */
    val mismatches: List<BalanceMismatch>,
    /** الحركات اللي فيها رصيد معلن (القابلة للمقارنة). */
    val checkedCount: Int,
    /** «توضيح الغموض؛ لا ادعاء تحديد أول فرق يقينًا» (spec/06). */
    val ambiguityNote: String?,
)

fun reconcileBalance(openingMinor: Halalas, openingAt: IsoDate, movements: List<LedgerMovement>): ReconcileResult {
    val perDay = movements.groupingBy { it.date }.eachCount()
    var balance = openingMinor
    var totalDebit = 0L
    var totalCredit = 0L
    var checked = 0
    val mismatches = mutableListOf<BalanceMismatch>()
    movements.forEachIndexed { index, m ->
        totalDebit = addMoney(totalDebit, m.debitMinor)
        totalCredit = addMoney(totalCredit, m.creditMinor)
        balance = addMoney(subtractMoney(balance, m.debitMinor), m.creditMinor)
        val stated = m.statedBalanceMinor ?: return@forEachIndexed
        checked++
        if (balance != stated) {
            mismatches += BalanceMismatch(index, m.date, balance, stated, subtractMoney(balance, stated), m.reference, m.label, perDay[m.date] ?: 1)
        }
    }
    val first = mismatches.firstOrNull()
    val note = when {
        first == null -> null
        first.sameDayCount > 1 ->
            uiText(TextKey.RECONCILE_FIRST_GAP_MANY, first.date, first.sameDayCount.toString())
        else -> uiText(TextKey.RECONCILE_FIRST_GAP_ONE, first.date)
    }
    return ReconcileResult(
        openingMinor, openingAt, balance, movements.lastOrNull()?.date, totalDebit, totalCredit,
        movements.size, mismatches, checked, note,
    )
}
