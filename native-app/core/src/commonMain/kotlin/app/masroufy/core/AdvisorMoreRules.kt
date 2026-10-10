package app.masroufy.core

/**
 * «المساعد المالي» — الأفكار الثمانية التانية (المالك شغّلها كلها 2026-10-05، OVERRIDES §68): unusual · beforePayday · payFirst ·
 * billJump · dupSubs · bigOne · goalNear · weekly. دوال نقية بأعداد صحيحة، والنص جوه التطبيق بس (شاشة القفل نص عام من النوع).
 * **القاعدة 10:** مدخل مش معروف ⇒ مفيش تنبيه. **قاعدة المعدل** (3 مرات · الخبطة ما بتتمدش) على unusual بس — الباقي مش معدل.
 * الثوابت كلها «اختيار Claude — المالك يقدر يغيّره».
 */

/** unusual: المتوقع للشهر ≥ 150% من معتاد البند (أقوى من habitVsGoal 130% — ده لأي بند، مش الاختياري بس). */
const val UNUSUAL_ABOVE_PERCENT = 150

/** beforePayday: آخر 5 أيام قبل القبض الجاي (غير «قبل المرتب بأيام قليلة» في ملخص التقويم = 3). */
const val ADVISOR_BEFORE_PAYDAY_DAYS = 5

/** payFirst: لحد 3 أيام بعد نزول المرتب. */
const val PAY_FIRST_WINDOW_DAYS = 3

/** billJump: الفاتورة ≥ 125% من وسيط آخر [BILL_JUMP_HISTORY] دفعات قبلها. */
const val BILL_JUMP_PERCENT = 125
const val BILL_JUMP_HISTORY = 3

/** dupSubs: اشتراكين شغالين أو أكتر في نفس التصنيف. */
const val DUP_SUBS_MIN = 2

/** bigOne: عملية واحدة ≥ 20% من مصروف الشهر المعتاد (وسيط آخر 3 شهور). */
const val BIG_ONE_PERCENT = 20

/** goalNear: المدّخر ≥ 90% من الهدف. */
const val GOAL_NEAR_PERCENT = 90

/** weekly: الأسبوع من الحد للسبت (السبت = 6 بنظام ISO)، والملخص بعد ما الأسبوع يخلص. */
const val WEEK_END_WEEKDAY = 6

/** unusual: «التسوق هذا الشهر 1,500 — بهذا المعدل ضعف المعتاد أو أكثر (950)». */
fun unusualSpendCandidate(categoryId: Id, name: String, reading: PaceReading, usualMinor: Halalas?, period: Period, currency: Currency): AlertCandidate? {
    val usual = usualMinor ?: return null
    if (usual <= 0) return null
    val projected = reading.projectedMinor ?: return null
    if (projected * 100 < usual * UNUSUAL_ABOVE_PERCENT) return null
    val phrase = when {
        projected * 100 >= usual * 300 -> TextKey.ADVISOR_RATIO_TRIPLE
        projected * 100 >= usual * 200 -> TextKey.ADVISOR_RATIO_DOUBLE
        else -> TextKey.ADVISOR_RATIO_ABOVE
    }
    return AlertCandidate(
        AlertKind.UNUSUAL_SPEND, "unusual|${period.start}|$categoryId", uiText(TextKey.ADVISOR_UNUSUAL_TITLE, name),
        uiText(TextKey.ADVISOR_UNUSUAL_BODY, name, formatMoney(reading.spentMinor, currency), uiText(phrase), formatMoney(usual, currency)),
    )
}

/**
 * beforePayday: آخر [ADVISOR_BEFORE_PAYDAY_DAYS] أيام قبل القبض الجاي ([LeftoverProjection.until]) واللي معاك أقل من اللي محتاجه لحده:
 * المستحقات اللي ليها مبلغ قبل القبض ([dueBeforeMinor]) + صرفك المعتاد للأيام دي ([usualMonthMinor] × الأيام ÷ أيام الشهر).
 * المعتاد مش معروف ⇒ بنقارن بالمستحقات بس. من غير مرتب · رصيد مش معروف · مفيش ولا حاجة معروفة نقارن بيها ⇒ ساكت.
 */
