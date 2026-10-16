package app.masroufy.core

/**
 * «اللي رجعلك» (مواصفة النقل §1.12 · §2.7 — `InvestReturns.dc.html`): «عائد استثمار» من أول يناير لحد النهارده بعملة البلد (من غير
 * المعكوس)، متجمّع بالتصنيف. ولو رجّعته تستثمره: نفس المبلغ كل شهر (`per` = المجموع ÷ الشهور اللي فاتت من السنة) 120 شهر بمتوسط
 * أملاكك، وبعدين بفلوس النهارده (خصم الغلاء الرسمي). أعداد صحيحة بس (نفس `monthlyRateFromAnnual` و`deflate` في `GrowthMath.kt`).
 */
data class ReturnRow(
    /** null = عملية من غير تصنيف ⇒ «عائد استثمار». */
    val categoryId: Id?,
    val totalMinor: Halalas,
    /** الشهور (1..12) اللي جه فيها، من غير تكرار وبالترتيب. */
    val months: List<Int>,
)

/** حساب «لو رجّعته تستثمره» — null في [InvestReturnsResult.math] = المتوسطات مش متاحة ⇒ «غير متاح». */
data class ReinvestMath(
    val perMonthMinor: Halalas,
    val mixBp: Int,
    /** بعد 10 سنين بفلوس النهارده لو رجّعته تستثمره. */
    val reinvestMinor: Halalas,
    /** نفس الفلوس بعد 10 سنين بفلوس النهارده لو صرفتها (الـ120 شهر من غير ربح). */
    val spentMinor: Halalas,
)

data class InvestReturnsResult(val rows: List<ReturnRow>, val totalMinor: Halalas, val math: ReinvestMath?)

const val REINVEST_MONTHS = 120

/** «عائد استثمار» بعملة [currency] بين أول السنة و[today] — من غير المعكوس ومن غير المقترح المستني تأكيد. */
fun isInvestReturn(t: Transaction, currency: Currency, today: IsoDate): Boolean =
    t.currency == currency && t.economicKind == EconomicKind.INVESTMENT_INCOME && t.reversedById == null && t.reversalOfId == null &&
        t.occurredAt.take(10) <= today && t.occurredAt.take(4) == today.take(4)

/**
 * [mixBp] = متوسط أملاكك (`holdingsMixBp`) أو متوسط الأسهم لو مفيش حاجة بتكبر · [cpiBp] = الغلاء الرسمي. أي واحد null ⇒ [ReinvestMath]
 * null. مفيش عائد السنة دي ⇒ null (الكارت بيقول «ما وصلكش …»).
 */
fun investReturns(txns: List<Transaction>, currency: Currency, today: IsoDate, mixBp: Int?, cpiBp: Int?): InvestReturnsResult? {
    val mine = txns.filter { isInvestReturn(it, currency, today) }
    val total = sumMoney(mine.map { it.amountMinor })
    if (mine.isEmpty() || total <= 0) return null
    val rows = mine.groupBy { it.categoryId }.map { (cat, list) ->
        ReturnRow(cat, sumMoney(list.map { it.amountMinor }), list.map { parseIsoDate(it.occurredAt.take(10)).month }.distinct().sorted())
    }.sortedByDescending { it.totalMinor }
    val math = if (mixBp == null || cpiBp == null) null else reinvest(total, parseIsoDate(today).month, mixBp, cpiBp)
    return InvestReturnsResult(rows, total, math)
}

/** `per` بالتقريب لأقرب هللة · 120 مرة: `pot += pot × شهري + per` · ثم خصم الغلاء 10 سنين. معدل برّه المسموح ⇒ null (مش رقم مخترع). */
fun reinvest(totalMinor: Halalas, monthsElapsed: Int, mixBp: Int, cpiBp: Int): ReinvestMath? = runCatching {
    require(monthsElapsed in 1..12)
    val per = mulDivHalfUp(totalMinor, 1, monthsElapsed.toLong())
    val m = monthlyRateFromAnnual(mixBp)
    var pot = 0L
    repeat(REINVEST_MONTHS) { pot = pot + mulDivWide(pot, m, MONTHLY_RATE_SCALE) + per }
    ReinvestMath(per, mixBp, deflate(pot, REINVEST_MONTHS, cpiBp), deflate(per * REINVEST_MONTHS, REINVEST_MONTHS, cpiBp))
}.getOrNull()
