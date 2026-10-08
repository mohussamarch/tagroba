package app.masroufy.core

/**
 * نصوص محرك التنبيهات (OVERRIDES §61): اللي بيظهر على **شريط الإشعارات وشاشة القفل**، و«ليه اتبعت دلوقتي» في الصفحة.
 *
 * **شاشة القفل — قرار المالك: المبالغ والأسامي بتستخبى دايمًا.** [SystemNotice] ما بيتبنيش غير من هنا
 * (المُنشئ `internal` في `core`)، والدوال اللي بتبنيه **ما بتاخدش أي نص من برا** — نوع التنبيه واتجاه الفلوس بس.
 * يعني مستحيل اسم محل أو شخص أو مبلغ يوصل للشريط حتى لو حد غلط في المستقبل؛ وفوق ده [isLockSafe] بيتفحص وقت التشغيل.
 */
class SystemNotice internal constructor(val title: String, val body: String) {
    override fun equals(other: Any?): Boolean = other is SystemNotice && other.title == title && other.body == body
    override fun hashCode(): Int = title.hashCode() * 31 + body.hashCode()
    override fun toString(): String = "SystemNotice($title | $body)"
}

/** نص يصلح لشاشة القفل: من غير أي رقم (أي لغة)، ولا علامة نسبة، ولا رمز عملة. */
fun isLockSafe(text: String): Boolean {
    if (text.any { it.isDigit() || it == '%' || it == '٪' }) return false
    return Currency.entries.none { c -> currencyLabel(c).let { it.isNotBlank() && text.contains(it) } } && !text.contains("•")
}

private fun lockKey(kind: AlertKind, flow: DueFlow): TextKey {
    val receive = flow == DueFlow.RECEIVE
    return when (kind) {
        AlertKind.DUE_SOON -> if (receive) TextKey.ALERT_LOCK_DUE_SOON_RECEIVE else TextKey.ALERT_LOCK_DUE_SOON_PAY
        AlertKind.DUE_TODAY -> if (receive) TextKey.ALERT_LOCK_DUE_TODAY_RECEIVE else TextKey.ALERT_LOCK_DUE_TODAY_PAY
        AlertKind.DUE_OVERDUE -> if (receive) TextKey.ALERT_LOCK_DUE_OVERDUE_RECEIVE else TextKey.ALERT_LOCK_DUE_OVERDUE_PAY
        AlertKind.BUDGET_THRESHOLD, AlertKind.BUDGET_EXCEEDED -> TextKey.ALERT_LOCK_BUDGET
        AlertKind.TRANSFER_QUESTION -> TextKey.ALERT_LOCK_QUESTION
        AlertKind.ZAKAT_SOON -> TextKey.ALERT_LOCK_ZAKAT_SOON
        AlertKind.ZAKAT_TODAY -> TextKey.ALERT_LOCK_ZAKAT_TODAY
        AlertKind.ZAKAT_OVERDUE -> TextKey.ALERT_LOCK_ZAKAT_OVERDUE
        AlertKind.BALANCE_MISMATCH -> TextKey.ALERT_LOCK_BALANCE
        AlertKind.PROFILE_INCOMPLETE -> TextKey.ALERT_LOCK_PROFILE
        AlertKind.OCCASION_SOON -> TextKey.ALERT_LOCK_OCCASION_SOON
        AlertKind.OCCASION_TODAY -> TextKey.ALERT_LOCK_OCCASION_TODAY
        AlertKind.INCOME_LATE -> TextKey.ALERT_LOCK_INCOME_LATE
        AlertKind.HABIT_VS_GOAL -> TextKey.ALERT_LOCK_HABIT
        AlertKind.OVERCOMMITTED -> TextKey.ALERT_LOCK_OVERCOMMITTED
        AlertKind.CAP_PACE -> TextKey.ALERT_LOCK_CAP_PACE
        AlertKind.UNUSUAL_SPEND -> TextKey.ALERT_LOCK_UNUSUAL
        AlertKind.BEFORE_PAYDAY -> TextKey.ALERT_LOCK_BEFORE_PAYDAY
        AlertKind.PAY_FIRST -> TextKey.ALERT_LOCK_PAY_FIRST
        AlertKind.BILL_JUMP -> TextKey.ALERT_LOCK_BILL_JUMP
        AlertKind.DUP_SUBS -> TextKey.ALERT_LOCK_DUP_SUBS
        AlertKind.BIG_ONE -> TextKey.ALERT_LOCK_BIG_ONE
        AlertKind.GOAL_NEAR -> TextKey.ALERT_LOCK_GOAL_NEAR
        AlertKind.WEEKLY_SUMMARY -> TextKey.ALERT_LOCK_WEEKLY
        AlertKind.SMS_CONFIRM -> TextKey.ALERT_LOCK_SMS_CONFIRM
        AlertKind.NEW_DEVICE_LOGIN, AlertKind.LINKED_ACCOUNT_ACTIVITY, AlertKind.MONTHLY_EMAIL -> TextKey.ALERT_LOCK_GENERIC
    }
}

