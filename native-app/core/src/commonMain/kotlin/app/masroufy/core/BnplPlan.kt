package app.masroufy.core

/**
 * r051 — لوحة «أول خصم» وقسم «مشتريات بالتقسيط» (التصميم المعتمد `BnplSheet.dc.html` · `Installments.dc.html` — OVERRIDES §102):
 * «خطتك» بتتحسب قدام المالك وهو بيختار (الدفعة = مبلغ أول خصم · الإجمالي = الدفعة × العدد — تأكيد المالك)، وجدول «المواعيد» لكل خطة.
 * كله من جدول الأقساط المشترك (`DueSchedule`) ⇒ نفس التواريخ اللي «المستحقات» و«القادم» والتنبيهات بتشوفها.
 */

/** «قسمتها على كام؟» — نفس الاختيارات لكل المزوّدين (التصميم المعتمد)، والمختار أول ما اللوحة تفتح = [BNPL_DEFAULT_SPLIT]. */
val BNPL_SPLITS: List<Int> = listOf(2, 3, 4, 6)
const val BNPL_DEFAULT_SPLIT = 4

/** حالة دفعة في الجدول: «اتدفعت» · «الجاية» (أول واحدة ما اتدفعتش كاملة) · بعدها. */
enum class BnplStepState { PAID, NEXT, LATER }

data class BnplStep(val number: Int, val dueAt: IsoDate, val amountMinor: Halalas, val state: BnplStepState)

/** «خطتك»: الإجمالي والباقي بعد أول دفعة والدفعات بمواعيدها. */
data class BnplPreview(val totalMinor: Halalas, val remainingMinor: Halalas, val currency: Currency, val steps: List<BnplStep>)

/** الجدول: أول ميعاد = يوم الخصم، والباقي في نفس اليوم كل شهر (آخر الشهر لو اليوم مش فيه) أو كل 14 يوم بالظبط. */
fun bnplSchedule(installmentMinor: Halalas, currency: Currency, firstDueAt: IsoDate, count: Int, cycle: BnplCycle): DueSchedule {
    require(count >= 1) { "count $count" }
    val total = totalFromCount(installmentMinor, count)
    return cycle.weeks?.let { DueSchedule(firstDueAt, it, installmentMinor, total, currency, unit = CycleUnit.WEEK) }
        ?: DueSchedule(firstDueAt, 1, installmentMinor, total, currency)
}

/** «خطتك» قبل الحفظ: الدفعة الأولى «اتدفعت» (الخصم نفسه) والتانية «الجاية». */
fun bnplPreview(amountMinor: Halalas, currency: Currency, firstDueAt: IsoDate, count: Int, cycle: BnplCycle): BnplPreview {
    val s = bnplSchedule(amountMinor, currency, firstDueAt, count, cycle)
    return BnplPreview(s.totalMinor, subtractMoney(s.totalMinor, amountMinor), currency, bnplSteps(s, amountMinor))
}

/**
 * الدفعات بحالتها من اللي اتدفع فعلًا ([paidMinor]): كاملة ⇒ «اتدفعت» · أول واحدة ناقصة ⇒ «الجاية» · الباقي بعدها. خطة اتدفع فيها
 * مبلغ مختلف عن القسط: الحالة من المجموع (اختيار التصميم (ب) — الباقي من اللي اتدفع فعلًا).
 */
fun bnplSteps(s: DueSchedule, paidMinor: Halalas): List<BnplStep> {
    val p = dueProgress(s, paidMinor, s.firstDueAt)
    return (1..p.count).map { n ->
        val state = when {
            n <= p.paidCount -> BnplStepState.PAID
            n == p.nextNumber -> BnplStepState.NEXT
            else -> BnplStepState.LATER
        }
        BnplStep(n, dueDateOf(s, n), installmentAmountOf(s, n), state)
    }
}
