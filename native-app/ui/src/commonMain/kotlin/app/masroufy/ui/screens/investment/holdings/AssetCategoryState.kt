package app.masroufy.ui.screens.investment.holdings

import app.masroufy.core.GrowthBucket
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.Id
import app.masroufy.core.InvestHoldKey
import app.masroufy.core.InvestRatesState
import app.masroufy.core.addMoney
import app.masroufy.core.formatQuantity
import app.masroufy.core.groupBucket
import app.masroufy.core.uiText

/** صف في «اللي معاك» بصفحة النوع. [valueMinor] null ⇒ «غير متاح». */
data class CategoryRow(
    val assetId: Id,
    val type: HoldingType,
    val name: String,
    val sub: String,
    val valueMinor: Halalas?,
    val purpose: HoldingPurpose,
)

/** ليه التشارت ظاهر أو لأ (القاعدة 10 — §1.9): فيه متوسط ومعدلات وقيمة ⇒ تشارت · غير كده جملة بالسبب. */
enum class CategoryChart { CHART, NO_AVERAGE, RATES_NA, VALUE_NA }

/**
 * صفحة النوع من الصورة: [startMinor] = مجموع المعروف من المجموعة كلها (كل الأغراض — بداية التشارت) · [totalMinor] نفس المجموع
 * للعرض فوق (null لو مفيش ولا قيمة معروفة ⇒ مش ظاهر).
 */
data class CategoryUi(
    val group: HoldingGroup,
    val count: Int,
    val rows: List<CategoryRow>,
    val startMinor: Halalas,
    val totalMinor: Halalas?,
    val chart: CategoryChart,
)

fun categoryUi(snapshot: HoldingsSnapshot, group: HoldingGroup, rates: InvestRatesState?): CategoryUi {
    val items = snapshot.items.filter { it.group == group }
    val known = items.mapNotNull { it.valueMinor }
    val start = known.fold(0L) { a, v -> addMoney(a, v) }
    val chart = when {
        groupBucket(group) == GrowthBucket.NONE -> CategoryChart.NO_AVERAGE
        rates !is InvestRatesState.Ready -> CategoryChart.RATES_NA
        start <= 0L -> CategoryChart.VALUE_NA
        else -> CategoryChart.CHART
    }
    return CategoryUi(group, items.size, items.map(::rowOf), start, start.takeIf { known.isNotEmpty() }, chart)
}

private fun rowOf(item: HoldingValue): CategoryRow {
    val a = item.asset
    val qty = formatQuantity(item.quantity)
    val sub = when (item.type) {
        HoldingType.GOLD -> (item.karat?.let { uiText(InvestHoldKey.HOLD_SUB_GRAMS_KARAT, qty, it.toString()) } ?: uiText(InvestHoldKey.HOLD_SUB_GRAMS, qty)) +
            if (item.jewellery) uiText(InvestHoldKey.HOLD_SUB_JEWEL) else ""
        HoldingType.SILVER -> uiText(InvestHoldKey.HOLD_SUB_GRAMS, qty) + (item.fineness?.let { " · $it" } ?: "") +
            if (item.jewellery) uiText(InvestHoldKey.HOLD_SUB_JEWEL) else ""
        HoldingType.STK -> uiText(InvestHoldKey.HOLD_SUB_SHARES, qty)
        HoldingType.FLAT, HoldingType.LAND, HoldingType.SHOP -> listOfNotNull(
            a.areaSqm?.let { uiText(InvestHoldKey.HOLD_SUB_AREA, formatQuantity(it)) },
            a.place,
        ).joinToString(" · ").ifEmpty { uiText(typeKey(item.type)) } + if ((a.monthlyRentMinor ?: 0L) > 0L) uiText(InvestHoldKey.HOLD_SUB_RENTED) else ""
        HoldingType.CO -> a.sharePercentBp?.let { uiText(InvestHoldKey.HOLD_SUB_SHARE_PCT, percentText(it)) } ?: uiText(typeKey(item.type))
        else -> uiText(typeKey(item.type))
    }
    return CategoryRow(a.id, item.type, a.name, sub, item.valueMinor, item.purpose)
}

/** 1250 نقطة أساس ⇒ «12.5» — قسمة صحيحة للعرض. */
private fun percentText(bp: Int): String {
    val whole = bp / 100
    val frac = bp % 100
    return if (frac == 0) whole.toString() else "$whole." + (if (frac % 10 == 0) (frac / 10).toString() else frac.toString().padStart(2, '0'))
}

/** «شيء واحد · بأسعار اليوم». */
internal fun categorySub(count: Int): String = countText(count) + uiText(InvestHoldKey.HOLD_CAT_TODAY_SUFFIX)

/** السنين المتاحة في صفحة النوع والافتراضي 10 (§1.9). */
val CATEGORY_SPANS = listOf(5, 10, 20)
