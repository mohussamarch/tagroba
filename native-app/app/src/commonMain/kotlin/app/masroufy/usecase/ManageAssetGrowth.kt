package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetError
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetProjection
import app.masroufy.core.DefaultRate
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Quantity
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.RentTerms
import app.masroufy.core.TextKey
import app.masroufy.core.assertQuantity
import app.masroufy.core.checkAnnualRate
import app.masroufy.core.checkRentTerms
import app.masroufy.core.computePosition
import app.masroufy.core.countryPack
import app.masroufy.core.currentValueOf
import app.masroufy.core.growthClassOf
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.projectAsset
import app.masroufy.core.uiText
import app.masroufy.core.unitPriceOf
import app.masroufy.port.AssetLotRepository
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.Clock

/**
 * «هتوصل لكام؟» لأصل موجود (OVERRIDES §69 · §69.6) — من غير شاشة:
 * - [setProfile]: يحفظ حقول العقار (سعر المتر × المساحة ولا القيمة كلها) والإيجار والزيادة المتوقعة **على الأصل نفسه** (نفس تخزينه —
 *   الحقول بتتكتب لو موجودة بس). بسعر المتر والكمية معروفة ⇒ **سعر الأصل بيتحدّث** (`assetPrices` يدوي بتاريخ سعر المتر) عشان
 *   المحفظة والورث والزكاة يشوفوا نفس القيمة (طلب المالك: «يتم تحديث سعر الشقة»).
 * - [project]: الصورة كاملة لسنة البيع. المعدل: اللي اتكتب دلوقتي ⇒ المحفوظ على الأصل ⇒ الافتراضي لنوعه بمصدره ⇒ «غير متاح».
 */

data class AssetGrowthInput(
    /** null = مش عقار (الحقول التلاتة اللي بعدها لازم تبقى فاضية). */
    val valuation: RealEstateValuation? = null,
    val areaSqm: Quantity? = null,
    val pricePerSqmMinor: Halalas? = null,
    /** تاريخ سعر المتر — null ⇒ النهارده. */
    val pricePerSqmAsOf: IsoDate? = null,
    val rent: RentTerms = RentTerms(),
    val expectedRateBp: Int? = null,
)

/** المعدل جاي منين في التوقّع. */
enum class ProjectionRateFrom { TYPED_NOW, SAVED_ON_ASSET, DEFAULT, NONE }

data class AssetProjectionView(
    val asset: Asset,
    val projection: AssetProjection,
    val rateFrom: ProjectionRateFrom,
    /** الافتراضي لنوع الأصل (لو ليه نوع) — مصدره وسطر الجنيه. */
    val default: DefaultRate?,
    /** سطر المصدر للمعدل المستخدم. */
    val rateSourceText: String,
)

data class ManageAssetGrowthDeps(
    val assets: AssetRepository,
    val lots: AssetLotRepository,
    val sales: AssetSaleRepository,
    val prices: AssetPriceRepository,
    val clock: Clock,
)

class ManageAssetGrowth(private val deps: ManageAssetGrowthDeps) {
    private suspend fun findAsset(id: Id): Asset =
        deps.assets.listAll().find { it.id == id } ?: throw AssetError(uiText(TextKey.ASSET_NOT_FOUND))

    private fun today(): IsoDate = deps.clock.nowIso().take(10)

    suspend fun setProfile(assetId: Id, input: AssetGrowthInput): Asset {
        val asset = findAsset(assetId)
        if (input.valuation == null && (input.areaSqm != null || input.pricePerSqmMinor != null || input.pricePerSqmAsOf != null)) {
            throw AssetError(uiText(TextKey.GROWTH_AREA_NEEDS_REAL_ESTATE))
        }
        input.areaSqm?.let { assertQuantity(it); if (it <= 0) throw AssetError(uiText(TextKey.GROWTH_AREA_POSITIVE)) }
        input.pricePerSqmMinor?.let { if (it <= 0) throw AssetError(uiText(TextKey.GROWTH_PRICE_SQM_POSITIVE)) }
        input.pricePerSqmAsOf?.let { if (!isValidIsoDate(it)) throw AssetError(uiText(TextKey.ASSET_FIELD_NOT_DATE, uiText(TextKey.ASSET_LABEL_PRICE_DATE))) }
        checkRentTerms(input.rent)
        input.expectedRateBp?.let(::checkAnnualRate)
        val next = asset.copy(
            valuation = input.valuation,
            areaSqm = input.areaSqm,
            pricePerSqmMinor = input.pricePerSqmMinor,
            pricePerSqmAsOf = input.pricePerSqmMinor?.let { input.pricePerSqmAsOf ?: today() },
            monthlyRentMinor = input.rent.monthlyMinor,
            rentIncreaseBp = input.rent.yearlyIncreaseBp,
            vacantMonthsPerYear = input.rent.vacantMonthsPerYear,
            expectedRateBp = input.expectedRateBp,
        )
        deps.assets.save(next)
        syncAreaPrice(next)
        return next
    }

