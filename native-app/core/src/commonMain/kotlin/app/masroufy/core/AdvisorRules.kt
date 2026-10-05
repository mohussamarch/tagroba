package app.masroufy.core

/**
 * «المساعد المالي» — القواعد الثلاثة اللي المالك فعّلها 2026-10-05 (OVERRIDES §68)، دوال نقية بأعداد صحيحة:
 * - **habitVsGoal:** صرف بند «اختياري» (قهوة · ترفيه …) أعلى من معتاده بوضوح **و**بالمعدل ده الخطة مش هتكمل.
 * - **overcommitted:** اللي حجزته للفترة (الحجز §65) أكتر من اللي معاك.
 * - **capPace:** بمعدل صرفك سقف ميزانية هيتعدّى قبل آخر الشهر المالي.
 * **قاعدة المالك للمعدل (§68):** المعدل محتاج **تكرار** ([MIN_REPEATS_FOR_PACE] مرات أو أكتر في الشهر)، و**الخبطة الواحدة**
 * (عملية ≥ [LUMP_PERCENT_OF_BASE]% من السقف، أو من معتاد الشهر لو مفيش سقف) **عمرها ما بتتمد على باقي الشهر**: بتتحسب في
 * «صرفت لحد دلوقتي» بس، وبرا المعدل اليومي وبرا عدّ المرات. تنبيهات «الميزانية قربت تخلص» (§61) منفصلة وما اتلمستش.
 * **القاعدة 10:** أي مدخل مش معروف (معتاد · رصيد · خطة) ⇒ مفيش تنبيه — مش تنبيه غلط.
 */

/** المعدل ما بيتحسبش قبل اليوم الخامس من الشهر المالي (اليوم الأول = 1). */
const val PACE_MIN_ELAPSED_DAYS = 5

/** قرار المالك (§68): تنبيه المعدل محتاج 3 عمليات أو أكتر في الشهر (من غير الخبطات). */
const val MIN_REPEATS_FOR_PACE = 3

/** الخبطة الواحدة: عملية ≥ 40% من سقف البند (أو من معتاده لو مفيش سقف) — ⚠️ مستني تأكيد المالك (§68). */
const val LUMP_PERCENT_OF_BASE = 40

/** «أعلى من المعتاد بوضوح»: المتوقع للشهر ≥ 130% من المعتاد. */
const val HABIT_ABOVE_USUAL_PERCENT = 130

/** المعتاد = وسيط آخر 3 شهور مالية مكتملة (من يوم الراتب). */
const val HABIT_BASELINE_MONTHS = 3

/** «النقص كبر بوضوح»: تنبيه تاني في نفس الفترة بس لو النقص زاد 25% أو أكتر عن أكبر نقص اتبعت. */
const val OVERCOMMIT_REGROW_PERCENT = 25

/**
 * البنود «الاختيارية» (discretionary) — قايمة ثابتة مكتوبة بمعرّفات شجرة التصنيفات (المعرّف ما بيتغيرش لما الاسم يتغير §66):
 * مطاعم وقهوة (وفروعها) · ترفيه (وفروعه) · التسوق (وفروعه) · عطور · إكسسوارات. أي فرع المستخدم ضافه تحت واحد منهم بيتحسب كمان.
 * البقالة والبيت والسيارة والصحة والتعليم والتبرعات **مش اختيارية** (اختيار Claude — المالك يقدر يغيّره).
 */
val DISCRETIONARY_CATEGORY_IDS: Set<Id> = setOf(
    "cat-مطاعم-وقهوه",
    "cat-ترفيه",
    "cat-التسوق",
    "cat-العنايه-الشخصيه--عطور",
    "cat-العنايه-الشخصيه--اكسسوارات",
)

/** التصنيف أو أي أب ليه في [DISCRETIONARY_CATEGORY_IDS]. [parentOf] = أب كل تصنيف. */
fun isDiscretionary(categoryId: Id?, parentOf: Map<Id, Id?>): Boolean {
    var id = categoryId
    var depth = 0
    while (id != null && depth++ < 8) {
        if (id in DISCRETIONARY_CATEGORY_IDS) return true
        id = parentOf[id]
    }
    return false
}

/** المعتاد: وسيط [HABIT_BASELINE_MONTHS] شهور معروفة كلها (شهر مش معروف ⇒ null). الصفر المحسوب صفر معروف. */
fun usualMonthMinor(months: List<Halalas?>): Halalas? {
    if (months.size < HABIT_BASELINE_MONTHS || months.any { it == null }) return null
    val sorted = months.map { it!! }.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (addMoney(sorted[mid - 1], sorted[mid]) + 1) / 2
}

/** خبطة واحدة؟ [base] = سقف البند أو معتاده (null أو صفر ⇒ مفيش حكم ⇒ مش خبطة). */
fun isLump(amountMinor: Halalas, base: Halalas?): Boolean =
    base != null && base > 0 && amountMinor * 100 >= base * LUMP_PERCENT_OF_BASE

