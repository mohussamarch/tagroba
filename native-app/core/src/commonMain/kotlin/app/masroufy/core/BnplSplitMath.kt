package app.masroufy.core

/**
 * r06-1b4 — خصم تابي/تمارا المجمّع و«الدفعة دي رقم كام؟» (التصميم المعتمد 2026-10-22 · OVERRIDES §108 تعديل r06-1b4). كله صافي وبالهللة.
 */

/** أقصى عدد أقساط بندوّر فيها على مجموع مطابق (2^10 = 1024 تجربة — صغير ومحدود). الزيادة بتتساب. */
const val BNPL_SUGGEST_MAX = 10

/**
 * «غالبًا: «أ» + «ب» = المبلغ بالظبط»: أقل عدد أقساط مستحقة (قسطين أو أكتر) مجموعهم = [chargeMinor] بالظبط. [dues] = (الخطة ⇒ قسطها)
 * لخطط المزوّد المفتوحة، بالترتيب. لو فيه أكتر من مجموعة بنفس أقل عدد ⇒ مش واضح ⇒ فاضية (ما بنخمّنش — اختيار Claude). فاضية = مفيش اقتراح.
 */
fun bnplSumSuggestion(chargeMinor: Halalas, dues: List<Pair<Id, Halalas>>): List<Id> {
    val items = dues.filter { it.second > 0 }.distinctBy { it.first }.take(BNPL_SUGGEST_MAX)
    if (chargeMinor <= 0 || items.size < 2) return emptyList()
    var best: Int? = null
    var bestSize = Int.MAX_VALUE
    var ambiguous = false
    for (mask in 1 until (1 shl items.size)) {
        val size = mask.countOneBits()
        if (size < 2 || size > bestSize) continue
        var sum = 0L
        for (i in items.indices) if (mask and (1 shl i) != 0) sum = addMoney(sum, items[i].second)
        if (sum != chargeMinor) continue
        if (size < bestSize) {
            best = mask; bestSize = size; ambiguous = false
        } else {
            ambiguous = true
        }
    }
    val m = best ?: return emptyList()
    if (ambiguous) return emptyList()
    return items.indices.filter { m and (1 shl it) != 0 }.map { items[it].first }
}

/** «وزّع على الأقساط» بيشتغل بس لو: جزئين على الأقل · كل جزء مقري (مش null) وأكبر من صفر · المجموع = الخصم بالظبط · كل مشترى جديد ليه اسم. */
fun bnplSplitReady(chargeMinor: Halalas, amounts: List<Halalas?>, newNamed: Boolean): Boolean {
    if (amounts.size < 2 || !newNamed || amounts.any { it == null || it <= 0 }) return false
    return sumMoney(amounts.filterNotNull()) == chargeMinor
}

/**
 * «الدفعة دي رقم [number]»: أول ميعاد في الخطة = يوم الدفعة دي ناقص (الرقم − 1) دورة — فالدفعة دي ميعادها النهارده واللي قبلها فاتوا.
 * [number] من 1 لـ[count].
 */
fun bnplFirstDueFor(date: IsoDate, cycle: BnplCycle, number: Int): IsoDate {
    require(number >= 1) { "number $number" }
    val weeks = cycle.weeks
    return if (weeks == null) shiftCycles(date, CycleUnit.MONTH, -(number - 1)) else shiftCycles(date, CycleUnit.WEEK, -(number - 1) * weeks)
}

/** الدفعات اللي اتدفعت قبل ما الخطة تتسجل ([InstallmentPlan.paidBeforeCount]) — من غير عمليات، ومش مصروف. */
fun paidBeforeMinor(p: InstallmentPlan): Halalas = multiplyMoneyByInt(p.installmentMinor, (p.paidBeforeCount ?: 0).coerceAtLeast(0).toLong())

/** المدفوع على الخطة = دفعاتها المربوطة بعمليات + اللي اتدفع قبل التسجيل. */
fun planPaidMinor(p: InstallmentPlan, payments: List<InstallmentPayment>): Halalas =
    addMoney(paidBeforeMinor(p), sumMoney(payments.filter { it.planId == p.id }.map { it.amountMinor }))

/** «خطتك» بـ«الدفعة دي رقم [number]»: اللي قبلها اتدفعت، والباقي = القسط × (العدد − الرقم). [number] = 1 ⇒ زي [bnplPreview]. */
fun bnplPreviewAt(amountMinor: Halalas, currency: Currency, date: IsoDate, count: Int, cycle: BnplCycle, number: Int): BnplPreview {
    val at = number.coerceIn(1, count)
    val s = bnplSchedule(amountMinor, currency, bnplFirstDueFor(date, cycle, at), count, cycle)
    val paid = multiplyMoneyByInt(amountMinor, at.toLong())
    return BnplPreview(s.totalMinor, subtractMoney(s.totalMinor, paid), currency, bnplSteps(s, paid))
}
