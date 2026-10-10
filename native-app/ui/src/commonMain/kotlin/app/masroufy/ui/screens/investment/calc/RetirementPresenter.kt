package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.CalcReason
import app.masroufy.core.Currency
import app.masroufy.core.EosPart
import app.masroufy.core.EosShare
import app.masroufy.core.Halalas
import app.masroufy.core.PensionStatus
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.usecase.RetirementOutcome

/**
 * «حاسبة التقاعد» — المشترك بين السعودية ومصر: البطاقة البطلة وسطري «مكافأة نهاية الخدمة» و«كم تدّخر شهريًا». كل رقم من `RetirementCalculator`.
 * [eosChosen] = المستخدم اختار إزاي الشغل هيخلص (بيتسأل كل مرة — مفيش افتراض). [eosError] = بيانات المكافأة نفسها غلط (مثلًا بداية العمل بعد
 * يوم التقاعد) ⇒ المعاش اتحسب من غيرها والمكافأة «غير متاح» بالسبب.
 */
data class RetirementResult(val outcome: RetirementOutcome, val eosChosen: Boolean = true, val eosError: String? = null)

data class RetirementUi(
    val pensionMinor: Halalas?,
    val heroNa: String,
    val heroSub: String,
    /** سبب «لا معاش» أو «غير متاح» تحت السطر. */
    val heroReason: String?,
    val outs: List<OutRow>,
    /** السن النظامية بالشهور (للملاحظة تحت «السن الذي تنوي التقاعد فيه»). */
    val legalAgeMonths: Int?,
)

/** أسباب «غير متاح» (ناقص بيانات) — الباقي «لا يُحسب» (مش مستحق أو مفيش وقت). */
private val NA_REASONS = setOf(
    CalcReason.NEED_WAGE, CalcReason.NEED_BASIC, CalcReason.NEED_HOUSING, CalcReason.NEED_MONTHS_AT_2024, CalcReason.NEED_SETTLEMENT_WAGE,
    CalcReason.NEED_EGYPT_LEGAL_AGE, CalcReason.EGYPT_PRE_2020, CalcReason.EGYPT_AFTER_LEGAL_AGE, CalcReason.NEED_YEARS, CalcReason.NEED_PENSION,
    CalcReason.NEED_JOB_START,
)

/** «غير متاح: …» ⇒ «…» (الرقم نفسه مكتوب «غير متاح» جنبه). */
fun stripNa(text: String): String = text.removePrefix(t(TextKey.NOT_AVAILABLE) + ": ").removePrefix(t(TextKey.NOT_AVAILABLE) + ":")

/** البطاقة البطلة لما الخانات لسه مش كاملة. */
fun retirementIncomplete(message: String? = null): RetirementUi =
    RetirementUi(null, t(UiKey.RETCALC_FILL), message ?: t(UiKey.RETCALC_FILL_SUB), null, emptyList(), null)

/** الحسبة نفسها وقعت (قراية مصادر الدخل) ⇒ «غير متاح» بسببه — مش «أكمل البيانات» ولا صفر. */
fun retirementFailed(): RetirementUi = RetirementUi(null, t(TextKey.NOT_AVAILABLE), t(UiKey.SHELL_LOAD_FAILED), null, emptyList(), null)

/**
 * [heroDetails] سطر «النظام · التقاعد عند … في … ، N شهر اشتراك» (بيختلف بين البلدين). [withEos] = السعودية (مصر «لا تنطبق» بتيجي من حالة الاستخدام).
 */
