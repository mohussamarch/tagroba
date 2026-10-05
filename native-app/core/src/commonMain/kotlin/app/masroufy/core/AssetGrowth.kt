package app.masroufy.core

/**
 * توقّع الأصل اللي عندك (طلب المالك §69: «عنده شقة … شاريها ب6 مليون ايام ما كان المتر ب25 الف و حاليا … المتر 40 الف و يتم تحديث سعر
 * الشقة … هيوصل سعرها لكام لو باعها 2030 + ايجار لو هو قام بتأجيرها»). **الصورة كاملة** (رد كارت المالك): قيمته سنة البيع + الإيجار لحد
 * وقتها + المكسب من يوم الشراء. دوال نقية بأعداد صحيحة — التخزين في `ManageAssetGrowth`.
 *
 * **قاعدة الحساب (على الورق):**
 * - السنين = سنة البيع − السنة الحالية (سنين كاملة — البيع في نفس يوم السنة). القيمة: × (1 + المعدل) كل سنة ([compoundYears]).
 * - الإيجار سنة بسنة: إيجار الشهر في السنة k = الشهري × (1 + الزيادة)^(k−1) (تقريب للهللة كل سنة) × (12 − الشهور الفاضية).
 *   **من غير استثمار الإيجار** (بيتجمع زي ما هو) — والسنة الحالية مش داخلة (البيع السنة دي ⇒ مفيش إيجار).
 * - المكسب من يوم الشراء = القيمة سنة البيع − تكلفة الشراء (من سجل الشراء + الرسوم). الإجمالي = المكسب + الإيجار.
 */

/** طريقة قيمة العقار — **اختيار لكل عقار** (رد كارت المالك). */
enum class RealEstateValuation(val wire: String) {
    /** سعر المتر × المساحة — تحديث سعر المتر بيحدّث القيمة. */
    AREA("area"),

    /** القيمة كلها (سعر الأصل العادي). */
    WHOLE("whole"),
    ;

    companion object {
        fun fromWire(wire: String): RealEstateValuation =
            entries.firstOrNull { it.wire == wire } ?: throw AssetError(uiText(TextKey.GROWTH_VALUATION_UNKNOWN, wire))
    }
}

/** أقصى سنين لقدام في التوقّع. */
const val MAX_PROJECTION_YEARS = 100

/** نوع الأصل في المقارنة (عشان المعدل الافتراضي): العقار ([isRealEstate] — العلامة أو طريقة القيمة) · الذهب · الأسهم والصناديق بعملة البلد بس. غير كده null. */
fun growthClassOf(asset: Asset, countryCurrency: Currency): GrowthClass? = when {
    asset.isRealEstate -> GrowthClass.REAL_ESTATE
    asset.kind == "gold" -> GrowthClass.GOLD
    (asset.kind == "stock" || asset.kind == "fund") && asset.currency == countryCurrency -> GrowthClass.LOCAL_STOCKS
    else -> null
}

/**
 * قيمة الأصل النهارده:
 * - بسعر المتر ⇒ المساحة × سعر المتر (null لو ناقص واحد).
 * - فيه شراء متسجل ([lotsRecorded]) ⇒ القيمة من سعر الأصل × الكمية ([AssetPosition.marketValueMinor] — صفر لو اتباع كله).
 * - عقار «القيمة كلها» (أو عقار بالعلامة من غير طريقة قيمة لسه — §69.7) من غير شراء متسجل ⇒ السعر نفسه هو قيمة العقار (وحدة واحدة)
 *   · غير كده ⇒ null («غير متاح» — **مش صفر**:
 *   من غير شراء الكمية مش معروفة).
 */
fun currentValueOf(asset: Asset, position: AssetPosition, lotsRecorded: Boolean = true): Halalas? = when {
    asset.valuation == RealEstateValuation.AREA ->
        if (asset.areaSqm != null && asset.pricePerSqmMinor != null) valueOfQuantity(asset.areaSqm, asset.pricePerSqmMinor) else null
    lotsRecorded -> position.marketValueMinor
    asset.isRealEstate -> when (val s = position.priceState) {
        is PriceState.Fresh -> s.price.pricePerUnitMinor
        is PriceState.Stale -> s.price.pricePerUnitMinor
        PriceState.Missing -> null
    }
    else -> null
}

/** مثال المالك بالعكس: اشتريت بـ[principalMinor] والمتر وقتها [pricePerSqmMinor] ⇒ المساحة (× 10^8). 6,000,000 ÷ 25,000 = 240 متر. */
fun areaFromPurchase(principalMinor: Halalas, pricePerSqmMinor: Halalas): Quantity {
    if (principalMinor <= 0) throw AssetError(uiText(TextKey.ASSET_FIELD_POSITIVE, uiText(TextKey.ASSET_LABEL_PURCHASE_VALUE)))
    if (pricePerSqmMinor <= 0) throw AssetError(uiText(TextKey.GROWTH_PRICE_SQM_POSITIVE))
    // نفس قسمة «سعر الوحدة» (المبلغ × 10^8 ÷ المقام) بتقريب واحد — هنا المقام سعر المتر
    return unitPriceOf(principalMinor, pricePerSqmMinor)!!
}

/** شروط الإيجار. كل خانة اختيارية: null = صفر (مش مأجّر · مفيش زيادة · مفيش شهور فاضية). */
data class RentTerms(val monthlyMinor: Halalas? = null, val yearlyIncreaseBp: Int? = null, val vacantMonthsPerYear: Int? = null)

