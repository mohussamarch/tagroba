package app.masroufy.port

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.Person
import app.masroufy.core.Tag
import app.masroufy.core.TransactionTag

/**
 * مستودعات المراجع والاستثمار والوسوم — باقي `repositories.ts`.
 * نفس القاعدة: تعريفات بلا تنفيذ، والمستودع بيتعمل مربوط ببلد (OVERRIDES §41).
 */

interface CategoryRepository {
    suspend fun listAll(): List<Category>

    suspend fun save(category: Category)
}

interface MerchantRepository {
    suspend fun listAll(): List<Merchant>

    suspend fun findByNormalizedName(normalizedName: String): Merchant?

    suspend fun saveMany(merchants: List<Merchant>)
}

interface RuleRepository {
    suspend fun listAll(): List<ClassificationRule>

    /** تحديث بالمعرّف (upsert) — **مش استبدال للقايمة**. */
    suspend fun saveMany(rules: List<ClassificationRule>)

    suspend fun deleteMany(ids: List<Id>)
}

interface PersonRepository {
    suspend fun listAll(): List<Person>

    suspend fun save(person: Person)
}

/**
 * الاستثمار. الاستعلام كله بحقل واحد (`assetId`) أو بالمجموعة كاملة،
 * فمفيش فهرس مركّب، والترتيب الزمني بيتعمل في الذاكرة.
 */
interface AssetRepository {
    suspend fun listAll(): List<Asset>

    suspend fun save(asset: Asset)
}

interface AssetLotRepository {
    suspend fun listByAsset(assetId: Id): List<AssetLot>

    suspend fun listAll(): List<AssetLot>

    suspend fun saveMany(lots: List<AssetLot>)

    suspend fun deleteMany(ids: List<Id>)
}

interface AssetSaleRepository {
    suspend fun listByAsset(assetId: Id): List<AssetSale>

    suspend fun listAll(): List<AssetSale>

    suspend fun saveMany(sales: List<AssetSale>)

    suspend fun deleteMany(ids: List<Id>)
}

/** سعر واحد محفوظ لكل أصل — الأحدث، والتاريخ محفوظ معاه دايمًا. */
interface AssetPriceRepository {
    suspend fun listAll(): List<AssetPrice>

    suspend fun save(price: AssetPrice)
}

/** إيصالات التنبيه — المفتاح هو `eventKey` نفسه، فالكتابة المكررة بتدهس نفسها من غير ضرر. */
interface NotificationReceiptRepository {
    suspend fun listAll(): List<NotificationReceipt>

    suspend fun saveMany(receipts: List<NotificationReceipt>)

    suspend fun deleteMany(eventKeys: List<String>)
}

interface RecurringRepository {
    suspend fun listAll(): List<app.masroufy.core.RecurringItem>

    suspend fun save(item: app.masroufy.core.RecurringItem)
}

interface TagRepository {
    suspend fun listAll(): List<Tag>

    suspend fun save(tag: Tag)

    suspend fun remove(id: Id)
}

interface TransactionTagRepository {
    /** روابط عمليات محددة — محدودة بعددها مش بكل السجل. */
    suspend fun listByTransactionIds(ids: List<Id>): List<TransactionTag>

    suspend fun listByTag(tagId: Id): List<TransactionTag>

    suspend fun saveMany(links: List<TransactionTag>)

    suspend fun deleteMany(ids: List<Id>)
}