fun beforePaydayCandidate(p: LeftoverProjection, dueBeforeMinor: Halalas, usualMonthMinor: Halalas?, periodDays: Int, today: IsoDate, currency: Currency): AlertCandidate? {
    val until = p.until ?: return null
    if (p.mode == LeftoverMode.FROM_WHAT_YOU_HAVE) return null
    val onHand = p.onHandMinor ?: return null
    val days = daysBetween(today, until)
    if (days < 1 || days > ADVISOR_BEFORE_PAYDAY_DAYS) return null
    if (usualMonthMinor == null && dueBeforeMinor <= 0) return null
    val usualDays = usualMonthMinor?.let { rateOfMoney(it, days.toLong(), periodDays.toLong()) } ?: 0L
    if (onHand >= addMoney(dueBeforeMinor, usualDays)) return null
    val monthEnd = p.mode == LeftoverMode.UNTIL_MONTH_END
    val head = uiText(if (monthEnd) TextKey.ADVISOR_BEFORE_PAY_BODY else TextKey.ADVISOR_BEFORE_NEXT_PAY_BODY, days.toString(), formatMoney(onHand, currency))
    val dues = if (dueBeforeMinor > 0) uiText(TextKey.ADVISOR_BEFORE_PAY_DUES, formatMoney(dueBeforeMinor, currency)) else null
    return AlertCandidate(
        AlertKind.BEFORE_PAYDAY, "beforepay|$until", uiText(TextKey.ADVISOR_BEFORE_PAY_TITLE),
        listOfNotNull(head, dues).joinToString(" — "),
    )
}

/**
 * payFirst: المرتب نزل ([salaryDate] جوه الفترة، لحد [PAY_FIRST_WINDOW_DAYS] أيام) ⇒ «حوّل المطلوب في الشهر لخطتك قبل ما تصرف».
 * خطة شغالة بس، بعملة البلد، ومطلوبها في الشهر معروف وأكبر من صفر. مرة لكل خطة في الفترة.
 * [countedIn] = الشهر المالي اللي الراتب ده بيتحسب فيه (§75-3 — `countingDate`): لو اتبعت، الراتب بتاعه لازم يكون للفترة [period]
 * أو اللي بعدها (نزل قبل يوم الراتب بشوية)، والموضوع بشهر حسابه ⇒ التنبيه يوم ما ينزل ومرة واحدة لشهره. من غيره ⇒ الراتب جوه [period].
 */
fun payFirstCandidate(
    salaryDate: IsoDate?,
    progress: GoalProgress,
    today: IsoDate,
    period: Period,
    currency: Currency,
    countedIn: Period? = null,
): AlertCandidate? {
    val paid = salaryDate ?: return null
    val since = daysBetween(paid, today)
    if (since < 0 || since > PAY_FIRST_WINDOW_DAYS) return null
    if (if (countedIn == null) paid < period.start else countedIn.start < period.start) return null
    val month = countedIn ?: period
    val g = progress.goal
    if (g.archived || g.currency != currency) return null
    if (progress.state !in setOf(GoalState.ON_TRACK, GoalState.BEHIND, GoalState.PACE_UNKNOWN, GoalState.NOT_STARTED)) return null
    val required = progress.requiredPerMonthMinor ?: return null
    if (required <= 0) return null
    return AlertCandidate(
        AlertKind.PAY_FIRST, "payfirst|${month.start}|${g.id}", uiText(TextKey.ADVISOR_PAY_FIRST_TITLE),
        uiText(TextKey.ADVISOR_PAY_FIRST_BODY, formatMoney(required, currency), g.name),
    )
}

/**
 * billJump: آخر دفعة لفاتورة/اشتراك دوري ≥ [BILL_JUMP_PERCENT]% من وسيط آخر [BILL_JUMP_HISTORY] دفعات قبلها
 * (مقارنة عملية بعملية — مش معدل). أقل من 3 دفعات قبلها ⇒ المعتاد مش معروف ⇒ ساكت.
 */
