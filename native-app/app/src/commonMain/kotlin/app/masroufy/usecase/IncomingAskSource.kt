package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AskSource
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository

/**
 * §75-1: كل داخل **مستني** (نوعه مش معروف ومش متأكد ومش راتب معروف — نفس تعريف `withEstimatedKinds`) = سؤال «الفلوس اللي دخلت دي
 * إيه؟» (`AskKind.INCOMING_KIND`) على العملية، في الأيام اللي `CountNeedsConfirmation` بيطلبها. لو شريحة تانية بتسأل عن نفس العملية
 * سؤال أدق (زي «ده راتبك؟» §75-2) العدّ بيسيب الأدق. مفيش إشعار جديد — سؤال جوه التطبيق وسطر في العدّ والتذكير الأسبوعي.
 */
class IncomingAskSource(
    private val txns: TransactionRepository,
    private val categories: CategoryRepository,
    /** البلد (`eg` / الافتراضي) — عشان العدّ على كذا بلد ما يخلطش. */
    private val spaceId: String = DEFAULT_SPACE_ID,
) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> {
        val rows = txns.listByDateRange(from, to)
        val view = withEstimatedKinds(rows, categories.listAll().associate { it.id to it.name })
        if (view.pendingIncomingIds.isEmpty()) return emptyList()
        val dateOf = rows.associate { it.id to it.occurredAt }
        return view.pendingIncomingIds.map { id -> PendingAsk(AskKind.INCOMING_KIND, spaceId, transactionId = id, date = dateOf[id]) }
    }
}
