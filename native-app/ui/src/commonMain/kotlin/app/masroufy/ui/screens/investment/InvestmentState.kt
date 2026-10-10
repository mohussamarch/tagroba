package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import app.masroufy.core.ASSET_KIND_LABELS
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PriceState
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.TextKey
import app.masroufy.core.displayKind
import app.masroufy.core.isRealEstate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.usecase.AssetProjectionView
import app.masroufy.usecase.AssetRow
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.PortfolioView
import app.masroufy.usecase.ProjectionRateFrom

/**
 * «الاستثمار» — من نتايج حالات الاستخدام لشكل الشاشة (`ManageAssets.listPortfolio` · `ManageAssetGrowth.project` · `LoadOnlineFeeds`).
 * **مفيش حساب فلوس هنا:** كل مبلغ جاي من حالة الاستخدام زي ما هو، و`null` بيفضل `null` («غير متاح» — CLAUDE.md #10).
 * ⚠️ تقسيم الإجمالي الملوّن (عقار ٨٠٪ · ذهب ٧٪ …) مالوش حالة استخدام ⇒ مش مرسوم (HANDOVER «ناقص في المنطق»).
 */
data class InvestmentUi(
    val currency: Currency,
    val empty: Boolean,
    val totalMinor: Halalas?,
    /** ليه الإجمالي «غير متاح» (أصل أو أكتر من غير سعر) — null لو الإجمالي معروف. */
    val totalNa: String?,
    val costMinor: Halalas,
    val unrealizedMinor: Halalas?,
    /** المكسب المحقق من كل البيع (`PortfolioTotals`) — null لو مفيش بيع خالص ⇒ السطر مش بيظهر (مش صفر). */
    val realizedMinor: Halalas?,
    val estates: List<EstateCard>,
    val others: List<AssetLine>,
)

/** كارت العقار و«كم ستساوي لو بعتها؟» ([ManageAssetGrowth.project] بسنة البيع المعروضة). */
data class EstateCard(
    val assetId: Id,
    val name: String,
    val subtitle: String,
    val valueMinor: Halalas?,
    val ask: String,
    val saleMinor: Halalas?,
    val rentLabel: String,
    val rentMinor: Halalas?,
    val gainMinor: Halalas?,
    /** «بزيادة ١٫٧٤٪ سنويًا = المصدر» أو سبب «غير متاح». */
    val rateLine: String,
)

/** صف أصل (غير العقار — والمؤرشف في الآخر). */
data class AssetLine(val assetId: Id, val name: String, val kind: String, val subtitle: String, val valueMinor: Halalas?, val archived: Boolean)

/**
 * سنة البيع في كارت العقار لحد ما تبقى محفوظة على الأصل: بعد ٤ سنين زي النموذج (٢٠٢٦ ⇒ ٢٠٣٠). ⚠️ `Asset` مالوش «سنة البيع»
 * (`ManageAssetGrowth.setProfile` ما بيحفظهاش) — HANDOVER «ناقص في المنطق».
 */
const val DEFAULT_SELL_YEARS_AHEAD = 4

fun defaultSellYear(today: IsoDate): Int = parseIsoDate(today).year + DEFAULT_SELL_YEARS_AHEAD

fun investmentUi(view: PortfolioView, projections: Map<Id, AssetProjectionView>, currency: Currency, today: IsoDate): InvestmentUi {
    val estates = view.rows.filter { it.asset.isRealEstate && !it.asset.archived }.map { estateCard(it, projections[it.asset.id], currency) }
    val others = view.rows.filter { !(it.asset.isRealEstate && !it.asset.archived) }.map { assetLine(it, today) }
    val missing = view.rows.filter { !it.asset.archived && it.position.marketValueMinor == null }
    val totalNa = when {
        view.totals.marketValueMinor != null -> null
        missing.size == 1 -> uiText(UiKey.INVEST_TOTAL_NA_ONE, missing.single().asset.name)
        else -> uiText(UiKey.INVEST_TOTAL_NA_MANY, sentenceNumber(view.totals.assetsWithoutPrice))
    }
    return InvestmentUi(
        currency = currency,
        empty = view.rows.isEmpty(),
        totalMinor = view.totals.marketValueMinor,
        totalNa = totalNa,
        costMinor = view.totals.costBasisMinor,
        unrealizedMinor = view.totals.unrealizedGainMinor,
        realizedMinor = view.totals.realizedGainMinor.takeIf { view.rows.any { it.sales.isNotEmpty() } },
        estates = estates,
        others = others,
    )
}

