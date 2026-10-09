package app.masroufy.ui.screens.investment

import app.masroufy.core.ASSET_KIND_LABELS
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PriceState
import app.masroufy.core.Quantity
import app.masroufy.core.RealEstateValuation
import app.masroufy.core.TextKey
import app.masroufy.core.displayKind
import app.masroufy.core.isRealEstate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.usecase.AssetProjectionView
import app.masroufy.usecase.AssetRow

/**
 * «تفاصيل الأصل» — من `ManageAssets.listPortfolio` (صف الأصل بمركزه وشراءاته ومبيعاته) و`ManageAssetGrowth.project` (قيمة العقار).
 * عرض بس: التكلفة والمكسب المحقق وغير المحقق والقيمة من المركز زي ما هم. ⚠️ «متوسط التكلفة للوحدة» و«مكسب كل بيعة» مالهمش حالة استخدام
 * ⇒ «غير متاح» والسطر من غير مكسبه (HANDOVER «ناقص في المنطق»).
 */
data class AssetDetailUi(
    val assetId: Id,
    val name: String,
    val kindLabel: String,
    val unit: String,
    val currency: Currency,
    val archived: Boolean,
    val realEstate: Boolean,
    /** القيمة النهارده — null ⇒ «غير متاح» + [naReason]. */
    val valueMinor: Halalas?,
    val naReason: String,
    val priceLine: String,
    val chip: PriceChip,
    val unrealizedMinor: Halalas?,
    val heldQuantity: Quantity,
    val qtyText: String,
    val qtySub: String,
    val avgSub: String,
    val costMinor: Halalas,
    /** null ⇒ مفيش بيع (بيتكتب «لا مبيعات»). */
    val realizedMinor: Halalas?,
    val realizedSub: String,
    /** مربوط برمز في ملف الأسعار. */
    val linked: Boolean,
    /** السعر قديم والأصل مربوط ⇒ شريط «تعذّر تحديث الأسعار». */
    val staleLinked: Boolean,
    val sourceTitle: String,
    val sourceBody: String,
    val log: List<LogRow>,
)

enum class PriceChip(val key: TextKey) {
    FRESH(TextKey.ASSET_DETAIL_CHIP_FRESH),
    STALE(TextKey.ASSET_DETAIL_CHIP_STALE),
    MANUAL(TextKey.ASSET_DETAIL_CHIP_MANUAL),
    MISSING(TextKey.ASSET_DETAIL_CHIP_MISSING),
}

enum class TradeKind { BUY, SELL }

/** سطر في «المشتريات والمبيعات»: الشراء بمبلغه (أحمر) والبيع بمبلغه (أخضر) — الرسوم في السطر التاني. */
data class LogRow(val kind: TradeKind, val date: String, val sortKey: String, val amountMinor: Halalas, val line: String, val linked: Boolean)

