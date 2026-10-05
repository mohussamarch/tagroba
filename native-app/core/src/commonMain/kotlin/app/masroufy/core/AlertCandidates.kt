package app.masroufy.core

/**
 * المرشحين للتنبيه النهارده من البيانات الموجودة (OVERRIDES §61) — دوال نقية.
 * كل واحد **بيختفي لوحده لما يتحل**: القسط اتربط بدفعة ⇒ مش في «المستحقات» ⇒ مالوش مرشح؛ الطرف اتقرر ⇒ مش سؤال؛ إلخ.
 * العناوين والتفاصيل هنا (فيها مبالغ وأسامي) **للصفحة جوه التطبيق بس** — الشريط بياخد [systemNoticeFor].
 */

/** المستحقات: «قرّب» (خلال [DUE_SOON_DAYS]) ⇒ النهارده ⇒ «عدّى». [monthScale] = سقف الشهر بعملة البند (null = مش معروف). */
fun dueAlertCandidates(items: List<DueItem>, today: IsoDate, monthScale: (Currency) -> Halalas?): List<AlertCandidate> =
    items.mapNotNull { item ->
        val stage = alertStage(item.dueAt, today, DUE_SOON_DAYS) ?: return@mapNotNull null
        val days = daysBetween(today, item.dueAt)
        val title = when (stage) {
            AlertStage.SOON -> uiText(TextKey.ALERT_DUE_SOON_TITLE, item.title, days.toString())
            AlertStage.TODAY -> uiText(TextKey.ALERT_DUE_TODAY_TITLE, item.title)
            AlertStage.OVERDUE -> uiText(TextKey.ALERT_DUE_OVERDUE_TITLE, item.title, (-days).toString())
        }
        val bodyKey = if (item.flow == DueFlow.RECEIVE) TextKey.ALERT_DUE_BODY_RECEIVE else TextKey.ALERT_DUE_BODY_PAY
        AlertCandidate(
            kind = dueKindFor(stage),
            threadKey = "due|${item.source.wire}|${item.sourceId}|${item.dueAt}|${item.flow.wire}",
            title = title,
            body = uiText(bodyKey, formatMoney(item.amountMinor, item.currency), item.dueAt),
            flow = item.flow,
            amountMinor = item.amountMinor,
            monthScaleMinor = monthScale(item.currency),
        )
    }

/**
 * الميزانية من نفس منطق `buildBudgetNotifications` (اللي **ما بيطلّعش حاجة على رقم ناقص** — §9). عتبة المستخدم ثم 100%:
 * نفس الموضوع (الفترة + الهدف) ⇒ «عدّى السقف» بيحل محل «عدّى العتبة» في الصفحة.
 */
fun budgetAlertCandidates(events: List<NotificationEvent>): List<AlertCandidate> =
    // عتبتين لنفس الهدف في نفس اليوم ⇒ الأعلى بس (عبور 80% و100% مع بعض = تنبيه واحد)
    events.groupBy { "budget|${it.periodStart}|${it.categoryId?.let { id -> "cat:$id" } ?: "total"}" }.map { (thread, same) ->
        val e = same.maxBy { it.threshold ?: 0 }
        AlertCandidate(
            kind = if ((e.threshold ?: 0) >= 100) AlertKind.BUDGET_EXCEEDED else AlertKind.BUDGET_THRESHOLD,
            threadKey = thread,
            title = e.title,
            body = e.body,
        )
    }

/** «زون التحويلات»: طرف التحويلات معاه كترت ومالوش قرار (§60). */
fun transferQuestionCandidates(questions: List<SuspiciousParty>): List<AlertCandidate> = questions.map { q ->
    AlertCandidate(
        kind = AlertKind.TRANSFER_QUESTION,
        threadKey = "transfer|${q.party.key}",
        title = uiText(TextKey.ALERT_TRANSFER_TITLE, q.party.label),
        body = uiText(TextKey.ALERT_TRANSFER_BODY, q.count.toString(), q.month),
    )
}

/**
 * الزكاة: قبل الميعاد بـ[ZAKAT_REMINDER_DAYS] ⇒ النهارده ⇒ «عدّى» لو الحساب لسه ما اتثبتش أو فيه باقي.
 * [remainingMinor] = الباقي بعد الدفعات (للسنة المثبّتة)؛ اتثبتت واتدفعت كلها ⇒ مفيش تنبيه (حتى لو اتعجّلت قبل الميعاد).
 */
