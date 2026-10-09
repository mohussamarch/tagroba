package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.ROSCA_MAX_CYCLES
import app.masroufy.core.RoscaAnswer
import app.masroufy.core.RoscaDraft
import app.masroufy.core.RoscaFrequency
import app.masroufy.core.RoscaQuestion
import app.masroufy.core.RoscaShare
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.core.shiftCycles
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.RoscaSetupState

/**
 * «جمعية جديدة» سؤال سؤال (`RoscaWizard`) فوق `RoscaSetup` (المسودة + السؤال الجاي + التحليل قبل الحفظ). الشاشة ماسكة نصوص الخانات بس،
 * والإجابة بتتفحص في `RoscaSetup.answer` (الخطأ بيرجع برسالته جنب السؤال، والرجوع وتغيير إجابة بيمسح المتعارض بس).
 * قيمة الدور: الخانة فاضية ⇒ «موافق على المحسوب» (`RoscaAnswer.Payout(null)`) — الشاشة ما بتحسبهاش.
 * ⚠️ ناقص: «قسطك X» تحت كل اختيار سهم (محتاج `roscaContributionOf` في حالة استخدام) ⇒ الاختيارات من غير السطر ده.
 */
data class WizardInputs(
    val name: String = "",
    val count: Int = 10,
    val amount: String = "",
    val frequency: RoscaFrequency = RoscaFrequency.MONTHLY,
    val first: IsoDate? = null,
    val share: RoscaShare = RoscaShare.ONE,
    val turns: List<Int> = emptyList(),
    val unknownTurn: Boolean = false,
    val payout: String = "",
)

/** ترتيب الأسئلة (سؤال قيمة الدور بيتشال لو دورك «غير معروف بعد»). */
fun wizardOrder(d: RoscaDraft): List<RoscaQuestion> =
    if (d.myTurns?.isEmpty() == true) RoscaQuestion.entries.filter { it != RoscaQuestion.PAYOUT } else RoscaQuestion.entries

/** السؤال اللي قبل [current] (null = التحليل ⇒ آخر سؤال). */
fun previousQuestion(d: RoscaDraft, current: RoscaQuestion?): RoscaQuestion? {
    val order = wizardOrder(d)
    if (current == null) return order.last()
    return order.getOrNull(order.indexOf(current) - 1)
}

/** «السؤال ٣ من ٨» — [current] null ⇒ التحليل. */
fun wizardStep(state: RoscaSetupState, current: RoscaQuestion?): Pair<Int, Int> {
    val order = wizardOrder(state.draft)
    return if (current == null) order.size to order.size else (order.indexOf(current) + 1) to order.size
}

/** نصوص الخانات من المسودة (لما المستخدم يرجع لسؤال اتجاوب). */
fun WizardInputs.from(d: RoscaDraft, plain: (Halalas) -> String): WizardInputs = copy(
    name = d.name ?: name,
    count = d.cycleCount ?: count,
    amount = d.shareAmountMinor?.let(plain) ?: amount,
    frequency = d.frequency ?: frequency,
    first = d.firstDueAt ?: first,
    share = d.share ?: share,
    turns = d.myTurns ?: turns,
    unknownTurn = d.myTurns?.isEmpty() ?: unknownTurn,
    payout = d.payoutMinor?.takeIf { it > 0 }?.let(plain) ?: payout,
)

/** الإجابة من الخانة — `null` + رسالة لو المبلغ مش مقروء (الفحص الباقي في `RoscaSetup.answer`). */
fun answerOf(q: RoscaQuestion, i: WizardInputs, currency: Currency): Pair<RoscaAnswer?, String?> = when (q) {
    RoscaQuestion.NAME -> RoscaAnswer.Name(i.name) to null
    RoscaQuestion.TURNS_COUNT -> RoscaAnswer.TurnsCount(i.count) to null
    RoscaQuestion.SHARE_AMOUNT -> tryParseMoney(i.amount, currency)?.let { RoscaAnswer.ShareAmount(it) to null } ?: (null to t(TextKey.DUE_INSTALLMENT_POSITIVE))
    RoscaQuestion.FREQUENCY -> RoscaAnswer.Frequency(i.frequency) to null
    RoscaQuestion.FIRST_DATE -> i.first?.let { RoscaAnswer.FirstDate(it) to null } ?: (null to t(TextKey.RW_ERR_FIRST))
    RoscaQuestion.SHARE -> RoscaAnswer.Share(i.share) to null
    RoscaQuestion.MY_TURN -> when {
        i.unknownTurn -> RoscaAnswer.MyTurns(emptyList()) to null
        i.turns.isEmpty() -> null to t(TextKey.RW_ERR_TURN)
        else -> RoscaAnswer.MyTurns(i.turns) to null
    }
    RoscaQuestion.PAYOUT -> if (i.payout.isBlank()) RoscaAnswer.Payout(null) to null
    else tryParseMoney(i.payout, currency)?.let { RoscaAnswer.Payout(it) to null } ?: (null to t(TextKey.ROSCA_PAYOUT_POSITIVE))
}

