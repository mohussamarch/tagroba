package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.duplicateChargeIds
import app.masroufy.core.shiftDays
import app.masroufy.port.AskSource
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository

/**
 * r06-darahim (PLAN-0.6 §12-5): سؤال «مكرر؟» — القاعدة في `DuplicateCharges.kt` (نقية). هنا:
 * - [scan]: بيعلّم الأحدث في كل زوج (`duplicateAskPending = true`) — **ولا بيمسح ولا بيغيّر حاجة تانية**. آمن التكرار، و«الاتنين صح»
 *   (`false`) ما بيرجعش يتسأل. دورة الخلفية بتشغّله على آخر [SCAN_DAYS] أيام (`RunBackgroundCycle`).
 * - [pending]: الأسئلة المفتوحة ⇒ «محتاجة تأكيد» (`CountNeedsConfirmation` — [AskKind.DUPLICATE_CHARGE]).
 * - [keepBoth]: «لأ، الاتنين صح». و«أيوه مكرر — شيله» = المسح الموجود بتراجعه (`DeleteTransaction` · لوحة المسح في التفاصيل).
 */
class DuplicateChargeAsks(
    private val spaceId: String,
    private val txns: TransactionRepository,
    private val clock: Clock,
) : AskSource {
    suspend fun scan(from: IsoDate, to: IsoDate): Int {
        val ids = duplicateChargeIds(txns.listByDateRange(from, to))
        val now = clock.nowIso()
        for (id in ids) txns.update(id, app.masroufy.port.TransactionPatch(duplicateAskPending = true, updatedAt = now))
        return ids.size
    }

    /**
     * آخر [SCAN_DAYS] أيام لحد النهارده. ساعة الجهاز بالتوقيت العالمي (UTC)، وتاريخ العملية بتوقيت البلد ⇒ بعد نص الليل المحلي اليوم المحلي
     * بيبقى بكرة بالعالمي (فحص 0.5.2 الساعة 00:10 الرياض). عشان كده النافذة بيوم زيادة لقدام — آمن، والمقارنة نفسها بالساعة المحلية للعمليات.
     */
    suspend fun scanRecent(): Int {
        val utcToday = clock.nowIso().take(10)
        return scan(shiftDays(utcToday, -SCAN_DAYS), shiftDays(utcToday, 1))
    }

    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> =
        txns.listByDateRange(from, to).filter { it.duplicateAskPending == true }
            .map { PendingAsk(AskKind.DUPLICATE_CHARGE, spaceId, transactionId = it.id, date = it.occurredAt.take(10)) }

    suspend fun keepBoth(transactionId: Id) {
        txns.update(transactionId, app.masroufy.port.TransactionPatch(duplicateAskPending = false, updatedAt = clock.nowIso()))
    }

    companion object {
        const val SCAN_DAYS = 3
    }
}
