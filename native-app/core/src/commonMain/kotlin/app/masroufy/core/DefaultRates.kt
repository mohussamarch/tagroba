package app.masroufy.core

/**
 * المعدل الافتراضي لكل نوع في مقارنة «هتوصل لكام؟» (OVERRIDES §69.3 · §69.6). **القاعدة 10:** كل رقم جنبه مصدره وفترته ورسمي ولا خاص؛
 * والمجهول «غير متاح» — **مش صفر**. المستخدم بيعدّل أي رقم (التعديل في حالة الاستخدام، مش هنا).
 */

/** الخمس اختيارات اللي المالك اختارها في حاسبة الادخار (بالترتيب ده). */
enum class GrowthClass(val wire: String, private val labelKey: TextKey) {
    GOLD("gold", TextKey.GROWTH_CLASS_GOLD),
    REAL_ESTATE("realEstate", TextKey.GROWTH_CLASS_REAL_ESTATE),
    DEPOSIT("deposit", TextKey.GROWTH_CLASS_DEPOSIT),
    LOCAL_STOCKS("localStocks", TextKey.GROWTH_CLASS_LOCAL_STOCKS),
    CASH("cash", TextKey.GROWTH_CLASS_CASH),
    ;

    /** الاسم بلغة العرض (بيتقري وقت العرض). */
    val label: String get() = uiText(labelKey)
}

/** مصدر الرقم الافتراضي للعقار — **مستني كارت المالك** (§69.3): (أ) الغلاء الرسمي متبني كامل · (ب) مؤشر العقار بالإعداد · (ج) سعر متر الحي لاحقًا. */
enum class RealEstateRateSource { OFFICIAL_CPI, PRICE_INDEX }

/** أسهم مصر — **مستني كارت المالك**: «المستخدم يكتب» (الافتراضي لحد الرد) ولا «19% وجنبها سطر الجنيه». */
enum class EgyptStocksMode { USER_TYPES, DEFAULT_WITH_NOTE }

data class GrowthRatesConfig(
    val realEstate: RealEstateRateSource = RealEstateRateSource.OFFICIAL_CPI,
    val egyptStocks: EgyptStocksMode = EgyptStocksMode.USER_TYPES,
)

/**
 * سطر «جزء كبير من الزيادة سببه نزول الجنيه» بأرقامه: الجنيه نزل [currencyFallBp] في السنة، والرقم بالدولار [inUsdBp]
 * (null = مفيش رقم لسه — المستخدم ما كتبش — فبيبان نزول الجنيه بس، **مش صفر**).
 */
data class CurrencyNote(val currencyFallBp: Int, val inUsdBp: Int?, val periodStart: String, val periodEnd: String) {
    val text: String
        get() = uiText(TextKey.GROWTH_NOTE_EGP) + " — " + if (inUsdBp == null) uiText(TextKey.GROWTH_NOTE_EGP_FALL, formatBp(currencyFallBp), periodStart, periodEnd)
        else uiText(TextKey.GROWTH_NOTE_EGP_DETAIL, formatBp(currencyFallBp), periodStart, periodEnd, formatBp(inUsdBp))
}

/**
 * المعدل الافتراضي لنوع. [rateBp] null ⇒ مفيش رقم (السبب في [reason]) — يا إما المستخدم لازم يكتبه ([userMustType]) يا إما الملف ناقص.
 * [source] سطر الملف اللي الرقم جاي منه (null للكاش — صفر بالتعريف). [info] معلومة جنبه (فايدة البنك المركزي جنب الوديعة).
 */
data class DefaultRate(
    val growthClass: GrowthClass,
    val rateBp: Int?,
    val source: AverageEntry?,
    val stale: Boolean,
    val userMustType: Boolean,
    val reason: String?,
    val note: CurrencyNote? = null,
    val info: AverageEntry? = null,
) {
    /** سطر المصدر للعرض: الاسم · الفترة · رسمي/خاص · يدوي اتراجع يوم … · قديم. */
    val sourceText: String get() = describeSource(growthClass, source, stale)
}

private fun Map<String, AverageEntry>.pick(saudi: Boolean, sa: String, eg: String): AverageEntry? = this[if (saudi) sa else eg]

