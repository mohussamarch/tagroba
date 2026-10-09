package app.masroufy.ui.screens.investment

import app.masroufy.core.Asset
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.RentTerms
import app.masroufy.core.TextKey
import app.masroufy.core.formatBp
import app.masroufy.core.formatQuantity
import app.masroufy.core.isRealEstate
import app.masroufy.core.normalizeDigits
import app.masroufy.core.parseMoney
import app.masroufy.core.parseQuantity
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.text.amount
import app.masroufy.usecase.AssetGrowthInput
import app.masroufy.usecase.AssetProjectionView
import app.masroufy.usecase.ProjectionRateFrom

/**
 * «الصورة كاملة» (`AssetProjection`) — الخانات زي ما اتكتبت ⇒ مدخل `ManageAssetGrowth.setProfile` (قراية بس)، ونتيجة
 * `ManageAssetGrowth.project` ⇒ شكل الشاشة. **الحساب كله في حالة الاستخدام:** النتيجة بتتعرض بعد الحفظ (والنسبة المكتوبة دلوقتي
 * بتتبعت `typedRateBp` من غير حفظ). ⚠️ «المجموع» (سعر البيع + الإيجار) و«سنة البيع» المحفوظة مالهمش حالة استخدام (HANDOVER).
 */
data class ProjectionDraft(
    val method: RealEstateValuation? = null,
    val area: String = "",
    val sqm: String = "",
    val whole: String = "",
    val rent: String = "",
    val increase: String = "",
    val vacant: String = "",
    val rate: String = "",
)

/** الخانات من المحفوظ على الأصل (عشان التعديل يبدأ من اللي موجود). [wholeMinor] قيمة العقار الحالية لو «القيمة كاملة». */
fun draftOf(asset: Asset, currency: Currency, wholeMinor: Halalas?): ProjectionDraft = ProjectionDraft(
    method = asset.valuation ?: if (asset.isRealEstate) RealEstateValuation.AREA else null,
    area = asset.areaSqm?.let(::formatQuantity).orEmpty(),
    sqm = asset.pricePerSqmMinor?.let { amount(it, currency) }.orEmpty(),
    whole = wholeMinor?.takeIf { asset.valuation == RealEstateValuation.WHOLE }?.let { amount(it, currency) }.orEmpty(),
    rent = asset.monthlyRentMinor?.let { amount(it, currency) }.orEmpty(),
    increase = asset.rentIncreaseBp?.let { formatBp(it).removeSuffix("%") }.orEmpty(),
    vacant = asset.vacantMonthsPerYear?.toString().orEmpty(),
)

sealed interface DraftParse {
    /** [typedRateBp] النسبة المكتوبة دلوقتي (null = فاضية). [wholeMinor] «القيمة كاملة» لو اتكتبت (بتتحفظ سعر للأصل). */
    data class Ok(val input: AssetGrowthInput, val typedRateBp: Int?, val wholeMinor: Halalas?) : DraftParse
    data class Invalid(val message: String) : DraftParse
}

/** نسبة مكتوبة («1.5» ⇒ 150 نقطة أساس) — نفس قارئ المبالغ (رقمين بعد العلامة بالظبط، من غير تقريب). */
private fun bpOf(text: String, currency: Currency): Int? = text.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency).toInt() }

fun parseDraft(d: ProjectionDraft, asset: Asset, currency: Currency): DraftParse {
    val vacantText = normalizeDigits(d.vacant).trim()
    val vacant = if (vacantText.isEmpty()) null else vacantText.toIntOrNull()
    if (vacantText.isNotEmpty() && vacant == null) return DraftParse.Invalid(uiText(TextKey.ASSET_PROJ_ERR_NUMBER))
    if (vacant != null && vacant !in 0..12) return DraftParse.Invalid(uiText(TextKey.ASSET_PROJ_ERR_VACANT))
    return try {
        val byArea = d.method == RealEstateValuation.AREA
        val typed = bpOf(d.rate, currency)
        val input = AssetGrowthInput(
            valuation = if (asset.isRealEstate || d.method != null) d.method else null,
            areaSqm = if (byArea) d.area.trim().takeIf { it.isNotEmpty() }?.let(::parseQuantity) else null,
            pricePerSqmMinor = if (byArea) d.sqm.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency) } else null,
            pricePerSqmAsOf = if (byArea && d.sqm.trim() == asset.pricePerSqmMinor?.let { amount(it, currency) }) asset.pricePerSqmAsOf else null,
            rent = RentTerms(d.rent.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency) }, bpOf(d.increase, currency), vacant),
            expectedRateBp = typed ?: asset.expectedRateBp,
        )
        val whole = if (d.method == RealEstateValuation.WHOLE) d.whole.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency) } else null
        DraftParse.Ok(input, typed, whole)
    } catch (e: IllegalArgumentException) {
        DraftParse.Invalid(uiText(TextKey.ASSET_PROJ_ERR_NUMBER))
    }
}

