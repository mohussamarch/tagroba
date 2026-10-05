package app.masroufy.usecase

import app.masroufy.core.ASSET_UNIT_DEFAULTS
import app.masroufy.core.Asset
import app.masroufy.core.AssetError
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPosition
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PortfolioTotals
import app.masroufy.core.Quantity
import app.masroufy.core.TextKey
import app.masroufy.core.assertQuantity
import app.masroufy.core.computePortfolioTotals
import app.masroufy.core.computePosition
import app.masroufy.core.formatMoney
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.uiText
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator

/**
 * ManageAssets — نقل `manageAssets.ts`: الاستثمار.
 * spec/01: «**البيع تسجيل فقط. لا تداول أو تنفيذ أوامر.**» الحساب كله في `core/Assets.kt`؛
 * هنا فحص المدخلات والربط بالتخزين، ومفيش إعادة حساب.
 */

data class AssetRow(val asset: Asset, val position: AssetPosition, val lots: List<AssetLot>, val sales: List<AssetSale>)

data class PortfolioView(val rows: List<AssetRow>, val totals: PortfolioTotals)

data class NewAsset(
    val name: String,
    val kind: String,
    val unitLabel: String? = null,
    val feedSymbol: String? = null,
    val currency: Currency? = null,
    val note: String? = null,
)

data class PurchaseInput(
    val assetId: Id,
    val purchasedAt: IsoDate,
    val quantity: Quantity,
    val principalMinor: Halalas,
    val feeMinor: Halalas? = null,
    val transactionId: Id? = null,
)

data class SaleInput(
    val assetId: Id,
    val soldAt: IsoDate,
    val quantity: Quantity,
    val grossProceedsMinor: Halalas,
    val feeMinor: Halalas? = null,
    val transactionId: Id? = null,
)

data class RecordedSale(val sale: AssetSale, val position: AssetPosition)

data class ManageAssetsDeps(
    val assets: AssetRepository,
    val lots: AssetLotRepository,
    val sales: AssetSaleRepository,
    val prices: AssetPriceRepository,
    val ids: IdGenerator,
    val clock: Clock,
)

private const val MAX_ASSET_NAME = 80

/** «رقم مش صحيح» مالوش حالة هنا: `Long` بيمنعه وقت الترجمة. */
class ManageAssets(private val deps: ManageAssetsDeps) {
    private fun requireDate(value: String, label: String): IsoDate {
        if (!isValidIsoDate(value)) throw AssetError(uiText(TextKey.ASSET_FIELD_NOT_DATE, label))
        return value
    }

    private fun requireAmount(value: Halalas, label: String, allowZero: Boolean = false): Halalas {
        if (value < 0) throw AssetError(uiText(TextKey.ASSET_FIELD_NEGATIVE, label))
        if (!allowZero && value == 0L) throw AssetError(uiText(TextKey.ASSET_FIELD_POSITIVE, label))
        return value
    }

    private fun requireQuantity(value: Quantity): Quantity {
        assertQuantity(value)
        if (value <= 0) throw AssetError(uiText(TextKey.ASSET_QUANTITY_POSITIVE))
        return value
    }

    private suspend fun findAsset(id: Id): Asset =
        deps.assets.listAll().find { it.id == id } ?: throw AssetError(uiText(TextKey.ASSET_NOT_FOUND))

    /** كل الأصول بمراكزها — تلات مجموعات كاملة والتجميع في الذاكرة، من غير استعلام لكل أصل. */
    suspend fun listPortfolio(today: IsoDate? = null): PortfolioView {
        val assets = deps.assets.listAll()
        val allLots = deps.lots.listAll()
        val allSales = deps.sales.listAll()
        val priceOf = deps.prices.listAll().associateBy { it.assetId }
        val asOf = today ?: deps.clock.nowIso().take(10)

        val rows = assets.map { asset ->
            val lots = allLots.filter { it.assetId == asset.id }
            val sales = allSales.filter { it.assetId == asset.id }
            AssetRow(asset, computePosition(asset.id, lots, sales, priceOf[asset.id], asOf), lots, sales)
        }.sortedWith { a, b ->
            // المملوك الأول، والمؤرشف في الآخر
            if (a.asset.archived != b.asset.archived) {
                if (a.asset.archived) 1 else -1
            } else {
                b.position.costBasisMinor.compareTo(a.position.costBasisMinor)
            }
        }
        return PortfolioView(rows, computePortfolioTotals(rows.map { it.position }))
    }

