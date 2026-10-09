package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.port.AskSource

/**
 * §75-8 (الشريحة S4) — أسئلة «نربطه؟» في عدّ «محتاجة تأكيد» والتذكير الأسبوعي (§75-15): سؤال لكل عملية عليها اقتراح ربط بقسط أو جمعية
 * ([SuggestDueLinks]). **مفيش إشعار جوال جديد** — سؤال على العملية جوه التطبيق بس. [spaceId] = البلد اللي المستودعات دي بتاعتها.
 */
class DueLinkAskSource(private val spaceId: String, private val suggestions: SuggestDueLinks) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> =
        suggestions.list(from, to).map { PendingAsk(AskKind.DUE_LINK, spaceId, transactionId = it.transactionId, date = it.transactionDate) }
}
