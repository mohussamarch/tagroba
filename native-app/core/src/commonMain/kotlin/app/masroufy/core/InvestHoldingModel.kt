package app.masroufy.core

/**
 * «الاستثمار» الجديد — العقد المشترك بين الحزم التلاتة (FUTURE · HOLDINGS · INSIGHTS) حسب مواصفة النقل §4.0 بند 1.
 * ⚠️ نسخة INSIGHTS (r05-insights): الحزم اتبنت بالتوازي من غير «الخطوة 0»، فكل حزمة محتاجة الأنواع دي تكتبها بنفس الأسامي.
 * وقت الدمج: نسخة واحدة بس تفضل (الأكمل — غالبًا HOLDINGS)، وكود INSIGHTS بيستخدم بس: [HoldingsSnapshot.items] ·
 * [HoldingValue.asset]/[HoldingValue.purpose]/[HoldingValue.valueMinor] · [investableSplit] · [InvestRatesState] · [holdingsMixBp].
 */

/** مجموعة «حاجاتي» (المربعات). */
enum class HoldingGroup(val wire: String) {
    METAL("metal"), PROPERTY("property"), SHARES("shares"), PERSONAL("personal"), COLLECTIBLES("collectibles"), OTHER("other");

    companion object {
        fun fromWire(wire: String?): HoldingGroup? = entries.firstOrNull { it.wire == wire }
    }
}

/** الغرض: استثمار · احتياطي · تجارة · شخصي (الشخصي برّه «كل فلوسك» وبرّه التوقع — Q1). */
enum class HoldingPurpose(val wire: String) {
    INVEST("invest"), RESERVE("reserve"), TRADE("trade"), PERSONAL("personal");

    companion object {
        fun fromWire(wire: String?): HoldingPurpose? = entries.firstOrNull { it.wire == wire }
    }
}

/** بيكبر بإيه في التوقع: العقار بالغلاء · الدهب بمتوسطه · الأسهم بمؤشرها · الباقي ما بيكبرش. */
enum class GrowthBucket { PROPERTY, GOLD, SHARES, NONE }

/** نوع الحاجة (جدول §2.6). */
enum class HoldingType(val wire: String, val group: HoldingGroup, val defaultPurpose: HoldingPurpose) {
    GOLD("gold", HoldingGroup.METAL, HoldingPurpose.INVEST),
    SILVER("silver", HoldingGroup.METAL, HoldingPurpose.RESERVE),
    FLAT("flat", HoldingGroup.PROPERTY, HoldingPurpose.INVEST),
    LAND("land", HoldingGroup.PROPERTY, HoldingPurpose.INVEST),
    SHOP("shop", HoldingGroup.PROPERTY, HoldingPurpose.TRADE),
    STK("stk", HoldingGroup.SHARES, HoldingPurpose.INVEST),
    CO("co", HoldingGroup.SHARES, HoldingPurpose.INVEST),
    CAR("car", HoldingGroup.PERSONAL, HoldingPurpose.PERSONAL),
    PHONE("phone", HoldingGroup.PERSONAL, HoldingPurpose.PERSONAL),
    PC("pc", HoldingGroup.PERSONAL, HoldingPurpose.PERSONAL),
    WATCH("watch", HoldingGroup.COLLECTIBLES, HoldingPurpose.PERSONAL),
    OUD("oud", HoldingGroup.COLLECTIBLES, HoldingPurpose.PERSONAL),
    ART("art", HoldingGroup.COLLECTIBLES, HoldingPurpose.PERSONAL),
    OTHER("other", HoldingGroup.OTHER, HoldingPurpose.PERSONAL);

    companion object {
        fun fromWire(wire: String?): HoldingType? = entries.firstOrNull { it.wire == wire }
    }
}

fun HoldingType.bucket(): GrowthBucket = when (this) {
    HoldingType.GOLD -> GrowthBucket.GOLD
    HoldingType.FLAT, HoldingType.LAND, HoldingType.SHOP -> GrowthBucket.PROPERTY
    HoldingType.STK, HoldingType.CO -> GrowthBucket.SHARES
    else -> GrowthBucket.NONE // الفضة: مفيش متوسط رسمي (Q4) ⇒ ثابتة
}

