package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.RecurringCandidate
import app.masroufy.core.RecurringItem
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.sentenceNumber
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.RecurringItemView
import app.masroufy.usecase.RecurringSaveInput
import app.masroufy.usecase.RecurringView

/**
 * «الاشتراكات والفواتير» و«تفاصيل الاشتراك» من `ManageRecurring.load` (المرشحين + المؤكدين بالمدفوع آخر ١٢ شهر والمتوقع سنويًا ومتأخر).
 * ⚠️ ناقص في حالة الاستخدام: مجموع «شهريًا» و«سنويًا تقريبًا» فوق القايمة ⇒ ما بيظهرش · قايمة الدفعات وآخر دفعة · «وصل خصم مطابق» وتحريك
 * الموعد لوحده (§75-٧) · حفظ «لا، مش اشتراك» للمرشح (بيتشال لحد ما الشاشة تتفتح تاني بس).
 */
data class SubRowUi(val itemId: String, val name: String, val sub: String, val chip: Chip, val chipText: String, val amountMinor: Halalas, val currency: Currency, val cycle: String)

data class CandidateUi(val key: String, val name: String, val why: String, val amountMinor: Halalas, val currency: Currency, val source: RecurringCandidate)

data class SubscriptionsUi(val candidate: CandidateUi?, val rows: List<SubRowUi>, val empty: Boolean)

internal fun subCycle(months: Int): String = when (months) {
    1 -> t(UiKey.SUBS_CYCLE_MONTHLY)
    3 -> t(UiKey.SUBS_CYCLE_QUARTERLY)
    12 -> t(UiKey.SUBS_CYCLE_YEARLY)
    else -> cycleText(months)
}

internal fun subKind(item: RecurringItem): String = t(if (item.kind == "bill") UiKey.SUBS_KIND_BILL else UiKey.SUBS_KIND_SUB)

/** حالة الاشتراك: متوقف · متأخرة (من حالة الاستخدام) · قريبة (٣ أيام) · قادمة. */
internal fun subStatus(v: RecurringItemView, today: IsoDate): Pair<Chip, String> = when {
    !v.item.active -> Chip.MUTED to t(UiKey.SUBS_CHIP_STOPPED)
    v.overdue -> Chip.OVERDUE to t(UiKey.SUBS_CHIP_LATE)
    daysBetween(today, v.item.nextDueAt) <= 3 -> Chip.SOON to t(UiKey.SUBS_CHIP_SOON)
    else -> Chip.UPCOMING to t(UiKey.SUBS_CHIP_NEXT)
}

fun candidateKey(c: RecurringCandidate) = c.merchantKey + "|" + c.currency.name

fun subscriptionsUi(view: RecurringView, today: IsoDate, dismissed: Set<String>): SubscriptionsUi {
    val rows = view.items.sortedBy { it.item.nextDueAt }.map { v ->
        val (chip, text) = subStatus(v, today)
        SubRowUi(v.item.id, v.item.name, t(UiKey.DUES_COMMA_JOIN, subKind(v.item), dateText(v.item.nextDueAt, today)), chip, text, v.item.expectedMinor, v.item.currency, subCycle(v.item.cycleMonths))
    }
    val c = view.candidates.firstOrNull { candidateKey(it) !in dismissed }
    return SubscriptionsUi(
        candidate = c?.let { CandidateUi(candidateKey(it), it.name, it.reason, it.expectedMinor, it.currency, it) },
        rows = rows,
        empty = rows.isEmpty() && c == null,
    )
}

/** «نعم، اشتراك» ⇒ خطة متابعة بس (الاقتراح ما بيكتبش عملية). */
fun RecurringCandidate.confirmInput() = RecurringSaveInput(
    name = name, merchantKey = merchantKey, kind = "subscription", cycleMonths = cycleMonths, expectedMinor = expectedMinor,
    currency = currency, nextDueAt = nextDueAt, active = true,
)

data class SubDetailUi(
    val view: RecurringItemView,
    val kindLine: String,
    val chip: Chip,
    val chipText: String,
    val nextLabel: String,
    val nextText: String,
    val nextSub: String,
    val paidCount: String,
    val annualNote: String,
    val cycle: String,
)

fun subDetailUi(v: RecurringItemView, today: IsoDate): SubDetailUi {
    val item = v.item
    val (chip, text) = subStatus(v, today)
    val gap = daysBetween(today, item.nextDueAt)
    val rel = if (gap < 0) t(UiKey.SUBS_LATE_BY, agoText(-gap)) else afterText(gap)
    return SubDetailUi(
        view = v,
        kindLine = t(UiKey.DUES_COMMA_JOIN, subKind(item), subCycle(item.cycleMonths)),
        chip = chip,
        chipText = text,
        nextLabel = t(if (item.active) UiKey.SUBS_F_NEXT else UiKey.SUBS_STOPPED_LABEL),
        nextText = if (item.active) dateText(item.nextDueAt, today) else t(UiKey.DUES_DASH),
        nextSub = if (item.active) t(UiKey.SUBS_NEXT_SUB, rel, amountLabel(item.expectedMinor, item.currency)) else t(UiKey.SUBS_STOPPED_SUB),
        paidCount = countText(v.paidCount, UiKey.SUBS_PAYMENTS_ONE, UiKey.SUBS_PAYMENTS_TWO, UiKey.SUBS_PAYMENTS_FEW, UiKey.SUBS_PAYMENTS_MANY, zero = UiKey.SUBS_PAYMENTS_NONE),
        annualNote = if (item.active) t(UiKey.SUBS_ANNUAL_NOTE, amountLabel(item.expectedMinor, item.currency, showCurrency = false), sentenceNumber(12 / item.cycleMonths)) else t(UiKey.SUBS_OUT_OF_CALC),
        cycle = subCycle(item.cycleMonths),
    )
}

/** الدورات المسموحة (`validateRecurring`): كل شهر · كل ٣ أشهر · كل سنة. */
val SUB_CYCLES = listOf(1, 3, 12)

/** تعديل المبلغ والدورة والموعد (نص المبلغ بيتقري بالهللة — قراية مش حساب). null + رسالة لو المبلغ مش صالح. */
fun subEditInput(item: RecurringItem, amount: String, cycleMonths: Int, nextDueAt: IsoDate, active: Boolean = item.active): Pair<RecurringSaveInput?, String?> {
    val minor = tryParseMoney(amount, item.currency)
    if (minor == null) return null to t(UiKey.DUES_ERR_FORMAT)
    if (minor <= 0) return null to t(UiKey.DUES_ERR_POSITIVE)
    return RecurringSaveInput(item.id, item.name, item.merchantKey, item.kind, cycleMonths, minor, item.currency, nextDueAt, active) to null
}

/** إيقاف أو استئناف المتابعة = نفس الخطة بـ`active` بس. */
fun RecurringItem.withActive(active: Boolean) = RecurringSaveInput(id, name, merchantKey, kind, cycleMonths, expectedMinor, currency, nextDueAt, active)
