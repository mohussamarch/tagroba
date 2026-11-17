package app.masroufy.core

/** سطور الحساب: القاعدة على كل واقعة ([rawZakatItems]) وبعدها سؤال الحول للحاجة «من زمان» (§121-7). */
fun zakatItems(country: ZakatCountry, holdings: List<ZakatHolding>, prices: ZakatPrices): List<ZakatItem> =
    rawZakatItems(country, holdings, prices).map(::askHawlIfLongAgo)

/**
 * §121-7 (قرار المالك): الحاجة «من زمان» ما بنعتبرش إن الحول عدّى عليها — بنسأل «عدّى عليها سنة هجرية؟». لحد الإجابة السطر المحسوب
 * بيبقى «مستني إجابة» ([ZakatItemStatus.NEEDS_FACT]) والإجمالي «غير متاح» — مش رقم مؤكد (قاعدة 10). السطر المعفي أو اللي مش داخل
 * ما بيتسألش (سؤال ملوش لازمة).
 */
private fun askHawlIfLongAgo(i: ZakatItem): ZakatItem {
    val h = i.holding as? ZakatHolding.LongAgo ?: return i
    if (i.status != ZakatItemStatus.COUNTED || !h.ownedLongAgo || h.hawlPassed != null) return i
    return i.copy(status = ZakatItemStatus.NEEDS_FACT, zakatableMinor = null, missingFact = ZAKAT_FACT_HAWL)
}

/** اسم الواقعة الناقصة في [ZakatItem.missingFact]. */
const val ZAKAT_FACT_HAWL = "hawlPassed"

/**
 * من إمتى الحاجة دخلت سلسلة الحول: «من زمان» + «أيوه عدّت» ⇒ من زمان · «لأ» ⇒ من يوم الحساب (ما نعرفش يومها جوه السنة، فما بتكمّلش
 * حول حد — اختيار Claude) · لسه ما اتجاوبش ⇒ زي ما هو (السطر نفسه مش محسوب لحد الإجابة).
 */
fun longAgoHeldSince(since: IsoDate?, hawlPassed: Boolean?, asOf: IsoDate): IsoDate? =
    if (isOwnedLongAgo(since) && hawlPassed == false) asOf else since
