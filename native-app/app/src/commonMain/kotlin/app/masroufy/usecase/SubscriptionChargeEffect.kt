package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.SUBSCRIPTION_CHARGE_DAYS
import app.masroufy.core.SourceRecord
import app.masroufy.core.applySubscriptionCharges
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.revertSubscriptionCharges
import app.masroufy.core.toDayNumber
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TransactionRepository

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

/**
 * التراجع عن دفعة فيها خصم اشتراك (مراجعة S4 — عقد C0 `BatchUndo`، يتسجل جنب `MergeUndo` في `RevertDeps.undoers`): الخصم اللي هيتمسح كان
 * حرّك الميعاد ⇒ الميعاد بيرجع دورة، فالدورة دي تطلع «متأخر» تاني لو مفيش خصم تاني دفعها. من غير تخزين جديد (`revertSubscriptionCharges`):
 * بيرجع بس لو الخصم ده **هو اللي** وصّل الميعاد لقيمته الحالية. بيشتغل جوه وحدة عمل التراجع، قبل المسح.
 */
class SubscriptionChargeUndo(private val items: RecurringRepository, private val txns: TransactionRepository) : BatchUndo {
    override suspend fun undo(batchId: Id, records: List<SourceRecord>, deleting: List<Id>) {
        if (deleting.isEmpty()) return
        val removed = txns.findByIds(deleting).filter { it.observedDirection == Direction.OUT }
        if (removed.isEmpty()) return
        val all = items.listAll()
        if (all.none { it.active && it.confirmed }) return
        // الخصم بيدفع ميعاد جوه ±٧ أيام منه، وخصم تاني بيدفع نفس الميعاد جوه ±٧ أيام منه ⇒ ±١٤ يوم حوالين الخصومات اللي هتتمسح
        val span = 2 * SUBSCRIPTION_CHARGE_DAYS
        val from = shift(removed.minOf { it.occurredAt }, -span)
        val to = shift(removed.maxOf { it.occurredAt }, span)
        for (item in revertSubscriptionCharges(all, removed, txns.listByDateRange(from, to))) items.save(item)
    }

    private fun shift(date: String, days: Int): String = dayNumberToIso(toDayNumber(parseIsoDate(date)) + days)
}
