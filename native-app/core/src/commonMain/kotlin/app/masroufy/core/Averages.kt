package app.masroufy.core

/**
 * ملف المتوسطات `public/averages.json` (OVERRIDES §69.6) — بيتولد برا التطبيق مرة في الشهر (`scripts/fetch-averages.mjs` في مهمة
 * الأسعار — CLAUDE.md #12). هنا القراية بس، بنفس قاعدة ملف الأسعار: **الشكل الغلط بيرمي، والسطر الغلط بيترفض لوحده بسببه**،
 * و**مفيش صفر مكان المجهول** — السطر الناقص أو الغلط مش موجود ⇒ التطبيق بيقول «غير متاح».
 */

/** مفاتيح الملف اللي التطبيق بيفهمها (أي مفتاح تاني بيتقري ومش بيتستخدم). */
object AverageKeys {
    const val GOLD_USD = "GOLD_USD"
    const val GOLD_USD_ANNUAL = "GOLD_USD_ANNUAL"
    const val GOLD_EGP = "GOLD_EGP"
    const val EGP_PER_USD = "EGP_PER_USD"
    const val CPI_SA = "CPI_SA"
    const val CPI_EG = "CPI_EG"
    const val STOCKS_SA = "STOCKS_SA"
    const val STOCKS_EG = "STOCKS_EG"
    const val POLICY_RATE_SA = "POLICY_RATE_SA"
    const val POLICY_RATE_EG = "POLICY_RATE_EG"
    const val REAL_ESTATE_INDEX_SA = "REAL_ESTATE_INDEX_SA"
    const val REAL_ESTATE_INDEX_EG = "REAL_ESTATE_INDEX_EG"
}

/**
 * سطر متوسط. [valueBp] نقاط أساس (1488 = 14.88%). [official] الرقم صادر من جهة رسمية ولا خاصة. [manual] رقم مكتوب بإيد في
 * السكربت ([reviewedOn] = آخر مراجعة) ولا محسوب آليًا ([computedOn]). [stale] آخر حساب فشل والرقم ده من قبله ([staleSince]).
 * [newsSourced] = ثقة «C» (اتنقل من خبر مش من صفحة الجهة).
 */
data class AverageEntry(
    val key: String,
    val valueBp: Int,
    val periodStart: String,
    val periodEnd: String,
    val official: Boolean,
    val manual: Boolean,
    val sourceName: String,
    val sourceUrl: String?,
    val method: String,
    val reviewedOn: IsoDate? = null,
    val computedOn: IsoDate? = null,
    val stale: Boolean = false,
    val staleSince: IsoDate? = null,
    val newsSourced: Boolean = false,
)

data class AveragesFeed(
    val generatedAt: String,
    val entries: Map<String, AverageEntry>,
    /** سطور اترفضت وقت القراية بسببها (key = المفتاح). */
    val rejected: List<FeedIssue>,
    /** مصادر فشلت وقت التوليد (key = المصدر). */
    val failures: List<FeedIssue>,
)

/** الملف أقدم من كده (المفروض بيتجدد كل شهر) ⇒ كل أرقامه بتتعلّم «قديمة». */
const val AVERAGES_STALE_AFTER_DAYS = 45

/** نسخة الشكل اللي التطبيق بيفهمها. */
const val AVERAGES_SCHEMA = 1

private val PERIOD = Regex("^\\d{4}(-(0[1-9]|1[0-2]))?$")

private fun text(value: Any?): String? = (value as? String)?.let(JsText::trim)?.ifEmpty { null }

/** عدد صحيح (Long أو Double من غير كسر) جوه حدود المعدل — null لو غير كده. */
private fun basisPoints(value: Any?): Int? = when (value) {
    is Int -> value.toLong()
    is Long -> value
    is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
    else -> null
}?.takeIf { it in MIN_ANNUAL_RATE_BP.toLong()..MAX_ANNUAL_RATE_BP.toLong() }?.toInt()

private fun dateOrNull(value: Any?): IsoDate? = text(value)?.takeIf { isValidIsoDate(it) }

