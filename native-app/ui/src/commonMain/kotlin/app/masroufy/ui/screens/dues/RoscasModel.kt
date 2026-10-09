package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.PayoutState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaForecast
import app.masroufy.core.RoscaPhase
import app.masroufy.core.TextKey
import app.masroufy.core.absMoney
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.RoscaView

/**
 * «الجمعيات» و«تفاصيل الجمعية» من `ManageRoscas.list` (`RoscaStatus`: دفعت · قبضت · موقفك · المكسب · المرحلة) و`forecast` (الأدوار ومواعيدها
 * والتحليل). كل رقم جاي من حالة الاستخدام — الشاشة بتشيل الإشارة بس للعرض («عليك للجمعية 500» مش «−500»).
 * ⚠️ ناقص في حالات الاستخدام: العمليات المربوطة بالجمعية بتواريخها (وفك ربطها من هنا) · اقتراح ربط عملية تشبه القسط (§75-٨) · اسم اللي بيلم الفلوس.
 */
data class RoscaCardUi(
    val roscaId: String,
    val name: String,
    val terms: String,
    val stage: String,
    val stageChip: Chip,
    val progressText: String,
    val turnText: String,
    val paidCount: Int,
    val count: Int,
    val paidMinor: Halalas,
    /** null = ما قبضتش لسه («—»). */
    val gotMinor: Halalas?,
    /** null = دورك مش معروف ⇒ «غير متاح». */
    val gainMinor: Halalas?,
    val position: String,
    val positionPositive: Boolean,
    val currency: Currency,
)

internal fun turnsCount(n: Int) = countText(n, TextKey.ROSCA_TURNS_ONE, TextKey.ROSCA_TURNS_TWO, TextKey.ROSCA_TURNS_FEW, TextKey.ROSCA_TURNS_MANY)

internal fun roscaTerms(r: Rosca): String = t(TextKey.ROSCAS_TERMS, turnsCount(r.cycleCount), amountLabel(r.contributionMinor, r.currency), cycleText(r.every, r.unit))

internal fun turnText(r: Rosca): String = when (r.myTurns.size) {
    0 -> t(TextKey.ROSCA_TURN_UNKNOWN_LINE)
    1 -> t(TextKey.ROSCA_TURN_N, sentenceNumber(r.myTurns[0]))
    else -> t(TextKey.ROSCA_TURNS_N2, sentenceNumber(r.myTurns[0]), sentenceNumber(r.myTurns[1]))
}

internal fun phaseOf(p: RoscaPhase): Pair<String, Chip> = when (p) {
    RoscaPhase.SAVING -> t(TextKey.ROSCA_PHASE_SAVING) to Chip.UPCOMING
    RoscaPhase.REPAYING -> t(TextKey.ROSCA_PHASE_REPAYING) to Chip.SOON
    RoscaPhase.DONE -> t(TextKey.ROSCA_PHASE_DONE) to Chip.MUTED
}

/** «الجمعية تحفظ لك 500» · «عليك للجمعية 500» · «انتهت بلا ربح ولا خسارة» · «لا لك ولا عليك». */
internal fun positionText(v: RoscaView): String {
    val pos = v.status.positionMinor
    return when {
        pos > 0 -> t(TextKey.ROSCA_POS_SAVED, amountLabel(pos, v.rosca.currency))
        pos < 0 -> t(TextKey.ROSCA_POS_OWED, amountLabel(absMoney(pos), v.rosca.currency))
        v.status.phase == RoscaPhase.DONE -> t(TextKey.ROSCA_POS_DONE)
        else -> t(TextKey.ROSCA_POS_ZERO)
    }
}

fun roscaCard(v: RoscaView): RoscaCardUi {
    val r = v.rosca
    val s = v.status
    val (stage, chip) = phaseOf(s.phase)
    return RoscaCardUi(
        roscaId = r.id,
        name = r.name,
        terms = roscaTerms(r),
        stage = stage,
        stageChip = chip,
        progressText = t(TextKey.INST_PAID_OF, sentenceNumber(s.contributions.paidCount), sentenceNumber(s.contributions.count)),
        turnText = turnText(r),
        paidCount = s.contributions.paidCount,
        count = s.contributions.count,
        paidMinor = s.paidMinor,
        gotMinor = s.receivedMinor.takeIf { it > 0 },
        gainMinor = s.gainMinor,
        position = positionText(v),
        positionPositive = s.positionMinor > 0 && s.phase != RoscaPhase.DONE,
        currency = r.currency,
    )
}

/** صف دور في «الأدوار ومواعيدها». */
data class TurnRowUi(val number: Int, val num: String, val date: String, val status: String, val mine: Boolean, val paid: Boolean, val late: Boolean, val payMinor: Halalas, val after: String?)

/** لون مبلغ في «التحليل»: عادي · أخضر (أكبر مبلغ تحفظه لك) · أحمر (أكبر دين عليك). */
enum class StatTint { PLAIN, INCOME, EXPENSE }

/** قيمة في «التحليل»: كلام · مبلغ (بلونه) · «غير متاح». */
sealed interface StatValue {
    data class Text(val text: String) : StatValue
    data class Money(val minor: Halalas, val signed: Boolean = false, val tint: StatTint = StatTint.PLAIN) : StatValue
    data object NA : StatValue
}

data class StatUi(val label: String, val note: String, val value: StatValue)

data class RoscaDetailUi(
    val card: RoscaCardUi,
    val heroLabel: String,
    /** الموقف من غير إشارة (العنوان بيقول لك ولا عليك). */
    val heroMinor: Halalas,
    val rows: List<TurnRowUi>,
    /** الأدوار الظاهرة قبل «كل الأدوار»: ٤ حوالين الجاي + دورك. */
    val focusRows: List<TurnRowUi>,
    val stats: List<StatUi>,
    val note: String,
)

