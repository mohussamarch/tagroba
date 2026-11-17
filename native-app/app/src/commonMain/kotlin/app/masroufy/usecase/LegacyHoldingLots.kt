package app.masroufy.usecase

import app.masroufy.core.AssetLot
import app.masroufy.core.Id
import app.masroufy.core.withLegacyOwnedLongAgo
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetRepository

/**
 * §121-6: مستودع الدفعات بعد قاعدة «من زمان» للحاجات القديمة ([withLegacyOwnedLongAgo]) — قراية بس، الكتابة زي ما هي.
 * بيتركّب في التجميع حوالين المستودع الحقيقي ⇒ كل حالات الاستخدام (البيع · الزكاة · المواريث · الشاشات) بتشوف نفس التاريخ.
 */
class LegacyHoldingLots(private val lots: AssetLotRepository, private val assets: AssetRepository) : AssetLotRepository {
    override suspend fun listByAsset(assetId: Id): List<AssetLot> {
        val raw = lots.listByAsset(assetId)
        if (raw.isEmpty()) return raw
        return withLegacyOwnedLongAgo(assets.listAll().filter { it.id == assetId }, raw)
    }

    override suspend fun listAll(): List<AssetLot> {
        val raw = lots.listAll()
        if (raw.isEmpty()) return raw
        return withLegacyOwnedLongAgo(assets.listAll(), raw)
    }

    override suspend fun saveMany(lots: List<AssetLot>) = this.lots.saveMany(lots)

    override suspend fun deleteMany(ids: List<Id>) = this.lots.deleteMany(ids)
}
