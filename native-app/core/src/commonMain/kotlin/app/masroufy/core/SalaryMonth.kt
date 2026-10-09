package app.masroufy.core

/**
 * الراتب قبل أول الشهر المالي بشوية ⇒ **بيتحسب للشهر الجديد** (قرار المالك §75-3). وقت العرض بس: تاريخ العملية (`occurredAt`) ما بيتغيرش
 * ولا بيتكتب حاجة — الشاشات بتسأل «العملية دي بتتحسب في أنهي يوم؟» ([countingDate]) وبتقرا الفترة من [countingReadStart].
 *
 * **اختيار Claude (المالك يقدر يغيّره):**
 * - «بشوية» = لحد [SALARY_EARLY_DAYS] يوم قبل يوم الراتب — نفس مهلة «المرتب البدري» اللي تنبيه المرتب المتأخر شغال بيها
 *   ([INCOME_EARLY_WINDOW_DAYS]). يعني الراتب اللي نزل في **النص التاني** من الشهر المالي بيتحسب للشهر الجاي، واللي نزل في نصه الأول
 *   (متأخر عن يوم الراتب لحد أسبوعين) بيفضل في شهره.
 * - المسافة **برقم اليوم في الشهر** مش بالتقويم ([daysBeforePayday]): الراتب اللي بينزل نفس اليوم كل شهر قراره واحد كل شهر — مهما كان
 *   طول الشهر (يوم راتب 1–7) أو يوم الراتب 29–31 اتقيد بآخر الشهر. نسخة 7 أيام بالتقويم كانت بتسيب الراتب في شهر وتنقله في شهر،
 *   فشهر يطلع من غير راتب وشهر بعده براتبين (مراجعة الشريحة S6). والحد في نص الشهر عشان الراتب اللي بيتقدم أو يتأخر كام يوم
 *   (إجازة، عيد) ما يعدّيش الحد.
 * - **الراتب بس** (`SALARY` — مش المكافأة ولا العمولة). الراتب = نوعه المتخزن، أو المعروف وقت العرض (تصنيف راتب §75-1) — يعني
 *   [countingDate] بتاخد العملية من العرض المعدود (`withEstimatedKinds`). [EstimatePolicy.LEGACY] ⇒ مفيش نقل (التطبيق القديم وملفات المرجع).
 */
const val SALARY_EARLY_DAYS = INCOME_EARLY_WINDOW_DAYS

/**
 * طول الشهر «الاسمي» لما يوم الراتب الجاي في الشهر اللي بعده — ثابت عشان القرار ما يتغيرش بطول الشهر. 31 (أطول شهر) ⇒ المسافة
 * الاسمية عمرها ما تقل عن الحقيقية، فالقراية من [countingReadStart] بتجيب كل راتب ممكن يتنقل للفترة.
 */
private const val NOMINAL_MONTH_DAYS = 31

/** أول يوم في الفترة اللي بعد الفترة اللي فيها [date]. */
fun nextPeriodStart(date: IsoDate, payday: Int): IsoDate =
    dayNumberToIso(toDayNumber(parseIsoDate(periodForDate(date, payday).end)) + 1)

/**
 * كام يوم قبل يوم الراتب الجاي — **برقم اليوم بس** ([payday] زي ما هو، من غير قيد آخر الشهر، والشهر 31 يوم): يوم 24 ويوم راتب 1 ⇒ 8 في
 * كل شهر (حتى لو الشهر 30 يوم). يوم الراتب نفسه ⇒ [NOMINAL_MONTH_DAYS] (أبعد حاجة).
 */
fun daysBeforePayday(date: IsoDate, payday: Int): Int {
    val day = parseIsoDate(date).day
    return if (day < payday) payday - day else NOMINAL_MONTH_DAYS + payday - day
}

/**
 * اليوم اللي العملية بتتحسب فيه في مجاميع الفترات: الراتب الوارد اللي نزل لحد [SALARY_EARLY_DAYS] يوم ([daysBeforePayday]) قبل يوم
 * الراتب ⇒ بداية الفترة الجاية. غير كده ⇒ تاريخها. [t] من العرض المعدود (نوعها بعد التقدير).
 * يوم راتب 29–31 في شهر أقصر: الفترة بتبدأ آخر يوم في الشهر — الراتب اللي نزل فيه أصلًا جوه الفترة الجديدة فبيفضل بتاريخه.
 */
fun countingDate(t: Transaction, payday: Int, policy: EstimatePolicy = EstimatePolicy.current): IsoDate {
    if (policy == EstimatePolicy.LEGACY || t.economicKind != EconomicKind.SALARY || t.observedDirection != Direction.IN) return t.occurredAt
    if (daysBeforePayday(t.occurredAt, payday) > SALARY_EARLY_DAYS) return t.occurredAt
    val (year, month, day) = parseIsoDate(t.occurredAt)
    val target = if (day < payday) formatIsoDate(DateParts(year, month, clampPaydayToMonth(year, month, payday))) else nextPeriodStart(t.occurredAt, payday)
    return maxOf(target, t.occurredAt)
}

/**
 * أول يوم لازم يتقرا عشان مجاميع فترة (أو مدى) بيبدأ [start] — راتبها ممكن ينزل قبلها بـ[SALARY_EARLY_DAYS] يوم. القراية بتفضل
 * محدودة بالفترة (ARCHITECTURE §5.6).
 */
fun countingReadStart(start: IsoDate, policy: EstimatePolicy = EstimatePolicy.current): IsoDate =
    if (policy == EstimatePolicy.LEGACY) start else addDaysIso(start, -SALARY_EARLY_DAYS)
