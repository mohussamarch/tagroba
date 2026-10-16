package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.HoldingType
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IsoDate
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatSubject
import app.masroufy.core.currentValueOf
import app.masroufy.core.holdingTypeOf
import app.masroufy.core.isRealEstate
import app.masroufy.core.purposeOf
import app.masroufy.port.Clock
import app.masroufy.port.ZakatFactRepository

data class LoadHoldingsDeps(val assets: ManageAssets, val zakatFacts: ZakatFactRepository, val clock: Clock, val currency: Currency)

/**
 * «حاجاتي» مرة واحدة لكل تحميل (مواصفة النقل §4.0 بند 4 — ⚠️ نسخة INSIGHTS لحد الدمج: الحزم اتبنت بالتوازي من غير «الخطوة 0»).
 * الأصول غير المؤرشفة بقيمتها النهارده (نفس المنطق الموجود — من غير حساب فلوس جديد): العقار `currentValueOf` · الباقي قيمة السوق
 * (سعر الملف أو اليدوي × الكمية؛ null لو مفيش سعر). الشبكة (`ZakatFact.purpose == WEAR`) ⇒ شخصي.
 */
class LoadHoldings(private val deps: LoadHoldingsDeps) {
    suspend fun load(today: IsoDate): HoldingsSnapshot {
        val view = deps.assets.listPortfolio(today)
        val wear = deps.zakatFacts.listAll()
            .filter { it.subject == ZakatSubject.ASSET && it.purpose == ZakatPurpose.WEAR }
            .map { it.subjectId }.toSet()
        val items = view.rows.filter { !it.asset.archived }.map { row ->
            val value = if (row.asset.isRealEstate) currentValueOf(row.asset, row.position, row.lots.isNotEmpty()) else row.position.marketValueMinor
            HoldingValue(row.asset, holdingTypeOf(row.asset), purposeOf(row.asset, row.asset.id in wear), value)
        }
        val goldMissing = items.any { it.type == HoldingType.GOLD && it.valueMinor == null }
        return HoldingsSnapshot(deps.currency, items, goldMissing)
    }
}
