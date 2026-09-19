package app.masroufy.core

import kotlin.math.abs

/**
 * الاشتراكات والفواتير الدورية — نقل `src/domain/recurring.ts` + الكيانات.
 * الاقتراح: ٣ دورات متتابعة، دفعة واحدة في الشهر بالكتير، خدمة مناسبة، المبلغ ±٥٪ والموعد ±٧ أيام.
 * الاقتراح **مش عملية مالية**.
 */
data class RecurringItem(
    val id: String,
    val name: String,
    val merchantKey: String,
    /** "subscription" / "bill". */
    val kind: String,
    /** 1 / 3 / 12. */
    val cycleMonths: Int,
    val expectedMinor: Halalas,
    val currency: Currency,
    val nextDueAt: IsoDate,
    val active: Boolean,
    val confirmed: Boolean,
)

data class RecurringCandidate(
    val name: String,
    val merchantKey: String,
    val currency: Currency,
    val expectedMinor: Halalas,
    val nextDueAt: IsoDate,
    val cycleMonths: Int,
    val transactionIds: List<String>,
    val reason: String,
)

fun shiftMonths(date: IsoDate, delta: Int): IsoDate {
    val p = parseIsoDate(date)
    val total = p.year * 12 + p.month - 1 + delta
    val year = total.floorDiv(12)
    val month = total.mod(12) + 1
    return formatIsoDate(DateParts(year, month, minOf(p.day, daysInMonth(year, month))))
}

fun recurringKey(t: Transaction): String =
    if (!t.merchantId.isNullOrEmpty()) "id:" + t.merchantId else "name:" + normalizeText(t.rawMerchantName ?: "")

private val SERVICES = Regex("اشتراك|اتصالات|فواتير|مرافق")
private val KNOWN_SERVICE = Regex("^(netflix|spotify|google one|youtube premium|icloud|stc|mobily|zain)(${JsText.B}|$)", RegexOption.IGNORE_CASE)
private val RECURRING_KINDS = setOf(EconomicKind.PURCHASE, EconomicKind.UNCLASSIFIED)

fun detectRecurring(rows: List<Transaction>, categories: List<Category>): List<RecurringCandidate> {
    val names = categories.associate { it.id to it.name }
    val groups = LinkedHashMap<String, MutableList<Transaction>>()
    val unique = LinkedHashMap<String, Transaction>().apply { rows.forEach { put(it.id, it) } }
    for (t in unique.values) {
        if (t.observedDirection != Direction.OUT || t.economicKind !in RECURRING_KINDS) continue
        val name = t.rawMerchantName?.let(JsText::trim)
        if (name.isNullOrEmpty()) continue
        val categoryText = names[t.categoryId ?: ""] ?: t.sourceCategory ?: ""
        if (!(SERVICES.containsMatchIn(categoryText) || KNOWN_SERVICE.containsMatchIn(name))) continue
        groups.getOrPut(recurringKey(t) + "|" + t.currency.name) { mutableListOf() }.add(t)
    }
    val out = mutableListOf<RecurringCandidate>()
    for (group0 in groups.values) {
        val group = group0.sortedBy { it.occurredAt }
        if (group.size < 3) continue
        if (group.map { it.occurredAt.take(7) }.toSet().size != group.size) continue
        val recent = group.takeLast(3)
        val p = recent.map { parseIsoDate(it.occurredAt) }
        val gaps = listOf(1, 2).map { i -> (p[i].year - p[i - 1].year) * 12 + p[i].month - p[i - 1].month }
        val cycle = gaps[0]
        if (cycle !in listOf(1, 3, 12) || gaps[1] != cycle) continue
        if ((1..2).any { i -> abs(daysBetween(shiftMonths(recent[i - 1].occurredAt, cycle), recent[i].occurredAt)) > 7 }) continue
        val amounts = recent.map { it.amountMinor }.sorted()
        val median = amounts[1]
        if (median <= 0 || amounts.any { it > MAX_SAFE_HALALAS || abs(it - median) * 100 > median * 5 }) continue
        val last = recent[2]
        out += RecurringCandidate(
            last.rawMerchantName!!, recurringKey(last), last.currency, median, shiftMonths(last.occurredAt, cycle), cycle, group.map { it.id },
            "٣ دورات متتابعة لخدمة مناسبة، بمبلغ متقارب ±٥٪ وموعد متقارب ±٧ أيام.",
        )
    }
    return out
}

data class RecurringSummary(val paidMinor: Halalas?, val paidCount: Int, val annualMinor: Halalas, val overdue: Boolean)

fun recurringSummary(item: RecurringItem, rows: List<Transaction>, today: IsoDate): RecurringSummary {
    val from = shiftMonths(today, -12)
    val matched = rows.filter {
        it.occurredAt > from && it.occurredAt <= today && it.currency == item.currency && it.observedDirection == Direction.OUT &&
            recurringKey(it) == item.merchantKey && it.economicKind in RECURRING_KINDS
    }
    val unique = LinkedHashMap<String, Transaction>().apply { matched.forEach { put(it.id, it) } }.values.toList()
    val annual = item.expectedMinor * (12 / item.cycleMonths)
    assertHalalas(annual)
    return RecurringSummary(
        if (item.merchantKey.startsWith("manual:")) null else sumMoney(unique.map { it.amountMinor }),
        unique.size, annual, item.active && item.nextDueAt < today,
    )
}

class RecurringError(message: String) : IllegalArgumentException(message)

fun validateRecurring(item: RecurringItem) {
    if (JsText.trim(item.name).isEmpty() || item.name.length > 120) throw RecurringError("اكتب اسم الخدمة، بحد أقصى ١٢٠ حرف.")
    if (item.cycleMonths !in listOf(1, 3, 12)) throw RecurringError("اختار دورة شهرية أو كل ٣ شهور أو سنوية.")
    if (item.kind !in listOf("subscription", "bill")) throw RecurringError("نوع الالتزام غير صالح.")
    assertHalalas(item.expectedMinor)
    if (item.expectedMinor <= 0) throw RecurringError("قيمة الدورة لازم تكون أكبر من صفر.")
    parseIsoDate(item.nextDueAt)
    assertHalalas(item.expectedMinor * (12 / item.cycleMonths))
    if (item.merchantKey.isEmpty() || item.merchantKey.length > 240) throw RecurringError("ربط الخدمة غير صالح.")
}
