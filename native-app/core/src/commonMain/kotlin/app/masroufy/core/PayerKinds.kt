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
private const val PART_SEP = '\t'

/** قاعدة الحساب: النوع + (لـ«حاجة تانية») الاسم بالإيموجي اللي بيتكتب على العملية. */
data class PayerRule(val kind: EconomicKind, val label: String? = null)

/** القاعدة المحفوظة للحساب ده على الجهة (null = لسه بيتسأل). الشكل: «النوع=مفتاح الطرف» و(اختياري) «⇥الاسم». */
fun payerRuleOf(source: IncomeSource, partyKey: String): PayerRule? =
    source.payerKindRules.firstNotNullOfOrNull { rule ->
        val at = rule.indexOf(RULE_SEP)
        val rest = if (at > 0) rule.substring(at + 1) else ""
        if (at <= 0 || rest.substringBefore(PART_SEP) != partyKey) null
        else EconomicKind.entries.firstOrNull { it.wire == rule.substring(0, at) }
            ?.let { PayerRule(it, rest.substringAfter(PART_SEP, "").ifBlank { null }) }
    }

/** النوع المحفوظ للحساب ده على الجهة (null = لسه بيتسأل). */
fun payerRuleKind(source: IncomeSource, partyKey: String): EconomicKind? = payerRuleOf(source, partyKey)?.kind

/** الجهة بعد حفظ (أو مسح لو [kind] null) قاعدة الحساب [partyKey]. */
fun withPayerRule(source: IncomeSource, partyKey: String, kind: EconomicKind?, label: String? = null): IncomeSource {
    val kept = source.payerKindRules.filterNot { it.substringAfter(RULE_SEP, "").substringBefore(PART_SEP) == partyKey }
    val tail = label?.takeIf { it.isNotBlank() }?.let { "$PART_SEP$it" }.orEmpty()
    return source.copy(payerKindRules = if (kind == null) kept else kept + "${kind.wire}$RULE_SEP$partyKey$tail")
}

/** التحويل بنوع القاعدة مؤكد (التصنيف ما بيتغيرش — زي [asConfirmedSalary]). [label] («حاجة تانية») بيتكتب ملاحظة لو مفيش ملاحظة. */
fun asConfirmedKind(t: Transaction, kind: EconomicKind, nowIso: String, label: String? = null): Transaction =
    t.copy(
        economicKind = kind, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, suggestedKind = null, updatedAt = nowIso,
        note = if (label != null && t.note.isNullOrBlank()) label else t.note,
    )

/** r06-1b3: الإيموجيات الـ12 لـ«حاجة تانية» (التصميم `MoneyWhat`) — الأول هو المختار من الأول. */
val PAYER_CUSTOM_EMOJIS: List<String> = listOf("💼", "🎁", "🧾", "🎓", "🏠", "🚗", "📱", "✈️", "🩺", "🍽️", "💡", "⭐")
const val PAYER_CUSTOM_NAME_MAX: Int = 40

/** اختيار «حاجة تانية» محفوظ لحساب واحد: الإيموجي + الاسم. */
data class PayerCustomKind(val emoji: String, val name: String) {
    val label: String get() = "$emoji $name"
}

/** اختيارات «حاجة تانية» المحفوظة للحساب [partyKey] بس (مش لأي حساب تاني ولا جهة تانية). */
fun payerCustomKindsOf(source: IncomeSource, partyKey: String): List<PayerCustomKind> =
    source.payerCustomKinds.mapNotNull { row ->
        val parts = row.split(PART_SEP)
        if (parts.size != 3 || parts[0] != partyKey || parts[2].isBlank()) null else PayerCustomKind(parts[1], parts[2])
    }

/** الجهة بعد حفظ اختيار [custom] للحساب [partyKey] — نفس الاسم موجود ⇒ زي ما هي. */
fun withPayerCustomKind(source: IncomeSource, partyKey: String, custom: PayerCustomKind): IncomeSource {
    if (payerCustomKindsOf(source, partyKey).any { it.name == custom.name }) return source
    return source.copy(payerCustomKinds = source.payerCustomKinds + listOf(partyKey, custom.emoji, custom.name).joinToString(PART_SEP.toString()))
}

/** الاسم صالح: مش فاضي، من غير فاصل، ومش أطول من [PAYER_CUSTOM_NAME_MAX]، والإيموجي من الـ12. */
fun validPayerCustomKind(custom: PayerCustomKind): Boolean =
    custom.name.isNotBlank() && custom.name.length <= PAYER_CUSTOM_NAME_MAX && PART_SEP !in custom.name && custom.emoji in PAYER_CUSTOM_EMOJIS

/** ليه التحويل ده مش شبه المرتب (السطر الأصفر): المبلغ بعيد · الميعاد بعيد (كلمة راتب مش موجودة دايمًا لما بيتسأل). */
data class PayerWhy(val amountFar: Boolean, val dateFar: Boolean)

fun payerWhyOf(t: Transaction, employer: IncomeSource, profilePayday: Int?): PayerWhy = PayerWhy(
    amountFar = !amountNearSalary(t.amountMinor, employer.expectedMinor),
    dateFar = employer.payFrequency != PayFrequency.WEEKLY && !nearPayday(t.occurredAt, employer.expectedDayOfMonth ?: profilePayday),
)

/** تحويل داخل من حساب جهة شغل مش راتب لوحده ولا ليه قاعدة ⇒ «الفلوس دي إيه؟». */
fun asksPayerKind(t: Transaction, sources: List<IncomeSource>, profilePayday: Int?): Boolean {
    if (t.observedDirection != Direction.IN || t.economicKindConfirmed) return false
    val employer = employerByPayer(t, sources) ?: return false
    if (salaryTxnKey(t.id) in employer.payerKeys || salaryByPayerAllowed(t, employer, profilePayday)) return false
    val key = transferPartyOf(t)?.key ?: return false
    return payerRuleKind(employer, key) == null
}
