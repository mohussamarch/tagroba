package app.masroufy.core

/**
 * الزكاة — جدول القواعد (OVERRIDES §62).
 *
 * **نظام البلد إجباري** (قرار المالك 2026-10-04 — «علشان منتعرض لمسائلة قانونية»): السعودية = دليل زكاة الأفراد من
 * هيئة الزكاة والضريبة والجمارك · مصر = دار الإفتاء المصرية. المستخدم **ما بيختارش رأي** — بيدخل وقائع بس.
 * لو الجهة نفسها ليها فتويين ⇒ **الأحدث** · لو مالهاش فتوى ⇒ **ما بيتحسبش** وبيظهر لوحده (`ZakatEffect.NO_RULING`).
 * كل قاعدة معاها مصدرها (الجهة · الوثيقة أو رقم الفتوى · الرابط · التاريخ). فتوى أحدث تطلع ⇒ السطر يتحدث هنا بتاريخها.
 * التطبيق **عمره ما يقول حاجة المرجع ما قالهاش**.
 */
enum class ZakatCountry(val code: String) {
    SA("SA"), EG("EG");

    companion object {
        /** بلد المساحة (§41) ⇒ قواعدها. بلد مالهاش قواعد ⇒ null (الميزة مش متاحة هناك — مش بنخمّن). */
        fun of(countryCode: String?): ZakatCountry? = entries.firstOrNull { it.code == countryCode?.uppercase() }
    }
}

enum class ZakatAuthority(val nameKey: TextKey) {
    ZATCA(TextKey.ZAKAT_AUTH_ZATCA), DAR_AL_IFTAA(TextKey.ZAKAT_AUTH_IFTAA);

    val label: String get() = uiText(nameKey)
}

/** النقط اللي ليها قاعدة. */
enum class ZakatTopic {
    RATE, CASH, NISAB, HAWL, MID_YEAR_DIP, WORN_JEWELRY, SAVED_METAL, DEBTS_OWED, TRADING_SHARES, LONG_TERM_SHARES,
    RECEIVABLE_STRONG, RECEIVABLE_DOUBTFUL, ROSCA_CREDIT, CRYPTO, PENSION,
    /** دين ليك **اتحصّل** في السنة دي وما كانش بيدخل الحساب السنوي ⇒ 2.5% مرة واحدة على اللي اتحصّل. */
    RECEIVABLE_COLLECTED,
    /** الأمانة: فلوس حد تاني معاك. */
    CUSTODY,
    /** أسهم طويلة الأجل في شركة **مش سعودية** (الإعفاء في الدليل مربوط بالشركات في المملكة). */
    FOREIGN_LONG_TERM_SHARES,
}

/** القاعدة بتعمل إيه في الحساب. */
enum class ZakatEffect {
    /** بيتحسب بقيمته. */
    COUNT,
    /** قيمته على الزكاة صفر بنص القاعدة (اللبس · مشكوك فيه · طويل الأجل). */
    EXEMPT,
    /** دين عليك — بيتعرض وما بيتخصمش. */
    NOT_DEDUCTED,
    /** المرجع ما حددش ⇒ ما بيتحسبش وبيظهر لوحده. */
    NO_RULING,
    /** قاعدة حساب (النسبة · النصاب · الحول) مش على أصل. */
    METHOD,
}

/**
 * مصدر القاعدة. [fatwaNumber] رقم فتوى دار الإفتاء لو فيه. [issued] سنة الإصدار لو معروفة (null = ما اتأكدش — مش بنخمّن).
 * [checkedOn] اليوم اللي اتراجع فيه المصدر. [pending] = لسه بندوّر على نص صريح، والقاعدة المكتوبة هي اللي المالك قرر يمشي بيها لحد ما يتلاقى.
 * [section] و[pages] البند والصفحات في دليل الهيئة (من البحث التكميلي §62) — بيتكتبوا جنب السطر.
 */
data class ZakatSource(
    val authority: ZakatAuthority,
    val documentKey: TextKey,
    val fatwaNumber: String?,
    val url: String,
    val issued: String?,
    val checkedOn: String,
    val pending: Boolean = false,
    val section: String? = null,
    val pages: String? = null,
) {
    val document: String
        get() {
            val base = if (fatwaNumber != null) uiText(documentKey, fatwaNumber) else uiText(documentKey)
            return when {
                section != null && pages != null -> uiText(TextKey.ZAKAT_DOC_SECTION_PAGES, base, section, pages)
                pages != null -> uiText(TextKey.ZAKAT_DOC_PAGES, base, pages)
                else -> base
            }
        }
}

data class ZakatRule(val country: ZakatCountry, val topic: ZakatTopic, val effect: ZakatEffect, val rulingKey: TextKey, val source: ZakatSource) {
    val ruling: String get() = uiText(rulingKey)
}

