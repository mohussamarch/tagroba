package app.masroufy.usecase

import app.masroufy.core.CalcReason
import app.masroufy.core.Currency
import app.masroufy.core.EosEnd
import app.masroufy.core.EosPart
import app.masroufy.core.GapOutcome
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PensionEstimate
import app.masroufy.core.SaudiPensionInput
import app.masroufy.core.TextKey
import app.masroufy.core.countryPack
import app.masroufy.core.egyptPensionEstimate
import app.masroufy.core.retirementGap
import app.masroufy.core.saudiEndOfService
import app.masroufy.core.saudiPensionEstimate
import app.masroufy.port.IncomeSourceRepository

/**
 * «حاسبة التقاعد» (قرار المالك §69) — حالة استخدام من غير شاشة. بتجاوب الاتنين: **المعاش المتوقع** و**كام تحوّش في الشهر**.
 * - **الراتب تلقائي من مصادر الدخل ويتعدّل** ([defaults]): الوظيفة الشغالة الوحيدة بعملة البلد اللي ليها مبلغ متوقع. أكتر من وظيفة شغالة
 *   ⇒ مفيش اختيار من عندنا («غير متاح» لحد ما يكتبه). نفس الوظيفة بتدّي **تاريخ بداية الشغل** لمكافأة نهاية الخدمة.
 * - **مكافأة نهاية الخدمة داخلة** (نظام العمل م84–87 — `EndOfService.kt`) لحد يوم التقاعد بنفس الأجر.
 * - **مصر:** المعاش «غير متاح» بالسبب (الجداول الرسمية ما اتقرتش)، والمكافأة «لا تنطبق».
 * - الفلوس المتحوشة **من غير أرباح** (اختيار المالك).
 */
data class RetirementDefaults(
    /** المرتب المتوقع من الوظيفة الشغالة الوحيدة — null = مش معروف (مفيش · أكتر من واحدة · من غير مبلغ). */
    val salaryMinor: Halalas?,
    val sourceId: Id?,
    /** بداية الوظيفة الشغالة الوحيدة (لمكافأة نهاية الخدمة). */
    val jobStartedAt: IsoDate?,
    /** عدد الوظايف الشغالة بعملة البلد — 0 = مالكش شغل حالي في مصادر الدخل. */
    val activeJobs: Int,
)

data class RetirementRequest(
    /** بلد الحساب الشغال ("SA" · "EG"). */
    val countryCode: String,
    /** السعودية: مدخلات المعاش. [SaudiPensionInput.averageWageMinor] null ⇒ المرتب من مصادر الدخل. */
    val saudi: SaudiPensionInput? = null,
    /** الأجر الفعلي الأخير لمكافأة نهاية الخدمة (م2: الأساسي + البدلات). null ⇒ المرتب من مصادر الدخل. */
    val eosWageMinor: Halalas? = null,
    /** بداية الشغل الحالي. null ⇒ من مصادر الدخل. */
    val jobStartedAt: IsoDate? = null,
    /** المستخدم قال صراحة «مالييش شغل حالي» ⇒ المكافأة «لا تنطبق» (من غير كده الناقص = «غير متاح»). */
    val noCurrentJob: Boolean = false,
    /** إزاي الشغل هيخلص يوم التقاعد — إجباري (مفيش افتراض): السن النظامية عادة = انتهاء العقد (كاملة)، والاستقالة بنسبها. */
    val eosEnd: EosEnd,
    /** م87: قوة قاهرة، أو العاملة خلال 6 شهور من الزواج أو 3 من الولادة ⇒ كاملة حتى مع الاستقالة. */
    val art87: Boolean = false,
    /** المبلغ اللي عايز تعيش بيه في الشهر — null = عايز المعاش بس. */
    val desiredMonthlyMinor: Halalas? = null,
    /** اللي متحوش معاك النهارده للتقاعد (بيتحسب زي ما هو — من غير أرباح). */
    val savedNowMinor: Halalas = 0,
    /** الفلوس تكفي كام سنة بعد التقاعد — أو [untilAgeMonths] «لحد سن كذا». الاتنين null ⇒ الفجوة «غير متاح». */
    val years: Int? = null,
    val untilAgeMonths: Int? = null,
)

data class RetirementOutcome(
    val defaults: RetirementDefaults,
    val pension: PensionEstimate,
    val endOfService: EosPart,
    /** null لو المستخدم ما كتبش المبلغ اللي عايز يعيش بيه. */
    val gap: GapOutcome?,
)

data class RetirementCalculatorDeps(val incomeSources: IncomeSourceRepository)

class RetirementCalculator(private val deps: RetirementCalculatorDeps) {
    /** الراتب وبداية الشغل من مصادر الدخل (الوظيفة الشغالة النهارده بعملة البلد). */
    suspend fun defaults(countryCode: String, today: IsoDate): RetirementDefaults {
        val currency = countryPack(countryCode).currency
        val jobs = deps.incomeSources.listAll().filter { isActiveJob(it, currency, today) }
        val only = jobs.singleOrNull()
        return RetirementDefaults(only?.expectedMinor, only?.id, only?.startedAt, jobs.size)
    }

    suspend fun calculate(request: RetirementRequest, today: IsoDate): RetirementOutcome {
        val defaults = defaults(request.countryCode, today)
        if (countryPack(request.countryCode).code != "SA") {
            val pension = egyptPensionEstimate()
            val gap = request.desiredMonthlyMinor?.let { retirementGap(it, pension, EosPart.NotApplicable(TextKey.CALC_EOS_EGYPT_NOT_APPLICABLE), request.savedNowMinor, request.years, request.untilAgeMonths) }
            return RetirementOutcome(defaults, pension, EosPart.NotApplicable(TextKey.CALC_EOS_EGYPT_NOT_APPLICABLE), gap)
        }
        val input = requireNotNull(request.saudi) { "saudi pension input missing" }
        val pension = saudiPensionEstimate(input.copy(today = today, averageWageMinor = input.averageWageMinor ?: defaults.salaryMinor))
        val eos = endOfService(request, defaults, pension)
        val gap = request.desiredMonthlyMinor?.let { retirementGap(it, pension, eos, request.savedNowMinor, request.years, request.untilAgeMonths) }
        return RetirementOutcome(defaults, pension, eos, gap)
    }

    private fun endOfService(request: RetirementRequest, defaults: RetirementDefaults, pension: PensionEstimate): EosPart {
        if (request.noCurrentJob) return EosPart.NotApplicable(TextKey.CALC_EOS_NO_JOB)
        val end = pension.retirementDate ?: return EosPart.Unavailable(pension.reason ?: CalcReason.NEED_PENSION)
        val start = request.jobStartedAt ?: defaults.jobStartedAt ?: return EosPart.Unavailable(CalcReason.NEED_JOB_START)
        val wage = request.eosWageMinor ?: defaults.salaryMinor ?: return EosPart.Unavailable(CalcReason.NEED_WAGE)
        return EosPart.Known(saudiEndOfService(wage, start, end, request.eosEnd, request.art87))
    }

    /** وظيفة (مش بارت تايم ولا عميل) شغالة النهارده بعملة البلد. */
    private fun isActiveJob(s: IncomeSource, currency: Currency, today: IsoDate): Boolean =
        s.kind == IncomeSourceKind.JOB && s.currency == currency && s.startedAt <= today && (s.endedAt?.let { it >= today } ?: true)
}