fun billJumpCandidate(item: RecurringItem, latestId: Id, latestMinor: Halalas, previousMinor: List<Halalas>): AlertCandidate? {
    if (previousMinor.size < BILL_JUMP_HISTORY) return null
    val usual = usualMonthMinor(previousMinor.takeLast(BILL_JUMP_HISTORY)) ?: return null
    if (usual <= 0 || latestMinor * 100 < usual * BILL_JUMP_PERCENT) return null
    return AlertCandidate(
        AlertKind.BILL_JUMP, "billjump|${item.id}|$latestId", uiText(TextKey.ADVISOR_BILL_JUMP_TITLE, item.name),
        uiText(TextKey.ADVISOR_BILL_JUMP_BODY, item.name, formatMoney(latestMinor, item.currency), formatMoney(usual, item.currency)),
    )
}

/** اشتراك شغال وتصنيفه (من آخر عملية متربطة بيه). */
data class SubscriptionPlace(val item: RecurringItem, val categoryId: Id, val categoryName: String)

/** dupSubs: [DUP_SUBS_MIN] اشتراكات شغالة أو أكتر في نفس التصنيف ⇒ «عندك أكثر من اشتراك في «بث وأفلام»». مرة لكل مجموعة. */
fun dupSubsCandidates(subs: List<SubscriptionPlace>): List<AlertCandidate> =
    subs.groupBy { it.categoryId }.filterValues { it.size >= DUP_SUBS_MIN }.map { (categoryId, same) ->
        val sorted = same.sortedBy { it.item.id }
        AlertCandidate(
            AlertKind.DUP_SUBS, "dupsubs|$categoryId|${sorted.joinToString(",") { it.item.id }}",
            uiText(TextKey.ADVISOR_DUP_SUBS_TITLE, same.first().categoryName),
            uiText(TextKey.ADVISOR_DUP_SUBS_BODY, sorted.joinToString("، ") { it.item.name }),
        )
    }

/** bigOne: عملية واحدة ≥ [BIG_ONE_PERCENT]% من مصروف الشهر المعتاد — معلومة (الخبطة بالظبط هي اللي بنمسكها هنا). */
fun bigOneCandidate(txnId: Id, amountMinor: Halalas, date: IsoDate, usualMonthMinor: Halalas?, currency: Currency): AlertCandidate? {
    val usual = usualMonthMinor ?: return null
    if (usual <= 0 || amountMinor * 100 < usual * BIG_ONE_PERCENT) return null
    val share = when {
        amountMinor >= usual -> TextKey.ADVISOR_SHARE_FULL
        amountMinor * 2 >= usual -> TextKey.ADVISOR_SHARE_HALF
        amountMinor * 4 >= usual -> TextKey.ADVISOR_SHARE_QUARTER
        else -> TextKey.ADVISOR_SHARE_FIFTH
    }
    return AlertCandidate(
        AlertKind.BIG_ONE, "bigone|$txnId", uiText(TextKey.ADVISOR_BIG_ONE_TITLE, date),
        uiText(TextKey.ADVISOR_BIG_ONE_BODY, formatMoney(amountMinor, currency), uiText(share)),
    )
}

/** goalNear: تشجيع — المدّخر ≥ [GOAL_NEAR_PERCENT]% («قاربت») أو الهدف اكتمل («أحسنت»). كل درجة مرة. */
fun goalNearCandidate(p: GoalProgress): AlertCandidate? {
    val g = p.goal
    val saved = p.savedMinor ?: return null
    if (g.archived) return null
    val amounts = arrayOf(formatMoney(saved, g.currency), formatMoney(g.targetMinor, g.currency))
    return when {
        saved >= g.targetMinor -> AlertCandidate(
            AlertKind.GOAL_NEAR, "goalnear|${g.id}|reached", uiText(TextKey.ADVISOR_GOAL_REACHED_TITLE, g.name), uiText(TextKey.ADVISOR_GOAL_REACHED_BODY, *amounts),
        )
        saved * 100 >= g.targetMinor * GOAL_NEAR_PERCENT -> AlertCandidate(
            AlertKind.GOAL_NEAR, "goalnear|${g.id}|near", uiText(TextKey.ADVISOR_GOAL_NEAR_TITLE, g.name),
            uiText(TextKey.ADVISOR_GOAL_NEAR_BODY, *amounts, formatMoney(subtractMoney(g.targetMinor, saved), g.currency)),
        )
        else -> null
    }
}

