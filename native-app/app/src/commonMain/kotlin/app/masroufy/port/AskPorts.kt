package app.masroufy.port

import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk

/**
 * عقد C0: مصدر أسئلة «محتاجة تأكيد» (§75-15). كل شريحة بتنفّذ واحد، و`CountNeedsConfirmation` بيجمعهم. [from]..[to] = نافذة الأيام
 * للأسئلة اللي على عمليات؛ المصدر اللي أسئلته على حاجة مستنية دلوقتي (زي رسايل الصندوق) ممكن يتجاهلها.
 */
fun interface AskSource {
    suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk>
}
