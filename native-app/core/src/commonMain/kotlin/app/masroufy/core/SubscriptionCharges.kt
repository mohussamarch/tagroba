package app.masroufy.core

import kotlin.math.abs

/**
 * §75-7 (قرار المالك 2026-10-08): **خصم الاشتراك بيتطابق لوحده بالمحل والمبلغ، والميعاد الجاي بيتحرك.** الشريحة S4.
 *
 * الخصم «بيدفع» اشتراك متأكد وشغال ([RecurringItem.active] و[RecurringItem.confirmed]) لو: فلوس طالعة · نفس العملة · نفس المحل
 * ([recurringKey] — نفس مفتاح الاقتراح والملخص) · نوعها شراء أو لسه ما اتحددش (زي `recurringSummary`) · مش تحويل بين محافظ ·
 * المبلغ ±٥٪ من المتوقع · والتاريخ ±٧ أيام من [RecurringItem.nextDueAt].
 * **اختيار Claude (المالك يقدر يغيّره):** السماحية هي نفسها بتاعة اقتراح الاشتراكات (`detectRecurring`: المبلغ ±٥٪ والموعد ±٧ أيام).
 *
 * الدفع بيحرّك الميعاد دورة (`shiftMonths(nextDueAt, cycleMonths)`) — ولو لسه مش بعد الخصم يتحرك تاني — فنفس الخصم مرتين (أو خصم مكرر
 * بنفس اليوم) بيحرّكه **مرة واحدة**: بعد الحركة الخصم بقى بعيد عن الميعاد الجديد. ده اللي بيخلّي اللحاق (`MatchSubscriptions.catchUp`)
 * آمن يتعاد. الاشتراك المتأخر بأكتر من أسبوع ما بيتطابقش (بيفضل «متأخر» لحد ما المالك يعدّله) — زي ما المالك قرر بالمحل والمبلغ والميعاد.
 */
const val SUBSCRIPTION_CHARGE_DAYS = 7
const val SUBSCRIPTION_CHARGE_PERCENT = 5

private val CHARGE_KINDS = setOf(EconomicKind.PURCHASE, EconomicKind.UNCLASSIFIED)

/** الخصم [t] بيدفع الاشتراك [item] عند ميعاده الحالي؟ */
fun paysSubscription(item: RecurringItem, t: Transaction): Boolean {
    if (!item.active || !item.confirmed || item.expectedMinor <= 0) return false
    if (t.observedDirection != Direction.OUT || t.currency != item.currency || t.economicKind !in CHARGE_KINDS || t.transferToWalletId != null) return false
    if (recurringKey(t) != item.merchantKey) return false
    if (abs(t.amountMinor - item.expectedMinor) * 100 > item.expectedMinor * SUBSCRIPTION_CHARGE_PERCENT) return false
    return abs(daysBetween(item.nextDueAt, t.occurredAt)) <= SUBSCRIPTION_CHARGE_DAYS
}

/** الميعاد الجاي بعد خصم يوم [chargeDate]: دورة على الأقل، ولحد ما يبقى بعد الخصم. */
fun nextDueAfterCharge(item: RecurringItem, chargeDate: IsoDate): IsoDate {
    var next = shiftMonths(item.nextDueAt, item.cycleMonths)
    var guard = 0
    while (next <= chargeDate && guard < DUE_MAX_INSTALLMENTS) {
        next = shiftMonths(next, item.cycleMonths)
        guard++
    }
    return next
}

/** خصم اتطابق: دفع ميعاد [paidDueAt] والميعاد بقى [nextDueAt]. */
data class SubscriptionCharge(val itemId: String, val transactionId: Id, val paidDueAt: IsoDate, val nextDueAt: IsoDate)

data class SubscriptionMatches(val changed: List<RecurringItem>, val charges: List<SubscriptionCharge>)

/**
 * بيعدّي على الخصومات بالتاريخ (وبعده المعرّف) ويحرّك كل اشتراك بيتدفع بيها. الخصم الواحد بيدفع اشتراك واحد بالكتير (مفيش اشتراكين بنفس
 * المحل والعملة — `ManageRecurring.save`). [SubscriptionMatches.changed] = الاشتراكات اللي ميعادها اتحرك بس (بآخر ميعاد).
 */
fun applySubscriptionCharges(items: List<RecurringItem>, charges: List<Transaction>): SubscriptionMatches {
    val current = LinkedHashMap<String, RecurringItem>().apply { items.forEach { put(it.id, it) } }
    val matched = mutableListOf<SubscriptionCharge>()
    val used = HashSet<Id>()
    for (t in charges.distinctBy { it.id }.sortedWith(compareBy({ it.occurredAt }, { it.id }))) {
        if (t.id in used) continue
        val item = current.values.firstOrNull { paysSubscription(it, t) } ?: continue
        val next = nextDueAfterCharge(item, t.occurredAt)
        matched += SubscriptionCharge(item.id, t.id, item.nextDueAt, next)
        current[item.id] = item.copy(nextDueAt = next)
        used += t.id
    }
    val original = items.associateBy { it.id }
    return SubscriptionMatches(current.values.filter { original[it.id]?.nextDueAt != it.nextDueAt }, matched)
}

/**
 * التراجع (مراجعة S4): الخصومات [removed] اتمسحت (التراجع عن دفعتها) ⇒ الاشتراك اللي ميعاده اتحرك **بيها** بيرجع دورة. من غير تخزين: الخصم
 * بيدفع الميعاد اللي قبل الحالي (`shiftMonths(nextDueAt, −cycleMonths)`) والدفع ده بيوصّل للميعاد الحالي بالظبط، ومفيش خصم تاني لسه موجود
 * ([kept]) بيدفع نفس الدورة. غير كده (المالك عدّل الميعاد · خصم تاني حرّكه بعده · خصم مكرر والأصلي فاضل) ما بيتلمسش.
 * الأحدث الأول — خصمين لنفس الاشتراك في نفس الدفعة بيرجعوا الاتنين. ⚠️ آخر الشهر: `shiftMonths` بيقص اليوم (31 ⇒ 28)، فالرجوع ممكن
 * يقع على يوم أبدر بكام يوم من الأصلي (نفس القص اللي حصل وهو رايح).
 */
fun revertSubscriptionCharges(items: List<RecurringItem>, removed: List<Transaction>, kept: List<Transaction>): List<RecurringItem> {
    val current = LinkedHashMap<String, RecurringItem>().apply { items.forEach { put(it.id, it) } }
    val gone = removed.map { it.id }.toSet()
    val still = kept.filter { it.id !in gone }
    for (t in removed.distinctBy { it.id }.sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })) {
        val item = current.values.firstOrNull { item ->
            val before = item.copy(nextDueAt = shiftMonths(item.nextDueAt, -item.cycleMonths))
            paysSubscription(before, t) && nextDueAfterCharge(before, t.occurredAt) == item.nextDueAt && still.none { paysSubscription(before, it) }
        } ?: continue
        current[item.id] = item.copy(nextDueAt = shiftMonths(item.nextDueAt, -item.cycleMonths))
    }
    val original = items.associateBy { it.id }
    return current.values.filter { original[it.id]?.nextDueAt != it.nextDueAt }
}
