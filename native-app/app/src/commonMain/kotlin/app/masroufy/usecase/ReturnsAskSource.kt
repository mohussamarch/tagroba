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
 * - `REVERSAL_CHECK` (§77-D — زي عقد C0: «التحويل رجع» وما اتلغتش الأصلية لوحدها) لكل عملية في الفترة عليها سؤال من أسئلة اللي رجعت:
 *   «ده استرداد؟» (ما لقيناش الأصلية — `suggestedKind` = استرداد) أو «نلغي الاتنين؟» (الأصلية مربوطة أو مؤكدة أو المرجع قصير —
 *   `suggestedKind` = تحويل داخلي). الشاشة بتعرف السؤال من الاقتراح. `REFUND_CONFIRM` مش هنا: ده استرداد المحل (§75-6 — الشريحة S2).
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
        val asks = txns.listByDateRange(from, to)
            .filter { awaitsRefundAnswer(it) || awaitsReversalAnswer(it) }
            .map { PendingAsk(AskKind.REVERSAL_CHECK, spaceId, transactionId = it.id, date = it.occurredAt) }
        return messages + asks
    }
}
