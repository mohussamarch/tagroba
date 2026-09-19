package app.masroufy.core

/**
 * التنبيهات جوه التطبيق + قفل البصمة + الشعارات — نقل `notifications.ts` + `appLock.ts` + `merchantLogo.ts`.
 * التنبيه **ما بيتولدش إلا من رقم حطه المستخدم** (spec/06)، وما بيتكررش لنفس الحدث (الإيصال).
 */
data class NotificationEvent(
    /** الفترة + الهدف + العتبة — عبور ٨٠٪ حدث، وعبور ١٠٠٪ بعده حدث تاني. */
    val eventKey: String,
    /** "budget_total" / "budget_category" / "recurring_due". */
    val kind: String,
    /** "info" / "warn" / "over". */
    val severity: String,
    val title: String,
    val body: String,
    val periodStart: IsoDate,
    val categoryId: Id? = null,
    val recurringId: Id? = null,
    val threshold: Int? = null,
)

data class NotificationReceipt(
    val eventKey: String,
    val threshold: Int?,
    val periodStart: IsoDate,
    val sentAt: String,
    val categoryId: Id? = null,
    val recurringId: Id? = null,
)

private val HARD_THRESHOLDS = listOf(100)

private fun severityFor(threshold: Int) = when {
    threshold >= 100 -> "over"
    threshold >= 90 -> "warn"
    else -> "info"
}

/** **كل** عتبة اتعدّت، مش الأعلى بس — عشان تنبيه ٨٠٪ ما يضيعش لو المستخدم ما فتحش أيام. */
private fun crossedThresholds(status: BudgetStatus, userThreshold: Int?): List<Int> {
    val candidates = LinkedHashSet(HARD_THRESHOLDS)
    if (userThreshold != null && userThreshold > 0) candidates += userThreshold
    return candidates.filter { status.usedTenthPercent >= it * 10L }.sorted()
}

data class CategoryBudgetForNotice(val categoryId: Id, val categoryName: String, val status: BudgetStatus, val thresholdPercent: Int?)

private fun describeStatus(status: BudgetStatus): String {
    val spent = formatMoney(status.spentMinor)
    val limit = formatMoney(status.limitMinor)
    return if (status.remainingMinor < 0) "صرفت $spent من $limit — زيادة ${formatMoney(-status.remainingMinor)}"
    else "صرفت $spent من $limit — فاضل ${formatMoney(status.remainingMinor)}"
}

/** تنبيهات الميزانية. المصروف مش معروف (عمليات من غير نوع) ⇒ مفيش تنبيه على رقم ناقص (CLAUDE.md #10). */
fun buildBudgetNotifications(
    periodStart: IsoDate,
    totalStatus: BudgetStatus?,
    totalThresholdPercent: Int?,
    categories: List<CategoryBudgetForNotice>,
    spentKnown: Boolean,
): List<NotificationEvent> {
    if (!spentKnown) return emptyList()
    val events = mutableListOf<NotificationEvent>()
    if (totalStatus != null) {
        for (t in crossedThresholds(totalStatus, totalThresholdPercent)) {
            events += NotificationEvent(
                "$periodStart|total|$t", "budget_total", severityFor(t),
                if (t >= 100) "عدّيت الميزانية" else "وصلت $t٪ من الميزانية", describeStatus(totalStatus), periodStart, threshold = t,
            )
        }
    }
    for (c in categories) {
        for (t in crossedThresholds(c.status, c.thresholdPercent)) {
            events += NotificationEvent(
                "$periodStart|cat:${c.categoryId}|$t", "budget_category", severityFor(t),
                if (t >= 100) "عدّيت سقف «${c.categoryName}»" else "«${c.categoryName}» وصل $t٪", describeStatus(c.status), periodStart,
                categoryId = c.categoryId, threshold = t,
            )
        }
    }
    return events.sortedByDescending { it.threshold ?: 0 }
}

fun filterUnseen(events: List<NotificationEvent>, receipts: List<NotificationReceipt>): List<NotificationEvent> {
    val seen = receipts.map { it.eventKey }.toSet()
    return events.filter { it.eventKey !in seen }
}

fun receiptFor(event: NotificationEvent, sentAt: String) = NotificationReceipt(
    event.eventKey, event.threshold, event.periodStart, sentAt,
    event.categoryId?.ifEmpty { null }, event.recurringId?.ifEmpty { null },
)

/** إيصالات الفترات اللي عدّت — حِمل من غير فايدة. */
fun staleReceipts(receipts: List<NotificationReceipt>, keepFromPeriodStart: IsoDate): List<NotificationReceipt> =
    receipts.filter { it.periodStart < keepFromPeriodStart }

/* ───────────────────────── قفل البصمة (OVERRIDES §21) ───────────────────────── */

const val LOCK_AFTER_BACKGROUND_MS = 5 * 60_000L

/** مقفول لحد ما يتشغل؛ أول فتح بيقفل؛ ٥ دقايق في الخلفية بيقفل؛ الساعة رجعت لورا ⇒ بيقفل احتياطًا. */
fun shouldLock(enabled: Boolean, hiddenAt: Long?, now: Long): Boolean {
    if (!enabled) return false
    if (hiddenAt == null) return true
    if (now < hiddenAt) return true
    return now - hiddenAt >= LOCK_AFTER_BACKGROUND_MS
}

/* ───────────────────────── شعارات التجار (OVERRIDES §25.1) ───────────────────────── */

data class MerchantLogoEntry(val names: List<String>, val file: String? = null, val domain: String? = null)

sealed interface LogoSource {
    data class Bundled(val file: String) : LogoSource
    data class Online(val domain: String) : LogoSource
}

private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

/** أول تعريف للاسم هو اللي بيفضل. */
fun buildLogoIndex(entries: List<MerchantLogoEntry>): Map<String, MerchantLogoEntry> {
    val index = LinkedHashMap<String, MerchantLogoEntry>()
    for (e in entries) for (name in e.names) {
        val key = normalizeText(name)
        if (key.isNotEmpty() && key !in index) index[key] = e
    }
    return index
}

/** الملف اللي جوه التطبيق بيغلب دايمًا؛ الأونلاين بس لو اتفعّل والدومين سليم. */
fun logoSourceFor(merchantName: String?, index: Map<String, MerchantLogoEntry>, onlineEnabled: Boolean, bundledFiles: Set<String>): LogoSource? {
    val entry = index[normalizeText(merchantName ?: "")] ?: return null
    if (!entry.file.isNullOrEmpty() && entry.file in bundledFiles) return LogoSource.Bundled(entry.file)
    val domain = entry.domain?.let(JsText::trim)?.lowercase()
    if (onlineEnabled && !domain.isNullOrEmpty() && DOMAIN.matches(domain)) return LogoSource.Online(domain)
    return null
}
