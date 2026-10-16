package app.masroufy.core

/**
 * ربط أصل بسطر نشرة بنقاوة (مراجعة r05 F3): سطر الفضة `SILVER_GRAM` (و`_EGP`) سعر الجرام **الصافي** (سبوت — زي ما الزكاة بتقراه في
 * `metalValueMinor`)، والفضة 925 أو 800 جرامها فيه فضة أقل. الأصل بيتربط بـ`<السطر>@<النقاوة من 1000>` (`SILVER_GRAM@925`) وسعره =
 * سعر الصافي × النقاوة ÷ 1000 (تقريب واحد لأقرب هللة) — كل شاشة بتقرا قيمة الأصل (الكمية × السعر) بتطلع صح من غير ما تعرف النقاوة،
 * وتحديث الأسعار (`SyncAssetPrices`) بيطبّق نفس المعامل. 999 وما فوق ⇒ الصافي نفسه (من غير لاحقة — زي الأول).
 */
const val FEED_PURITY_SEP = "@"

/** [symbol] سطر النشرة نفسه · [finenessPerMille] النقاوة من 1000 (null = السعر زي ما هو). */
data class FeedLink(val symbol: String, val finenessPerMille: Int?)

/** «SILVER_GRAM@925» ⇒ (SILVER_GRAM، 925) · من غير لاحقة صالحة ⇒ الربط كله رمز (زي الأول). */
fun parseFeedLink(link: String): FeedLink {
    val at = link.lastIndexOf(FEED_PURITY_SEP)
    if (at <= 0) return FeedLink(link, null)
    val fineness = link.substring(at + 1).toIntOrNull()?.takeIf { it in 1..MAX_DISCOUNTED_FINENESS } ?: return FeedLink(link, null)
    return FeedLink(link.substring(0, at), fineness)
}

/** الربط اللي بيتخزن على الأصل: النقاوة أقل من 999 ⇒ بلاحقة، وإلا الرمز زي ما هو. */
fun feedLinkOf(symbol: String, finenessPerMille: Int?): String =
    if (finenessPerMille != null && finenessPerMille in 1..MAX_DISCOUNTED_FINENESS) symbol + FEED_PURITY_SEP + finenessPerMille else symbol

/** سعر وحدة الأصل من سعر الصافي: × النقاوة ÷ 1000 بتقريب واحد (النص لبعيد عن الصفر) · null ⇒ السعر زي ما هو. */
fun pricedAtPurity(purePriceMinor: Halalas, finenessPerMille: Int?): Halalas =
    if (finenessPerMille == null || finenessPerMille !in 1..MAX_DISCOUNTED_FINENESS) purePriceMinor
    else mulDivWide(purePriceMinor, finenessPerMille.toLong(), 1_000)

/** 999 فضة نقية عمليًا ⇒ السطر الصافي من غير خصم (زي الأصول القديمة). */
private const val MAX_DISCOUNTED_FINENESS = 998