/**
 * المعدلات الافتراضية للخمسة لبلد [countryCode] ("SA" · "EG"؛ null = البلد الافتراضي السعودية زي باقي التطبيق).
 * [feed] null ⇒ الملف لسه ما وصلش ⇒ كله «غير متاح» إلا الكاش. ملف أقدم من [AVERAGES_STALE_AFTER_DAYS] ⇒ كل أرقامه «قديمة».
 */
fun defaultRates(feed: AveragesFeed?, countryCode: String?, config: GrowthRatesConfig = GrowthRatesConfig(), today: IsoDate? = null): List<DefaultRate> {
    val code = countryCode?.uppercase() ?: DEFAULT_COUNTRY_PACK.code
    val known = code == "SA" || code == "EG"
    val saudi = code == "SA"
    val fileOld = feed != null && today != null && averagesFileIsOld(feed, today)
    val e = feed?.entries.orEmpty()
    fun missingReason() = uiText(if (!known) TextKey.GROWTH_NA_COUNTRY else if (feed == null) TextKey.GROWTH_NA_NO_FILE else TextKey.GROWTH_NA_MISSING)
    fun fromEntry(cls: GrowthClass, entry: AverageEntry?, note: CurrencyNote? = null) =
        if (!known || entry == null) DefaultRate(cls, null, null, false, false, missingReason())
        else DefaultRate(cls, entry.valueBp, entry, entry.stale || fileOld, false, null, note)

    val fall = if (saudi) null else e[AverageKeys.EGP_PER_USD]
    val gold = if (!known) fromEntry(GrowthClass.GOLD, null) else if (saudi) fromEntry(GrowthClass.GOLD, e[AverageKeys.GOLD_USD]) else {
        val egp = e[AverageKeys.GOLD_EGP]
        val usd = e[AverageKeys.GOLD_USD_ANNUAL]
        // الجزء اللي من نزول الجنيه = (1 + بالجنيه) ÷ (1 + بالدولار) − 1 على نفس الأساس (OVERRIDES §69.3)
        val note = if (egp != null && usd != null) CurrencyNote(devaluationPartBp(egp.valueBp, usd.valueBp), usd.valueBp, egp.periodStart, egp.periodEnd) else null
        fromEntry(GrowthClass.GOLD, egp, note)
    }
    val realEstate = when (config.realEstate) {
        RealEstateRateSource.OFFICIAL_CPI -> fromEntry(GrowthClass.REAL_ESTATE, e.pick(saudi, AverageKeys.CPI_SA, AverageKeys.CPI_EG))
        RealEstateRateSource.PRICE_INDEX -> fromEntry(GrowthClass.REAL_ESTATE, e.pick(saudi, AverageKeys.REAL_ESTATE_INDEX_SA, AverageKeys.REAL_ESTATE_INDEX_EG))
    }
    // الوديعة: فايدة بنكه هو (المستخدم يكتبها) + فايدة البنك المركزي معلومة جنبها — مش مكانها (اختيار المالك §69.3)
    val policy = if (known) e.pick(saudi, AverageKeys.POLICY_RATE_SA, AverageKeys.POLICY_RATE_EG) else null
    val deposit = DefaultRate(GrowthClass.DEPOSIT, null, null, policy != null && (policy.stale || fileOld), true, uiText(TextKey.GROWTH_TYPE_BANK_RATE), info = policy)
    val stocks = when {
        !known -> fromEntry(GrowthClass.LOCAL_STOCKS, null)
        saudi -> fromEntry(GrowthClass.LOCAL_STOCKS, e[AverageKeys.STOCKS_SA])
        config.egyptStocks == EgyptStocksMode.USER_TYPES ->
            DefaultRate(GrowthClass.LOCAL_STOCKS, null, null, false, true, uiText(TextKey.GROWTH_TYPE_YOUR_RATE), note = fall?.let { stockNote(null, it) })
        else -> {
            val entry = e[AverageKeys.STOCKS_EG]
            fromEntry(GrowthClass.LOCAL_STOCKS, entry, if (entry != null && fall != null) stockNote(entry.valueBp, fall) else null)
        }
    }
    val cash = DefaultRate(GrowthClass.CASH, 0, null, false, false, null)
    return listOf(gold, realEstate, deposit, stocks, cash)
}