    /** سعر المتر الجديد (مثال المالك: كان 25,000 وبقى 40,000) ⇒ قيمة العقار وسعره بيتحدّثوا. */
    suspend fun updatePricePerSqm(assetId: Id, pricePerSqmMinor: Halalas, asOf: IsoDate? = null): Asset {
        val asset = findAsset(assetId)
        if (asset.valuation != RealEstateValuation.AREA) throw AssetError(uiText(TextKey.GROWTH_NOT_AREA_VALUED))
        return setProfile(assetId, profileOf(asset).copy(pricePerSqmMinor = pricePerSqmMinor, pricePerSqmAsOf = asOf))
    }

    /** بسعر المتر والاتنين معروفين والكمية المملوكة أكبر من صفر ⇒ سعر الوحدة = القيمة ÷ الكمية (يدوي، بتاريخ سعر المتر). */
    private suspend fun syncAreaPrice(asset: Asset) {
        if (asset.valuation != RealEstateValuation.AREA || asset.areaSqm == null || asset.pricePerSqmMinor == null) return
        val position = computePosition(asset.id, deps.lots.listByAsset(asset.id), deps.sales.listByAsset(asset.id))
        if (position.heldQuantity <= 0) return // مفيش شراء متسجل ⇒ التوقّع بياخد قيمة المساحة مباشرة، ومفيش سعر وحدة يتحسب
        val value = currentValueOf(asset, position) ?: return
        val perUnit = unitPriceOf(value, position.heldQuantity) ?: return
        deps.prices.save(AssetPrice(asset.id, perUnit, asset.pricePerSqmAsOf ?: today(), "manual"))
    }

    /**
     * الصورة كاملة لـ[assetId] لو اتباع سنة [sellYear]. [typedRateBp] رقم كتبه المستخدم دلوقتي (مش بيتحفظ — [setProfile] للحفظ).
     * [defaults] من `LoadDefaultRates` لبلد [countryCode] (null = الملف لسه ما وصلش ⇒ من غير افتراضي).
     */
    suspend fun project(assetId: Id, sellYear: Int, countryCode: String?, defaults: DefaultRatesView?, typedRateBp: Int? = null, today: IsoDate? = null): AssetProjectionView {
        val asset = findAsset(assetId)
        val day = today ?: today()
        val lots = deps.lots.listByAsset(asset.id)
        val price = deps.prices.listAll().firstOrNull { it.assetId == asset.id }
        val position = computePosition(asset.id, lots, deps.sales.listByAsset(asset.id), price, day)
        val cls = growthClassOf(asset, countryPack(countryCode).currency)
        val default = cls?.let { defaults?.of(it) }
        typedRateBp?.let(::checkAnnualRate)
        val (rate, from) = when {
            typedRateBp != null -> typedRateBp to ProjectionRateFrom.TYPED_NOW
            asset.expectedRateBp != null -> asset.expectedRateBp to ProjectionRateFrom.SAVED_ON_ASSET
            default?.rateBp != null -> default.rateBp to ProjectionRateFrom.DEFAULT
            else -> null to ProjectionRateFrom.NONE
        }
        val projection = projectAsset(
            currentValueMinor = currentValueOf(asset, position, lotsRecorded = lots.isNotEmpty()),
            costBasisMinor = if (lots.isEmpty()) null else position.costBasisMinor,
            purchasedAt = lots.minOfOrNull { it.purchasedAt },
            today = day,
            sellYear = sellYear,
            rateBp = rate,
            rent = RentTerms(asset.monthlyRentMinor, asset.rentIncreaseBp, asset.vacantMonthsPerYear),
        )
        val sourceText = when (from) {
            ProjectionRateFrom.TYPED_NOW, ProjectionRateFrom.SAVED_ON_ASSET -> uiText(TextKey.GROWTH_YOUR_RATE)
            ProjectionRateFrom.DEFAULT -> default!!.sourceText
            ProjectionRateFrom.NONE -> default?.reason ?: uiText(TextKey.GROWTH_NA_ASSET_RATE)
        }
        return AssetProjectionView(asset, projection, from, default, sourceText)
    }
}

/** الحقول المحفوظة على الأصل كمدخل (عشان تعديل خانة واحدة يسيب الباقي زي ما هو). */
fun profileOf(asset: Asset): AssetGrowthInput = AssetGrowthInput(
    asset.valuation, asset.areaSqm, asset.pricePerSqmMinor, asset.pricePerSqmAsOf,
    RentTerms(asset.monthlyRentMinor, asset.rentIncreaseBp, asset.vacantMonthsPerYear), asset.expectedRateBp,
)
