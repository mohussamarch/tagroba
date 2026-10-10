package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind

/**
 * الاسترداد من محل (§75-6 — قرار المالك 2026-10-08 ✗ غير المقترح: «يقترح «استرداد» ويستنى تأكيده»): رسالة [SmsKind.REFUND] **عمرها ما
 * بتتسجل لوحدها** (`SMS_WAIT_REFUND` في `SmsWaitReasons.kt` بيخليها مستنية)، ولما **المالك** يسجّلها بنفسه («سجّل الكل» = التأكيد)
 * بتتسجل «استرداد» مؤكد ([EconomicKind.REFUND_RECEIVED]) — بينقّص المصروف، مش دخل (§42).
 * التسجيل التلقائي ([RecordContext.byOwner] = false) ما بيأكدش استرداد أبدًا، حتى لو قاعدة الانتظار اتغيرت في يوم.
 * النوع اللي اتأكد قبل كده (زي «حسابي التاني» من زون التحويلات) ما بيتلمسش.
 */
class RefundConfirmEffect : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        if (!ctx.byOwner) return
        for (line in ctx.lines) {
            if (line.sms?.kind != SmsKind.REFUND) continue
            val t = line.transaction
            if (t.observedDirection != Direction.IN || t.economicKindConfirmed) continue
            line.transaction = t.copy(
                economicKind = EconomicKind.REFUND_RECEIVED, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }
}