fun checkRentTerms(rent: RentTerms) {
    if (rent.monthlyMinor != null && (rent.monthlyMinor < 0 || rent.monthlyMinor > MAX_SAFE_HALALAS)) throw AssetError(uiText(TextKey.GROWTH_RENT_NOT_NEGATIVE))
    rent.yearlyIncreaseBp?.let(::checkAnnualRate)
    if (rent.vacantMonthsPerYear != null && rent.vacantMonthsPerYear !in 0..12) throw AssetError(uiText(TextKey.GROWTH_VACANT_RANGE))
}

/** الإيجار اللي هيتقبض في [years] سنة كاملة جاية. */
fun rentUntil(rent: RentTerms, years: Int): Halalas {
    checkRentTerms(rent)
    val monthly = rent.monthlyMinor ?: 0L
    val occupied = 12L - (rent.vacantMonthsPerYear ?: 0)
    var thisYearMonthly = monthly
    var total = 0L
    for (k in 1..years) {
        if (k > 1) thisYearMonthly = mulDivWide(thisYearMonthly, BASIS_POINTS + (rent.yearlyIncreaseBp ?: 0), BASIS_POINTS)
        total = addMoney(total, multiplyMoneyByInt(thisYearMonthly, occupied))
    }
    return total
}

/** الحاجة الناقصة في التوقّع — كل واحدة ليها نص بيقول تكتب إيه. */
enum class ProjectionGap(private val key: TextKey) {
    NO_VALUE(TextKey.GROWTH_NA_ASSET_VALUE),
    NO_COST(TextKey.GROWTH_NA_ASSET_COST),
    NO_RATE(TextKey.GROWTH_NA_ASSET_RATE),
    ;

    val text: String get() = uiText(key)
}

data class AssetProjection(
    val sellYear: Int,
    val years: Int,
    val rateBp: Int?,
    val currentValueMinor: Halalas?,
    val valueAtSaleMinor: Halalas?,
    val rentTotalMinor: Halalas,
    /** تكلفة الشراء (من السجل) — null لو مفيش شراء متسجل. */
    val costBasisMinor: Halalas?,
    val purchasedAt: IsoDate?,
    val gainNowMinor: Halalas?,
    val gainAtSaleMinor: Halalas?,
    /** المكسب سنة البيع + الإيجار. */
    val totalGainMinor: Halalas?,
    val gaps: List<ProjectionGap>,
)

/**
 * الصورة كاملة لأصل لحد [sellYear]. [currentValueMinor] null ⇒ القيمة «غير متاح» · [rateBp] null ⇒ قيمة سنة البيع «غير متاح» ·
 * [costBasisMinor] null ⇒ المكسب «غير متاح». الإيجار بيتحسب في كل الأحوال (خاناته اختيارية).
 */
fun projectAsset(
    currentValueMinor: Halalas?,
    costBasisMinor: Halalas?,
    purchasedAt: IsoDate?,
    today: IsoDate,
    sellYear: Int,
    rateBp: Int?,
    rent: RentTerms,
): AssetProjection {
    val thisYear = parseIsoDate(today).year
    val years = sellYear - thisYear
    if (years < 0 || years > MAX_PROJECTION_YEARS) throw AssetError(uiText(TextKey.GROWTH_SELL_YEAR_RANGE, thisYear.toString(), (thisYear + MAX_PROJECTION_YEARS).toString()))
    rateBp?.let(::checkAnnualRate)
    val atSale = if (currentValueMinor != null && rateBp != null) compoundYears(currentValueMinor, years, rateBp) else null
    val rentTotal = rentUntil(rent, years)
    val gainNow = if (currentValueMinor != null && costBasisMinor != null) subtractMoney(currentValueMinor, costBasisMinor) else null
    val gainAtSale = if (atSale != null && costBasisMinor != null) subtractMoney(atSale, costBasisMinor) else null
    val gaps = buildList {
        if (currentValueMinor == null) add(ProjectionGap.NO_VALUE)
        if (costBasisMinor == null) add(ProjectionGap.NO_COST)
        if (rateBp == null) add(ProjectionGap.NO_RATE)
    }
    return AssetProjection(
        sellYear, years, rateBp, currentValueMinor, atSale, rentTotal, costBasisMinor, purchasedAt, gainNow, gainAtSale,
        gainAtSale?.let { addMoney(it, rentTotal) }, gaps,
    )
}

/** فحص حقول «هتوصل لكام؟» على الأصل في النسخة الشاملة (لو موجودة بس — الأصل القديم من غيرها سليم). */
internal fun checkAssetGrowthRow(row: Map<String, Any?>): String? {
    row["valuation"]?.let { v -> if (RealEstateValuation.entries.none { it.wire == v }) return "valuation" }
    row["areaSqm"]?.let { if (!isSafeInteger(it) || numberOf(it)!! <= 0) return "areaSqm" }
    row["pricePerSqmMinor"]?.let { if ((numberOf(it) ?: 0.0) <= 0) return "pricePerSqmMinor" }
    row["pricePerSqmAsOf"]?.let { if (it !is String || !isValidIsoDate(it)) return "pricePerSqmAsOf" }
    for (field in listOf("rentIncreaseBp", "expectedRateBp")) {
        row[field]?.let { if (!isSafeInteger(it) || numberOf(it)!! !in MIN_ANNUAL_RATE_BP.toDouble()..MAX_ANNUAL_RATE_BP.toDouble()) return field }
    }
    row["vacantMonthsPerYear"]?.let { if (!isSafeInteger(it) || numberOf(it)!! !in 0.0..12.0) return "vacantMonthsPerYear" }
    // علامة العقار (§69.7): منطقية، ومعاها النوع المتخزن "other" بس (العقار مش دهب ولا سهم)
    row["realEstate"]?.let { if (it !is Boolean || (it && row["kind"] != "other")) return "realEstate" }
    // سعر المتر من غير المساحة (أو العكس) في طريقة «سعر المتر» مسموح — القيمة «غير متاح» لحد ما الاتنين يتكتبوا
    return null
}
