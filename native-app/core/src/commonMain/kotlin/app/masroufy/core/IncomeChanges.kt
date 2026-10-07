package app.masroufy.core

/**
 * «غيّرت شغلي» وأسئلته (OVERRIDES §48 · §64) — دوال نقية؛ الحفظ في `ManageIncomeSources`.
 *
 * - **«مبروك» على بداية مصدر جديد بس.** قفل مصدر لوحده **ما بيتقالش معاه أي كلام ولا سؤال** (ممكن يكون اتفصل).
 *   سؤال «مكافأة نهاية الخدمة» **اتشال** (رد المالك §64-٧) — بقت اختيار على الإيداع نفسه من الشركة (`EconomicKind.END_OF_SERVICE`).
 * - **المواصلات ما بتتسألش هنا أبدًا** (نص المالك §64: بتتستنتج من قبل كده). سؤال «هتروح بيها الشغل؟» بيطلع بس
 *   لما الملف يقول إنه اشترى عربية وعنده شغل شغال ([shouldAskCarToWork]).
 * - يوم المرتب الجديد: لو مش مكتوب ⇒ يتسأل؛ لو مختلف عن بداية الشهر المالي ⇒ «تغيّر بداية شهرك المالي؟».
 * - المرتب المتوقع اختياري ويتعدّى.
 */
sealed interface IncomeFollowUp {
    /** «المرتب الجديد بينزل يوم كام؟» */
    data class AskPayday(val sourceId: Id) : IncomeFollowUp

    /** «بتقبض إمتى؟ كل شهر ولا كل أسبوع؟» — للبارت تايم بدل [AskPayday] (رد المالك §65: «يتسأل بشكل منفصل»). */
    data class AskPayFrequency(val sourceId: Id) : IncomeFollowUp

    /** «تغيّر بداية شهرك المالي ليوم [day]؟» — الموافقة بتعدّي على استخدام الملف. */
    data class ChangeMonthStart(val sourceId: Id, val day: Int) : IncomeFollowUp

    /** «المرتب المتوقع كام؟» — اختياري. */
    data class AskExpectedSalary(val sourceId: Id) : IncomeFollowUp

    /** «هتروح بيها الشغل؟» — بعد شراء عربية وعنده شغل شغال بس. */
    data object CarToWork : IncomeFollowUp
}

/** نتيجة «غيّرت شغلي»: [congratulate] = أيوه **بس** لو فيه مصدر جديد اتفتح. */
data class JobChangeOutcome(val congratulate: Boolean, val followUps: List<IncomeFollowUp>)

/**
 * الأسئلة بعد قفل [closed] و/أو فتح [opened]. [monthStartDay] = بداية الشهر المالي في الملف (يوم المرتب).
 * القفل **ما بيطلّعش أي سؤال** (سؤال المكافأة اتشال — رد المالك §64-٧). اختيارات Claude (§64 «قرارات تنفيذ»): يوم المرتب والمرتب
 * المتوقع للوظيفة والبارت تايم بس · تغيير بداية الشهر للوظيفة بس
 * (شغل جنب ما يحرّكش شهرك).
 */
fun jobChangeOutcome(closed: IncomeSource?, opened: IncomeSource?, monthStartDay: Int): JobChangeOutcome {
    val out = mutableListOf<IncomeFollowUp>()
    if (opened != null) out += newSourceFollowUps(opened, monthStartDay)
    return JobChangeOutcome(congratulate = opened != null, followUps = out)
}

/**
 * أسئلة مصدر لسه متفتح (أو اتجاوب فيه يوم المرتب): اليوم ⇒ بداية الشهر ⇒ المرتب المتوقع.
 * **البارت تايم بيتسأل دورية القبض** (شهري ولا أسبوعي، وإمتى) بدل يوم المرتب (رد المالك §65). **المعاش** بيتسأل يومه بس
 * (عشان «فاضلك تقريبًا» يعرف القبض الجاي) — اختيار Claude.
 */
fun newSourceFollowUps(source: IncomeSource, monthStartDay: Int): List<IncomeFollowUp> {
    val out = mutableListOf<IncomeFollowUp>()
    when (source.kind) {
        // الوظيفة الأسبوعي (§64-٤) بتتسأل يومها في الأسبوع لو مش مكتوب، ومن غير «تغيّر بداية شهرك؟» (مالهاش يوم في الشهر)
        IncomeSourceKind.JOB -> when {
            source.payFrequency == PayFrequency.WEEKLY -> if (source.payWeekday == null) out += IncomeFollowUp.AskPayFrequency(source.id)
            source.expectedDayOfMonth == null -> out += IncomeFollowUp.AskPayday(source.id)
            else -> monthStartFollowUp(source, monthStartDay)?.let { out += it }
        }
        IncomeSourceKind.PART_TIME -> if (!hasKnownPayDay(source)) out += IncomeFollowUp.AskPayFrequency(source.id)
        IncomeSourceKind.PENSION -> if (source.expectedDayOfMonth == null) out += IncomeFollowUp.AskPayday(source.id)
        else -> return emptyList()
    }
    if (source.kind in SALARIED_KINDS && source.expectedMinor == null) out += IncomeFollowUp.AskExpectedSalary(source.id)
    return out
}

/** «تغيّر بداية شهرك المالي؟» لو يوم مرتب الوظيفة مختلف عن بداية الشهر في الملف. */
fun monthStartFollowUp(source: IncomeSource, monthStartDay: Int): IncomeFollowUp.ChangeMonthStart? {
    val day = source.expectedDayOfMonth ?: return null
    if (source.kind != IncomeSourceKind.JOB || day == monthStartDay) return null
    return IncomeFollowUp.ChangeMonthStart(source.id, day)
}

/**
 * «هتروح بيها الشغل؟» (§64): **لحظة** ما الملف يتحوّل لـ«عنده عربية» (من «لأ» أو «ما اتجاوبش») وعنده وظيفة أو بارت تايم
 * شغال النهارده. الحفظ تاني وهو لسه «عنده عربية» ما بيسألش تاني (مرة واحدة لكل تغيير).
 */
fun shouldAskCarToWork(before: UserProfile?, after: UserProfile, sources: List<IncomeSource>, today: IsoDate): Boolean =
    before?.hasCar != true && after.hasCar == true && sourcesActiveOn(sources, today).any { it.kind in SALARIED_KINDS }

/** نص السؤال للشاشة. */
fun incomeFollowUpText(f: IncomeFollowUp): String = when (f) {
    is IncomeFollowUp.AskPayday -> uiText(TextKey.INCOME_Q_PAYDAY)
    is IncomeFollowUp.AskPayFrequency -> uiText(TextKey.INCOME_Q_PAY_FREQUENCY)
    is IncomeFollowUp.ChangeMonthStart -> uiText(TextKey.INCOME_Q_MONTH_START, f.day.toString())
    is IncomeFollowUp.AskExpectedSalary -> uiText(TextKey.INCOME_Q_EXPECTED_SALARY)
    IncomeFollowUp.CarToWork -> uiText(TextKey.INCOME_Q_CAR_TO_WORK)
}

/** «مبروك» على الشغل الجديد — بيتعرض بس لو [JobChangeOutcome.congratulate]. */
fun incomeCongratsText(source: IncomeSource): String = uiText(TextKey.INCOME_CONGRATS, source.name)