/** سطر الجنيه على أسهم مصر: نزول الجنيه من الملف، والرقم بالدولار لو فيه رقم ([rateBp] null ⇒ بيتحسب لما المستخدم يكتب). */
fun stockNote(rateBp: Int?, fall: AverageEntry): CurrencyNote =
    CurrencyNote(fall.valueBp, rateBp?.let { inUsdTermsBp(it, fall.valueBp) }, fall.periodStart, fall.periodEnd)

/** التضخم الرسمي للبلد (زرار «بقيمة فلوس النهارده») — null لو مش موجود (والزرار بيقول «غير متاح»). */
fun inflationEntry(feed: AveragesFeed?, countryCode: String?): AverageEntry? {
    val code = countryCode?.uppercase() ?: DEFAULT_COUNTRY_PACK.code
    return when (code) {
        "SA" -> feed?.entries?.get(AverageKeys.CPI_SA)
        "EG" -> feed?.entries?.get(AverageKeys.CPI_EG)
        else -> null
    }
}

/** اسم المصدر بلغة العرض من مفتاح السطر — المفتاح المجهول بياخد الاسم المكتوب في الملف زي ما هو. */
private fun sourceLabel(entry: AverageEntry): String = when (entry.key) {
    AverageKeys.GOLD_USD, AverageKeys.GOLD_USD_ANNUAL -> uiText(TextKey.GROWTH_SRC_GOLD)
    AverageKeys.GOLD_EGP -> uiText(TextKey.GROWTH_SRC_GOLD_EGP)
    AverageKeys.EGP_PER_USD -> uiText(TextKey.GROWTH_SRC_EGP_RATE)
    AverageKeys.CPI_SA -> uiText(TextKey.GROWTH_SRC_CPI_SA)
    AverageKeys.CPI_EG -> uiText(TextKey.GROWTH_SRC_CPI_EG)
    AverageKeys.STOCKS_SA -> uiText(TextKey.GROWTH_SRC_STOCKS_SA)
    AverageKeys.STOCKS_EG -> uiText(TextKey.GROWTH_SRC_STOCKS_EG)
    AverageKeys.POLICY_RATE_SA -> uiText(TextKey.GROWTH_SRC_POLICY_SA)
    AverageKeys.POLICY_RATE_EG -> uiText(TextKey.GROWTH_SRC_POLICY_EG)
    AverageKeys.REAL_ESTATE_INDEX_SA -> uiText(TextKey.GROWTH_SRC_RE_INDEX_SA)
    AverageKeys.REAL_ESTATE_INDEX_EG -> uiText(TextKey.GROWTH_SRC_RE_INDEX_EG)
    else -> entry.sourceName
}

/** سطر المصدر الكامل لسطر من الملف. */
fun describeEntry(entry: AverageEntry, stale: Boolean = entry.stale): String {
    val parts = mutableListOf(
        sourceLabel(entry),
        if (entry.periodStart == entry.periodEnd) entry.periodEnd else "${entry.periodStart}–${entry.periodEnd}",
        uiText(if (entry.official) TextKey.GROWTH_OFFICIAL else TextKey.GROWTH_PRIVATE),
    )
    if (entry.newsSourced) parts += uiText(TextKey.GROWTH_NEWS_SOURCED)
    if (entry.manual && entry.reviewedOn != null) parts += uiText(TextKey.GROWTH_MANUAL_REVIEWED, entry.reviewedOn)
    if (stale) parts += uiText(TextKey.GROWTH_STALE, entry.staleSince ?: entry.computedOn ?: entry.periodEnd)
    return parts.joinToString(" · ")
}

private fun describeSource(cls: GrowthClass, source: AverageEntry?, stale: Boolean): String = when {
    source != null -> describeEntry(source, stale)
    cls == GrowthClass.CASH -> uiText(TextKey.GROWTH_SRC_CASH)
    else -> NOT_AVAILABLE
}
