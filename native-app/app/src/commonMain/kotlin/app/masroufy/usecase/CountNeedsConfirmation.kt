package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.Period
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.port.AskSource

/**
 * «عندك كذا عملية محتاجة تأكيد» (قرار المالك §75-15) — العدّ اللي الشاشات والتذكير الأسبوعي بيسألوه. بيجمع أسئلة كل الشرايح
 * ([AskSource] لكل شريحة ولكل بلد) في قايمة واحدة:
 * - **الحاجة الواحدة بتتعد مرة:** نفس العملية (أو نفس الرسالة) في نفس البلد عليها كذا سؤال ⇒ بيفضل **الأدق** (أول واحد في ترتيب [AskKind]).
 * - **النافذة (اختيار Claude — المالك يقدر يغيّره):** أسئلة العمليات من أول الفترة المالية الحالية — أو من [LOOKBACK_DAYS] أيام قبل
 *   النهارده لو ده أبدر — لحد النهارده. الأسبوع اللي فات جوه دايمًا: العملية اللي وصلت في آخر أيام الشهر اللي فات وما اتأكدتش
 *   بتفضل في العدّ أول أسبوع من الشهر الجديد، فكل سؤال بيطلع في تذكير أسبوعي واحد على الأقل. العملية الأقدم من كده بتفضل في شاشتها،
 *   بس ما بتكبّرش العدّ كل أسبوع. و**كل** رسايل البنك المستنية (هي مستنية دلوقتي مهما كان تاريخها). سؤال على عملية من غير تاريخ
 *   بيتحسب (المصدر هو اللي حدده في النافذة).
 * - **البلد:** [load] بـ`spaceId` ⇒ أسئلة البلد دي بس — المساعد بيشتغل مرة لكل بلد، فكل ملخص أسبوعي بيعدّ بلده وما يتكررش نفس السؤال
 *   في ملخصين. من غيره ⇒ كل البلاد (الشاشة اللي بتجمع).
 *   **البلد مش معروف** (`PendingAsk.spaceId` = "" — رسالة ما اتقريتش ومرسلها مش مربوط ببلد واحدة — S1): بيتعد في ملخص **كل** بلد لحد
 *   ما يتحل (اختيار الدامج — المالك يقدر يغيّره): أحسن من إنه ما يطلعش في أي ملخص خالص.
 */
data class NeedsConfirmation(
    /** الأسئلة بعد منع التكرار — الأدق الأول، وجوه النوع الواحد الأحدث الأول. */
    val asks: List<PendingAsk>,
    val total: Int,
    /** العدد لكل نوع (الأنواع اللي مالهاش سؤال مش موجودة). */
    val byKind: Map<AskKind, Int>,
)

class CountNeedsConfirmation(private val sources: List<AskSource>) {
    suspend fun load(today: IsoDate, period: Period, spaceId: String? = null): NeedsConfirmation {
        val weekAgo = dayNumberToIso(toDayNumber(parseIsoDate(today)) - LOOKBACK_DAYS)
        // اليوم قبل الفترة (طلب غلط) ⇒ يوم البداية بس، زي الأول
        val from = if (today < period.start) period.start else minOf(period.start, weekAgo)
        val to = if (today < period.start) period.start else minOf(today, period.end)
        val best = LinkedHashMap<String, PendingAsk>()
        val loose = mutableListOf<PendingAsk>()
        for (source in sources) {
            for (ask in source.pending(from, to)) {
                if (spaceId != null && ask.spaceId != spaceId && ask.spaceId.isNotEmpty()) continue
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

    companion object {
        /** الأسبوع اللي فات دايمًا جوه النافذة (اختيار Claude). */
        const val LOOKBACK_DAYS = 7
    }
}