private fun estateCard(row: AssetRow, p: AssetProjectionView?, currency: Currency): EstateCard {
    val a = row.asset
    val area = a.areaSqm
    val sqm = a.pricePerSqmMinor
    val subtitle = if (a.valuation == RealEstateValuation.AREA && area != null && sqm != null) {
        uiText(UiKey.INVEST_RE_SUB_AREA, qtyText(area), moneyText(sqm, currency))
    } else {
        uiText(TextKey.ASSET_KIND_REAL_ESTATE)
    }
    val proj = p?.projection
    val year = proj?.sellYear
    val rate = proj?.rateBp
    val rateLine = when {
        p == null -> uiText(TextKey.NOT_AVAILABLE)
        rate != null && p.rateFrom != ProjectionRateFrom.NONE -> uiText(UiKey.INVEST_RE_RATE, pctText(rate), p.rateSourceText)
        else -> p.rateSourceText
    }
    return EstateCard(
        assetId = a.id,
        name = a.name,
        subtitle = subtitle,
        valueMinor = proj?.currentValueMinor ?: row.position.marketValueMinor.takeIf { row.lots.isNotEmpty() },
        ask = uiText(UiKey.INVEST_RE_ASK, year?.let(::yearText) ?: uiText(TextKey.NOT_AVAILABLE)),
        saleMinor = proj?.valueAtSaleMinor,
        rentLabel = uiText(UiKey.INVEST_RE_RENT_UNTIL, year?.let(::yearText) ?: ""),
        rentMinor = proj?.rentTotalMinor,
        gainMinor = proj?.totalGainMinor,
        rateLine = rateLine,
    )
}

/** سطر الأصل: الكمية + حالة السعر (اليوم · بتاريخ · قديم · غير متاح) أو «مؤرشف». */
internal fun assetLine(row: AssetRow, today: IsoDate): AssetLine {
    val a = row.asset
    val p = row.position
    val qty = qtyUnit(p.heldQuantity, a.unitLabel)
    val state = when {
        a.archived -> uiText(UiKey.INVEST_ARCHIVED_SUB)
        else -> priceStateText(p.priceState, today)
    }
    val kindLabel = ASSET_KIND_LABELS[a.displayKind] ?: a.displayKind
    val sub = if (p.heldQuantity > 0) uiText(UiKey.INVEST_ROW_SUB, qty, state) else uiText(UiKey.INVEST_ROW_SUB, kindLabel, state)
    return AssetLine(a.id, a.name, a.displayKind, sub, p.marketValueMinor, a.archived)
}

internal fun priceStateText(state: PriceState, today: IsoDate): String = when (state) {
    PriceState.Missing -> uiText(UiKey.INVEST_PRICE_NONE)
    is PriceState.Fresh -> if (state.price.asOf == today) uiText(UiKey.INVEST_PRICE_TODAY) else uiText(UiKey.INVEST_PRICE_ON, dateText(state.price.asOf))
    is PriceState.Stale -> uiText(UiKey.INVEST_PRICE_OLD, dateText(state.price.asOf))
}

/** شريط «تعذّر تحديث الأسعار»: الملف ما نزلش دلوقتي (والمعروض بآخر نسخة) أو ما نزلش خالص (بسببه). null = مفيش مشكلة. */
fun feedProblem(state: FeedState<*>?): String? = when (state) {
    null -> null
    is FeedState.Ready -> if (state.refreshFailed != null) uiText(UiKey.INVEST_ERROR_BODY) else null
    is FeedState.Unavailable -> state.reason
}

/** «٣ خطط» بقاعدة العدد العربي (١ · ٢ · ٣–١٠ · ١١+). */
fun goalsHint(count: Int): String = when {
    count <= 0 -> uiText(UiKey.INVEST_GOALS_NONE)
    count == 1 -> uiText(UiKey.INVEST_GOALS_ONE)
    count == 2 -> uiText(UiKey.INVEST_GOALS_TWO)
    count <= 10 -> uiText(UiKey.INVEST_GOALS_FEW, sentenceNumber(count))
    else -> uiText(UiKey.INVEST_GOALS_MANY, sentenceNumber(count))
}