private fun lockNotice(bodyKey: TextKey): SystemNotice {
    val notice = SystemNotice(uiText(TextKey.ALERT_LOCK_TITLE), uiText(bodyKey))
    check(isLockSafe(notice.title) && isLockSafe(notice.body)) { "lock-screen text leaks a number or amount: $bodyKey" }
    return notice
}

/** نص الشريط لتنبيه واحد — عام دايمًا («عندك ميعاد دفع النهارده»)، والتفاصيل جوه التطبيق. */
fun systemNoticeFor(kind: AlertKind, flow: DueFlow = DueFlow.PAY): SystemNotice = lockNotice(lockKey(kind, flow))

/** نص الشريط للملخص — من غير عدد («كذا تنبيه») عشان العدد كمان ما يبانش. */
fun digestNotice(): SystemNotice = lockNotice(TextKey.ALERT_LOCK_DIGEST)

private fun whyKey(kind: AlertKind): Pair<TextKey, List<String>> = when (kind) {
    AlertKind.DUE_SOON -> TextKey.ALERT_WHY_DUE_SOON to listOf(DUE_SOON_DAYS.toString())
    AlertKind.DUE_TODAY -> TextKey.ALERT_WHY_DUE_TODAY to emptyList()
    AlertKind.DUE_OVERDUE -> TextKey.ALERT_WHY_DUE_OVERDUE to emptyList()
    AlertKind.BUDGET_THRESHOLD -> TextKey.ALERT_WHY_BUDGET_THRESHOLD to emptyList()
    AlertKind.BUDGET_EXCEEDED -> TextKey.ALERT_WHY_BUDGET_EXCEEDED to emptyList()
    AlertKind.TRANSFER_QUESTION -> TextKey.ALERT_WHY_TRANSFER to emptyList()
    AlertKind.ZAKAT_SOON -> TextKey.ALERT_WHY_ZAKAT_SOON to listOf(ZAKAT_REMINDER_DAYS.toString())
    AlertKind.ZAKAT_TODAY -> TextKey.ALERT_WHY_ZAKAT_TODAY to emptyList()
    AlertKind.ZAKAT_OVERDUE -> TextKey.ALERT_WHY_ZAKAT_OVERDUE to emptyList()
    AlertKind.BALANCE_MISMATCH -> TextKey.ALERT_WHY_BALANCE to emptyList()
    AlertKind.PROFILE_INCOMPLETE -> TextKey.ALERT_WHY_PROFILE to emptyList()
    AlertKind.OCCASION_SOON -> TextKey.ALERT_WHY_OCCASION_SOON to emptyList()
    AlertKind.OCCASION_TODAY -> TextKey.ALERT_WHY_OCCASION_TODAY to emptyList()
    AlertKind.INCOME_LATE -> TextKey.ALERT_WHY_INCOME_LATE to listOf(LATE_INCOME_GRACE_DAYS.toString())
    AlertKind.HABIT_VS_GOAL -> TextKey.ALERT_WHY_HABIT to emptyList()
    AlertKind.OVERCOMMITTED -> TextKey.ALERT_WHY_OVERCOMMITTED to emptyList()
    AlertKind.CAP_PACE -> TextKey.ALERT_WHY_CAP_PACE to emptyList()
    AlertKind.UNUSUAL_SPEND -> TextKey.ALERT_WHY_UNUSUAL to emptyList()
    AlertKind.BEFORE_PAYDAY -> TextKey.ALERT_WHY_BEFORE_PAYDAY to emptyList()
    AlertKind.PAY_FIRST -> TextKey.ALERT_WHY_PAY_FIRST to emptyList()
    AlertKind.BILL_JUMP -> TextKey.ALERT_WHY_BILL_JUMP to emptyList()
    AlertKind.DUP_SUBS -> TextKey.ALERT_WHY_DUP_SUBS to emptyList()
    AlertKind.BIG_ONE -> TextKey.ALERT_WHY_BIG_ONE to emptyList()
    AlertKind.GOAL_NEAR -> TextKey.ALERT_WHY_GOAL_NEAR to emptyList()
    AlertKind.WEEKLY_SUMMARY -> TextKey.ALERT_WHY_WEEKLY to emptyList()
    AlertKind.SMS_CONFIRM -> TextKey.ALERT_WHY_SMS_CONFIRM to emptyList()
    AlertKind.NEW_DEVICE_LOGIN, AlertKind.LINKED_ACCOUNT_ACTIVITY, AlertKind.MONTHLY_EMAIL -> TextKey.ALERT_WHY_SERVER to emptyList()
}

