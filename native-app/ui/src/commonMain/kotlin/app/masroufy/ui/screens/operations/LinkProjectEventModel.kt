package app.masroufy.ui.screens.operations

import app.masroufy.core.EVENT_SHARE_WHOLE
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.eventShareMinor
import app.masroufy.core.latinizeDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.core.subtractMoney
import app.masroufy.ui.text.t
import app.masroufy.usecase.EventDetail

/** حدث في اللوحة: الاسم + «١٨ أكتوبر، حدث خالد» أو «حدثك». */
data class EventChoice(val id: Id, val name: String, val sub: String)

/** الحدث المربوط بالعملية دلوقتي بنسبته (الربط بيتخزن بنسبة 1..100 — §64). */
data class EventLinkNow(val eventId: Id, val name: String, val sharePercent: Int)

/** الأحداث الشغالة (المؤرشف ما بيتعرضش — النموذج) من تفاصيلها (`ManageEvents.detail`). */
fun eventChoices(details: List<EventDetail>): List<EventChoice> = details.filter { !it.event.archived }.map { d ->
    val whose = d.hostName?.let { t(TextKey.LINK_PROJECT_EVENT_HOSTED, it) } ?: t(TextKey.LINK_PROJECT_EVENT_MINE)
    EventChoice(d.event.id, d.event.name, t(TextKey.LINK_PROJECT_EVENT_WHEN, dayMonth(d.event.date), whose))
}

/** الحدث اللي العملية مربوطة بيه (أي دور) — العملية بحدث واحد بس. */
fun eventLinkOf(details: List<EventDetail>, transactionId: Id): EventLinkNow? {
    for (d in details) {
        val link = d.transactions.firstOrNull { it.transaction.id == transactionId } ?: continue
        return EventLinkNow(d.event.id, d.event.name, link.link.sharePercent)
    }
    return null
}

/** النسبة المكتوبة (أرقام عربي أو لاتيني) ⇒ رقم صحيح؛ مش رقم ⇒ `null`. */
fun parsePercent(text: String): Int? = latinizeDigits(text).filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= 3 }?.toInt()

/**
 * نصيب الحدث والباقي برّاه — من `core` (`eventShareMinor` بالتقريب المعتمد نص لفوق على الهللة، و`subtractMoney`). نسبة برّه 1..100 ⇒ `null`.
 */
fun shareSplit(totalMinor: Halalas, percent: Int?): Pair<Halalas, Halalas>? {
    if (percent == null || percent !in 1..EVENT_SHARE_WHOLE) return null
    val share = eventShareMinor(totalMinor, percent)
    return share to subtractMoney(totalMinor, share)
}

/** «٥٠٪» في الجمل. */
fun percentText(p: Int): String = t(TextKey.LINK_PROJECT_EVENT_PERCENT, sentenceNumber(p))

/** النسب السريعة جنب الخانة. */
val QUICK_SHARES = listOf(100, 75, 50, 25)

/**
 * خطأ الحفظ قبل حالة الاستخدام: حدث تاني مربوط ولسه ما اتفكّش · نسبة برّه 1..100. `null` = جاهز.
 * [pending] = اختار حدث غير المربوط من غير «فك الربط».
 */
fun projectEventError(pending: Boolean, eventPicked: Boolean, percent: Int?): String? = when {
    pending -> t(TextKey.LINK_PROJECT_EVENT_ERR_PENDING)
    eventPicked && (percent == null || percent !in 1..EVENT_SHARE_WHOLE) -> t(TextKey.LINK_PROJECT_EVENT_ERR_SHARE)
    else -> null
}
