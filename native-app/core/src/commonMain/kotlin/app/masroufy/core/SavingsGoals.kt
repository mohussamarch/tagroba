package app.masroufy.core

/**
 * «خطة الادخار» (المساعد المالي — قرار المالك 2026-10-05، OVERRIDES §68): هدف بمبلغ وتاريخ، والبرنامج بيقول المدّخر لحد دلوقتي ·
 * المفروض يكون وصل كام النهارده (خط مستقيم من البداية للتاريخ) · قدام ولا ورا وبكام · محتاج كام في الشهر للشهور الباقية.
 *
 * - **على مستوى الحساب** (زي الأشخاص والمناسبات §41 · §64) — مجموعتين [SAVINGS_GOALS_GROUP] و[GOAL_CONTRIBUTIONS_GROUP].
 * - **المدّخر من مصدر واحد:** محفظة مربوطة (رصيدها كله — اختيار Claude: اربط حساب مخصص للهدف) **أو** سجل إيداعات بإيدك.
 *   المحفظة المربوطة بتتجاهل الإيداعات اليدوية (بتفضل متخزنة لو رجعت يدوي).
 * - **القاعدة 10:** رصيد مش معروف ⇒ كل الأرقام «غير متاح» (null) — مش صفر.
 * أعداد صحيحة بس (الهللة)، والتقريب في القسمة لفوق عشان «محتاج في الشهر» يوصّل للهدف فعلًا.
 */
const val SAVINGS_GOALS_GROUP = "savingsGoals"
const val GOAL_CONTRIBUTIONS_GROUP = "goalContributions"

/** الخطط وإيداعاتها — بيتكتبوا في النسخة الشاملة مع بعض بس لو أي واحدة فيها حاجة. */
val GOAL_BACKUP_GROUPS = listOf(SAVINGS_GOALS_GROUP, GOAL_CONTRIBUTIONS_GROUP)

const val GOAL_NAME_MAX = 60

/** أقل مدة من بداية الخطة قبل ما نتوقع «هتوصل لكام» من معدلك (أقل من كده المعدل مش معلومة). */
const val GOAL_PACE_MIN_DAYS = 30

/**
 * الخطة. [startDate] و[targetDate] بيتخزنوا باسم `startedAt` و`deadline` (أسماء تواريخ النسخة الشاملة بتفحصها أصلًا).
 * [linkedWalletId] + [linkedSpaceId] = المدّخر من رصيد المحفظة دي (في البلد دي)؛ null = يدوي من [GoalContribution].
 * [starred] = الخطة اللي عليها نجمة ⭐ — habitVsGoal بيقيس عليها (رد المالك، صفحة الضبط 2026-10-05). **خطة واحدة بس في الحساب**
 * (`ManageSavingsGoals.star` بيشيل النجمة من الباقيين). بيتكتب في المستند **بس لو true** ⇒ المستندات والنسخ القديمة هي هي.
 */
data class SavingsGoal(
    val id: Id,
    val name: String,
    val targetMinor: Halalas,
    val currency: Currency,
    val startDate: IsoDate,
    val targetDate: IsoDate,
    val linkedWalletId: Id? = null,
    val linkedSpaceId: String? = null,
    val archived: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val starred: Boolean = false,
) {
    val manual: Boolean get() = linkedWalletId == null
}

/** إيداع يدوي في خطة (موجب دايمًا — زي باقي المبالغ في النسخة الشاملة). */
data class GoalContribution(
    val id: Id,
    val goalId: Id,
    val date: IsoDate,
    val amountMinor: Halalas,
    val createdAt: String,
    val note: String? = null,
)

class SavingsGoalError(message: String) : IllegalArgumentException(message)

/** فحص الخطة قبل التخزين: اسم · مبلغ موجب · تاريخ الهدف بعد البداية · المحفظة والبلد مع بعض. */
fun checkSavingsGoal(goal: SavingsGoal): SavingsGoal {
    val name = JsText.collapseWhitespace(JsText.trim(goal.name))
    if (name.isEmpty()) throw SavingsGoalError(uiText(TextKey.GOAL_NAME_REQUIRED))
    if (name.length > GOAL_NAME_MAX) throw SavingsGoalError(uiText(TextKey.GOAL_NAME_LENGTH, GOAL_NAME_MAX.toString()))
    if (goal.targetMinor <= 0 || goal.targetMinor > MAX_SAFE_HALALAS) throw SavingsGoalError(uiText(TextKey.GOAL_TARGET_POSITIVE))
    if (!isValidIsoDate(goal.startDate) || !isValidIsoDate(goal.targetDate) || goal.targetDate <= goal.startDate) {
        throw SavingsGoalError(uiText(TextKey.GOAL_DATES_ORDER))
    }
    if ((goal.linkedWalletId == null) != (goal.linkedSpaceId == null)) throw SavingsGoalError(uiText(TextKey.GOAL_WALLET_NOT_FOUND))
    return goal.copy(name = name)
}

/** مجموع الإيداعات اليدوية لحد [upTo] (شامل). */
fun manualSavedMinor(contributions: List<GoalContribution>, goalId: Id, upTo: IsoDate): Halalas =
    sumMoney(contributions.filter { it.goalId == goalId && it.date <= upTo }.map { it.amountMinor })