enum class RateChip(val key: TextKey) {
    TYPED(TextKey.ASSET_PROJ_RATE_TYPED),
    SAVED(TextKey.ASSET_PROJ_RATE_SAVED),
    DEFAULT(TextKey.ASSET_PROJ_RATE_DEFAULT),
    OLD(TextKey.ASSET_PROJ_RATE_OLD),
    MISSING(TextKey.ASSET_PROJ_RATE_MISSING),
}

data class ProjectionUi(
    val name: String,
    val subtitle: String,
    val realEstate: Boolean,
    val currency: Currency,
    val valueMinor: Halalas?,
    val sellYear: Int,
    val yearsLabel: String,
    val chip: RateChip,
    /** «١٫٧٤٪ سنويًا» أو «غير متاح». */
    val rateValue: String,
    val rateKnown: Boolean,
    val rateSource: String,
    val resultTitle: String,
    val saleLabel: String,
    val saleMinor: Halalas?,
    val rentLabel: String,
    val rentMinor: Halalas,
    val gainMinor: Halalas?,
    /** الناقص وإيه اللي يتكتب (من `ProjectionGap`) — فاضي لو كله معروف. */
    val gaps: List<String>,
    /** المعدل الافتراضي من ملف قديم ⇒ «تقريبي». */
    val approx: Boolean,
)

fun projectionUi(v: AssetProjectionView, currency: Currency): ProjectionUi {
    val p = v.projection
    val chip = when (v.rateFrom) {
        ProjectionRateFrom.TYPED_NOW -> RateChip.TYPED
        ProjectionRateFrom.SAVED_ON_ASSET -> RateChip.SAVED
        ProjectionRateFrom.DEFAULT -> if (v.default?.stale == true) RateChip.OLD else RateChip.DEFAULT
        ProjectionRateFrom.NONE -> RateChip.MISSING
    }
    val year = yearText(p.sellYear)
    val bought = p.purchasedAt
    val cost = p.costBasisMinor
    val subtitle = if (cost != null && bought != null) {
        uiText(TextKey.ASSET_PROJ_SUB, v.asset.name, dateText(bought), moneyText(cost, currency))
    } else {
        uiText(TextKey.ASSET_PROJ_SUB_NO_COST, v.asset.name)
    }
    return ProjectionUi(
        name = v.asset.name,
        subtitle = subtitle,
        realEstate = v.asset.isRealEstate,
        currency = currency,
        valueMinor = p.currentValueMinor,
        sellYear = p.sellYear,
        yearsLabel = yearsAhead(p.years),
        chip = chip,
        rateValue = p.rateBp?.let { uiText(TextKey.ASSET_PROJ_RATE_VALUE, pctText(it)) } ?: uiText(TextKey.NOT_AVAILABLE),
        rateKnown = p.rateBp != null,
        rateSource = v.rateSourceText,
        resultTitle = uiText(TextKey.ASSET_PROJ_RESULT, year),
        saleLabel = uiText(TextKey.ASSET_PROJ_SALE_PRICE, year),
        saleMinor = p.valueAtSaleMinor,
        rentLabel = uiText(TextKey.INVEST_RE_RENT_UNTIL, year),
        rentMinor = p.rentTotalMinor,
        gainMinor = p.totalGainMinor,
        gaps = p.gaps.map { it.text },
        approx = chip == RateChip.OLD,
    )
}

/** «بعد ٤ سنوات» بقاعدة العدد (صفر · ١ · ٢ · ٣–١٠ · ١١+). */
fun yearsAhead(years: Int): String = when {
    years <= 0 -> uiText(TextKey.ASSET_PROJ_YEARS_ZERO)
    years == 1 -> uiText(TextKey.ASSET_PROJ_YEARS_ONE)
    years == 2 -> uiText(TextKey.ASSET_PROJ_YEARS_TWO)
    years <= 10 -> uiText(TextKey.ASSET_PROJ_YEARS_FEW, sentenceNumber(years))
    else -> uiText(TextKey.ASSET_PROJ_YEARS_MANY, sentenceNumber(years))
}
