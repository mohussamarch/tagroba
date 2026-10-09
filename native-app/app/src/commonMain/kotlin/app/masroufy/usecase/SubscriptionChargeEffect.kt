package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.applySubscriptionCharges
import app.masroufy.port.RecurringRepository

/**
 * §75-7 (الشريحة S4) — أثر وقت التسجيل: خصم الاشتراك اللي اتسجل (من رسالة **أو** كشف — نفس الخط) بيتطابق لوحده بالمحل والمبلغ والميعاد
 * (`core/SubscriptionCharges.kt`) والميعاد الجاي بيتحرك. بيشتغل **بعد** ما الدفعة تتقفل: فشله ما بيرجّعش التسجيل، و`MatchSubscriptions.catchUp`
 * في دورة الخلفية بيلحق أي خصم فاته (أو اتسجل بالإيد). بيلمس الاشتراكات المتأكدة والشغالة بس.
 */
class SubscriptionChargeEffect(private val items: RecurringRepository) : RecordEffect {
    override suspend fun afterCommit(ctx: RecordContext) {
        val charges = ctx.lines.map { it.transaction }.filter { it.observedDirection == Direction.OUT }
        if (charges.isEmpty()) return
        val all = items.listAll()
        if (all.none { it.active && it.confirmed }) return
        for (item in applySubscriptionCharges(all, charges).changed) items.save(item)
    }
}