fun retirementUi(result: RetirementResult, currency: Currency, heroDetails: (RetirementOutcome) -> String?, gapSubKey: TextRef): RetirementUi {
    val o = result.outcome
    val p = o.pension
    val heroNa = when (p.status) {
        PensionStatus.ESTIMATED -> ""
        PensionStatus.NOT_ELIGIBLE -> t(UiKey.RETCALC_NO_PENSION)
        PensionStatus.UNAVAILABLE -> t(TextKey.NOT_AVAILABLE)
    }
    val reason = p.reason?.let { stripNa(it.text) }
    val details = heroDetails(o)
    return RetirementUi(
        pensionMinor = p.pensionMinor,
        heroNa = heroNa,
        heroSub = details ?: reason.orEmpty(),
        heroReason = if (details != null) reason else null,
        outs = listOf(eosRow(result, currency), gapRow(result, currency, gapSubKey)),
        legalAgeMonths = p.legalAgeMonths,
    )
}

private fun eosRow(r: RetirementResult, currency: Currency): OutRow {
    val label = t(UiKey.RETCALC_EOS)
    val na = t(TextKey.NOT_AVAILABLE)
    if (r.eosError != null) return OutRow(label, null, na, r.eosError, warn = true)
    return when (val e = r.outcome.endOfService) {
        is EosPart.NotApplicable -> OutRow(label, null, t(UiKey.RETCALC_NOT_APPLICABLE), stripNa(uiText(e.key)).substringAfter(": "), warn = true)
        is EosPart.Unavailable -> OutRow(label, null, na, stripNa(e.reason.text), warn = true)
        is EosPart.Known -> if (!r.eosChosen) OutRow(label, null, na, t(UiKey.RETCALC_EOS_PICK_END), warn = true)
        else {
            val s = e.result.service
            OutRow(label, e.result.payableMinor, null, t(UiKey.RETCALC_EOS_SUB, sentenceNumber(s.fullYears), sentenceNumber(s.extraDays), shareLabel(e.result.share)), warn = false)
        }
    }
}

private fun gapRow(r: RetirementResult, currency: Currency, subKey: TextRef): OutRow {
    val label = t(UiKey.RETCALC_GAP)
    val na = t(TextKey.NOT_AVAILABLE)
    if (r.eosError != null) return OutRow(label, null, na, r.eosError, warn = true)
    if (!r.eosChosen && r.outcome.endOfService is EosPart.Known) return OutRow(label, null, na, t(UiKey.RETCALC_EOS_PICK_END), warn = true)
    val g = r.outcome.gap
    if (g == null) {
        // المستخدم ما كتبش المبلغ اللي عايز يعيش بيه — لو المعاش نفسه مش معروف، سببه هو الأهم
        val pensionReason = r.outcome.pension.reason
        return if (r.outcome.pension.pensionMinor == null && pensionReason != null) OutRow(label, null, valueFor(pensionReason), stripNa(pensionReason.text), warn = true)
        else OutRow(label, null, na, t(UiKey.RETCALC_GAP_NEED_WANT), warn = true)
    }
    val gap = g.gap ?: return OutRow(label, null, valueFor(g.reason!!), stripNa(g.reason!!.text), warn = true)
    // السعودية: «… − المكافأة − ما معك …» · مصر: «… − اللي معاك …» (مفيش مكافأة) — نفس المتغيرات
    val sub = t(subKey, money(gap.shortfallPerMonthMinor, currency), sentenceNumber(gap.lastingMonths), money(gap.totalNeedMinor, currency), money(gap.remainingMinor, currency), sentenceNumber(gap.monthsToSave))
    return OutRow(label, gap.perMonthMinor, null, sub, warn = false)
}

private fun valueFor(reason: CalcReason): String = if (reason in NA_REASONS) t(TextKey.NOT_AVAILABLE) else t(UiKey.RETCALC_NOT_COUNTED)

private fun shareLabel(share: EosShare): String = t(
    when (share) {
        EosShare.NONE -> UiKey.RETCALC_EOS_NONE
        EosShare.THIRD -> UiKey.RETCALC_EOS_THIRD
        EosShare.TWO_THIRDS -> UiKey.RETCALC_EOS_TWO_THIRDS
        EosShare.FULL -> UiKey.RETCALC_EOS_FULL
    },
)

/** « ← طُبّق». */
fun applied(yes: Boolean): String = if (yes) t(UiKey.RETCALC_HOW_APPLIED) else ""
