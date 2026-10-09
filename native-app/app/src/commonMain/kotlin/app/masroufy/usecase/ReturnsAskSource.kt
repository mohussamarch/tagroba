package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.awaitsRefundAnswer
import app.masroufy.core.awaitsReversalAnswer
import app.masroufy.port.AskSource
import app.masroufy.port.TransactionRepository

/**
 * أسئلة الشريحة S3 لعدّاد «محتاجة تأكيد» والتذكير الأسبوعي (§75-15) — مش إشعار جوال جديد:
 * - `REVERSAL_CHECK` لكل عملية في الفترة مستنية «نلغي الاتنين؟» (§77-D — الأصلية مربوطة أو نوعها مؤكد).
 * - `REFUND_CONFIRM` لكل عملية في الفترة مستنية «ده استرداد؟» (§75-6 · §77-D — ومنها الاسترداد اللي الشريحة S2 بتقترحه).
 * - `FOREIGN_LOCAL_AMOUNT` لكل رسالة أجنبية مستنية المبلغ المحلي في البلد دي ([foreign] — §75-12)، **مهما قدمت** لحد ما تتجاوب.
 * البلد = [spaceId] (المستودع مربوط بالبلد). الرسالة اللي ليها سؤال هنا ممكن تتعد كمان «رسالة مستنية» من مصدر تاني — العدّاد بيشيل
 * التكرار بالرسالة/العملية ويسيب الأدق (ترتيب `AskKind`).
 */
class ReturnsAskSource(
    private val spaceId: String,
    private val txns: TransactionRepository,
    private val foreign: ForeignSmsAsks? = null,
) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> {
        val messages = foreign?.pendingFor(spaceId, to).orEmpty()
        val asks = txns.listByDateRange(from, to).mapNotNull { t ->
            when {
                awaitsReversalAnswer(t) -> PendingAsk(AskKind.REVERSAL_CHECK, spaceId, transactionId = t.id, date = t.occurredAt)
                awaitsRefundAnswer(t) -> PendingAsk(AskKind.REFUND_CONFIRM, spaceId, transactionId = t.id, date = t.occurredAt)
                else -> null
            }
        }
        return messages + asks
    }
}
