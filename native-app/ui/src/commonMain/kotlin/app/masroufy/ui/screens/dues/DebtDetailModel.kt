package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DueStatus
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.parseIsoDate
import app.masroufy.core.shiftMonths
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.PersonRow

/**
 * «تفاصيل الدين» (`DebtDetail`): الالتزام من `listWithBalances` (المتبقي جاهز من حالة الاستخدام) + ميعاده من `LoadDues`.
 * ⚠️ ناقص في حالات الاستخدام (ما بيظهرش هنا): المسدَّد لحد دلوقتي ونسبته · قايمة التسويات بتواريخها (`Settlement` من غير تاريخ) ·
 * عملية الأصل (اسمها وتاريخها) · قراية مواعيد الدين المحفوظة (`DebtTerms` — «فيها فوائد؟» بتتحفظ بس ما بتتقريش) · سؤال الحوالة المستنية.
 */
data class TermOption(val date: IsoDate, val label: String, val rel: String)

data class DebtDetailUi(
    val obligationId: String,
    val personId: String,
    val personName: String,
    val initial: String,
    val forYou: Boolean,
    val kind: ObligationKind,
    val kindLine: String,
    val remainLabel: String,
    val remainingMinor: Halalas,
    val originalMinor: Halalas,
    val currency: Currency,
    val ofOriginal: String,
    val signal: Chip?,
    val signalText: String?,
    val opening: Boolean,
    val originValue: String,
    val originHint: String?,
    val typeText: String,
    val typeHint: String,
    val dueAt: IsoDate?,
    val termsValue: String,
    val termOptions: List<TermOption>,
    val done: Boolean,
)

/** null = الالتزام مش في القايمة النشطة (اتسدد بالكامل — `listWithBalances` بيشيله — أو مش موجود). */
fun debtDetailUi(people: List<PersonRow>, dueItems: List<DueItem>, obligationId: String, today: IsoDate): DebtDetailUi? {
    val person = people.firstOrNull { p -> p.obligations.any { it.obligation.id == obligationId } } ?: return null
    val row = person.obligations.first { it.obligation.id == obligationId }
    val o = row.obligation
    val due = debtDueMap(dueItems)[o.id]
    val forYou = o.kind == ObligationKind.RECEIVABLE
    val opening = o.originTransactionId == null
    val (chip, text) = debtSignal(due, today)
    val typeKey = when {
        forYou -> TextKey.DEBT_TYPE_RECEIVABLE
        o.kind == ObligationKind.CUSTODY_PAYABLE -> TextKey.DEBT_TYPE_CUSTODY
        opening -> TextKey.DEBT_TYPE_OLD_ON_YOU
        else -> TextKey.DEBT_TYPE_LOAN
    }
    return DebtDetailUi(
        obligationId = o.id,
        personId = person.person.id,
        personName = person.person.name,
        initial = initialOf(person.person.name),
        forYou = forYou,
        kind = o.kind,
        kindLine = reasonOf(o),
        remainLabel = t(if (forYou) TextKey.DEBT_REMAIN_FOR else TextKey.DEBT_REMAIN_ON),
        remainingMinor = row.remainingMinor,
        originalMinor = o.originalMinor,
        currency = o.currency,
        // المتبقي = الأصل (مقارنة مش حساب) ⇒ «لم يُسدَّد شيء بعد»؛ غير كده «من أصل X» بس (المسدَّد مش في حالة الاستخدام)
        ofOriginal = t(
            if (row.remainingMinor == o.originalMinor) TextKey.DEBT_OF_ORIGINAL_NONE else TextKey.DEBT_OF_ORIGINAL,
            amountLabel(o.originalMinor, o.currency, showCurrency = false),
        ),
        signal = if (due == null) null else chip,
        signalText = text,
        opening = opening,
        originValue = t(if (opening) TextKey.DEBT_ORIGIN_BEFORE_APP else TextKey.DEBT_ORIGIN_TXN),
        originHint = if (opening) t(TextKey.DEBT_ORIGIN_OLD_HINT) else null,
        typeText = t(typeKey),
        typeHint = t(TextKey.DEBT_TYPE_HINT, t(if (forYou) TextKey.DUES_ON_YOU else TextKey.DUES_FOR_YOU)),
        dueAt = due?.dueAt,
        termsValue = due?.let { t(TextKey.DEBT_TERMS_ONE_PAYMENT, t(TextKey.DEBTS_DUE_ON, dateText(it.dueAt, today))) } ?: t(TextKey.DEBTS_NO_DUE),
        termOptions = termOptions(today),
        done = false,
    )
}