/**
 * قراية المعدل في الفترة لحد النهارده. [projectedMinor] = الخبطات + المعتاد اليومي × أيام الفترة؛ null = بدري
 * (قبل [PACE_MIN_ELAPSED_DAYS]) أو أقل من [MIN_REPEATS_FOR_PACE] عمليات متكررة.
 */
data class PaceReading(
    val spentMinor: Halalas,
    val lumpMinor: Halalas,
    val lumpCount: Int,
    val regularMinor: Halalas,
    val regularCount: Int,
    val elapsedDays: Int,
    val totalDays: Int,
    val projectedMinor: Halalas?,
)

/** [amounts] نصيبك من كل عملية في البند في الفترة لحد النهارده. */
fun readPace(amounts: List<Halalas>, lumpBase: Halalas?, today: IsoDate, period: Period): PaceReading {
    val elapsed = (daysBetween(period.start, today) + 1).coerceIn(0, period.days)
    val (lumps, regular) = amounts.partition { isLump(it, lumpBase) }
    val lumpSum = sumMoney(lumps)
    val regularSum = sumMoney(regular)
    val spent = addMoney(lumpSum, regularSum)
    val projected = when {
        elapsed < PACE_MIN_ELAPSED_DAYS || regular.size < MIN_REPEATS_FOR_PACE -> null
        elapsed >= period.days -> spent
        else -> addMoney(lumpSum, rateOfMoney(regularSum, period.days.toLong(), elapsed.toLong()))
    }
    return PaceReading(spent, lumpSum, lumps.size, regularSum, regular.size, elapsed, period.days, projected)
}

/** قسمة صحيحة لفوق لأعداد موجبة. */
private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0) 0 else (a + b - 1) / b

/**
 * capPace: بمعدلك السقف هيتعدّى قبل آخر الفترة. ما بيطلعش لو السقف اتعدّى خلاص (ده تنبيه «عدّى السقف» §61) أو المعدل مش معروف.
 * اليوم المتوقع = النهارده + الباقي من السقف ÷ المعتاد اليومي (لفوق)، ومش بعد آخر الفترة. [targetKey] = `total` أو `cat:<id>`.
 */
fun capPaceCandidate(label: String, targetKey: String, capMinor: Halalas, reading: PaceReading, today: IsoDate, period: Period, currency: Currency): AlertCandidate? {
    val projected = reading.projectedMinor ?: return null
    if (capMinor <= 0 || reading.spentMinor >= capMinor || projected <= capMinor || reading.regularMinor <= 0) return null
    val left = subtractMoney(capMinor, reading.spentMinor)
    val days = ceilDiv(multiplyMoneyByInt(left, reading.elapsedDays.toLong()), reading.regularMinor).coerceIn(1L, period.days.toLong())
    val crossing = minOf(addDaysIso(today, days.toInt()), period.end)
    return AlertCandidate(
        kind = AlertKind.CAP_PACE,
        threadKey = "cappace|${period.start}|$targetKey",
        title = uiText(TextKey.ADVISOR_CAP_PACE_TITLE, label),
        body = uiText(
            TextKey.ADVISOR_CAP_PACE_BODY, formatMoney(reading.spentMinor, currency), formatMoney(capMinor, currency),
            formatMoney(projected, currency), crossing,
        ),
    )
}

/** بند اختياري في الشهر ده + الخطة اللي بنقيس عليها. */
data class HabitCheck(
    val categoryId: Id,
    val categoryName: String,
    val reading: PaceReading,
    /** المعتاد ([usualMonthMinor]) — null = مش معروف. */
    val usualMinor: Halalas?,
    /** الخطة اللي بنقيس عليها — null = مفيش خطة ⇒ مفيش تنبيه. */
    val progress: GoalProgress?,
    val period: Period,
    val currency: Currency,
)

/**
 * habitVsGoal: المتوقع للبند ≥ [HABIT_ABOVE_USUAL_PERCENT]% من معتاده، والزيادة دي لو استمرت كل شهر لحد تاريخ الخطة ⇒
 * «هتوصل لكام» يقل عن الهدف. النص: «القهوة هذا الشهر 640 ر.س — بهذا المعدل ستحوش 9,200 بدل 12,000 آخر السنة».
 * الاقتراح بـ«مرات» مش بأصناف (قرار المالك §68: القهوة مكان مش مشروب).
 */
