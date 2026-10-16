package app.masroufy.core

/**
 * «حاجاتي» في شاشة «الاستثمار» الجديدة (مواصفة r05 §2.6 · §4.0 بند 1) — العقد المشترك بين أقسام الشاشة: نوع الحاجة ومجموعتها وغرضها،
 * وقيمة كل حاجة النهارده، ومعدلات المتوسطات الرسمية. **كل المبالغ هللات (`Long`) والمعدلات نقاط أساس (`Int`)** (CLAUDE.md #1).
 *
 * ⚠️ الملف ده المفروض يتعمل في «الخطوة 0» (العقود) قبل الحزم التلاتة؛ اتعمل في حزمة HOLDINGS لأن الخطوة 0 ما اتعملتش قبلها — بنفس
 * أسامي وتوقيعات المواصفة بالظبط، فلو حزمة تانية عملته برضه الدمج بياخد نسخة واحدة.
 */
enum class HoldingGroup(val wire: String) {
    METAL("metal"), PROPERTY("property"), SHARES("shares"), PERSONAL("personal"), COLLECTIBLES("collectibles"), OTHER("other");

    companion object {
        fun fromWire(wire: String?): HoldingGroup? = entries.firstOrNull { it.wire == wire }
    }
}

/** الغرض (وسم على كل حاجة): الشخصي ظاهر بس **برّه الإجمالي** (سؤال المالك Q1 لسه مفتوح). */
enum class HoldingPurpose(val wire: String) {
    INVEST("invest"), RESERVE("reserve"), PERSONAL("personal"), TRADE("trade");

    companion object {
        fun fromWire(wire: String?): HoldingPurpose? = entries.firstOrNull { it.wire == wire }
    }
}

/** «جت منين» — null على الأصل ⇒ شراء. */
enum class HoldingOrigin(val wire: String) {
    BUY("buy"), INHERIT("inherit"), GIFT("gift");

    companion object {
        fun fromWire(wire: String?): HoldingOrigin? = entries.firstOrNull { it.wire == wire }
    }
}

/** أقصى طول لخانة «المكان». */
const val MAX_HOLDING_PLACE = 80

/** أي متوسط رسمي بيكبّر الحاجة في التوقّع: العقار ⇐ الغلاء الرسمي · الدهب ⇐ متوسط الدهب · الأسهم ⇐ متوسط الأسهم · غير كده من غير زيادة. */
enum class GrowthBucket { PROPERTY, GOLD, SHARES, NONE }

/** النوع اللي المستخدم اختاره في «ضيف حاجة» (جدول §2.6): مجموعته وغرضه الافتراضي. */
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
    OTHER("other", HoldingGroup.OTHER, HoldingPurpose.PERSONAL),
    ;

    val metal: Boolean get() = group == HoldingGroup.METAL
    val property: Boolean get() = group == HoldingGroup.PROPERTY

    companion object {
        fun fromWire(wire: String?): HoldingType? = entries.firstOrNull { it.wire == wire }
    }
}

/** الفضة من غير متوسط رسمي ⇒ من غير زيادة (سؤال المالك Q4). */
fun HoldingType.bucket(): GrowthBucket = when (this) {
    HoldingType.GOLD -> GrowthBucket.GOLD
    HoldingType.FLAT, HoldingType.LAND, HoldingType.SHOP -> GrowthBucket.PROPERTY
    HoldingType.STK, HoldingType.CO -> GrowthBucket.SHARES
    else -> GrowthBucket.NONE
}

/** نوع الحاجة: المكتوب على الأصل، أو من النوع القديم (gold ⇒ دهب · silver ⇒ فضة · stock/fund ⇒ أسهم · عقار ⇒ شقة · غيره ⇒ «حاجة تانية»). */
fun holdingTypeOf(asset: Asset): HoldingType = HoldingType.fromWire(asset.holdingType) ?: when {
    asset.isRealEstate -> HoldingType.FLAT
    asset.kind == "gold" -> HoldingType.GOLD
    asset.kind == ASSET_KIND_SILVER -> HoldingType.SILVER
    asset.kind == "stock" || asset.kind == "fund" -> HoldingType.STK
    else -> HoldingType.OTHER
}

