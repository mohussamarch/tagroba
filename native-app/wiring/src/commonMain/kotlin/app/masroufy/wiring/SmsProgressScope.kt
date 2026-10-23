package app.masroufy.wiring

import app.masroufy.port.SmsInboxPort
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.SmsRunProgress

/**
 * smoke 0.5.4 ملاحظة 5 — تقدم «أكّد الأشكال» / «اقرأ اللي فات» بييجي من لفة على الصندوق كله (كل البلاد) ⇒ مصر كانت بتكتب لحظة «مستني تأكيدك: 619»
 * (رسايل السعودية) قبل ما التقرير النهائي (`SmsGraph.mine`) يستقر على 1. هنا العدد بيتظبط على بلد الشاشة **قبل ما يتعرض**: رسايل البلاد
 * التانية في الصندوق بتتحسب **مرة واحدة قبل اللفة** وبتتطرح من كل تقدم (من غير ما تتحسب تاني في النص).
 */
internal suspend fun scopedProgress(inbox: SmsInboxPort, manage: ManageSmsInbox, progress: (SmsRunProgress) -> Unit): (SmsRunProgress) -> Unit {
    val others = othersInBox(inbox.window(null).count, manage.refresh().count)
    return { p -> progress(scopeProgress(p, others)) }
}

internal fun othersInBox(boxCount: Int, mineCount: Int): Int = (boxCount - mineCount).coerceAtLeast(0)

internal fun scopeProgress(p: SmsRunProgress, others: Int): SmsRunProgress = p.copy(queued = (p.queued - others).coerceAtLeast(0))