/** [organizer] = اسم اللي بيلم الفلوس لو متسجل (`Rosca.organizerPersonId` ⇒ شخص) — بيتضاف لسطر الشروط «تدفع إلى نورة». */
fun roscaDetailUi(v: RoscaView, f: RoscaForecast, today: IsoDate, organizer: String? = null): RoscaDetailUi {
    val r = v.rosca
    val s = v.status
    val known = r.myTurns.isNotEmpty()
    val received = s.payouts.filter { it.state == PayoutState.RECEIVED }.map { it.turn }.toSet()
    val payout = amountLabel(r.payoutMinor, r.currency, showCurrency = false)
    val rows = f.rows.map { row ->
        val mine = row.number in r.myTurns
        val paid = row.number <= s.contributions.paidCount
        val late = !paid && row.dueAt < today
        val status = when {
            mine -> t(if (row.number in received) TextKey.ROSCA_ROW_MINE_GOT else TextKey.ROSCA_ROW_MINE, payout)
            paid -> t(TextKey.ROSCA_ROW_PAID)
            late -> t(TextKey.DUES_CHIP_LATE)
            else -> t(TextKey.DUES_CHIP_NEXT)
        }
        val pos = row.positionAfterMinor
        val after = if (!known) null else when {
            pos > 0 -> t(TextKey.ROSCA_ROW_SAVED, amountLabel(pos, r.currency, showCurrency = false))
            pos < 0 -> t(TextKey.ROSCA_ROW_OWED, amountLabel(absMoney(pos), r.currency, showCurrency = false))
            else -> t(TextKey.ROSCA_ROW_EVEN)
        }
        TurnRowUi(row.number, sentenceNumber(row.number), dateText(row.dueAt, today), status, mine, paid, late, row.payMinor, after)
    }
    val next = s.contributions.paidCount + 1
    val from = maxOf(1, minOf(next - 1, r.cycleCount - 3))
    val focus = rows.filter { it.number in from until from + 4 || it.mine }
    val gain = f.gainMinor
    val stats = listOf(
        StatUi(
            t(TextKey.ROSCA_ST_PAYOUT_DATE), if (known) turnText(r) else t(TextKey.ROSCA_TURN_UNKNOWN_LINE),
            if (known) StatValue.Text(f.payoutDates.joinToString(t(TextKey.ROSCA_AND)) { dateText(it, today) }) else StatValue.NA,
        ),
        StatUi(
            t(TextKey.ROSCA_ST_GAIN),
            t(when { gain == null -> TextKey.ROSCA_ST_GAIN_NEEDS_TURN; gain == 0L -> TextKey.ROSCA_ST_GAIN_EVEN; gain > 0 -> TextKey.ROSCA_ST_GAIN_PROFIT; else -> TextKey.ROSCA_ST_GAIN_FEES }),
            when { gain == null -> StatValue.NA; gain == 0L -> StatValue.Text(t(TextKey.ROSCA_GAIN_NONE)); else -> StatValue.Money(gain, signed = true) },
        ),
        StatUi(t(TextKey.ROSCA_ST_TOTAL_PAY), t(TextKey.ROSCA_ST_UNTIL, dateText(f.lastDueAt, today)), StatValue.Money(f.totalPayMinor)),
        StatUi(t(TextKey.ROSCA_ST_TOTAL_GET), if (known) t(TextKey.ROSCA_ST_IN, turnsCount(r.myTurns.size)) else "", f.totalReceiveMinor?.let { StatValue.Money(it) } ?: StatValue.NA),
        StatUi(t(TextKey.ROSCA_ST_BEFORE), t(TextKey.ROSCA_ST_BEFORE_NOTE), f.paymentsBeforePayout?.let { StatValue.Text(installmentsCount(it)) } ?: StatValue.NA),
        StatUi(t(TextKey.ROSCA_ST_AFTER), t(TextKey.ROSCA_ST_AFTER_NOTE), f.paymentsAfterPayout?.let { StatValue.Text(installmentsCount(it)) } ?: StatValue.NA),
        StatUi(t(TextKey.ROSCA_ST_PEAK_SAVED), t(TextKey.ROSCA_ST_PEAK_SAVED_NOTE), if (known) StatValue.Money(f.peakSavedMinor, tint = StatTint.INCOME) else StatValue.NA),
        StatUi(t(TextKey.ROSCA_ST_PEAK_OWED), t(TextKey.ROSCA_ST_PEAK_OWED_NOTE), if (known) StatValue.Money(f.peakOwedMinor, tint = StatTint.EXPENSE) else StatValue.NA),
    )
    val pos = s.positionMinor
    return RoscaDetailUi(
        card = roscaCard(v).let { c -> if (organizer.isNullOrBlank()) c else c.copy(terms = t(TextKey.ROSCA_TERMS_TO, c.terms, organizer)) },
        heroLabel = t(
            when {
                s.phase == RoscaPhase.DONE && pos == 0L -> TextKey.ROSCA_POS_DONE
                pos >= 0 -> TextKey.ROSCA_HERO_SAVED
                else -> TextKey.ROSCA_HERO_OWED
            },
        ),
        heroMinor = absMoney(pos),
        rows = rows,
        focusRows = focus,
        stats = stats,
        note = t(if (known) TextKey.ROSCA_NOTE_KNOWN else TextKey.ROSCA_NOTE_UNKNOWN),
    )
}
