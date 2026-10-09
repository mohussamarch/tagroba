package app.masroufy.core

/**
 * الراتب قبل أول الشهر المالي بشوية ⇒ **بيتحسب للشهر الجديد** (قرار المالك §75-3). وقت العرض بس: تاريخ العملية (`occurredAt`) ما بيتغيرش
 * ولا بيتكتب حاجة — الشاشات بتسأل «العملية دي بتتحسب في أنهي يوم؟» ([countingDate]) وبتقرا الفترة من [countingReadStart].
 *
 * **اختيار Claude (المالك يقدر يغيّره):** «بشوية» = [SALARY_EARLY_DAYS] أيام قبل بداية الفترة الجاية، و**الراتب بس** (`SALARY` — مش
 * المكافأة ولا العمولة). الراتب = نوعه المتخزن، أو المعروف وقت العرض (تصنيف راتب §75-1) — يعني [countingDate] بتاخد العملية من
 * العرض المعدود (`withEstimatedKinds`). [EstimatePolicy.LEGACY] ⇒ مفيش نقل (التطبيق القديم وملفات المرجع).
 */
const val SALARY_EARLY_DAYS = 7

/** أول يوم في الفترة اللي بعد الفترة اللي فيها [date]. */
fun nextPeriodStart(date: IsoDate, payday: Int): IsoDate =
    dayNumberToIso(toDayNumber(parseIsoDate(periodForDate(date, payday).end)) + 1)

/**
 * اليوم اللي العملية بتتحسب فيه في مجاميع الفترات: الراتب الوارد اللي نزل لحد [SALARY_EARLY_DAYS] أيام قبل بداية الفترة الجاية ⇒
 * بداية الفترة الجاية. غير كده ⇒ تاريخها. [t] من العرض المعدود (نوعها بعد التقدير).
 */
fun countingDate(t: Transaction, payday: Int, policy: EstimatePolicy = EstimatePolicy.current): IsoDate {
    if (policy == EstimatePolicy.LEGACY || t.economicKind != EconomicKind.SALARY || t.observedDirection != Direction.IN) return t.occurredAt
    val next = nextPeriodStart(t.occurredAt, payday)
    return if (daysBetween(t.occurredAt, next) <= SALARY_EARLY_DAYS) next else t.occurredAt
}

/**
 * أول يوم لازم يتقرا عشان مجاميع فترة (أو مدى) بيبدأ [start] — راتبها ممكن ينزل قبلها بـ[SALARY_EARLY_DAYS] أيام. القراية بتفضل
 * محدودة بالفترة (ARCHITECTURE §5.6).
 */
fun countingReadStart(start: IsoDate, policy: EstimatePolicy = EstimatePolicy.current): IsoDate =
    if (policy == EstimatePolicy.LEGACY) start else addDaysIso(start, -SALARY_EARLY_DAYS)
