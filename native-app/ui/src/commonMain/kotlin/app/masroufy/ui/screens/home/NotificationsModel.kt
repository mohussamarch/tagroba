package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.ui.graphics.Color
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.IsoDate
import app.masroufy.core.SystemNotice
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.systemNoticeFor
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.usecase.AlertInboxView

/**
 * صفحة الإشعارات (`Notifications` — §61 · §74 · آخر §76): من `RunAlertEngine.inbox` (الأحدث الأول، و«ليه اتبعت دلوقتي» جاهزة من المنطق).
 * قسمين زي النموذج: **«بانتظار قرارك»** (الأنواع اللي محتاجة قرار — `AlertKind.needsDecision`) ثم **«جديد»** (الباقي). كل سطر: الرمز بلون مجموعته ·
 * العنوان · التفاصيل · «لماذا الآن» · إمتى · نقطة «جديد» · «مقفولة» لو مجموعته مقفولة · اسم البلد لو موجود · «×».
 */
enum class NotifIcon(val icon: Lucide, val color: Color) {
    SMS(Lucide.MESSAGE_SQUARE_TEXT, Ink.primary),
    MOVE(Lucide.ARROW_LEFT_RIGHT, Ink.transfer),
    BUDGET(Lucide.CIRCLE_ALERT, Ink.expense),
    DUE(Lucide.RECEIPT, Ink.focus),
    DEBT(Lucide.HAND_COINS, Color(0xFFA55060)),
    OTHER(Lucide.BELL, Ink.muted),
}

/** فين بيودّي الضغط على السطر (الشاشات اللي لسه ما اتسجلتش ⇒ أقرب تبويب ليها — ⚠️ المسارات الدقيقة بتتوصل وقت الدمج). */
enum class NotifTarget { BANK_SMS, REVIEW, OPERATIONS, PEOPLE, INVESTMENT, PROFILE, MORE }

data class NotifRow(
    val threadKey: String,
    val title: String,
    val body: String,
    val why: String,
    val whenText: String,
    val icon: NotifIcon,
    val unread: Boolean,
    val muted: Boolean,
    val spaceLabel: String?,
    val target: NotifTarget,
)

data class NotifSection(val title: String, val rows: List<NotifRow>)

fun iconOf(kind: AlertKind): NotifIcon = when (kind.group) {
    AlertGroup.BANK_SMS -> NotifIcon.SMS
    AlertGroup.QUESTIONS -> NotifIcon.MOVE
    AlertGroup.BUDGET -> NotifIcon.BUDGET
    AlertGroup.DUES -> if (kind == AlertKind.DUE_OVERDUE) NotifIcon.DEBT else NotifIcon.DUE
    AlertGroup.OCCASIONS -> NotifIcon.DEBT
    AlertGroup.ADVISOR, AlertGroup.ZAKAT, AlertGroup.INCOME -> NotifIcon.DUE
    else -> NotifIcon.OTHER
}

fun targetOf(kind: AlertKind): NotifTarget = when (kind.group) {
    AlertGroup.BANK_SMS -> NotifTarget.BANK_SMS
    AlertGroup.QUESTIONS -> NotifTarget.REVIEW
    AlertGroup.BUDGET, AlertGroup.DUES, AlertGroup.BALANCE -> NotifTarget.OPERATIONS
    AlertGroup.OCCASIONS -> NotifTarget.PEOPLE
    AlertGroup.ZAKAT, AlertGroup.ADVISOR -> NotifTarget.INVESTMENT
    AlertGroup.PROFILE -> NotifTarget.PROFILE
    else -> NotifTarget.MORE
}

/** «اليوم» · «أمس» · «قبل يومين» · «قبل 3–10 أيام» · «قبل 11+ يومًا» — من تاريخ السطر (أول 10 حروف من `createdAt`). */
fun whenText(createdAt: String, today: IsoDate): String {
    val day = createdAt.take(10)
    if (!isValidIsoDate(day)) return ""
    val ago = daysBetween(day, today)
    return when {
        ago <= 0 -> t(UiKey.NOTIFICATIONS_TODAY)
        ago == 1 -> t(UiKey.NOTIFICATIONS_YESTERDAY)
        ago == 2 -> t(UiKey.NOTIFICATIONS_TWO_DAYS)
        ago <= 10 -> t(UiKey.NOTIFICATIONS_FEW_DAYS, sentenceNumber(ago))
        else -> t(UiKey.NOTIFICATIONS_MANY_DAYS, sentenceNumber(ago))
    }
}

/**
 * القسمين من الصفحة. [unread] = مفاتيح السطور اللي لسه ما اتقرتش (نفس قراية الجرس — `BellState.items`)، و[gone] = اللي اتمسح بـ«×».
 * قسم فاضي ما بيظهرش.
 */
fun notificationSections(inbox: List<AlertInboxView>, unread: Set<String>, gone: Set<String>, today: IsoDate): List<NotifSection> {
    val rows = inbox.filter { it.entry.threadKey !in gone }.map { v ->
        val e = v.entry
        NotifRow(
            threadKey = e.threadKey, title = e.title, body = e.body, why = v.reason, whenText = whenText(e.createdAt, today),
            icon = iconOf(e.kind), unread = e.threadKey in unread, muted = v.muted, spaceLabel = e.spaceLabel, target = targetOf(e.kind),
        )
    }
    val decision = inbox.filter { it.entry.kind.needsDecision }.map { it.entry.threadKey }.toSet()
    return listOf(
        NotifSection(t(UiKey.NOTIFICATIONS_WAITING), rows.filter { it.threadKey in decision }),
        NotifSection(t(UiKey.NOTIFICATIONS_NEW), rows.filter { it.threadKey !in decision }),
    ).filter { it.rows.isNotEmpty() }
}

/**
 * «هكذا يظهر على شاشة القفل»: نص الشريط العام (`systemNoticeFor` — من غير مبالغ ولا أسامي ولا عدد) لأول سطر ظاهر مش مقفول.
 * null = مفيش سطر ⇒ الصندوق ما بيظهرش.
 */
fun lockPreviewOf(inbox: List<AlertInboxView>, gone: Set<String>): SystemNotice? =
    inbox.firstOrNull { !it.muted && it.entry.threadKey !in gone }?.let { systemNoticeFor(it.entry.kind, it.entry.flow) }