/** آخر سبت خلص (قبل النهارده) — نهاية الأسبوع اللي بنلخصه. */
fun lastWeekEnd(today: IsoDate): IsoDate {
    var d = addDaysIso(today, -1)
    while (isoWeekday(d) != WEEK_END_WEEKDAY) d = addDaysIso(d, -1)
    return d
}

/** مصروف أسبوع: المجموع ولكل تصنيف وعدد العمليات. */
data class WeekSpend(val totalMinor: Halalas, val byCategory: Map<Id?, Halalas>, val count: Int)

/**
 * §75-15: «عندك N عملية محتاجة تأكيد» بصيغة العدد الصح بالعربي (واحدة · اتنين · 3–10 جمع · غير كده مفرد — بآخر رقمين).
 * [count] ≤ 0 ⇒ null (مفيش سطر).
 */
fun weeklyAsksLine(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> uiText(TextKey.ADVISOR_WEEKLY_ASKS_ONE)
    count == 2 -> uiText(TextKey.ADVISOR_WEEKLY_ASKS_TWO)
    count % 100 in 3..10 -> uiText(TextKey.ADVISOR_WEEKLY_ASKS_FEW, count.toString())
    else -> uiText(TextKey.ADVISOR_WEEKLY_ASKS_MANY, count.toString())
}

/**
 * weekly: «صرفت X هذا الأسبوع · أكثر/أقل بـY من الأسبوع الماضي · الأعلى: «بند» (Z)». [thisWeek] null = الأسبوع مش معروف
 * (فيه عملية من غير نوع) ⇒ من غير سطر الصرف. الأسبوعين فاضيين ⇒ مفيش بيانات ⇒ من غير سطر الصرف. الأسبوع اللي فات مش معروف ⇒ من غير مقارنة.
 * §75-15: [needsConfirmation] > 0 ⇒ سطر «عندك N عملية محتاجة تأكيد» في الآخر — **حتى لو الصرف مش معروف** (التذكير هو اللي بيعرّفه).
 * من غير سطر صرف ولا تذكير ⇒ ساكت. نص شاشة القفل بيفضل عام ومن غير عدد (`ALERT_LOCK_WEEKLY`).
 */
fun weeklySummaryCandidate(
    weekEnd: IsoDate,
    thisWeek: WeekSpend?,
    lastWeek: WeekSpend?,
    names: Map<Id, String>,
    currency: Currency,
    needsConfirmation: Int = 0,
): AlertCandidate? {
    val parts = weeklySpendParts(thisWeek, lastWeek, names, currency)
    weeklyAsksLine(needsConfirmation)?.let { parts += it }
    if (parts.isEmpty()) return null
    return AlertCandidate(AlertKind.WEEKLY_SUMMARY, "weekly|$weekEnd", uiText(TextKey.ADVISOR_WEEKLY_TITLE), parts.joinToString(" · "))
}

private fun weeklySpendParts(thisWeek: WeekSpend?, lastWeek: WeekSpend?, names: Map<Id, String>, currency: Currency): MutableList<String> {
    val now = thisWeek ?: return mutableListOf()
    if (now.count == 0 && (lastWeek?.count ?: 0) == 0) return mutableListOf()
    val parts = mutableListOf(uiText(TextKey.ADVISOR_WEEKLY_SPENT, formatMoney(now.totalMinor, currency)))
    lastWeek?.takeIf { it.count > 0 }?.let { last ->
        val diff = subtractMoney(now.totalMinor, last.totalMinor)
        parts += when {
            diff > 0 -> uiText(TextKey.ADVISOR_WEEKLY_MORE, formatMoney(diff, currency))
            diff < 0 -> uiText(TextKey.ADVISOR_WEEKLY_LESS, formatMoney(-diff, currency))
            else -> uiText(TextKey.ADVISOR_WEEKLY_SAME)
        }
    }
    now.byCategory.filter { it.key != null && it.value > 0 }.maxWithOrNull(compareBy<Map.Entry<Id?, Halalas>> { it.value }.thenByDescending { it.key })
        ?.let { top -> names[top.key]?.let { parts += uiText(TextKey.ADVISOR_WEEKLY_TOP, it, formatMoney(top.value, currency)) } }
    return parts
}
