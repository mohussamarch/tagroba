package app.masroufy.core

import kotlin.math.abs

/**
 * PLAN-0.6 §7 (قرار المالك 2026-10-17): «نفس الشخص اللي بيحولي فلوس معينة علي مدار الشهر … ممكن هو نفسه يكون بيحول الراتب».
 * ربط الحساب بجهة الشغل **ما بقاش** بيخلّي أي تحويل منه «راتب» لوحده. الراتب لوحده في حالتين بس:
 * 1. نص الرسالة فيه «راتب» أو «رواتب» أو «مرتب» ([hasSalaryWord]).
 * 2. المبلغ قريب من مرتب الجهة المكتوب ([IncomeSource.expectedMinor]) في حدود [SALARY_AMOUNT_TOLERANCE_PERCENT]%، **و**اليوم في حدود
 *    [SALARY_DATE_TOLERANCE_DAYS] أيام من يوم الراتب (يوم الجهة، وإلا يوم الملف) ([nearPayday]).
 * غير كده التحويل بيتسأل «الفلوس دي إيه؟» ([PAYER_ASK_KINDS])، والرد بيتحفظ **قاعدة على الحساب** ([IncomeSource.payerKindRules])
 * بتتطبق على التحويلات الجاية من نفس الحساب اللي مش راتب. الحدّين اقتراح (❓ في OVERRIDES — المالك يغيّرهم).
 * دوال نقية — مفيش حساب فلوس بكسور: المقارنة بأعداد صحيحة بالهللة.
 */
const val SALARY_AMOUNT_TOLERANCE_PERCENT: Long = 10
const val SALARY_DATE_TOLERANCE_DAYS: Int = 3

/** الكلمات اللي بتأكد «راتب» من نص الرسالة. */
val PAYER_SALARY_WORDS: List<String> = listOf("راتب", "رواتب", "مرتب")

/** ردود «الفلوس دي إيه؟» الجاهزة: استرداد مصاريف · مكافأة أو بدل · سلفة (و«حاجة تانية» = أي نوع داخل من القايمة الطويلة). */
val PAYER_ASK_KINDS: List<EconomicKind> = listOf(EconomicKind.REFUND_RECEIVED, EconomicKind.BONUS, EconomicKind.ADVANCE_RECEIVED)

/** نص الرسالة (الوصف · اسم التاجر · نوع العملية من البنك) فيه كلمة راتب. */
fun hasSalaryWord(t: Transaction): Boolean =
    listOfNotNull(t.rawDescription, t.rawMerchantName, t.sourceCategory, t.sourceOperationType, t.note)
        .any { text -> PAYER_SALARY_WORDS.any { it in text } }

/** المبلغ في حدود ±10% من [expected] — بالضرب في أعداد صحيحة (|الفرق| × 100 ≤ المتوقع × 10). */
fun amountNearSalary(amountMinor: Halalas, expected: Halalas?): Boolean {
    if (expected == null || expected <= 0) return false
    return abs(amountMinor - expected) * 100 <= expected * SALARY_AMOUNT_TOLERANCE_PERCENT
}

/** اليوم في حدود [SALARY_DATE_TOLERANCE_DAYS] من يوم الراتب [payday] في الشهر ده أو اللي قبله أو اللي بعده (29–31 بيتقيد بآخر الشهر). */
fun nearPayday(date: IsoDate, payday: Int?): Boolean {
    if (payday == null || payday !in 1..31) return false
    val parts = parseIsoDate(date.take(10))
    val day = toDayNumber(parts)
    return (-1..1).any { off ->
        val idx = parts.year * 12 + (parts.month - 1) + off
        val y = idx / 12
        val m = idx % 12 + 1
        abs(toDayNumber(DateParts(y, m, minOf(payday, daysInMonth(y, m)))) - day) <= SALARY_DATE_TOLERANCE_DAYS
    }
}

/**
 * التحويل ده من حساب الجهة يتسجل «راتب» لوحده: كلمة راتب، أو مبلغ قريب + ميعاد قريب. الأسبوعي: المبلغ القريب كفاية (يوم القبض
 * الأسبوعي بيتكرر كل 7 أيام فالميعاد مش علامة — اختيار Claude).
 */
fun salaryByPayerAllowed(t: Transaction, employer: IncomeSource, profilePayday: Int?): Boolean {
    if (hasSalaryWord(t)) return true
    if (!amountNearSalary(t.amountMinor, employer.expectedMinor)) return false
    if (employer.payFrequency == PayFrequency.WEEKLY) return true
    return nearPayday(t.occurredAt, employer.expectedDayOfMonth ?: profilePayday)
}

private const val RULE_SEP = '='

/** النوع المحفوظ للحساب ده على الجهة (null = لسه بيتسأل). */
fun payerRuleKind(source: IncomeSource, partyKey: String): EconomicKind? =
    source.payerKindRules.firstNotNullOfOrNull { rule ->
        val at = rule.indexOf(RULE_SEP)
        if (at <= 0 || rule.substring(at + 1) != partyKey) null
        else EconomicKind.entries.firstOrNull { it.wire == rule.substring(0, at) }
    }

/** الجهة بعد حفظ (أو مسح لو [kind] null) قاعدة الحساب [partyKey]. */
fun withPayerRule(source: IncomeSource, partyKey: String, kind: EconomicKind?): IncomeSource {
    val kept = source.payerKindRules.filterNot { it.substringAfter(RULE_SEP, "") == partyKey }
    return source.copy(payerKindRules = if (kind == null) kept else kept + "${kind.wire}$RULE_SEP$partyKey")
}

/** التحويل بنوع القاعدة مؤكد (التصنيف ما بيتغيرش — زي [asConfirmedSalary]). */
fun asConfirmedKind(t: Transaction, kind: EconomicKind, nowIso: String): Transaction =
    t.copy(economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, suggestedKind = null, updatedAt = nowIso)

/** تحويل داخل من حساب جهة شغل مش راتب لوحده ولا ليه قاعدة ⇒ «الفلوس دي إيه؟». */
fun asksPayerKind(t: Transaction, sources: List<IncomeSource>, profilePayday: Int?): Boolean {
    if (t.observedDirection != Direction.IN || t.economicKindConfirmed) return false
    val employer = employerByPayer(t, sources) ?: return false
    if (salaryTxnKey(t.id) in employer.payerKeys || salaryByPayerAllowed(t, employer, profilePayday)) return false
    val key = transferPartyOf(t)?.key ?: return false
    return payerRuleKind(employer, key) == null
}