/** الدين اختفى من القايمة النشطة بعد تسوية بالكامل ⇒ نفس الصفحة بـ«سُدّد بالكامل» (المتبقي صفر — مش رقم محسوب: الالتزام مالوش متبقي). */
fun DebtDetailUi.settledFully(): DebtDetailUi = copy(remainingMinor = 0, signal = null, signalText = null, termOptions = emptyList(), done = true)

/** «دفعة واحدة بكامل المتبقي في»: أول الشهر الجاي والاتنين بعده (نفس اختيارات النموذج). */
internal fun termOptions(today: IsoDate): List<TermOption> {
    val p = parseIsoDate(today)
    val firstOfMonth = "${p.year}-${p.month.toString().padStart(2, '0')}-01"
    return (1..3).map { n ->
        val date = shiftMonths(firstOfMonth, n)
        TermOption(date, dayMonth(date), afterText(daysBetween(today, date)))
    }
}

/** فحص مبلغ التسوية قبل الإرسال — **مقارنة بس** بالمتبقي اللي جاي من حالة الاستخدام (الزيادة ممنوعة؛ تحويلها لأمانة مش متاح بعد). */
sealed interface SettleInput {
    data object Empty : SettleInput
    data object BadFormat : SettleInput
    data object NotPositive : SettleInput
    data object OverRemaining : SettleInput
    data class Ok(val minor: Halalas, val full: Boolean) : SettleInput
}

fun settleInput(text: String, remainingMinor: Halalas, currency: Currency): SettleInput {
    if (text.isBlank()) return SettleInput.Empty
    val minor = tryParseMoney(text, currency) ?: return SettleInput.BadFormat
    return when {
        minor <= 0 -> SettleInput.NotPositive
        minor > remainingMinor -> SettleInput.OverRemaining
        else -> SettleInput.Ok(minor, minor == remainingMinor)
    }
}

/** رسالة بعد الحفظ: «سُدّد الدين بالكامل» · «سُجّل التحصيل من فهد» · «سُجّل السداد لعمر» (المتبقي الجديد بيظهر في الصفحة من حالة الاستخدام). */
fun settledToast(ok: SettleInput.Ok, target: SettleTarget): String = when {
    ok.full -> t(TextKey.SETTLE_SAVED_FULL)
    target.forYou -> t(TextKey.SETTLE_SAVED_FOR, target.personName)
    else -> t(TextKey.SETTLE_SAVED_ON, target.personName)
}

fun SettleInput.errorText(remainingMinor: Halalas, currency: Currency): String? = when (this) {
    SettleInput.Empty, is SettleInput.Ok -> null
    SettleInput.BadFormat -> t(TextKey.DUES_ERR_FORMAT)
    SettleInput.NotPositive -> t(TextKey.DUES_ERR_POSITIVE)
    SettleInput.OverRemaining -> t(TextKey.SETTLE_ERR_OVER, amountLabel(remainingMinor, currency))
}

/** «دين قديم»: لازم الاتجاه والمبلغ. ⚠️ `addOpeningDebt` بيحفظ بالريال دايمًا ⇒ في بلد عملتها غير الريال الحفظ مقفول (عشان ما يتسجلش رقم بعملة غلط). */
sealed interface OpeningInput {
    data object NoSide : OpeningInput
    data object BadAmount : OpeningInput
    data object NotPositive : OpeningInput
    data object CurrencyNotReady : OpeningInput
    data class Ok(val kind: ObligationKind, val minor: Halalas) : OpeningInput
}

fun openingInput(kind: ObligationKind?, text: String, spaceCurrency: Currency): OpeningInput {
    if (spaceCurrency != Currency.SAR) return OpeningInput.CurrencyNotReady
    if (kind == null) return OpeningInput.NoSide
    val minor = tryParseMoney(text, spaceCurrency) ?: return OpeningInput.BadAmount
    return if (minor <= 0) OpeningInput.NotPositive else OpeningInput.Ok(kind, minor)
}

fun OpeningInput.errorText(): String? = when (this) {
    is OpeningInput.Ok -> null
    OpeningInput.NoSide -> t(TextKey.OPENING_ERR_SIDE)
    OpeningInput.BadAmount -> t(TextKey.DUES_ERR_FORMAT)
    OpeningInput.NotPositive -> t(TextKey.DUES_ERR_POSITIVE)
    OpeningInput.CurrencyNotReady -> t(TextKey.OPENING_ERR_CURRENCY)
}

/** «فات موعدها منذ …» على البطاقة البترولية (أو «بعد …»). */
internal fun DebtDetailUi.isLate(): Boolean = signal == Chip.OVERDUE

internal fun dueStatusChip(status: DueStatus): Chip = when (status) {
    DueStatus.OVERDUE -> Chip.OVERDUE
    DueStatus.SOON -> Chip.SOON
    DueStatus.UPCOMING -> Chip.UPCOMING
}
