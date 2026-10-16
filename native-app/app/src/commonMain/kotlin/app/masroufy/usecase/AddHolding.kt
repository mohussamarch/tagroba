package app.masroufy.usecase

import app.masroufy.core.ASSET_KIND_REAL_ESTATE
import app.masroufy.core.ASSET_KIND_SILVER
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.Currency
import app.masroufy.core.feedLinkOf
import app.masroufy.core.pricedAtPurity
import app.masroufy.core.HoldingOrigin
import app.masroufy.core.HoldingType
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.NewHolding
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.RentTerms
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.subtractMoney
import app.masroufy.core.unitPriceOf
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.UnitOfWork

/** وقائع الزكاة للمعادن (للبس/ادخار · العيار · النقاوة) — التشغيل بيوصّلها بـ`ManageZakat.setAssetFacts` (مصدر واحد مع الزكاة). */
fun interface MetalFactsWriter {
    suspend fun write(assetId: Id, purpose: ZakatPurpose, karat: Int?, fineness: Int?)
}

data class AddHoldingDeps(
    val assets: ManageAssets,
    val growth: ManageAssetGrowth,
    val assetRepo: AssetRepository,
    val lots: AssetLotRepository,
    val prices: AssetPriceRepository,
    val feedPrice: FeedPriceLookup,
    val metalFacts: MetalFactsWriter,
    val currency: Currency,
    val uow: UnitOfWork,
    val ids: IdGenerator,
)

/** النوع المتخزن (التطبيق الحالي بيرفض أي نوع مش في قايمته — جدول §2.6): العقار ⇒ "other" + علامة · الفضة ⇒ "other" + علامة (في المحوّل). */
fun storedKindFor(type: HoldingType): String = when (type) {
    HoldingType.GOLD -> "gold"
    HoldingType.SILVER -> ASSET_KIND_SILVER
    HoldingType.FLAT, HoldingType.LAND, HoldingType.SHOP -> ASSET_KIND_REAL_ESTATE
    HoldingType.STK -> "stock"
    else -> "other"
}

/**
 * «حفظ» في «ضيف حاجة» (مواصفة r05 §4.2) — وحدة عمل واحدة: الأصل بنوعه المتخزن وحقول «حاجاتي» ⇒ الشراء (أو اللي وصلك النهارده) ⇒
 * القيمة: المعادن مربوطة بسعر الملف لعيارها · العقار قيمة كاملة بمساحته وإيجاره · الباقي سعر يدوي بالقيمة اللي اتكتبت ⇒ وقائع الزكاة للمعادن.
 *
 * التكلفة: «سعر الشرا كله» (والمصنعية جوّاه ⇒ بتتسجل رسوم من نفس المبلغ، مش فوقه) — مش مكتوبة ⇒ صفر و`costKnown=false` ⇒ المكسب
 * «غير متاح». الأنواع اللي خانتها «القيمة النهارده» بس (العقار · الشركة · الشخصي · المقتنيات) تكلفتها مش معروفة بالتعريف (❓ سؤال للمالك).
 * الاسم متكرر ⇒ رقم جنبه («ذهب 2»).
 */
class AddHolding(private val deps: AddHoldingDeps) {
    suspend fun add(h: NewHolding, today: IsoDate): Id = deps.uow.run {
        val name = uniqueName(h.name)
        val created = deps.assets.addAsset(NewAsset(name, storedKindFor(h.type), currency = deps.currency))
        val costGiven = (h.type.metal || h.type == HoldingType.STK) && h.priceMinor != null
        val asset = created.copy(
            holdingType = h.type.wire,
            purpose = h.purpose.wire,
            origin = h.origin.takeIf { it != HoldingOrigin.BUY }?.wire,
            costKnown = if (costGiven) null else false,
            place = h.place?.let(::jsTrim)?.takeIf { it.isNotEmpty() },
            sharePercentBp = h.sharePercentBp,
        )
        deps.assetRepo.save(asset)

        // الشراء: المصنعية جوه السعر ⇒ الأصل = السعر − المصنعية والرسوم = المصنعية (المجموع = السعر بالظبط)
        val price = if (costGiven) h.priceMinor!! else 0L
        val fee = if (costGiven) minOf(h.makingMinor ?: 0L, price) else 0L
        deps.lots.saveMany(listOf(AssetLot(deps.ids.next("lot"), asset.id, today, h.quantity, subtractMoney(price, fee), fee)))

        when {
            h.type.metal -> {
                val symbol = metalFeedSymbol(h.type, h.karat ?: h.fineness ?: 0, deps.currency)
                if (symbol != null) {
                    // سطر الفضة سعر الجرام الصافي ⇒ 925 أو 800 بتتربط بالنقاوة وسعرها × النقاوة ÷ 1000 (`core/FeedPurity.kt`)
                    val fineness = h.fineness.takeIf { h.type == HoldingType.SILVER }
                    val feed = runCatching { deps.feedPrice.find(symbol, deps.currency) }.getOrNull()
                    deps.assets.linkToFeed(asset.id, feedLinkOf(feed?.symbol ?: symbol, fineness))
                    if (feed != null) deps.prices.save(AssetPrice(asset.id, pricedAtPurity(feed.pricePerUnitMinor, fineness), feed.asOf, "feed"))
                }
                deps.metalFacts.write(asset.id, if (h.jewellery) ZakatPurpose.WEAR else ZakatPurpose.SAVING, h.karat, h.fineness)
            }
            h.type.property -> {
                deps.growth.setProfile(
                    asset.id,
                    AssetGrowthInput(valuation = RealEstateValuation.WHOLE, areaSqm = h.areaSqm, rent = RentTerms(monthlyMinor = h.monthlyRentMinor)),
                )
                h.valueMinor?.let { deps.assets.setPrice(asset.id, unitPriceOf(it, h.quantity) ?: it, today) }
            }
            h.type == HoldingType.STK -> {
                // سهم من غير سطر في الملف ⇒ سعر يدوي = سعر الشرا ÷ العدد (بيقدم بعد 7 أيام زي أي سعر يدوي)
                if (costGiven) unitPriceOf(price, h.quantity)?.takeIf { it > 0L }?.let { deps.assets.setPrice(asset.id, it, today) }
            }
            else -> h.valueMinor?.let { deps.assets.setPrice(asset.id, unitPriceOf(it, h.quantity) ?: it, today) }
        }
        asset.id
    }

    private suspend fun uniqueName(raw: String): String {
        val base = jsTrim(raw)
        val taken = deps.assetRepo.listAll().map { jsTrim(it.name) }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }
}
