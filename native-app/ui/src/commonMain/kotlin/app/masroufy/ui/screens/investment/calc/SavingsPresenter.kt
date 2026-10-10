package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_CALC_MONTHS
import app.masroufy.core.SavingVerdict
import app.masroufy.core.TextKey
import app.masroufy.core.absMoney
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.usecase.ActualSaving
import app.masroufy.usecase.SavingsReachOutcome
import app.masroufy.usecase.SavingsTargetOutcome

/**
 * «حاسبة الادخار» (`SavingsCalculator`) — من نتيجة حالة الاستخدام لحالة الشاشة. **عرض بس**: كل مبلغ جاي من `SavingsCalculator`
 * (الاتجاهين + المقارنة باللي بتحوّشه فعلًا)، والشاشة بتقرا الخانات وبتختار الكلام.
 */
enum class SavingsMode { TARGET, MONTHLY }

object SavingsFields {
    const val TARGET = "target"
    const val DATE = "date"
    const val HAVE = "have"
    const val MONTHLY = "monthly"
    const val MONTHS = "months"
}

sealed interface SavingsRequest {
    data class Target(val targetMinor: Halalas, val haveMinor: Halalas, val date: IsoDate) : SavingsRequest
    data class Reach(val monthlyMinor: Halalas, val months: Int, val haveMinor: Halalas) : SavingsRequest
}

/** الخانات بعد القراية: أخطاء كل خانة، والطلب لو كله صح. */
data class SavingsCheck(val errors: Map<String, String>, val request: SavingsRequest?)

fun checkSavings(mode: SavingsMode, f: Map<String, String>, currency: Currency, today: IsoDate): SavingsCheck {
    val errors = mutableMapOf<String, String>()
    val have = when (val p = parseAmountField(f[SavingsFields.HAVE].orEmpty(), currency)) {
        Parsed.Empty -> 0L
        Parsed.Bad -> null.also { errors[SavingsFields.HAVE] = t(TextKey.MONEY_BAD_FORMAT) }
        is Parsed.Ok -> p.value.takeIf { it >= 0 } ?: null.also { errors[SavingsFields.HAVE] = t(TextKey.CALC_SAVED_NOT_NEGATIVE) }
    }
    if (mode == SavingsMode.TARGET) {
        val target = parseAmountField(f[SavingsFields.TARGET].orEmpty(), currency).orNull?.takeIf { it > 0 }
        if (target == null) errors[SavingsFields.TARGET] = t(TextKey.CALC_TARGET_POSITIVE)
        val date = parseDateField(f[SavingsFields.DATE].orEmpty()).orNull?.takeIf { it > today }
        if (date == null) errors[SavingsFields.DATE] = t(TextKey.CALC_DATE_AFTER_TODAY)
        val ok = target != null && date != null && have != null
        return SavingsCheck(errors, if (ok) SavingsRequest.Target(target!!, have!!, date!!) else null)
    }
    val monthly = parseAmountField(f[SavingsFields.MONTHLY].orEmpty(), currency).orNull?.takeIf { it > 0 }
    if (monthly == null) errors[SavingsFields.MONTHLY] = t(TextKey.CALC_MONTHLY_POSITIVE)
    val months = parseCountField(f[SavingsFields.MONTHS].orEmpty()).orNull?.takeIf { it in 1..MAX_CALC_MONTHS }
    if (months == null) errors[SavingsFields.MONTHS] = t(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString())
    val ok = monthly != null && months != null && have != null
    return SavingsCheck(errors, if (ok) SavingsRequest.Reach(monthly!!, months!!, have!!) else null)
}

/** نتيجة حالة الاستخدام بالاتجاهين. */
sealed interface SavingsOutcome {
    data class Target(val value: SavingsTargetOutcome) : SavingsOutcome
    data class Reach(val value: SavingsReachOutcome) : SavingsOutcome
}

enum class VerdictTone { UNKNOWN, ENOUGH, SHORT }

/** عمود شهر في المقارنة: [heightDp] من 84 (النموذج: أعلى عمود 48 فوقه الرقم وتحته الاسم) · [unknown] = الشهر غير معروف. */
data class SavingBar(val label: String, val valueText: String, val heightDp: Int, val unknown: Boolean)

data class SavingsResultUi(
    val heroLabel: String,
    val heroAmountMinor: Halalas,
    val heroSub: String,
    val verdict: String,
    val verdictTone: VerdictTone,
    val bars: List<SavingBar>,
    val compareLine: String,
    val compareNote: String,
    val compareUnknown: Boolean,
    /** «لو وضعتها في…»: المبلغ الشهري (0 ⇒ ما بتظهرش) والمدة واللي معاك. */
    val growthMonthlyMinor: Halalas,
    val growthMonths: Int,
    val growthStartMinor: Halalas,
    /** للرسالة بعد «حوّلها لخطة». */
    val perMonthMinor: Halalas,
    val endDate: IsoDate,
)

