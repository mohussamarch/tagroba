package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.DuesTotals
import app.masroufy.core.Halalas
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dueDateOf
import app.masroufy.core.installmentSchedule
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.InstallmentView

/**
 * «الأقساط» و«تفاصيل الخطة» من `ManageInstallments.list` (الخطة + `DueProgress`: المتبقي والمدفوع بالعدّ والقسط الجاي + المبلغ المستلم).
 * «المتبقي عليك في الأقساط» = `DuesTotals.installmentsLeftMinor` (من `LoadDues`). المواعيد في الجدول = `dueDateOf` (تواريخ بس، من غير فلوس).
 * ⚠️ ناقص في حالات الاستخدام: أرباح التمويل للخطة (الإجمالي − الأصل · المدفوع منها · الباقي · في كل قسط) ⇒ «غير متاح» ·
 * مجموع الأقساط الشهري ⇒ ما بيظهرش · الأقساط المربوطة بتواريخها ⇒ ما بتظهرش · اقتراح ربط عملية تشبه القسط (§75-8).
 */
data class PlanCardUi(
    val planId: String,
    val name: String,
    val kindText: String,
    val installmentMinor: Halalas,
    val currency: Currency,
    val cycle: String,
    val progressText: String,
    val nextText: String,
    val late: Boolean,
    val paidCount: Int,
    val count: Int,
    val financing: Boolean,
    /** null = لسه ما اتربطش (مش صفر). */
    val receivedMinor: Halalas?,
)

data class InstallmentsUi(val leftMinor: Halalas, val currency: Currency, val plansCount: String, val cards: List<PlanCardUi>)

internal fun kindText(p: InstallmentPlan): String {
    val kind = t(if (p.kind == InstallmentKind.FINANCING) UiKey.INST_KIND_FINANCING else UiKey.INST_KIND_PURCHASE)
    val withProfit = if (p.hasInterest == true) t(UiKey.DUES_COMMA_JOIN, kind, t(UiKey.INST_WITH_PROFIT)) else kind
    return if (p.provider.isBlank()) withProfit else t(UiKey.DUES_COMMA_JOIN, withProfit, p.provider)
}

/** «القادم 27 أكتوبر» · «متأخر منذ 27 سبتمبر» · «اكتملت». */
internal fun nextLine(v: InstallmentView, today: IsoDate, withNumber: Boolean): Pair<String, Boolean> {
    val next = v.progress.nextDueAt ?: return t(UiKey.INST_DONE) to false
    if (next < today) return t(UiKey.INST_LATE_SINCE, dateText(next, today)) to true
    val n = v.progress.nextNumber
    return (if (withNumber && n != null) t(UiKey.INST_NEXT_N, dateText(next, today), sentenceNumber(n)) else t(UiKey.INST_NEXT, dateText(next, today))) to false
}

fun installmentsUi(views: List<InstallmentView>, totals: DuesTotals, today: IsoDate, currency: Currency): InstallmentsUi = InstallmentsUi(
    leftMinor = totals.installmentsLeftMinor,
    currency = currency,
    plansCount = countText(views.size, UiKey.INST_PLANS_ONE, UiKey.INST_PLANS_TWO, UiKey.INST_PLANS_FEW, UiKey.INST_PLANS_MANY),
    cards = views.map { planCard(it, today) },
)

internal fun planCard(v: InstallmentView, today: IsoDate): PlanCardUi {
    val p = v.plan
    val (next, late) = nextLine(v, today, withNumber = false)
    return PlanCardUi(
        planId = p.id,
        name = p.name,
        kindText = kindText(p),
        installmentMinor = p.installmentMinor,
        currency = p.currency,
        cycle = cycleText(p.cycleMonths),
        progressText = t(UiKey.INST_PAID_OF, sentenceNumber(v.progress.paidCount), sentenceNumber(v.progress.count)),
        nextText = next,
        late = late,
        paidCount = v.progress.paidCount,
        count = v.progress.count,
        financing = p.kind == InstallmentKind.FINANCING,
        receivedMinor = v.receivedMinor,
    )
}

/** خانة في جدول الأقساط: اتدفع · متأخر · الجاي · باقي. */
enum class Cell { PAID, LATE, NEXT, UP }

data class YearRowUi(val label: String, val cells: List<Cell>)

data class PlanDetailUi(
    val card: PlanCardUi,
    val done: Boolean,
    val leftLabel: String,
    val leftMinor: Halalas,
    val instLine: String,
    val nextText: String,
    val years: List<YearRowUi>,
    val schedAria: String,
    val lastLine: String,
    val totalMinor: Halalas,
    val principalMinor: Halalas,
    /** عملية «مبلغ تمويل مستلم» المربوطة (لفك الربط). */
    val receivedTransactionId: String?,
    val expenseNote: String,
)

fun planDetailUi(v: InstallmentView, today: IsoDate): PlanDetailUi {
    val p = v.plan
    val card = planCard(v, today)
    val schedule = installmentSchedule(p)
    val count = v.progress.count
    val cells = (1..count).map { n ->
        when {
            n <= v.progress.paidCount -> Cell.PAID
            dueDateOf(schedule, n) < today -> Cell.LATE
            n == v.progress.nextNumber -> Cell.NEXT
            else -> Cell.UP
        }
    }
    val years = cells.chunked(12).mapIndexed { i, row ->
        val from = monthYearText(dueDateOf(schedule, i * 12 + 1))
        val to = monthYearText(dueDateOf(schedule, i * 12 + row.size))
        YearRowUi(if (count > 12) t(UiKey.INST_YEAR_LABEL, sentenceNumber(i + 1), from, to) else t(UiKey.INST_RANGE, from, to), row)
    }
    val late = cells.count { it == Cell.LATE }
    val last = dueDateOf(schedule, count)
    val (next, _) = nextLine(v, today, withNumber = true)
    val done = v.progress.done
    val financing = p.kind == InstallmentKind.FINANCING
    val inst = amountLabel(p.installmentMinor, p.currency, showCurrency = false)
    return PlanDetailUi(
        card = card,
        done = done,
        leftLabel = t(if (done) UiKey.INST_DONE_HERO else UiKey.INST_LEFT),
        leftMinor = v.progress.remainingMinor,
        instLine = t(UiKey.INST_LINE, amountLabel(p.installmentMinor, p.currency), cycleText(p.cycleMonths), amountLabel(p.totalMinor, p.currency)),
        nextText = next,
        years = years,
        schedAria = if (late > 0) t(UiKey.INST_SCHED_ARIA, sentenceNumber(v.progress.paidCount), sentenceNumber(count), sentenceNumber(late))
        else t(UiKey.INST_SCHED_ARIA_NOLATE, sentenceNumber(v.progress.paidCount), sentenceNumber(count)),
        lastLine = t(UiKey.INST_LAST_LINE, dateText(last, today), cycleText(p.cycleMonths), sentenceNumber(parseIsoDate(p.firstDueAt).day)),
        totalMinor = p.totalMinor,
        principalMinor = p.principalMinor,
        receivedTransactionId = p.receivedTransactionId,
        expenseNote = if (financing) t(UiKey.INST_NOTE_FIN, inst) else t(UiKey.INST_NOTE_BUY),
    )
}