fun assetDetailUi(row: AssetRow, projection: AssetProjectionView?, currency: Currency, today: IsoDate): AssetDetailUi {
    val a = row.asset
    val p = row.position
    val realEstate = a.isRealEstate
    val unit = a.unitLabel
    // العقار من «الصورة كاملة» (المساحة × سعر المتر أو سعره) · غيره من المركز (من غير شراء = صفر مملوك — معروف، مش مجهول)
    val value = if (realEstate) projection?.projection?.currentValueMinor else p.marketValueMinor
    val byArea = a.valuation == RealEstateValuation.AREA
    val area = a.areaSqm
    val sqm = a.pricePerSqmMinor
    val priceLine = when {
        byArea && area != null && sqm != null ->
            uiText(TextKey.ASSET_DETAIL_AREA_LINE, qtyText(area), moneyText(sqm, currency), dateOrToday(a.pricePerSqmAsOf ?: today, today))
        byArea -> uiText(TextKey.ASSET_DETAIL_AREA_NONE)
        else -> when (val s = p.priceState) {
            PriceState.Missing -> uiText(TextKey.ASSET_DETAIL_PRICE_NONE)
            is PriceState.Fresh -> uiText(TextKey.ASSET_DETAIL_PRICE_LINE, moneyText(s.price.pricePerUnitMinor, currency), unit, dateOrToday(s.price.asOf, today))
            is PriceState.Stale -> uiText(TextKey.ASSET_DETAIL_PRICE_LINE, moneyText(s.price.pricePerUnitMinor, currency), unit, dateOrToday(s.price.asOf, today))
        }
    }
    val linked = !a.feedSymbol.isNullOrEmpty()
    val chip = when {
        value == null || (!realEstate && p.priceState == PriceState.Missing) -> PriceChip.MISSING
        byArea || !linked -> PriceChip.MANUAL
        p.priceState is PriceState.Stale -> PriceChip.STALE
        p.priceState is PriceState.Fresh && (p.priceState as PriceState.Fresh).price.source == "manual" -> PriceChip.MANUAL
        else -> PriceChip.FRESH
    }
    val kindLabel = ASSET_KIND_LABELS[a.displayKind] ?: a.displayKind
    val log = row.lots.map { lot ->
        LogRow(TradeKind.BUY, dateText(lot.purchasedAt), lot.purchasedAt + "0", lot.principalMinor, feeLine(lot.quantity, unit, lot.feeMinor, currency), lot.transactionId != null)
    } + row.sales.map { sale ->
        LogRow(TradeKind.SELL, dateText(sale.soldAt), sale.soldAt + "1", sale.grossProceedsMinor, feeLine(sale.quantity, unit, sale.feeMinor, currency), sale.transactionId != null)
    }
    return AssetDetailUi(
        assetId = a.id,
        name = a.name,
        kindLabel = kindLabel,
        unit = unit,
        currency = currency,
        archived = a.archived,
        realEstate = realEstate,
        valueMinor = value,
        naReason = uiText(if (realEstate) TextKey.ASSET_DETAIL_NA_RE else TextKey.ASSET_DETAIL_NA_PRICE),
        priceLine = priceLine,
        chip = chip,
        unrealizedMinor = if (realEstate) projection?.projection?.gainNowMinor else p.unrealizedGainMinor,
        heldQuantity = p.heldQuantity,
        qtyText = qtyUnit(p.heldQuantity, unit),
        qtySub = if (realEstate && area != null) uiText(TextKey.INVEST_QTY_UNIT, qtyText(area), uiText(TextKey.ASSET_PROJ_SQM_UNIT)) else kindLabel,
        avgSub = uiText(TextKey.ASSET_DETAIL_AVG_SUB, unit),
        costMinor = p.costBasisMinor,
        realizedMinor = if (row.sales.isEmpty()) null else p.realizedGainMinor,
        realizedSub = if (row.sales.isEmpty()) uiText(TextKey.ASSET_DETAIL_NOT_SOLD) else uiText(TextKey.ASSET_DETAIL_FROM_SALES, sentenceNumber(row.sales.size)),
        linked = linked,
        staleLinked = linked && p.priceState is PriceState.Stale,
        sourceTitle = uiText(if (byArea) TextKey.ASSET_DETAIL_SRC_SQM else if (linked) TextKey.ASSET_DETAIL_SRC_LINKED else TextKey.ASSET_DETAIL_SRC_MANUAL),
        sourceBody = when {
            byArea -> uiText(TextKey.ASSET_DETAIL_SRC_SQM_BODY)
            linked -> uiText(TextKey.ASSET_DETAIL_SRC_LINKED_BODY, a.feedSymbol!!)
            else -> uiText(TextKey.ASSET_DETAIL_SRC_MANUAL_BODY)
        },
        log = log.sortedByDescending { it.sortKey },
    )
}

private fun feeLine(q: Quantity, unit: String, fee: Halalas, currency: Currency): String =
    if (fee > 0) uiText(TextKey.ASSET_DETAIL_LINE_FEE, qtyUnit(q, unit), moneyText(fee, currency))
    else uiText(TextKey.ASSET_DETAIL_LINE_NO_FEE, qtyUnit(q, unit))