/** [date] + [months] شهر، واليوم بيتقيد بآخر الشهر (31 يناير + شهر = 28/29 فبراير). */
fun addMonthsClamped(date: IsoDate, months: Int): IsoDate {
    val (y, m, d) = parseIsoDate(date)
    val total = y * 12 + (m - 1) + months
    val year = total.floorDiv(12)
    val month = total.mod(12) + 1
    return formatIsoDate(DateParts(year, month, minOf(d, daysInMonth(year, month))))
}

/**
 * الشهور الباقية من [from] لـ[to] **بالتقويم مش بـ30 يوم** (لفوق): أصغر n ≥ 1 بحيث [from] + n شهر ≥ [to].
 * [to] قبل [from] ⇒ 0. نفس اليوم ⇒ 1 (الباقي مطلوب النهارده).
 */
fun monthsUntil(from: IsoDate, to: IsoDate): Int {
    if (to < from) return 0
    var n = 1
    while (addMonthsClamped(from, n) < to) n++
    return n
}

/** قسمة لفوق لمبلغ موجب على عدد موجب — «محتاج في الشهر» ما يطلعش أقل من اللازم بهللة. */
fun ceilDivMoney(amount: Halalas, parts: Int): Halalas {
    assertHalalas(amount)
    require(parts > 0)
    if (amount <= 0) return 0
    return (amount + parts - 1) / parts
}

enum class GoalState {
    /** المدّخر مش معروف (رصيد المحفظة مش معروف) — ولا رقم. */
    UNKNOWN,
    NOT_STARTED,
    ON_TRACK,
    BEHIND,
    /** المدّخر معروف بس بداية الخط مش معروفة (المحفظة اتفتحت في التطبيق بعد بداية الخطة) ⇒ مفيش «قدام/ورا». */
    PACE_UNKNOWN,
    REACHED,
    /** التاريخ عدّى والمبلغ ما اكتملش. */
    OVERDUE,
}

/**
 * حالة الخطة النهارده. كل رقم null = «غير متاح» (القاعدة 10).
 * [expectedMinor] المفروض يكون وصل النهارده: من المدّخر يوم البداية للهدف على خط مستقيم **بالأيام**.
 * [aheadMinor] موجب = قدام، سالب = ورا. [requiredPerMonthMinor] = الباقي ÷ الشهور الباقية بالتقويم (لفوق).
 * [projectedAtTargetMinor] = هتوصل لكام يوم الهدف **بمعدلك من البداية** (بعد [GOAL_PACE_MIN_DAYS] بس).
 */
data class GoalProgress(
    val goal: SavingsGoal,
    val state: GoalState,
    val savedMinor: Halalas?,
    val startSavedMinor: Halalas?,
    val expectedMinor: Halalas?,
    val aheadMinor: Halalas?,
    val remainingMinor: Halalas?,
    val monthsLeft: Int?,
    val requiredPerMonthMinor: Halalas?,
    val projectedAtTargetMinor: Halalas?,
)

/** [savedMinor] المدّخر النهارده، [startSavedMinor] المدّخر يوم البداية (null = مش معروف). */
fun goalProgress(goal: SavingsGoal, savedMinor: Halalas?, startSavedMinor: Halalas?, today: IsoDate): GoalProgress {
    val target = goal.targetMinor
    if (savedMinor == null) return GoalProgress(goal, GoalState.UNKNOWN, null, startSavedMinor, null, null, null, null, null, null)
    val remaining = maxOf(0L, subtractMoney(target, savedMinor))
    val months = monthsUntil(today, goal.targetDate)
    val required = if (months == 0) null else ceilDivMoney(remaining, months)
    if (savedMinor >= target) {
        return GoalProgress(goal, GoalState.REACHED, savedMinor, startSavedMinor, null, null, 0, months, 0, null)
    }
    if (today > goal.targetDate) {
        return GoalProgress(goal, GoalState.OVERDUE, savedMinor, startSavedMinor, target, subtractMoney(savedMinor, target), remaining, 0, null, savedMinor)
    }
    if (today < goal.startDate) {
        val ahead = startSavedMinor?.let { subtractMoney(savedMinor, it) }
        return GoalProgress(goal, GoalState.NOT_STARTED, savedMinor, startSavedMinor, startSavedMinor, ahead, remaining, months, required, null)
    }
    val start = startSavedMinor
        ?: return GoalProgress(goal, GoalState.PACE_UNKNOWN, savedMinor, null, null, null, remaining, months, required, null)
    val total = daysBetween(goal.startDate, goal.targetDate)
    val elapsed = daysBetween(goal.startDate, today)
    val expected = if (start >= target) target else addMoney(start, rateOfMoney(subtractMoney(target, start), elapsed.toLong(), total.toLong()))
    val ahead = subtractMoney(savedMinor, expected)
    val projected = if (elapsed < GOAL_PACE_MIN_DAYS) null else {
        val growth = subtractMoney(savedMinor, start)
        maxOf(0L, addMoney(savedMinor, rateOfMoney(growth, (total - elapsed).toLong(), elapsed.toLong())))
    }
    val state = if (ahead >= 0) GoalState.ON_TRACK else GoalState.BEHIND
    return GoalProgress(goal, state, savedMinor, start, expected, ahead, remaining, months, required, projected)
}