/** سطر واحد ⇒ المتوسط، أو سبب الرفض (نص للمطور/المعاينة). */
private fun readEntry(key: String, e: Map<*, *>): Pair<AverageEntry?, String?> {
    val value = basisPoints(e["valueBp"]) ?: return null to uiText(TextKey.AVERAGES_BAD_VALUE)
    val start = text(e["periodStart"])
    val end = text(e["periodEnd"])
    if (start == null || end == null || !PERIOD.matches(start) || !PERIOD.matches(end) || end < start) return null to uiText(TextKey.AVERAGES_BAD_PERIOD)
    val official = e["official"] as? Boolean ?: return null to uiText(TextKey.AVERAGES_NO_OFFICIAL_FLAG)
    val source = text(e["sourceName"]) ?: return null to uiText(TextKey.FEED_NO_SOURCE)
    val kind = text(e["kind"])
    if (kind != "manual" && kind != "computed") return null to uiText(TextKey.AVERAGES_BAD_KIND)
    val reviewedOn = dateOrNull(e["reviewedOn"])
    // الرقم اليدوي من غير تاريخ مراجعة = رقم من غير مصدر واضح
    if (kind == "manual" && reviewedOn == null) return null to uiText(TextKey.AVERAGES_NO_REVIEW_DATE)
    val entry = AverageEntry(
        key = key,
        valueBp = value,
        periodStart = start,
        periodEnd = end,
        official = official,
        manual = kind == "manual",
        sourceName = source,
        sourceUrl = text(e["sourceUrl"]),
        method = text(e["method"]) ?: "",
        reviewedOn = reviewedOn,
        computedOn = dateOrNull(e["computedOn"]),
        stale = e["stale"] == true,
        staleSince = dateOrNull(e["staleSince"]),
        newsSourced = text(e["confidence"]) == "C",
    )
    return entry to null
}

/** بيقرا ملف المتوسطات (JSON متحوّل لـ Map/List/أرقام/نصوص — زي `parsePriceFeed`). */
fun parseAveragesFeed(raw: Any?): AveragesFeed {
    val root = raw as? Map<*, *> ?: throw PriceFeedError(uiText(TextKey.FEED_BAD_SHAPE))
    val schema = basisPoints(root["schema"])
    if (schema != AVERAGES_SCHEMA) throw PriceFeedError(uiText(TextKey.AVERAGES_SCHEMA_UNKNOWN, schema?.toString() ?: "—"))
    val generatedAt = text(root["generatedAt"]) ?: throw PriceFeedError(uiText(TextKey.FEED_NO_GENERATED_AT))
    if (!isValidIsoDate(generatedAt.take(10))) throw PriceFeedError(uiText(TextKey.FEED_NO_GENERATED_AT))
    val rows = root["averages"] as? Map<*, *> ?: throw PriceFeedError(uiText(TextKey.AVERAGES_NONE))
    val entries = LinkedHashMap<String, AverageEntry>()
    val rejected = mutableListOf<FeedIssue>()
    for ((k, e) in rows) {
        val key = k.toString()
        if (e !is Map<*, *>) { rejected += FeedIssue(key, uiText(TextKey.FEED_ENTRY_NOT_OBJECT)); continue }
        val (entry, reason) = readEntry(key, e)
        if (entry != null) entries[key] = entry else rejected += FeedIssue(key, reason!!)
    }
    val failures = (root["failures"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        .map { FeedIssue(text(it["source"]) ?: uiText(TextKey.FEED_UNKNOWN_SOURCE), text(it["reason"]) ?: uiText(TextKey.FEED_NO_REASON)) }
    return AveragesFeed(generatedAt, entries, rejected, failures)
}

/** الملف كله قديم؟ (أقدم من [AVERAGES_STALE_AFTER_DAYS] يوم من [today]). */
fun averagesFileIsOld(feed: AveragesFeed, today: IsoDate): Boolean = daysBetween(feed.generatedAt.take(10), today) > AVERAGES_STALE_AFTER_DAYS
