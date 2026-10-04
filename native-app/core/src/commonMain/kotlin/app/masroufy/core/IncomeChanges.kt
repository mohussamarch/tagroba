package app.masroufy.core

/**
 * «غيّرت شغلي» وأسئلته (OVERRIDES §48 · §64) — دوال نقية؛ الحفظ في `ManageIncomeSources`.
 *
 * - **«مبروك» على بداية مصدر جديد بس.** قفل مصدر لوحده **ما بيتقالش معاه أي كلام** (ممكن يكون اتفصل) —
 *   سؤال عن الأثر بس: وظيفة في السعودية ⇒ «فيه مكافأة نهاية خدمة جاية؟» (سؤال راجع، مش التزام بيتخزن).
 * - **المواصلات ما بتتسألش هنا أبدًا** (نص المالك §64: بتتستنتج من قبل كده). سؤال «هتروح بيها الشغل؟» بيطلع بس
 *   لما الملف يقول إنه اشترى عربية وعنده شغل شغال ([shouldAskCarToWork]).
 * - يوم المرتب الجديد: لو مش مكتوب ⇒ يتسأل؛ لو مختلف عن بداية الشهر المالي ⇒ «تغيّر بداية شهرك المالي؟».
 * - المرتب المتوقع اختياري ويتعدّى.
 */
sealed interface IncomeFollowUp {
    /** «فيه مكافأة نهاية خدمة جاية؟» — معلومة للشاشة بس، مفيش التزام بيتعمل. */
    data class EndOfServiceBenefit(val sourceId: Id) : IncomeFollowUp

    /** «المرتب الجديد بينزل يوم كام؟» */
    data class AskPayday(val sourceId: Id) : IncomeFollowUp

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
 * اختيارات Claude (§64 «قرارات تنفيذ»): «السعودية» = عملة المصدر ريال سعودي · المكافأة بتتسأل للوظيفة ([IncomeSourceKind.JOB])
 * حتى لو فيه شغل جديد (المكافأة بتاعة القديم) · يوم المرتب والمرتب المتوقع للوظيفة والبارت تايم بس · تغيير بداية الشهر للوظيفة بس
 * (شغل جنب ما يحرّكش شهرك).
 */
fun jobChangeOutcome(closed: IncomeSource?, opened: IncomeSource?, monthStartDay: Int): JobChangeOutcome {
    val out = mutableListOf<IncomeFollowUp>()
    if (closed != null && closed.kind == IncomeSourceKind.JOB && closed.currency == Currency.SAR) out += IncomeFollowUp.EndOfServiceBenefit(closed.id)
    if (opened != null) out += newSourceFollowUps(opened, monthStartDay)
    return JobChangeOutcome(congratulate = opened != null, followUps = out)
}

/** أسئلة مصدر لسه متفتح (أو اتجاوب فيه يوم المرتب): اليوم ⇒ بداية الشهر ⇒ المرتب المتوقع. */
fun newSourceFollowUps(source: IncomeSource, monthStartDay: Int): List<IncomeFollowUp> {
    if (source.kind !in SALARIED_KINDS) return emptyList()
    val out = mutableListOf<IncomeFollowUp>()
    val day = source.expectedDayOfMonth
    if (day == null) out += IncomeFollowUp.AskPayday(source.id) else monthStartFollowUp(source, monthStartDay)?.let { out += it }
    if (source.expectedMinor == null) out += IncomeFollowUp.AskExpectedSalary(source.id)
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
    is IncomeFollowUp.EndOfServiceBenefit -> uiText(TextKey.INCOME_Q_END_OF_SERVICE)
    is IncomeFollowUp.AskPayday -> uiText(TextKey.INCOME_Q_PAYDAY)
    is IncomeFollowUp.ChangeMonthStart -> uiText(TextKey.INCOME_Q_MONTH_START, f.day.toString())
    is IncomeFollowUp.AskExpectedSalary -> uiText(TextKey.INCOME_Q_EXPECTED_SALARY)
    IncomeFollowUp.CarToWork -> uiText(TextKey.INCOME_Q_CAR_TO_WORK)
}

/** «مبروك» على الشغل الجديد — بيتعرض بس لو [JobChangeOutcome.congratulate]. */
fun incomeCongratsText(source: IncomeSource): String = uiText(TextKey.INCOME_CONGRATS, source.name)