fun habitVsGoalCandidate(h: HabitCheck): AlertCandidate? {
    val usual = h.usualMinor ?: return null
    if (usual <= 0) return null
    val projected = h.reading.projectedMinor ?: return null
    if (projected * 100 < usual * HABIT_ABOVE_USUAL_PERCENT) return null
    val p = h.progress ?: return null
    if (p.state != GoalState.ON_TRACK && p.state != GoalState.BEHIND) return null
    val atTarget = p.projectedAtTargetMinor ?: return null
    val months = p.monthsLeft ?: return null
    if (months <= 0) return null
    val excess = subtractMoney(projected, usual)
    val withHabit = maxOf(0L, subtractMoney(atTarget, multiplyMoneyByInt(excess, months.toLong())))
    if (withHabit >= p.goal.targetMinor) return null

    val c = h.currency
    val spent = formatMoney(h.reading.spentMinor, c)
    val body = if (p.goal.targetDate.endsWith("-12-31")) {
        uiText(TextKey.ADVISOR_HABIT_BODY_YEAR_END, h.categoryName, spent, formatMoney(withHabit, c), formatMoney(p.goal.targetMinor, c))
    } else {
        uiText(TextKey.ADVISOR_HABIT_BODY_BY_DATE, h.categoryName, spent, formatMoney(withHabit, c), formatMoney(p.goal.targetMinor, c), p.goal.targetDate)
    }
    val parts = mutableListOf(body)
    timesPerWeekToCut(h.reading, excess, h.period)?.let { parts += uiText(TextKey.ADVISOR_HABIT_SUGGEST, it.toString()) }
    return AlertCandidate(
        kind = AlertKind.HABIT_VS_GOAL,
        threadKey = "habit|${h.period.start}|${h.categoryId}",
        title = uiText(TextKey.ADVISOR_HABIT_TITLE, h.categoryName),
        body = parts.joinToString(" · "),
    )
}

/**
 * «قلّل N مرات في الأسبوع»: الزيادة ÷ متوسط المرة (من العمليات المتكررة بس) ⇒ مرات في الشهر ⇒ في الأسبوع (لفوق)،
 * ومش أكتر من عدد مراتك الحالي في الأسبوع. null = مفيش رقم له معنى.
 */
fun timesPerWeekToCut(reading: PaceReading, excessMinor: Halalas, period: Period): Int? {
    if (reading.regularCount <= 0 || reading.elapsedDays <= 0 || excessMinor <= 0) return null
    val perVisit = reading.regularMinor / reading.regularCount
    if (perVisit <= 0) return null
    val perMonth = ceilDiv(excessMinor, perVisit)
    val perWeek = ceilDiv(perMonth * 7, period.days.toLong())
    val nowPerWeek = ceilDiv(reading.regularCount * 7L, reading.elapsedDays.toLong())
    return minOf(perWeek, nowPerWeek).toInt().takeIf { it >= 1 }
}

/** النقص اللي اتبعت عنه قبل كده في الفترة دي (من إيصالات المحرك) — [threadPrefix] = بادئة البلد (`eg:`) أو فاضي للسعودية. */
fun overcommitSentGaps(eventKeys: Collection<String>, threadPrefix: String, anchor: IsoDate): List<Halalas> {
    val head = "${threadPrefix}overcommit|$anchor|"
    val tail = "|${AlertKind.OVERCOMMITTED.wire}"
    return eventKeys.filter { it.startsWith(head) && it.endsWith(tail) }.mapNotNull { it.removePrefix(head).removeSuffix(tail).toLongOrNull() }
}

/**
 * overcommitted: المحسوب (الحجز) للفترة أكتر من الفلوس اللي معاك ([LeftoverProjection] — نفس «فاضلك تقريبًا» §65).
 * رصيد مش معروف ⇒ مفيش تنبيه. **مرة لما يحصل، وتاني بس لو النقص كبر** [OVERCOMMIT_REGROW_PERCENT]% عن أكبر نقص اتبعت:
 * الموضوع فيه النقص اللي اتبعت عنه ⇒ إيصالات المحرك نفسها بتمنع التكرار. [sentGaps] null = الإيصالات مش متوصلة ⇒ مرة واحدة للفترة.
 * [anchor] = أول الفترة المالية.
 */
fun overcommitCandidate(p: LeftoverProjection, currency: Currency, anchor: IsoDate, sentGaps: List<Halalas>?, monthScaleMinor: Halalas? = null): AlertCandidate? {
    val onHand = p.onHandMinor ?: return null
    val gap = subtractMoney(p.countedMinor, onHand)
    if (gap <= 0) return null
    val thread = when {
        sentGaps == null -> "overcommit|$anchor"
        else -> {
            val largest = sentGaps.maxOrNull()
            val step = if (largest == null || gap * 100 >= largest * (100 + OVERCOMMIT_REGROW_PERCENT)) gap else largest
            "overcommit|$anchor|$step"
        }
    }
    val bodyKey = when (p.mode) {
        LeftoverMode.UNTIL_MONTH_END -> TextKey.ADVISOR_OVERCOMMIT_BODY_MONTH
        LeftoverMode.UNTIL_NEXT_PAY -> TextKey.ADVISOR_OVERCOMMIT_BODY_NEXT_PAY
        LeftoverMode.FROM_WHAT_YOU_HAVE -> TextKey.ADVISOR_OVERCOMMIT_BODY_ANY
    }
    val body = uiText(bodyKey, formatMoney(p.countedMinor, currency), formatMoney(onHand, currency), formatMoney(gap, currency))
    return AlertCandidate(
        kind = AlertKind.OVERCOMMITTED,
        threadKey = thread,
        title = uiText(TextKey.ADVISOR_OVERCOMMIT_TITLE),
        body = if (p.approximate) "$body ${uiText(TextKey.ADVISOR_APPROXIMATE)}" else body,
        amountMinor = gap,
        monthScaleMinor = monthScaleMinor,
    )
}