/** 2.5% = 25 في الألف — كسر صحيح، مفيش عشري (CLAUDE.md #1). */
const val ZAKAT_RATE_PER_THOUSAND = 25L
/** النصاب بالجرام الصافي (الهيئة) — والمصري 85 جم من عيار 21. */
const val NISAB_GOLD_GRAMS = 85L
const val NISAB_SILVER_GRAMS = 595L
const val NISAB_EG_GOLD_KARAT = 21

private const val CHECKED = "2026-10-04"
private val ZATCA_GUIDE = ZakatSource(
    ZakatAuthority.ZATCA, TextKey.ZAKAT_DOC_ZATCA_GUIDE, null,
    "https://www.zatca.gov.sa/ar/HelpCenter/guidelines/Documents/Zakat_Individual.pdf", "2023", CHECKED,
)

/** البند والصفحات في دليل الهيئة (مارس 2023) — من البحث التكميلي §62. */
private fun zatca(section: String?, pages: String) = ZATCA_GUIDE.copy(section = section, pages = pages)

/**
 * فتوى من دار الإفتاء. [pageId] رقم صفحتها على موقع الدار — **غير رقم الفتوى** في الفتاوى اللي اتراجعت بإيد (مثلًا الفتوى 4399
 * صفحتها 14460). الفتاوى الأقدم في الجدول مكتوب رقمها زي ما اتسجل أول مرة (⚠️ HANDOVER: يتراجع هل هو رقم فتوى ولا رقم صفحة).
 */
private fun iftaa(number: String?, issued: String? = null, pending: Boolean = false, pageId: String? = number) = ZakatSource(
    ZakatAuthority.DAR_AL_IFTAA,
    if (number != null) TextKey.ZAKAT_DOC_FATWA else TextKey.ZAKAT_DOC_IFTAA_GENERAL,
    number,
    if (pageId != null) "https://www.dar-alifta.org/ar/fatwa/details/$pageId" else "https://www.dar-alifta.org",
    issued, CHECKED, pending,
)

/** فتاوى البحث التكميلي (§62 «🔎») — اتراجعوا على موقع الدار، والرابط بصفحة الفتوى. */
private val IFTAA_HAWL_413 = iftaa("413", "2008", pageId = "11216")
private val IFTAA_DIP_5890 = iftaa("5890", "1985", pageId = "16824")
private val IFTAA_DEBT_4399 = iftaa("4399", "2002", pageId = "14460")
private val IFTAA_SHARES_8767 = iftaa("8767", "2025", pageId = "22181")
private val GUIDE_RECEIVABLES = zatca("3.4", "19–20")
private val GUIDE_SHARES = zatca("3.6", "22–23")

/**
 * الجدول نفسه. مصر: النصاب فتوى 2279 (2007) · الحلي 8848 (2025) · الدين عليك **5025 (2020) — الأحدث** وبتلغي 7004 (1996) — الأرقام دي أرقام الفتاوى، وأرقام الصفحات على الموقع (11653 · 22484 · 15532 · 17652) في `pageId` ·
 * **البحث التكميلي (§62):** اليوم الثابت 413 (2008؛ والتعجيل 7503 سنة 2023) · النصاب أول السنة وآخرها 5890 (1985) ·
 * الدين ليك عند التحصيل 4399 (2002) · الأسهم 8767 (2025 — الأحدث، بتلغي 3133 سنة 1996).
 * السعودية: دليل الهيئة، وجنب اللي البحث حدد بنده: الحول ص14 و19 و27 · النزول §3.2.1 ص18 · الديون ليك §3.4 ص19–20 ·
 * الأسهم §3.6 ص22–23. **الأمانة والسهم الأجنبي: الدليل ساكت** ⇒ «المرجع الرسمي ما حددش».
 */