/** الغرض: المكتوب على الأصل ⇒ المشغولات «شخصي» (سؤال المالك Q2) ⇒ الافتراضي للنوع. [jewellery] = واقعة الزكاة «للبس» (`ZakatPurpose.WEAR`). */
fun purposeOf(asset: Asset, jewellery: Boolean): HoldingPurpose {
    HoldingPurpose.fromWire(asset.purpose)?.let { return it }
    val type = holdingTypeOf(asset)
    return if (jewellery && type.metal) HoldingPurpose.PERSONAL else type.defaultPurpose
}

/**
 * حاجة واحدة بقيمتها النهارده. [valueMinor] null ⇒ «غير متاح» (من غير سعر · بعملة تانية) — **مش صفر**، وبرّه أي إجمالي.
 * [quantity] المملوك (جرام · سهم · وحدة) بمقياس 10^8. [karat] للدهب و[fineness] للفضة من وقائع الزكاة (null = مش مكتوب).
 */
data class HoldingValue(
    val asset: Asset,
    val type: HoldingType,
    val purpose: HoldingPurpose,
    val valueMinor: Halalas?,
    val quantity: Quantity,
    val karat: Int? = null,
    val fineness: Int? = null,
    val jewellery: Boolean = false,
) {
    val group: HoldingGroup get() = type.group
}

/** كل الحاجات غير المؤرشفة لبلد بعملتها. [goldPriceMissing] = فيه دهب من غير سعر النهارده ⇒ ملاحظة «حاجاتي» والدهب برّه الإجمالي. */
data class HoldingsSnapshot(
    val currency: Currency,
    val items: List<HoldingValue>,
    val goldPriceMissing: Boolean,
    /** رد المالك 2026-10-16 ٢أ: الشخصي داخل المجموع وفي التوقّع؟ (الافتراضي لأ — `UserSetting.HoldingsPrefs`). */
    val includePersonal: Boolean = false,
)

/** اللي بيدخل «هيبقى معاك كام»: غير الشخصي بقيمة معروفة، مقسوم على نوع الزيادة. [unknownCount] غير الشخصي من غير قيمة. */
data class InvestableSplit(
    val propertyMinor: Halalas,
    val goldMinor: Halalas,
    val sharesMinor: Halalas,
    val noGrowthMinor: Halalas,
    val unknownCount: Int,
)

/** [includePersonal] = اختيار المستخدم (`HoldingsSnapshot.includePersonal`) — FUTURE لازم يبعته. */
fun investableSplit(items: List<HoldingValue>, includePersonal: Boolean = false): InvestableSplit {
    var property = 0L
    var gold = 0L
    var shares = 0L
    var flat = 0L
    var unknown = 0
    for (item in items) {
        if (item.purpose == HoldingPurpose.PERSONAL && !includePersonal) continue
        val v = item.valueMinor
        if (v == null) { unknown++; continue }
        when (item.type.bucket()) {
            GrowthBucket.PROPERTY -> property = addMoney(property, v)
            GrowthBucket.GOLD -> gold = addMoney(gold, v)
            GrowthBucket.SHARES -> shares = addMoney(shares, v)
            GrowthBucket.NONE -> flat = addMoney(flat, v)
        }
    }
    return InvestableSplit(property, gold, shares, flat, unknown)
}

/** المعدلات الرسمية للبلد بمصادرها (ملف المتوسطات): الغلاء · الدهب · الأسهم. */
data class InvestRates(
    val cpiBp: Int,
    val goldBp: Int,
    val sharesBp: Int,
    val cpi: AverageEntry,
    val gold: AverageEntry,
    val shares: AverageEntry,
)

/** الكل أو لا شيء (زي النموذج): ناقص واحد ⇒ [Unavailable] بأسامي الناقص. */
sealed interface InvestRatesState {
    data class Ready(val rates: InvestRates) : InvestRatesState
    data class Unavailable(val missing: List<String>) : InvestRatesState
}

/** معدل «زي حاجاتي»: متوسط مرجّح بالقيمة للعقار والدهب والأسهم (تقريب لتحت)؛ null لو مفيش منهم حاجة. الثابت مش داخل (زي النموذج). */
fun holdingsMixBp(split: InvestableSplit, rates: InvestRates): Int? {
    val den = addMoney(split.propertyMinor, split.goldMinor, split.sharesMinor)
    if (den <= 0L) return null
    val num = multiplyMoneyByInt(split.propertyMinor, rates.cpiBp.toLong()) +
        multiplyMoneyByInt(split.goldMinor, rates.goldBp.toLong()) + multiplyMoneyByInt(split.sharesMinor, rates.sharesBp.toLong())
    return num.floorDiv(den).toInt()
}