    suspend fun addAsset(input: NewAsset): Asset {
        val name = jsTrim(input.name)
        if (name.isEmpty()) throw AssetError(uiText(TextKey.ASSET_NAME_REQUIRED))
        if (name.length > MAX_ASSET_NAME) throw AssetError(uiText(TextKey.PROFILE_NAME_TOO_LONG, "$MAX_ASSET_NAME"))
        if (deps.assets.listAll().any { jsTrim(it.name) == name }) throw AssetError(uiText(TextKey.ASSET_NAME_DUPLICATE, name))

        val asset = Asset(
            id = deps.ids.next("asset"),
            name = name,
            kind = input.kind,
            unitLabel = input.unitLabel?.let(::jsTrim)?.takeIf { it.isNotEmpty() } ?: ASSET_UNIT_DEFAULTS.getValue(input.kind),
            currency = input.currency ?: Currency.SAR,
            archived = false,
            feedSymbol = input.feedSymbol?.takeIf { it.isNotEmpty() },
            note = input.note?.let(::jsTrim)?.takeIf { it.isNotEmpty() },
        )
        deps.assets.save(asset)
        return asset
    }

    /** بيربط أصل برمز في ملف الأسعار أو بيفكّه. الفك **ما بيمسحش** آخر سعر — الرقم القديم بتاريخه أنفع من فراغ. */
    suspend fun linkToFeed(assetId: Id, feedSymbol: String?): Asset {
        val next = findAsset(assetId).copy(feedSymbol = feedSymbol?.takeIf { it.isNotEmpty() })
        deps.assets.save(next)
        return next
    }

    /** أرشفة مش حذف — الأصل اللي ليه سجل بيفضل ظاهر (نفس قاعدة الأشخاص). */
    suspend fun archiveAsset(assetId: Id, archived: Boolean) {
        deps.assets.save(findAsset(assetId).copy(archived = archived))
    }

    /** الرسوم بتنضم للتكلفة. */
    suspend fun recordPurchase(input: PurchaseInput): AssetLot {
        val asset = findAsset(input.assetId)
        val id = deps.ids.next("lot")
        val lot = AssetLot(
            id = id,
            assetId = asset.id,
            purchasedAt = requireDate(input.purchasedAt, uiText(TextKey.ASSET_LABEL_PURCHASE_DATE)),
            quantity = requireQuantity(input.quantity),
            principalMinor = requireAmount(input.principalMinor, uiText(TextKey.ASSET_LABEL_PURCHASE_VALUE)),
            feeMinor = requireAmount(input.feeMinor ?: 0, uiText(TextKey.ASSET_LABEL_FEES), allowZero = true),
            transactionId = input.transactionId?.takeIf { it.isNotEmpty() },
        )
        deps.lots.saveMany(listOf(lot))
        return lot
    }

    /**
     * بيع كلي أو جزئي. spec/02: «**لا دخل معيشة من كامل الحصيلة**» — الحصيلة بتتسجل هنا ومش بتتكتب كعملية دخل.
     * «مفيش بيع لأكتر من المملوك» متكتبة مرة واحدة في `computePosition` — والفحص قبل أي كتابة.
     */
    suspend fun recordSale(input: SaleInput): RecordedSale {
        val asset = findAsset(input.assetId)
        val id = deps.ids.next("sale")
        val sale = AssetSale(
            id = id,
            assetId = asset.id,
            soldAt = requireDate(input.soldAt, uiText(TextKey.ASSET_LABEL_SALE_DATE)),
            quantity = requireQuantity(input.quantity),
            grossProceedsMinor = requireAmount(input.grossProceedsMinor, uiText(TextKey.ASSET_LABEL_PROCEEDS)),
            feeMinor = requireAmount(input.feeMinor ?: 0, uiText(TextKey.ASSET_LABEL_FEES), allowZero = true),
            transactionId = input.transactionId?.takeIf { it.isNotEmpty() },
        )
        if (sale.feeMinor > sale.grossProceedsMinor) {
            throw AssetError(uiText(TextKey.ASSET_FEES_OVER_PROCEEDS, formatMoney(sale.feeMinor), formatMoney(sale.grossProceedsMinor)))
        }
        val position = computePosition(asset.id, deps.lots.listByAsset(asset.id), deps.sales.listByAsset(asset.id) + sale)
        deps.sales.saveMany(listOf(sale))
        return RecordedSale(sale, position)
    }

    /** سعر يدوي بتاريخه. spec/01: «السعر المفقود/القديم واضح». */
    suspend fun setPrice(assetId: Id, pricePerUnitMinor: Halalas, asOf: IsoDate? = null): AssetPrice {
        val asset = findAsset(assetId)
        val price = AssetPrice(
            assetId = asset.id,
            pricePerUnitMinor = requireAmount(pricePerUnitMinor, uiText(TextKey.ASSET_LABEL_PRICE)),
            asOf = requireDate(asOf ?: deps.clock.nowIso().take(10), uiText(TextKey.ASSET_LABEL_PRICE_DATE)),
            source = "manual",
        )
        deps.prices.save(price)
        return price
    }
}
