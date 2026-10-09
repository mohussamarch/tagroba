package app.masroufy.ui.screens.investment

import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.usecase.AlertInboxView

/**
 * «التحليلات الذكية» (`Advisor`): كروت مجموعة «المساعد المالي» من صفحة الإشعارات (`RunAlertEngine.inbox` — نفس اللي المحرك قرّره:
 * العنوان والشرح من المساعد، و«لماذا؟» = «ليه اتبعت دلوقتي»، وإمتى = طريقة التوصيل). من غير أي حساب هنا.
 * ⚠️ «ساكتة الآن — ومتى تظهر» (الأفكار اللي ما طلعتش وسبب سكوتها) مالهاش حالة استخدام ⇒ مش مرسومة (HANDOVER).
 */
enum class AdvisorIcon { TREND, ALERT, PIGGY, RECEIPT, CALENDAR }

/** فعل الكارت ⇒ الشاشة اللي بتتفتح (النموذج: خطط الادخار · الميزانيات · العمليات). */
enum class AdvisorLink(val key: TextKey) {
    GOALS(TextKey.ADVISOR_SCREEN_OPEN_GOALS),
    BUDGETS(TextKey.ADVISOR_SCREEN_OPEN_BUDGETS),
    OPS(TextKey.ADVISOR_SCREEN_OPEN_OPS),
}

data class AdvisorCard(
    val threadKey: String,
    val title: String,
    val body: String,
    val icon: AdvisorIcon,
    /** فكرة حلوة (الخطة قربت · ادفع لنفسك) ⇒ أخضر بدل الكهرماني. */
    val good: Boolean,
    val link: AdvisorLink?,
    val whenText: String,
    val why: String,
)

/** [enabled] = مجموعة المساعد شغالة. [cards] فاضية وشغالة ⇒ «المساعد ساكت الآن». */
data class AdvisorUi(val enabled: Boolean, val cards: List<AdvisorCard>) {
    val quiet: Boolean get() = enabled && cards.isEmpty()
}

/**
 * [dismissed] = مواضيع الإشعارات اللي المستخدم مسحها بـ«×» (قرار المالك 2026-10-09: الإشعار الممسوح بيشيل كارته) — **المنطق في فرع
 * `assistant-engine`**؛ هنا مكان التوصيل بس (فاضي لحد الدمج).
 */
fun advisorUi(inbox: List<AlertInboxView>, enabled: Boolean, dismissed: Set<String> = emptySet()): AdvisorUi {
    if (!enabled) return AdvisorUi(false, emptyList())
    val cards = inbox.filter { it.entry.kind.group == AlertGroup.ADVISOR && it.entry.threadKey !in dismissed }.map { v ->
        val k = v.entry.kind
        AdvisorCard(
            threadKey = v.entry.threadKey,
            title = v.entry.title,
            body = v.entry.body,
            icon = iconOf(k),
            good = k == AlertKind.GOAL_NEAR || k == AlertKind.PAY_FIRST,
            link = linkOf(k),
            whenText = whenText(v.entry.decision.delivery),
            why = v.reason,
        )
    }
    return AdvisorUi(true, cards)
}

private fun iconOf(k: AlertKind): AdvisorIcon = when (k) {
    AlertKind.HABIT_VS_GOAL, AlertKind.UNUSUAL_SPEND -> AdvisorIcon.TREND
    AlertKind.PAY_FIRST, AlertKind.GOAL_NEAR -> AdvisorIcon.PIGGY
    AlertKind.BILL_JUMP, AlertKind.DUP_SUBS, AlertKind.BIG_ONE -> AdvisorIcon.RECEIPT
    AlertKind.WEEKLY_SUMMARY -> AdvisorIcon.CALENDAR
    else -> AdvisorIcon.ALERT
}

private fun linkOf(k: AlertKind): AdvisorLink? = when (k) {
    AlertKind.HABIT_VS_GOAL, AlertKind.PAY_FIRST, AlertKind.GOAL_NEAR -> AdvisorLink.GOALS
    AlertKind.CAP_PACE -> AdvisorLink.BUDGETS
    AlertKind.UNUSUAL_SPEND, AlertKind.BILL_JUMP, AlertKind.DUP_SUBS, AlertKind.BIG_ONE, AlertKind.WEEKLY_SUMMARY -> AdvisorLink.OPS
    else -> null
}

private fun whenText(d: AlertDelivery): String = uiText(
    when (d) {
        AlertDelivery.DIGEST -> TextKey.ADVISOR_SCREEN_WHEN_DIGEST
        AlertDelivery.AT_USUAL_TIME -> TextKey.ADVISOR_SCREEN_WHEN_USUAL
        AlertDelivery.SEND_NOW -> TextKey.ADVISOR_SCREEN_WHEN_NOW
        AlertDelivery.INBOX_ONLY -> TextKey.ADVISOR_SCREEN_WHEN_PAGE
    },
)