fun heroLabel(mode: SavingsMode): String = t(if (mode == SavingsMode.TARGET) TextKey.SAVCALC_HERO_TARGET else TextKey.SAVCALC_HERO_REACH)

fun savingsResultUi(outcome: SavingsOutcome, currency: Currency): SavingsResultUi {
    val (comparison, actual) = when (outcome) {
        is SavingsOutcome.Target -> outcome.value.comparison to outcome.value.actual
        is SavingsOutcome.Reach -> outcome.value.comparison to outcome.value.actual
    }
    val verdictTone = when (comparison.verdict) {
        SavingVerdict.UNKNOWN -> VerdictTone.UNKNOWN
        SavingVerdict.ENOUGH -> VerdictTone.ENOUGH
        SavingVerdict.SHORT -> VerdictTone.SHORT
    }
    val verdict = when (comparison.verdict) {
        SavingVerdict.UNKNOWN -> t(TextKey.SAVCALC_VERDICT_UNKNOWN)
        SavingVerdict.ENOUGH -> t(TextKey.SAVCALC_VERDICT_ENOUGH)
        SavingVerdict.SHORT -> t(TextKey.SAVCALC_VERDICT_SHORT, money(absMoney(comparison.gapMinor!!), currency))
    }
    val avg = comparison.actualMinor
    val compareLine = if (avg == null) t(TextKey.CALC_ACTUAL_UNKNOWN)
    else t(TextKey.SAVCALC_COMPARE_LINE, money(avg, currency), money(comparison.requiredMinor, currency)) +
        (comparison.gapMinor?.takeIf { it >= 0 }?.let { t(TextKey.SAVCALC_COMPARE_EXTRA, money(it, currency)) } ?: "")
    val common = Triple(verdict, verdictTone, compareLine)
    return when (outcome) {
        is SavingsOutcome.Target -> {
            val plan = outcome.value.plan
            val sub = if (plan.remainingMinor > 0) t(TextKey.SAVCALC_SUB_TARGET, monthsPhrase(plan.months), fullDate(plan.targetDate), money(plan.remainingMinor, currency))
            else t(TextKey.SAVCALC_SUB_HAVE_ALL)
            SavingsResultUi(
                heroLabel(SavingsMode.TARGET), plan.perMonthMinor, sub, common.first, common.second, bars(actual, currency), common.third,
                compareNote(actual), avg == null, plan.perMonthMinor, plan.months, plan.alreadySavedMinor, plan.perMonthMinor, plan.targetDate,
            )
        }
        is SavingsOutcome.Reach -> {
            val r = outcome.value.reach
            val sub = t(TextKey.SAVCALC_SUB_REACH, fullDate(r.endDate), monthsPhrase(r.months), money(r.monthlyMinor, currency))
            SavingsResultUi(
                heroLabel(SavingsMode.MONTHLY), r.reachedMinor, sub, common.first, common.second, bars(actual, currency), common.third,
                compareNote(actual), avg == null, r.monthlyMinor, r.months, r.alreadySavedMinor, r.monthlyMinor, r.endDate,
            )
        }
    }
}

/** الشهر المالي باسم الشهر اللي بيخلص فيه (الشهر اللي بيبدأ 28 يونيو هو «يوليو» — زي النموذج). */
private fun bars(actual: ActualSaving?, currency: Currency): List<SavingBar> {
    val months = actual?.months.orEmpty()
    val top = months.mapNotNull { it.savedMinor }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    return months.map { m ->
        val label = monthName(parseIsoDate(m.period.end).month)
        val v = m.savedMinor
        if (v == null) SavingBar(label, t(TextKey.SAVCALC_MONTH_UNKNOWN), 40, unknown = true)
        else SavingBar(label, plainAmount(v, currency), maxOf(6L, v * 48 / top).toInt(), unknown = false)
    }
}

/** يوم بداية الشهر المالي (يوم الراتب) من الفترات نفسها — أي ٣ شهور ورا بعض فيهم شهر ٣١ يوم، فالأكبر = يوم الراتب. */
private fun compareNote(actual: ActualSaving?): String {
    val payday = actual?.months?.maxOfOrNull { parseIsoDate(it.period.start).day }
    return if (payday == null) t(TextKey.SAVCALC_COMPARE_NOTE_PLAIN) else t(TextKey.SAVCALC_COMPARE_NOTE, sentenceNumber(payday))
}

/** الرسالة بعد «حوّلها لخطة ادخار». */
fun goalToast(goalName: String, ui: SavingsResultUi, currency: Currency): String =
    t(TextKey.SAVCALC_GOAL_TOAST, goalName, money(ui.perMonthMinor, currency), fullDate(ui.endDate))
