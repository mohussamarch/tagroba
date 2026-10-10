package app.masroufy.core

/**
 * «دخلك زاد ٢٠٪ من ساعة الشغل الجديد — ومصروفك زاد ٢٥٪» (OVERRIDES §47 · §48) — دالة نقية من غير نص ولا تنبيه.
 *
 * بعد ما المصدر الجديد يكمّل **3 شهور مالية كاملة**: متوسط الدخل الشهري ومتوسط المصروف الحقيقي في الـ3 شهور اللي قبل البداية
 * والـ3 اللي بعدها، والفرق **بالعُشر من المية** (125 = 12.5٪) بأعداد صحيحة من غير كسور عائمة.
 * - **النقوط ومكافأة نهاية الخدمة برا متوسط الدخل** (§44.1 · §64-٧ — مرة واحدة مش دخل شهري).
 * - الشهر اللي فيه البداية لو مش من أوله **ما بيدخلش** لا قبل ولا بعد (نصه قديم ونصه جديد).
 * - أي شهر من الستة **مفيهوش ولا عملية** بعملة المصدر ⇒ `null` كله (مفيش رقم من غير مصدر — قاعدة 10).
 * - مفيش دخل قبل (أول شغل) ⇒ المتوسطات موجودة والنسبة `null` (القسمة على صفر مش رقم).
 */
const val COMPARISON_MONTHS = 3

data class SourceStartComparison(
    val sourceId: Id,
    val currency: Currency,
    val before: List<Period>,
    val after: List<Period>,
    val incomeBeforeAvgMinor: Halalas,
    val incomeAfterAvgMinor: Halalas,
    /** المصروف الحقيقي = الشخصي + المستبعد من الميزانية (الاتنين اتصرفوا فعلًا). */
    val expenseBeforeAvgMinor: Halalas,
    val expenseAfterAvgMinor: Halalas,
    val incomeChangeTenthPercent: Long?,
    val expenseChangeTenthPercent: Long?,
    /** فيه عمليات لسه نوعها ما اتحددش في الستة شهور ⇒ الأرقام ناقصة والشاشة تقول كده (`assessCoverage`). */
    val totalsReliable: Boolean,
)

private fun periodAt(index: Int, payday: Int): Period = buildPeriod(index.floorDiv(12), index.mod(12) + 1, payday)

/** الـ3 شهور قبل والـ3 بعد بداية المصدر. */
fun comparisonPeriods(startedAt: IsoDate, payday: Int): Pair<List<Period>, List<Period>> {
    val first = periodForDate(startedAt, payday)
    val firstAfter = periodIndex(first) + if (first.start == startedAt) 0 else 1
    val firstBefore = periodIndex(first) - COMPARISON_MONTHS
    return (0 until COMPARISON_MONTHS).map { periodAt(firstBefore + it, payday) } to (0 until COMPARISON_MONTHS).map { periodAt(firstAfter + it, payday) }
}

/**
 * التغيّر بالعُشر من المية من [before] لـ[after] (تقريب نص لبعيد عن الصفر). `null` لو [before] مش موجب أو [after] سالب.
 * كله `Long`: الاتنين أقل من الحد الآمن ⇒ الفرق × 1000 جوه مدى `Long`.
 */
fun changeTenthPercent(before: Halalas, after: Halalas): Long? {
    if (before <= 0 || after < 0) return null
    val num = (after - before) * 1000
    var q = num / before
    val r = num % before
    if (2 * (if (r < 0) -r else r) >= before) q += if (num < 0) -1 else 1
    return q
}

private fun average(sum: Halalas, n: Int): Halalas {
    val q = sum / n
    val r = sum % n
    return if (2 * (if (r < 0) -r else r) >= n) q + (if (sum < 0) -1 else 1) else q
}

/**
 * المقارنة، أو `null` لو المصدر لسه ما كمّلش 3 شهور كاملة لحد [today] أو فيه شهر من غير بيانات.
 * كل عملية في شهر **حسابها** زي الرئيسية (§75-3 — `countingDate`: الراتب اللي نزل قبل يوم الراتب بشوية في الشهر الجديد) ⇒ [transactions]
 * لازم تبدأ من `countingReadStart` لأول شهر.
 */
fun compareAroundSourceStart(
    source: IncomeSource,
    transactions: List<Transaction>,
    allocations: List<PersonAllocation>,
    payday: Int,
    today: IsoDate,
    policy: EstimatePolicy = EstimatePolicy.current,
): SourceStartComparison? {
    val (before, after) = comparisonPeriods(source.startedAt, payday)
    if (after.last().end >= today) return null
    val mine = transactions.filter { it.currency == source.currency }.map { countingDate(it, payday, policy) to it }
    fun inPeriod(p: Period) = mine.filter { (day, _) -> day >= p.start && day <= p.end }.map { it.second }
    val months = (before + after).map(::inPeriod)
    if (months.any { it.isEmpty() }) return null
    fun income(txns: List<Transaction>) = computePeriodTotals(txns.filter { it.economicKind !in NOT_IN_INCOME_AVERAGES }, allocations).incomeMinor
    fun expense(txns: List<Transaction>) = computePeriodTotals(txns, allocations).let { addMoney(it.personalExpenseMinor, it.excludedExpenseMinor) }
    val b = months.take(COMPARISON_MONTHS)
    val a = months.drop(COMPARISON_MONTHS)
    val incomeBefore = sumMoney(b.map(::income))
    val incomeAfter = sumMoney(a.map(::income))
    val expenseBefore = sumMoney(b.map(::expense))
    val expenseAfter = sumMoney(a.map(::expense))
    return SourceStartComparison(
        sourceId = source.id,
        currency = source.currency,
        before = before,
        after = after,
        incomeBeforeAvgMinor = average(incomeBefore, COMPARISON_MONTHS),
        incomeAfterAvgMinor = average(incomeAfter, COMPARISON_MONTHS),
        expenseBeforeAvgMinor = average(expenseBefore, COMPARISON_MONTHS),
        expenseAfterAvgMinor = average(expenseAfter, COMPARISON_MONTHS),
        // النسبة من المجاميع مش المتوسطات المقرّبة — نفس النسبة بالظبط من غير تقريب مرتين
        incomeChangeTenthPercent = changeTenthPercent(incomeBefore, incomeAfter),
        expenseChangeTenthPercent = changeTenthPercent(expenseBefore, expenseAfter),
        totalsReliable = assessCoverage(months.flatten()).totalsReliable,
    )
}
