package app.masroufy.data

import app.masroufy.core.ALERT_INBOX_GROUP
import app.masroufy.core.ALERT_RECEIPTS_GROUP
import app.masroufy.core.ALERT_SETTINGS_GROUP
import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertFactor
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.AlertKind
import app.masroufy.core.DueFlow
import app.masroufy.core.LocalMoment
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.AlertInboxEntry

/**
 * محرك التنبيهات على فايربيز (OVERRIDES §61 — رد المالك (١)، جلسة 18): ثلاث مجموعات **على مستوى الحساب**، في كوتلن بس.
 * التعلّم (ساعاتك وتفاعلك) **مالوش محوّل هنا عن قصد** — على الجوال بس (موديول الجهاز).
 * معرّف مستند الصفحة والإيصال = المفتاح نفسه متشفّر (`receiptDocId`) — المواضيع فيها «|» و«:» وممكن «/» من أسامي الأطراف.
 */
object AlertCodecs {
    /** مستند لكل مجموعة (المعرّف = اسمها) ⇒ قفلها على جوال بيقفلها على التاني، والتاني بيكتب فوق نفس المستند. */
    val alertSettings: DocCodec<AlertGroupSetting> = codec(
        ALERT_SETTINGS_GROUP, { it.group.wire },
        { s -> doc { req("group", s.group.wire); req("enabled", s.enabled) } },
        { d -> AlertGroupSetting(d.wire("group") { AlertGroup.fromWire(it)!! }, d.bool("enabled")) },
    )

    /** إيصال «اتبعت» — نفس فكرة `notificationReceipts` بتاع التطبيق الحالي بس مجموعة لوحدها (ما يتربطش بيه بالغلط). */
    val alertReceipts: DocCodec<NotificationReceipt> = codec(
        ALERT_RECEIPTS_GROUP, { receiptDocId(it.eventKey) },
        { n -> doc { req("eventKey", n.eventKey); req("periodStart", n.periodStart); req("sentAt", n.sentAt) } },
        { r -> NotificationReceipt(r.str("eventKey"), null, r.str("periodStart"), r.str("sentAt")) },
    )

    /**
     * سطر في صفحة الإشعارات — واحد لكل موضوع. «ليه اتبعت كده» (`decision`) بيتخزن بأجزائه (مش كنص) عشان يتكتب بلغة الجوال
     * اللي بيعرضه. العنوان والتفاصيل نص اتكتب وقت التنبيه (فيه مبالغ وأسامي — بيانات المستخدم نفسه في حسابه)، وأي رقم طويل
     * (5 أرقام ورا بعض أو أكتر) بيتقص لآخر 4 قبل ما يتكتب.
     */
    val alertInbox: DocCodec<AlertInboxEntry> = codec(
        ALERT_INBOX_GROUP, { receiptDocId(it.threadKey) },
        { e ->
            doc {
                req("threadKey", e.threadKey); req("eventKey", e.eventKey); req("kind", e.kind.wire); req("flow", e.flow.wire)
                // النص الحر بيتقص زي العمليات (CLAUDE.md #11) — المفاتيح لأ (قصها كان هيخلّي موضوعين مختلفين واحد)
                req("title", sanitizeAccountNumbers(e.title)); req("body", sanitizeAccountNumbers(e.body))
                req("delivery", e.decision.delivery.wire); opt("deliverAtDate", e.decision.deliverAt?.date); opt("deliverAtHour", e.decision.deliverAt?.hour)
                req("inAppWindow", e.decision.inAppWindow); req("factors", e.decision.factors.map { it.name.lowercase() })
                req("createdAt", e.createdAt); opt("openedAt", e.openedAt); opt("spaceLabel", e.spaceLabel)
            }
        },
        { d ->
            val kind = d.wire("kind") { AlertKind.fromWire(it)!! }
            val factors = d.strings("factors").orEmpty().map { f ->
                AlertFactor.entries.firstOrNull { it.name.lowercase() == f } ?: throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, ALERT_INBOX_GROUP, "factors", f))
            }
            val at = d.strOrNull("deliverAtDate")?.let { date ->
                runCatching { LocalMoment(date, d.int("deliverAtHour")) }.getOrElse { throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, ALERT_INBOX_GROUP, "deliverAtHour", date)) }
            }
            AlertInboxEntry(
                d.str("threadKey"), d.str("eventKey"), kind, d.wire("flow") { w -> DueFlow.entries.first { it.wire == w } }, d.str("title"), d.str("body"),
                AlertDecision(kind, d.wire("delivery") { w -> AlertDelivery.entries.first { it.wire == w } }, at, d.bool("inAppWindow"), factors),
                d.str("createdAt"), d.strOrNull("openedAt"), d.strOrNull("spaceLabel"),
            )
        },
    )

    val all: List<DocCodec<*>> = listOf(alertSettings, alertInbox, alertReceipts)
}