private fun factorText(f: AlertFactor, decision: AlertDecision): String = when (f) {
    AlertFactor.URGENT -> uiText(TextKey.ALERT_FACTOR_URGENT)
    AlertFactor.MONEY_COMING_IN -> uiText(TextKey.ALERT_FACTOR_MONEY_IN)
    AlertFactor.BIG_FOR_MONTH -> uiText(TextKey.ALERT_FACTOR_BIG)
    AlertFactor.SMALL_FOR_MONTH -> uiText(TextKey.ALERT_FACTOR_SMALL)
    AlertFactor.NEEDS_DECISION -> uiText(TextKey.ALERT_FACTOR_DECISION)
    AlertFactor.YOU_OPEN_THESE -> uiText(TextKey.ALERT_FACTOR_OPEN)
    AlertFactor.YOU_SKIP_THESE -> uiText(TextKey.ALERT_FACTOR_SKIP)
    AlertFactor.USUAL_HOUR_NOW -> uiText(TextKey.ALERT_FACTOR_USUAL_NOW)
    AlertFactor.WAIT_FOR_USUAL_HOUR -> uiText(TextKey.ALERT_FACTOR_WAIT, (decision.deliverAt?.hour ?: 0).toString())
    AlertFactor.HOURS_NOT_LEARNED -> uiText(TextKey.ALERT_FACTOR_NOT_LEARNED)
    AlertFactor.BUNDLED -> uiText(TextKey.ALERT_FACTOR_BUNDLED)
    AlertFactor.PAGE_ONLY -> uiText(TextKey.ALERT_FACTOR_PAGE_ONLY)
    AlertFactor.GROUP_OFF -> uiText(TextKey.ALERT_FACTOR_GROUP_OFF)
}

/** «ليه اتبعت دلوقتي» — جوه التطبيق بس (§61): السبب الأساسي من النوع، وبعده كل عامل غيّر القرار بالترتيب. */
fun alertReasonText(decision: AlertDecision): String {
    val (key, args) = whyKey(decision.kind)
    val parts = listOf(uiText(key, *args.toTypedArray())) + decision.factors.map { factorText(it, decision) }
    return parts.joinToString(" · ")
}

fun alertGroupLabel(group: AlertGroup): String = uiText(
    when (group) {
        AlertGroup.DUES -> TextKey.ALERT_GROUP_DUES
        AlertGroup.BUDGET -> TextKey.ALERT_GROUP_BUDGET
        AlertGroup.QUESTIONS -> TextKey.ALERT_GROUP_QUESTIONS
        AlertGroup.ZAKAT -> TextKey.ALERT_GROUP_ZAKAT
        AlertGroup.BALANCE -> TextKey.ALERT_GROUP_BALANCE
        AlertGroup.PROFILE -> TextKey.ALERT_GROUP_PROFILE
        AlertGroup.OCCASIONS -> TextKey.ALERT_GROUP_OCCASIONS
        AlertGroup.SECURITY -> TextKey.ALERT_GROUP_SECURITY
        AlertGroup.LINKED_ACCOUNTS -> TextKey.ALERT_GROUP_LINKED_ACCOUNTS
        AlertGroup.EMAIL -> TextKey.ALERT_GROUP_EMAIL
        AlertGroup.INCOME -> TextKey.ALERT_GROUP_INCOME
        AlertGroup.ADVISOR -> TextKey.ALERT_GROUP_ADVISOR
        AlertGroup.BANK_SMS -> TextKey.ALERT_GROUP_BANK_SMS
    },
)
