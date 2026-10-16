package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DailyValue
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MANUAL_UNITS
import app.masroufy.core.UserSetting
import app.masroufy.core.daysBetween
import app.masroufy.core.isRealEstate
import app.masroufy.core.shiftSnapshot
import app.masroufy.core.todayChange
import app.masroufy.port.Clock
import app.masroufy.port.UserSettingsStore

/** «استثماراتك النهارده»: [pctBp] التغيّر · [gainMinor] بالهللة · [sinceDate] null = من امبارح، وإلا تاريخ آخر يوم اتشاف. */
data class InvestTodayView(val pctBp: Int, val gainMinor: Halalas, val currency: Currency, val sinceDate: IsoDate?)

data class LoadInvestTodayDeps(val settings: UserSettingsStore, val holdings: LoadHoldings, val currency: Currency, val clock: Clock)

/**
 * كارت الرئيسية «استثماراتك النهارده» (مواصفة النقل §1.14 · §2.7 — Q9): قيم «حاجاتي» النهارده (غير الشخصي والقيمة معروفة) ⇒ اللقطة
 * المحفوظة للبلد (يوم جديد ⇒ القديم بقى «قبل») ⇒ التغيّر على الحاجات اللي في اليومين بس. null = مفيش يوم قبل كده أو قيمته ≤ 0 ⇒ «غير متاح».
 * الكتابة بس لما القيم تتغير (مش كل فتحة).
 */
class LoadInvestToday(private val deps: LoadInvestTodayDeps) {
    suspend fun load(spaceId: String, today: IsoDate): InvestTodayView? {
        // بالكمية جنب القيمة (شراء زيادة أو بيع جزء مش حركة سعر) · العقار قيمته مكتوبة باليد ⇒ أي تعديل = أساس جديد مش مكسب
        val now: Map<Id, DailyValue> = deps.holdings.load(today).items
            .filter { it.purpose != HoldingPurpose.PERSONAL && it.valueMinor != null }
            .associate { h -> h.asset.id to DailyValue(h.valueMinor!!, if (h.asset.isRealEstate) MANUAL_UNITS else h.quantity) }
        val stored = deps.settings.listAll().filterIsInstance<UserSetting.InvestDaily>().firstOrNull { it.spaceId == spaceId }
        val next = shiftSnapshot(stored, spaceId, today, now, deps.clock.nowIso())
        if (stored == null || next.copy(updatedAt = stored.updatedAt) != stored) deps.settings.save(next)
        val change = todayChange(next) ?: return null
        val since = change.prevDate.takeIf { daysBetween(it, today) != 1 }
        return InvestTodayView(change.pctBp, change.gainMinor, deps.currency, since)
    }
}