val ZAKAT_RULES: List<ZakatRule> = listOf(
    ZakatRule(ZakatCountry.SA, ZakatTopic.RATE, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_RATE, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.CASH, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_CASH, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.NISAB, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_NISAB_SA, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.HAWL, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_HAWL_FIXED, zatca(null, "14, 19, 27")),
    ZakatRule(ZakatCountry.SA, ZakatTopic.MID_YEAR_DIP, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_DIP_RESTART, zatca("3.2.1", "18")),
    ZakatRule(ZakatCountry.SA, ZakatTopic.WORN_JEWELRY, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_WORN_EXEMPT, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.SAVED_METAL, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_SAVED_COUNTED, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.DEBTS_OWED, ZakatEffect.NOT_DEDUCTED, TextKey.ZAKAT_RULE_DEBTS_NOT_DEDUCTED, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.TRADING_SHARES, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_TRADING_FULL, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.LONG_TERM_SHARES, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_LONG_TERM_COMPANY_PAYS, GUIDE_SHARES),
    ZakatRule(ZakatCountry.SA, ZakatTopic.FOREIGN_LONG_TERM_SHARES, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, GUIDE_SHARES),
    ZakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_STRONG, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_RECEIVABLE_STRONG, GUIDE_RECEIVABLES),
    ZakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_DOUBTFUL, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_RECEIVABLE_DOUBTFUL, GUIDE_RECEIVABLES),
    ZakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_COLLECTED, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_RECEIVABLE_COLLECTED, GUIDE_RECEIVABLES),
    ZakatRule(ZakatCountry.SA, ZakatTopic.ROSCA_CREDIT, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_ROSCA_RECEIVABLE, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.CRYPTO, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.PENSION, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.CUSTODY, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, ZATCA_GUIDE),

    ZakatRule(ZakatCountry.EG, ZakatTopic.RATE, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_RATE, iftaa("2279", "2007", pageId = "11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.CASH, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_CASH, iftaa("2279", "2007", pageId = "11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.NISAB, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_NISAB_EG, iftaa("2279", "2007", pageId = "11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.HAWL, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_HAWL_FIXED, IFTAA_HAWL_413),
    ZakatRule(ZakatCountry.EG, ZakatTopic.MID_YEAR_DIP, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_DIP_START_END, IFTAA_DIP_5890),
    ZakatRule(ZakatCountry.EG, ZakatTopic.WORN_JEWELRY, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_WORN_EXEMPT, iftaa("8848", "2025", pageId = "22484")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.SAVED_METAL, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_SAVED_COUNTED, iftaa("8848", "2025", pageId = "22484")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.DEBTS_OWED, ZakatEffect.NOT_DEDUCTED, TextKey.ZAKAT_RULE_DEBTS_NOT_DEDUCTED, iftaa("5025", "2020", pageId = "15532")),
    // 8767: شركة تجارية أو نية بيع ⇒ القيمة السوقية · شركة إنتاج/خدمات للاستثمار ⇒ الأرباح بس
    ZakatRule(ZakatCountry.EG, ZakatTopic.TRADING_SHARES, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_TRADING_FULL, IFTAA_SHARES_8767),
    // ⚠️ «الأرباح بس»: الأرباح نفسها مش متسجلة في الأصول لسه ⇒ قيمة السهم على الزكاة صفر، والأرباح شغل جاي (HANDOVER)
    ZakatRule(ZakatCountry.EG, ZakatTopic.LONG_TERM_SHARES, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_LONG_TERM_DIVIDENDS, IFTAA_SHARES_8767),
    // في مصر جنسية الشركة ما بتفرقش (8767) ⇒ نفس قاعدة طويل الأجل، والسؤال ما بيتسألش أصلًا
    ZakatRule(ZakatCountry.EG, ZakatTopic.FOREIGN_LONG_TERM_SHARES, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_LONG_TERM_DIVIDENDS, IFTAA_SHARES_8767),
    // 4399: «يزكي دينه حين قبضه لسنة واحدة فقط» من غير تفريق بين مرجو ومشكوك ⇒ ما بيدخلش الحساب السنوي
    ZakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_STRONG, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_RECEIVABLE_ON_COLLECTION, IFTAA_DEBT_4399),
    ZakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_DOUBTFUL, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_RECEIVABLE_ON_COLLECTION, IFTAA_DEBT_4399),
    ZakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_COLLECTED, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_RECEIVABLE_COLLECTED, IFTAA_DEBT_4399),
    ZakatRule(ZakatCountry.EG, ZakatTopic.ROSCA_CREDIT, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null, pending = true)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.CRYPTO, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.PENSION, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.CUSTODY, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null)),
)

fun zakatRule(country: ZakatCountry, topic: ZakatTopic): ZakatRule =
    ZAKAT_RULES.first { it.country == country && it.topic == topic }

class ZakatError(message: String) : IllegalArgumentException(message)

/**
 * تصنيف «زكاة» — الزكاة المدفوعة **مصروف عادي** (قرار المالك §62-ب). المعرّف ثابت عشان الجهازين يلاقوا نفس التصنيف،
 * وبيتعمل لو مش موجود بس (اللي المستخدم غيّره ما يتكتبش فوقه) — زي `DuesCategories`. ⚠️ الرمز والألوان مؤقتة (§55).
 */
object ZakatCategory {
    const val ID = "cat-zakat"

    fun defaults(): List<Category> = listOf(Category(ID, null, "زكاة", "hand-heart", "#8a6410", "#e9c46a", true, 910))
}

/** الميزة بتظهر مع «المحتوى الإسلامي: ظاهر» (§46 و§62-د) — مشغّل من الأول (null = ما اتغيرش ⇒ ظاهر). */
fun zakatVisible(profile: UserProfile?): Boolean = profile?.islamicContentVisible != false
