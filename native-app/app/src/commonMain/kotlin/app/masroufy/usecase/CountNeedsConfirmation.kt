package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.Period
import app.masroufy.port.AskSource

/**
 * «عندك كذا عملية محتاجة تأكيد» (قرار المالك §75-15) — العدّ اللي الشاشات والتذكير الأسبوعي بيسألوه. بيجمع أسئلة كل الشرايح
 * ([AskSource] لكل شريحة ولكل بلد) في قايمة واحدة:
 * - **الحاجة الواحدة بتتعد مرة:** نفس العملية (أو نفس الرسالة) في نفس البلد عليها كذا سؤال ⇒ بيفضل **الأدق** (أول واحد في ترتيب [AskKind]).
 * - **النافذة (اختيار Claude — المالك يقدر يغيّره):** أسئلة العمليات من أول الفترة المالية الحالية لحد النهارده بس (العملية القديمة
 *   اللي ما اتأكدتش بتفضل في شاشتها، بس ما بتكبّرش العدّ كل أسبوع)، و**كل** رسايل البنك المستنية (هي مستنية دلوقتي مهما كان تاريخها).
 *   سؤال على عملية من غير تاريخ بيتحسب (المصدر هو اللي حدده في النافذة).
 */
data class NeedsConfirmation(
    /** الأسئلة بعد منع التكرار — الأدق الأول، وجوه النوع الواحد الأحدث الأول. */
    val asks: List<PendingAsk>,
    val total: Int,
    /** العدد لكل نوع (الأنواع اللي مالهاش سؤال مش موجودة). */
    val byKind: Map<AskKind, Int>,
)

class CountNeedsConfirmation(private val sources: List<AskSource>) {
    suspend fun load(today: IsoDate, period: Period): NeedsConfirmation {
        val from = period.start
        val to = if (today < from) from else minOf(today, period.end)
        val best = LinkedHashMap<String, PendingAsk>()
        val loose = mutableListOf<PendingAsk>()
        for (source in sources) {
            for (ask in source.pending(from, to)) {
                val date = ask.date
                if (ask.transactionId != null && date != null && (date < from || date > to)) continue
                val key = keyOf(ask)
                if (key == null) {
                    loose += ask
                    continue
                }
                val seen = best[key]
                if (seen == null || ask.kind.ordinal < seen.kind.ordinal) best[key] = ask
            }
        }
        val asks = (best.values + loose).sortedWith(
            compareBy<PendingAsk> { it.kind.ordinal }.thenByDescending { it.date ?: "" }.thenBy { it.spaceId }.thenBy { it.transactionId ?: it.messageId ?: "" },
        )
        val byKind = LinkedHashMap<AskKind, Int>()
        for (ask in asks) byKind[ask.kind] = (byKind[ask.kind] ?: 0) + 1
        return NeedsConfirmation(asks, asks.size, byKind)
    }

    /** نفس الحاجة = نفس البلد + نفس العملية (أو نفس الرسالة لو لسه في الصندوق). سؤال مالوش لا ده ولا ده بيتعد لوحده. */
    private fun keyOf(ask: PendingAsk): String? = when {
        ask.transactionId != null -> "${ask.spaceId}|t|${ask.transactionId}"
        ask.messageId != null -> "${ask.spaceId}|m|${ask.messageId}"
        else -> null
    }
}
