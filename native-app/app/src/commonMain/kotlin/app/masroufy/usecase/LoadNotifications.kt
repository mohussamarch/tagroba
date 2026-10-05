package app.masroufy.usecase

import app.masroufy.core.CategoryBudgetForNotice
import app.masroufy.core.NotificationEvent
import app.masroufy.core.buildBudgetNotifications
import app.masroufy.core.filterUnseen
import app.masroufy.core.receiptFor
import app.masroufy.core.staleReceipts
import app.masroufy.port.Clock
import app.masroufy.port.NotificationReceiptRepository

/**
 * LoadNotifications — نقل `loadNotifications.ts`: التنبيهات اللي ما اتشافتش من حالة الميزانية.
 * ⚠️ مفيش دفع من سيرفر (باقة Spark، CLAUDE.md #12): دي تنبيهات بتظهر جوه التطبيق لما يتفتح، وبس.
 * المدخل ناتج شاشة الميزانية نفسه — ما بيحسبش مصروف ولا سقف من جديد، فمستحيل يخالف رقم الشاشة.
 */

/** `all` = كل اللي متولد دلوقتي (لصفحة الإشعارات)؛ `unseen` = اللي المستخدم ماشافهوش. */
data class NotificationsView(val unseen: List<NotificationEvent>, val all: List<NotificationEvent>)

/**
 * تنبيهات الميزانية من ناتج شاشة الميزانية — مشتركة بين الصفحة دي ومحرك التنبيهات (§61).
 * المصروف مش معروف ⇒ ولا تنبيه (§9 — رقم ناقص مش أساس لتخويف).
 */
fun budgetNotificationEvents(budget: BudgetScreenData): List<NotificationEvent> {
    val nameOf = budget.categories.associate { it.id to it.name }
    val limitOf = budget.categoryBudgets.associateBy { it.categoryId }
    return buildBudgetNotifications(
        periodStart = budget.period.start,
        totalStatus = budget.totalStatus,
        totalThresholdPercent = budget.budget?.thresholdPercent,
        // التصنيف من غير سقف ما بيدخلش أصلًا (spec/06)، واللي المستخدم قافل تنبيهه كمان
        categories = budget.lines.mapNotNull { line ->
            val status = line.status ?: return@mapNotNull null
            val limit = limitOf[line.categoryId]
            if (limit != null && !limit.notifyEnabled) return@mapNotNull null
            CategoryBudgetForNotice(line.categoryId, nameOf[line.categoryId] ?: line.categoryId, status, limit?.thresholdPercent)
        },
        spentKnown = budget.spentKnown,
    )
}

class LoadNotifications(private val receipts: NotificationReceiptRepository, private val clock: Clock) {
    /** بيبني التنبيهات الحالية من غير ما يعلّم حاجة مقروءة. */
    suspend fun load(budget: BudgetScreenData): NotificationsView {
        val all = budgetNotificationEvents(budget)
        return NotificationsView(filterUnseen(all, receipts.listAll()), all)
    }

    /** الإيصال هو اللي بيمنع ظهورها تاني، فالتعليم **بعد** ما المستخدم يشوفها فعلًا مش قبل. */
    suspend fun markSeen(events: List<NotificationEvent>) {
        if (events.isEmpty()) return
        val now = clock.nowIso()
        receipts.saveMany(events.map { receiptFor(it, now) })
    }

    /** تنضيف إيصالات الفترات اللي فاتت — اختياري، وفشله ما يمنعش التنبيهات. */
    suspend fun pruneOldReceipts(keepFromPeriodStart: String): Int {
        val stale = staleReceipts(receipts.listAll(), keepFromPeriodStart)
        if (stale.isEmpty()) return 0
        receipts.deleteMany(stale.map { it.eventKey })
        return stale.size
    }
}
