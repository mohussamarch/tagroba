package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import app.masroufy.core.EVENT_SHARE_WHOLE
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.latinizeDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.EventDetail

/** حدث في اللوحة: الاسم + «١٨ أكتوبر، حدث خالد» أو «حدثك». */
data class EventChoice(val id: Id, val name: String, val sub: String)

/**
 * الحدث المربوط بالعملية دلوقتي بنسبته (الربط بيتخزن بنسبة 1..100 — §64) و[shareMinor] نصيبه بالهللة **من حالة الاستخدام**
 * (`ManageEvents.detail` ⇒ `EventLinkedTransaction.shareMinor`) — الشاشة ما بتحسبوش.
 */
data class EventLinkNow(val eventId: Id, val name: String, val sharePercent: Int, val shareMinor: Halalas)

/** الأحداث الشغالة (المؤرشف ما بيتعرضش — النموذج) من تفاصيلها (`ManageEvents.detail`). */
fun eventChoices(details: List<EventDetail>): List<EventChoice> = details.filter { !it.event.archived }.map { d ->
    val whose = d.hostName?.let { t(UiKey.LINK_PROJECT_EVENT_HOSTED, it) } ?: t(UiKey.LINK_PROJECT_EVENT_MINE)
    EventChoice(d.event.id, d.event.name, t(UiKey.LINK_PROJECT_EVENT_WHEN, dayMonth(d.event.date), whose))
}

/** الحدث اللي العملية مربوطة بيه (أي دور) — العملية بحدث واحد بس. */
fun eventLinkOf(details: List<EventDetail>, transactionId: Id): EventLinkNow? {
    for (d in details) {
        val link = d.transactions.firstOrNull { it.transaction.id == transactionId } ?: continue
        return EventLinkNow(d.event.id, d.event.name, link.link.sharePercent, link.shareMinor)
    }
    return null
}

/** النسبة المكتوبة (أرقام عربي أو لاتيني) ⇒ رقم صحيح؛ مش رقم ⇒ `null`. */
fun parsePercent(text: String): Int? = latinizeDigits(text).filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= 3 }?.toInt()

/**
 * سطرين «على «الحدث»» و«الباقي خارج الحدث» **بالنسبة** (٥٠٪ · ٥٠٪) — عدد صحيح من ١٠٠ مش فلوس. نصيب الحدث بالريال بيتحسب في حالة الاستخدام
 * وقت الربط وبيظهر على الكارت بعد الحفظ (`EventLinkNow.shareMinor`)؛ المعاينة بالمبلغ قبل الحفظ محتاجة حالة استخدام (ناقصة). برّه 1..100 ⇒ `null`.
 */
fun sharePercents(percent: Int?): Pair<Int, Int>? =
    if (percent == null || percent !in 1..EVENT_SHARE_WHOLE) null else percent to (EVENT_SHARE_WHOLE - percent)

/** «٥٠٪» في الجمل. */
fun percentText(p: Int): String = t(UiKey.LINK_PROJECT_EVENT_PERCENT, sentenceNumber(p))

/** النسب السريعة جنب الخانة. */
val QUICK_SHARES = listOf(100, 75, 50, 25)

/**
 * خطأ الحفظ قبل حالة الاستخدام: حدث تاني مربوط ولسه ما اتفكّش · نسبة برّه 1..100. `null` = جاهز.
 * [pending] = اختار حدث غير المربوط من غير «فك الربط».
 */
fun projectEventError(pending: Boolean, eventPicked: Boolean, percent: Int?): String? = when {
    pending -> t(UiKey.LINK_PROJECT_EVENT_ERR_PENDING)
    eventPicked && (percent == null || percent !in 1..EVENT_SHARE_WHOLE) -> t(UiKey.LINK_PROJECT_EVENT_ERR_SHARE)
    else -> null
}

/**
 * اللي الحفظ هيعمله في الحدث (من غير ما يحسب فلوس): يفك الربط القديم؟ يربط بالحدث المختار بالنسبة؟ تغيير النسبة = فك وربط من جديد
 * (مفيش تعديل نسبة بخطوة واحدة في كوتلن — §76 «ناقص»). الوارد (نقطة) ما بيتربطش من هنا.
 */
data class EventPlan(val unlinkOld: Id?, val linkTo: Id?, val share: Int)

fun eventPlan(linked: EventLinkNow?, picked: Id?, unlinked: Boolean, percent: Int?, outgoing: Boolean): EventPlan {
    val share = percent ?: EVENT_SHARE_WHOLE
    if (!outgoing) return EventPlan(null, null, share)
    val changed = linked != null && (picked != linked.eventId || share != linked.sharePercent)
    val unlinkOld = linked?.eventId?.takeIf { unlinked || changed }
    val linkTo = picked?.takeIf { linked == null || unlinked || changed }
    return EventPlan(unlinkOld, linkTo, share)
}