/** النوع من الأصل القديم (من غير `holdingType`): دهب · فضة · سهم/صندوق ⇒ أسهم · عقار ⇒ شقة · الباقي ⇒ «تاني». */
fun holdingTypeOf(asset: Asset): HoldingType = when {
    asset.realEstate -> HoldingType.FLAT
    asset.kind == "gold" -> HoldingType.GOLD
    asset.kind == "silver" -> HoldingType.SILVER
    asset.kind == "stock" || asset.kind == "fund" -> HoldingType.STK
    else -> HoldingType.OTHER
}

/** الغرض: الشبكة (`ZakatFact.purpose == WEAR`) ⇒ شخصي، وإلا الافتراضي للنوع. */
fun purposeOf(asset: Asset, jewellery: Boolean): HoldingPurpose =
    if (jewellery) HoldingPurpose.PERSONAL else holdingTypeOf(asset).defaultPurpose

/** حاجة واحدة بقيمتها النهارده. [valueMinor] null = القيمة مش معروفة (برّه المجاميع ومعدودة). */
data class HoldingValue(val asset: Asset, val type: HoldingType, val purpose: HoldingPurpose, val valueMinor: Halalas?)

/** «حاجاتي» مرة واحدة لكل تحميل (الأصول غير المؤرشفة) — [goldPriceMissing] = سعر الدهب النهارده مش موجود. */
data class HoldingsSnapshot(val currency: Currency, val items: List<HoldingValue>, val goldPriceMissing: Boolean)

/** اللي بيدخل التوقع (غير الشخصي، القيمة معروفة) لكل طريقة كبر. [unknownCount] حاجات قيمتها مش معروفة. */
data class InvestableSplit(
    val propertyMinor: Halalas,
    val goldMinor: Halalas,
    val sharesMinor: Halalas,
    val noGrowthMinor: Halalas,
    val unknownCount: Int,
) {
    val totalMinor: Halalas get() = propertyMinor + goldMinor + sharesMinor + noGrowthMinor
}

fun investableSplit(items: List<HoldingValue>): InvestableSplit {
    var property = 0L; var gold = 0L; var shares = 0L; var none = 0L; var unknown = 0
    for (h in items) {
        if (h.purpose == HoldingPurpose.PERSONAL) continue
        val v = h.valueMinor ?: run { unknown++; null } ?: continue
        when (h.type.bucket()) {
            GrowthBucket.PROPERTY -> property += v
            GrowthBucket.GOLD -> gold += v
            GrowthBucket.SHARES -> shares += v
            GrowthBucket.NONE -> none += v
        }
    }
    return InvestableSplit(property, gold, shares, none, unknown)
}

/** المتوسطات الرسمية (أو المؤشر) بمصدرها: الغلاء · الدهب · الأسهم — نقاط أساس. */
data class InvestRates(
    val cpiBp: Int,
    val goldBp: Int,
    val sharesBp: Int,
    val cpi: AverageEntry,
    val gold: AverageEntry,
    val shares: AverageEntry,
)

/** كله أو مفيش (زي النموذج — Claude choice ❓): أي متوسط ناقص ⇒ [Unavailable] بالمفاتيح الناقصة. */
sealed interface InvestRatesState {
    data class Ready(val rates: InvestRates) : InvestRatesState
    data class Unavailable(val missing: List<String>) : InvestRatesState
}

/**
 * متوسط أملاكك (§2.4): `(عقار·غلاء + دهب·دهب + أسهم·أسهم) / (عقار + دهب + أسهم)` بالتقريب لتحت؛ اللي ما بيكبرش مش داخل (زي النموذج).
 * null = مفيش ولا حاجة بتكبر. أعداد صحيحة بس — القسمة على مرحلتين عشان الضرب ما يعدّيش حد `Long`.
 */
fun holdingsMixBp(split: InvestableSplit, rates: InvestRates): Int? {
    val den = split.propertyMinor + split.goldMinor + split.sharesMinor
    if (den <= 0L) return null
    val parts = listOf(split.propertyMinor to rates.cpiBp, split.goldMinor to rates.goldBp, split.sharesMinor to rates.sharesBp)
    // v = q·den + r ⇒ Σ v·bp / den = Σ q·bp + ⌊Σ r·bp / den⌋ بالظبط (r < den ⇒ الضرب صغير)
    var whole = 0L
    var rest = 0L
    for ((v, bp) in parts) {
        whole += (v / den) * bp
        rest += (v % den) * bp
    }
    return (whole + rest.floorDiv(den)).toInt()
}
