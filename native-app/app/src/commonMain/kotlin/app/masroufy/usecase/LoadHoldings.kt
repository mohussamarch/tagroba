package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.HoldingType
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IsoDate
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.currentValueOf
import app.masroufy.core.holdingTypeOf
import app.masroufy.core.isRealEstate
import app.masroufy.core.purposeOf
import app.masroufy.port.ZakatFactRepository

/**
 * «حاجاتي» من بيانات المساحة (مواصفة r05 §2.6 · §4.0 بند 4): كل أصل مش مؤرشف بقيمته النهارده — نفس منطق القيمة الموجود من غير
 * حساب جديد (المعادن والأسهم والباقي = المركز × السعر · العقار = `currentValueOf`). ⚠️ «الخطوة 0» — اتعملت هنا لأن الحزم بدأت قبلها.
 *
 * القيمة «غير متاح» (null، مش صفر) لو: مفيش سعر · مفيش شراء متسجل خالص (غير العقار) · عملة الأصل غير عملة البلد.
 * الأصل اللي اتباع كله (شراء متسجل والمملوك صفر) مش «حاجة معاك» ⇒ مش ظاهر.
 */
data class LoadHoldingsDeps(val assets: ManageAssets, val zakatFacts: ZakatFactRepository, val currency: Currency)

class LoadHoldings(private val deps: LoadHoldingsDeps) {
    suspend fun load(today: IsoDate): HoldingsSnapshot {
        val view = deps.assets.listPortfolio(today)
        val facts = deps.zakatFacts.listAll().associateBy { it.subjectId }
        val items = view.rows
            .filter { !it.asset.archived && !(it.lots.isNotEmpty() && it.position.heldQuantity <= 0L) }
            .map { row ->
                val asset = row.asset
                val fact = facts[asset.id]
                val jewellery = fact?.purpose == ZakatPurpose.WEAR
                val value = when {
                    asset.currency != deps.currency -> null
                    asset.isRealEstate -> currentValueOf(asset, row.position, lotsRecorded = row.lots.isNotEmpty())
                    row.lots.isEmpty() -> null
                    else -> row.position.marketValueMinor
                }
                HoldingValue(asset, holdingTypeOf(asset), purposeOf(asset, jewellery), value, row.position.heldQuantity, fact?.karat, fact?.fineness, jewellery)
            }
        val goldMissing = items.any { it.type == HoldingType.GOLD && it.valueMinor == null && it.asset.currency == deps.currency && it.quantity > 0L }
        return HoldingsSnapshot(deps.currency, items, goldMissing)
    }
}
