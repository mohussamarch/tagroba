package app.masroufy.usecase

import app.masroufy.core.AverageEntry
import app.masroufy.core.Currency
import app.masroufy.core.FeedPrice
import app.masroufy.core.GOLD_KARATS
import app.masroufy.core.GrowthBucket
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.Id
import app.masroufy.core.InvestRatesState
import app.masroufy.core.Quantity
import app.masroufy.core.AssetError
import app.masroufy.core.TextKey
import app.masroufy.core.groupBucket
import app.masroufy.core.pricedAtPurity
import app.masroufy.core.growHoldingGroup
import app.masroufy.core.uiText
import app.masroufy.core.valueOfQuantity
import app.masroufy.port.AssetRepository

/** سطر من ملف الأسعار بعملة البلد (`feedPriceIn`: الرمز، أو الرمز + «_EGP»…) — التشغيل بيقرا `LoadOnlineFeeds.prices()`. */
fun interface FeedPriceLookup {
    suspend fun find(symbol: String, currency: Currency): FeedPrice?
}

/** رمز سعر المعدن في الملف: الدهب سطر لكل عيار (`GOLD_21K_GRAM`) · الفضة `SILVER_GRAM` — ومصر بـ«_EGP» (§2.6). عيار مش في الملف ⇒ null. */
fun metalFeedSymbol(type: HoldingType, karatOrFineness: Int, currency: Currency): String? {
    val base = when (type) {
        HoldingType.GOLD -> if (karatOrFineness in GOLD_KARATS) "GOLD_${karatOrFineness}K_GRAM" else return null
        HoldingType.SILVER -> "SILVER_GRAM"
        else -> return null
    }
    return if (currency == Currency.EGP) base + "_EGP" else base
}

/** «قيمته النهارده ≈» في «ضيف حاجة»: الجرامات × سعر جرام العيار (الفضة: الصافي × النقاوة) النهارده من الملف — null لو الملف أو السطر مش موجود (مش صفر). */
class PreviewHoldingValue(private val feedPrice: FeedPriceLookup) {
    suspend fun metalToday(type: HoldingType, karatOrFineness: Int, grams: Quantity, currency: Currency): Halalas? {
        if (grams <= 0L) return null
        val symbol = metalFeedSymbol(type, karatOrFineness, currency) ?: return null
        val price = runCatching { feedPrice.find(symbol, currency) }.getOrNull() ?: return null
        // الفضة: السطر سعر الصافي ⇒ × النقاوة ÷ 1000 (نفس سعر الأصل بعد الحفظ — `core/FeedPurity.kt`)
        val fineness = karatOrFineness.takeIf { type == HoldingType.SILVER }
        return valueOfQuantity(grams, pricedAtPurity(price.pricePerUnitMinor, fineness))
    }
}

/** خطّين صفحة النوع لكل سنة (0..السنين) بفلوس النهارده + المعدل ومصدره (القاعدة 10: المصدر بيتكتب تحت التشارت). */
data class GroupProjection(val leave: List<Halalas>, val plan: List<Halalas>, val rateBp: Int, val source: AverageEntry)

/**
 * توقّع صفحة النوع (نقل `grow()` — §2.6): المعادن بمتوسط الدهب · العقار بالغلاء · الأسهم بمتوسط الأسهم. null ⇒ مفيش تشارت:
 * النوع من غير متوسط رسمي · المعدلات مش متاحة · القيمة صفر أو مش معروفة.
 */
class ProjectHoldingGroup {
    fun project(startMinor: Halalas, addMinor: Halalas, group: HoldingGroup, rates: InvestRatesState?, years: Int): GroupProjection? {
        val r = (rates as? InvestRatesState.Ready)?.rates ?: return null
        if (startMinor <= 0L || addMinor < 0L) return null
        val (bp, source) = when (groupBucket(group)) {
            GrowthBucket.GOLD -> r.goldBp to r.gold
            GrowthBucket.PROPERTY -> r.cpiBp to r.cpi
            GrowthBucket.SHARES -> r.sharesBp to r.shares
            GrowthBucket.NONE -> return null
        }
        val g = growHoldingGroup(startMinor, addMinor, bp, r.cpiBp, years)
        return GroupProjection(g.leave, g.plan, bp, source)
    }
}

/** «خطتي: أزوّد كل شهر» — المدى والخطوة والافتراضي لكل بلد (§1.9): السعودية 0..5,000 ر.س كل 50 (500) · مصر 0..20,000 ج.م كل 250 (2,000). */
data class PlanSlider(val maxMinor: Halalas, val stepMinor: Halalas, val defaultMinor: Halalas) {
    val steps: Int get() = (maxMinor / stepMinor).toInt()
    fun atStep(i: Int): Halalas = i.coerceIn(0, steps).toLong() * stepMinor
    fun stepOf(minor: Halalas): Int = (minor / stepMinor).toInt().coerceIn(0, steps)
}

fun planSliderFor(currency: Currency): PlanSlider =
    if (currency == Currency.EGP) PlanSlider(2_000_000L, 25_000L, 200_000L) else PlanSlider(500_000L, 5_000L, 50_000L)

/** تغيير الغرض من «تفاصيل الأصل» (§4.2). */
class SetHoldingPurpose(private val assets: AssetRepository) {
    suspend fun set(assetId: Id, purpose: HoldingPurpose) {
        val asset = assets.listAll().firstOrNull { it.id == assetId } ?: throw AssetError(uiText(TextKey.ASSET_NOT_FOUND))
        assets.save(asset.copy(purpose = purpose.wire))
    }
}
