package app.masroufy.usecase

import app.masroufy.core.IsoDate
import app.masroufy.core.SUBSCRIPTION_CHARGE_DAYS
import app.masroufy.core.SubscriptionCharge
import app.masroufy.core.applySubscriptionCharges
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.shiftMonths
import app.masroufy.core.toDayNumber
import app.masroufy.port.RecurringRepository
import app.masroufy.port.TransactionRepository

/**
 * §75-7 (الشريحة S4) — **اللحاق**: خصومات اشتراكات اتسجلت قبل كده أو بالإيد أو أثر التسجيل فاتها (`SubscriptionChargeEffect` بعد الحفظ
 * ممكن يفشل) بتحرّك الميعاد الجاي. آمن يتعاد: الخصم اللي حرّك الميعاد بقى بعيد عن الميعاد الجديد. دورة الخلفية بتناديه قبل ما مرشحين
 * التنبيهات يتجمعوا — فاشتراك اتدفع ما بيطلعش «متأخر».
 * القراية من (أقدم ميعاد − ٧ أيام) لحد النهارده، وبحد ١٣ شهر لورا (زي قراية الاشتراكات — ARCHITECTURE §5.6).
 */
data class MatchSubscriptionsDeps(val items: RecurringRepository, val txns: TransactionRepository)

class MatchSubscriptions(private val deps: MatchSubscriptionsDeps) {
    /** الخصومات اللي اتطابقت في التشغيلة دي (فاضية = مفيش حاجة اتحركت). */
    suspend fun catchUp(today: IsoDate): List<SubscriptionCharge> {
        val all = deps.items.listAll()
        val open = all.filter { it.active && it.confirmed && !it.merchantKey.startsWith("manual:") }
        if (open.isEmpty()) return emptyList()
        val oldestWindow = open.minOf { it.nextDueAt }.let { dayNumberToIso(toDayNumber(parseIsoDate(it)) - SUBSCRIPTION_CHARGE_DAYS) }
        val floor = shiftMonths(today, -13)
        val from = if (oldestWindow < floor) floor else oldestWindow
        if (from > today) return emptyList()
        val matches = applySubscriptionCharges(all, deps.txns.listByDateRange(from, today))
        for (item in matches.changed) deps.items.save(item)
        return matches.charges
    }
}