fun zakatAlertCandidate(year: ZakatYear, remainingMinor: Halalas?, today: IsoDate): AlertCandidate? {
    if (year.closed && remainingMinor != null && remainingMinor <= 0) return null
    val stage = alertStage(year.dueAt, today, ZAKAT_REMINDER_DAYS) ?: return null
    val title = when (stage) {
        AlertStage.SOON -> uiText(TextKey.ALERT_ZAKAT_SOON_TITLE, daysBetween(today, year.dueAt).toString())
        AlertStage.TODAY -> uiText(TextKey.ALERT_ZAKAT_TODAY_TITLE)
        AlertStage.OVERDUE -> uiText(TextKey.ALERT_ZAKAT_OVERDUE_TITLE)
    }
    return AlertCandidate(zakatKindFor(stage), "zakat|${year.id}", title, uiText(TextKey.ALERT_ZAKAT_BODY, year.dueAt))
}

/** المطابقة: سطور رصيدها مختلف عن الكشف. الموضوع = المحفظة + أول اختلاف ⇒ اختلاف جديد = تنبيه جديد. */
fun balanceMismatchCandidate(walletId: Id, walletName: String, mismatches: List<BalanceMismatch>): AlertCandidate? {
    val first = mismatches.minByOrNull { it.index } ?: return null
    return AlertCandidate(
        AlertKind.BALANCE_MISMATCH, "balance|$walletId|${first.date}",
        uiText(TextKey.ALERT_BALANCE_TITLE, walletName), uiText(TextKey.ALERT_BALANCE_BODY, mismatches.size.toString(), first.date),
    )
}

/** كارت «ملفك X%» (§63) — في الصفحة بس، وبيختفي لما الملف يكمل. null = النسبة مش متحسبة. */
fun profileCompletionCandidate(percent: Int?): AlertCandidate? {
    if (percent == null || percent >= 100) return null
    val p = percent.coerceAtLeast(0)
    return AlertCandidate(AlertKind.PROFILE_INCOMPLETE, "profile|$p", uiText(TextKey.ALERT_PROFILE_TITLE, p.toString()), uiText(TextKey.ALERT_PROFILE_BODY))
}

/**
 * مناسبة الشخص (§64): «قرّب» من [occasionSoonDays] يوم (مدتك أو أسبوع) ⇒ النهارده. مفيش «عدّى» — بعدها المرة الجاية
 * بموضوع جديد (الموضوع = المناسبة + يومها). السطر جوه التطبيق فيه اسم الشخص، ولو نقّطك أو نقّطته قبل كده
 * بيتكتب **للمعلومية** («نقّطك 2,000 في فرحك») — آخر مرة في كل اتجاه. [personName] null = مناسبتك إنت.
 * الشريط بياخد نص عام من النوع بس («عندك مناسبة قريبة») زي كل التنبيهات.
 */
fun occasionAlertCandidate(
    occasion: Occasion,
    today: IsoDate,
    personName: String?,
    eventName: String?,
    badges: List<GiftBadge> = emptyList(),
): AlertCandidate? {
    val next = nextOccurrence(occasion, today) ?: return null
    val days = daysBetween(today, next)
    if (days > occasionSoonDays(occasion)) return null
    val what = occasionTitle(occasion, personName, eventName)
    val kind = if (days == 0) AlertKind.OCCASION_TODAY else AlertKind.OCCASION_SOON
    val title = if (days == 0) uiText(TextKey.ALERT_OCCASION_TODAY_TITLE, what) else uiText(TextKey.ALERT_OCCASION_SOON_TITLE, what, days.toString())
    val when_ = uiText(if (personName == null && occasion.sourceEventId != null) TextKey.ALERT_OCCASION_BODY_OWN else TextKey.ALERT_OCCASION_BODY, next)
    val reciprocity = listOf(Direction.IN, Direction.OUT).mapNotNull { d -> badges.firstOrNull { it.direction == d } }.map(::giftBadgeText)
    return AlertCandidate(kind, "occasion|${occasion.id}|$next", title, (listOf(when_) + reciprocity).joinToString(" · "))
}
