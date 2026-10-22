package app.masroufy.core

/**
 * r06-darahim (PLAN-0.6 §12-5): **«مكرر؟»** — خصمين من **نفس المحل** (الاسم بعد [normalizeText] — أو نفس التاجر المحفوظ)، بنفس العملة،
 * والمبلغ متقارب (الفرق ≤ 1% من الأكبر أو ≤ وحدة كاملة — ريال أو جنيه — أيهم أكبر)، وفي خلال [DUPLICATE_WINDOW_SECONDS] ثانية ⇒
 * **الأحدث** بيتسأل عليه. عمره ما بيتمسح لوحده.
 * - **من غير ساعة ⇒ من غير سؤال** (القاعدة 10): العملية اللي دقتها «يوم» ما بنعرفش بينهم كام ثانية.
 * - الحساب كله بأعداد صحيحة (الهللة) — مفيش كسور.
 */
const val DUPLICATE_WINDOW_SECONDS = 60L

private val CLOCK_SECONDS = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

/** ثانية العملية من أول يوم 1970 (توقيتها المحلي — المقارنة بين عمليتين في نفس البلد)، أو null لو مالهاش ساعة. */
fun operationSecondOf(t: Transaction): Long? {
    if (t.datePrecision != "minute") return null
    val m = CLOCK_SECONDS.find(t.sourceTime?.trim().orEmpty()) ?: return null
    val (h, min) = m.groupValues[1].toInt() to m.groupValues[2].toInt()
    val s = m.groupValues[3].ifEmpty { "0" }.toInt()
    if (h > 23 || min > 59 || s > 59) return null
    val day = runCatching { toDayNumber(parseIsoDate(t.occurredAt.take(10))) }.getOrNull() ?: return null
    return day.toLong() * 86_400L + h * 3_600L + min * 60L + s
}

/** المبلغين متقاربين: الفرق ≤ max(1% من الأكبر، 100 وحدة صغرى). */
fun closeAmounts(a: Halalas, b: Halalas): Boolean {
    val diff = if (a > b) a - b else b - a
    val big = maxOf(a, b)
    return diff * 100 <= big || diff <= 100L
}

private fun merchantKeyOf(t: Transaction): String? =
    t.merchantId?.let { "m:$it" } ?: t.rawMerchantName?.let(::normalizeText)?.takeIf { it.isNotEmpty() }?.let { "n:$it" }

/**
 * معرّفات **الأحدث** في كل زوج مكرر محتمل من [rows]. بيشوف الخصومات بس (طالع · مش تحويل لمحفظة) واللي ما اتجاوبش عليها قبل كده
 * (`duplicateAskPending == null` — «الاتنين صح» ما بيرجعش يتسأل).
 */
fun duplicateChargeIds(rows: List<Transaction>): Set<Id> {
    val timed = rows.mapNotNull { t ->
        if (t.observedDirection != Direction.OUT || t.transferToWalletId != null) return@mapNotNull null
        val key = merchantKeyOf(t) ?: return@mapNotNull null
        val at = operationSecondOf(t) ?: return@mapNotNull null
        Triple(t, key, at)
    }.sortedWith(compareBy<Triple<Transaction, String, Long>> { it.third }.thenBy { it.first.sourceOrder }.thenBy { it.first.id })
    val out = LinkedHashSet<Id>()
    for (i in timed.indices) {
        val (newer, key, at) = timed[i]
        if (newer.duplicateAskPending != null) continue
        for (j in i - 1 downTo 0) {
            val (older, oKey, oAt) = timed[j]
            if (at - oAt > DUPLICATE_WINDOW_SECONDS) break
            if (oKey == key && older.currency == newer.currency && closeAmounts(older.amountMinor, newer.amountMinor)) {
                out += newer.id
                break
            }
        }
    }
    return out
}