/** عدد الأدوار في سؤال «كم دورًا؟» (من ٢ لـ٦٠). */
fun stepCount(count: Int, delta: Int): Int = (count + delta).coerceIn(2, ROSCA_MAX_CYCLES)

/** اختيار دور: لو وصلت للعدد المطلوب، الأقدم بيتشال (نفس النموذج). */
fun toggleTurn(turns: List<Int>, turn: Int, needed: Int): List<Int> =
    if (turn in turns) turns - turn else (if (turns.size >= needed) turns.drop(1) else turns) + turn

/** «أكتوبر» تحت رقم الدور (أو «٧ أكتوبر» لو بالأسبوع) — تاريخ قبض الدور من أول دفعة والدورية. */
fun turnWhen(d: RoscaDraft, turn: Int, today: IsoDate): String? {
    val first = d.firstDueAt ?: return null
    val f = d.frequency ?: return null
    return dateText(shiftCycles(first, f.unit, (turn - 1) * f.every), today)
}

data class WizardFact(val label: String, val value: StatValue)
data class WizardAnswer(val question: RoscaQuestion, val label: String, val value: String)
data class WizardSummaryUi(val heroLabel: String, val heroValue: String?, val heroSub: String, val facts: List<WizardFact>, val answers: List<WizardAnswer>)

fun wizardSummary(state: RoscaSetupState, today: IsoDate): WizardSummaryUi? {
    val f = state.preview ?: return null
    val d = state.draft
    val known = d.myTurns?.isNotEmpty() == true
    val cur = d.currency
    val payout = d.payoutMinor ?: 0
    val facts = listOf(
        WizardFact(t(TextKey.ROSCA_ST_TOTAL_PAY), StatValue.Money(f.totalPayMinor)),
        WizardFact(t(TextKey.ROSCA_ST_TOTAL_GET), f.totalReceiveMinor?.let { StatValue.Money(it) } ?: StatValue.NA),
        WizardFact(t(TextKey.ROSCA_ST_GAIN), f.gainMinor?.let { if (it == 0L) StatValue.Text(t(TextKey.ROSCA_GAIN_NONE)) else StatValue.Money(it, signed = true) } ?: StatValue.NA),
        WizardFact(t(TextKey.ROSCA_ST_BEFORE), f.paymentsBeforePayout?.let { StatValue.Text(installmentsCount(it)) } ?: StatValue.NA),
        WizardFact(t(TextKey.ROSCA_ST_AFTER), f.paymentsAfterPayout?.let { StatValue.Text(installmentsCount(it)) } ?: StatValue.NA),
        WizardFact(t(TextKey.ROSCA_ST_PEAK_SAVED), if (known) StatValue.Money(f.peakSavedMinor, tint = StatTint.INCOME) else StatValue.NA),
        WizardFact(t(TextKey.ROSCA_ST_PEAK_OWED), if (known) StatValue.Money(f.peakOwedMinor, tint = StatTint.EXPENSE) else StatValue.NA),
        WizardFact(t(TextKey.RW_LAST), StatValue.Text(dateText(f.lastDueAt, today))),
    )
    val answers = buildList {
        add(WizardAnswer(RoscaQuestion.NAME, t(TextKey.RW_A_NAME), d.name.orEmpty()))
        add(WizardAnswer(RoscaQuestion.TURNS_COUNT, t(TextKey.RW_A_COUNT), sentenceNumber(d.cycleCount ?: 0)))
        add(WizardAnswer(RoscaQuestion.SHARE_AMOUNT, t(TextKey.RW_A_AMOUNT), amountLabel(d.shareAmountMinor ?: 0, cur)))
        add(WizardAnswer(RoscaQuestion.FREQUENCY, t(TextKey.RW_A_FREQ), d.frequency?.let { t(it.labelKey) }.orEmpty()))
        add(WizardAnswer(RoscaQuestion.FIRST_DATE, t(TextKey.RW_A_FIRST), d.firstDueAt?.let { dateText(it, today) }.orEmpty()))
        add(WizardAnswer(RoscaQuestion.SHARE, t(TextKey.RW_A_SHARE), d.share?.let { t(it.labelKey) }.orEmpty()))
        val turns = d.myTurns.orEmpty()
        add(WizardAnswer(RoscaQuestion.MY_TURN, t(TextKey.RW_A_TURN), if (turns.isEmpty()) t(TextKey.ROSCA_TURN_UNKNOWN_CHOICE) else turns.joinToString(t(TextKey.ROSCA_AND)) { sentenceNumber(it) }))
        if (known) add(WizardAnswer(RoscaQuestion.PAYOUT, t(TextKey.RW_A_PAYOUT), amountLabel(payout, cur)))
    }
    return WizardSummaryUi(
        heroLabel = t(if (known) TextKey.RW_HERO_GET else TextKey.RW_HERO_WHEN),
        heroValue = if (known) f.payoutDates.joinToString(t(TextKey.ROSCA_AND)) { dateText(it, today) } else null,
        heroSub = if (!known) t(TextKey.RW_HERO_UNKNOWN_SUB) else if (d.myTurns.orEmpty().size > 1) t(TextKey.RW_HERO_EACH, amountLabel(payout, cur)) else amountLabel(payout, cur),
        facts = facts,
        answers = answers,
    )
}
