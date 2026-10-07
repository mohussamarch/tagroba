package app.masroufy.memory

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Id
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository

/** الاستثمار في الذاكرة — نقل `memoryAssetRepositories.ts`، بنفس ترتيب الإدخال. */
class MemoryAssetRepository(seed: List<Asset> = emptyList()) : AssetRepository {
    private val items = LinkedHashMap<Id, Asset>()

    init {
        for (a in seed) items[a.id] = a
    }

    override suspend fun listAll(): List<Asset> = items.values.toList()

    override suspend fun save(asset: Asset) {
        items[asset.id] = asset
    }
}

class MemoryAssetLotRepository(seed: List<AssetLot> = emptyList()) : AssetLotRepository {
    private val items = LinkedHashMap<Id, AssetLot>()

    init {
        for (l in seed) items[l.id] = l
    }

    override suspend fun listByAsset(assetId: Id): List<AssetLot> = items.values.filter { it.assetId == assetId }

    override suspend fun listAll(): List<AssetLot> = items.values.toList()

    override suspend fun saveMany(lots: List<AssetLot>) {
        for (l in lots) items[l.id] = l
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}

class MemoryAssetSaleRepository(seed: List<AssetSale> = emptyList()) : AssetSaleRepository {
    private val items = LinkedHashMap<Id, AssetSale>()

    init {
        for (s in seed) items[s.id] = s
    }

    override suspend fun listByAsset(assetId: Id): List<AssetSale> = items.values.filter { it.assetId == assetId }

    override suspend fun listAll(): List<AssetSale> = items.values.toList()

    override suspend fun saveMany(sales: List<AssetSale>) {
        for (s in sales) items[s.id] = s
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}

/** سعر واحد لكل أصل — مفتاحه `assetId` مش معرّف مستقل. */
class MemoryAssetPriceRepository(seed: List<AssetPrice> = emptyList()) : AssetPriceRepository {
    private val items = LinkedHashMap<Id, AssetPrice>()

    init {
        for (p in seed) items[p.assetId] = p
    }

    override suspend fun listAll(): List<AssetPrice> = items.values.toList()

    override suspend fun save(price: AssetPrice) {
        items[price.assetId] = price
    }
}
