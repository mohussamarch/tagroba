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
 */
data class ZakatSource(
    val authority: ZakatAuthority,
    val documentKey: TextKey,
    val fatwaNumber: String?,
    val url: String,
    val issued: String?,
    val checkedOn: String,
    val pending: Boolean = false,
) {
    val document: String get() = if (fatwaNumber != null) uiText(documentKey, fatwaNumber) else uiText(documentKey)
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

private fun iftaa(number: String?, issued: String? = null, pending: Boolean = false) = ZakatSource(
    ZakatAuthority.DAR_AL_IFTAA,
    if (number != null) TextKey.ZAKAT_DOC_FATWA else TextKey.ZAKAT_DOC_IFTAA_GENERAL,
    number,
    if (number != null) "https://www.dar-alifta.org/ar/fatwa/details/$number" else "https://www.dar-alifta.org",
    issued, CHECKED, pending,
)

/**
 * الجدول نفسه. مصر: النصاب 11653 · الحلي 22484 · الأسهم 22181 · الدين عليك **15532 (2020) — الأحدث** وبتلغي 17652 (1996).
 * ⚠️ «يوم ثابت» في مصر مكتوب في §62 «مسموح في فتاوى الدار» من غير رقم ⇒ المصدر عام لحد ما يتحدد الرقم.
 */
val ZAKAT_RULES: List<ZakatRule> = listOf(
    ZakatRule(ZakatCountry.SA, ZakatTopic.RATE, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_RATE, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.CASH, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_CASH, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.NISAB, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_NISAB_SA, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.HAWL, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_HAWL_FIXED, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.MID_YEAR_DIP, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_DIP_RESTART, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.WORN_JEWELRY, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_WORN_EXEMPT, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.SAVED_METAL, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_SAVED_COUNTED, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.DEBTS_OWED, ZakatEffect.NOT_DEDUCTED, TextKey.ZAKAT_RULE_DEBTS_NOT_DEDUCTED, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.TRADING_SHARES, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_TRADING_FULL, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.LONG_TERM_SHARES, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_LONG_TERM_COMPANY_PAYS, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_STRONG, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_RECEIVABLE_STRONG, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.RECEIVABLE_DOUBTFUL, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_RECEIVABLE_DOUBTFUL, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.ROSCA_CREDIT, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_ROSCA_RECEIVABLE, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.CRYPTO, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, ZATCA_GUIDE),
    ZakatRule(ZakatCountry.SA, ZakatTopic.PENSION, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, ZATCA_GUIDE),

    ZakatRule(ZakatCountry.EG, ZakatTopic.RATE, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_RATE, iftaa("11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.CASH, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_CASH, iftaa("11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.NISAB, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_NISAB_EG, iftaa("11653")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.HAWL, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_HAWL_FIXED, iftaa(null)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.MID_YEAR_DIP, ZakatEffect.METHOD, TextKey.ZAKAT_RULE_DIP_FIXED_DAY, iftaa(null, pending = true)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.WORN_JEWELRY, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_WORN_EXEMPT, iftaa("22484")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.SAVED_METAL, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_SAVED_COUNTED, iftaa("22484")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.DEBTS_OWED, ZakatEffect.NOT_DEDUCTED, TextKey.ZAKAT_RULE_DEBTS_NOT_DEDUCTED, iftaa("15532", "2020")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.TRADING_SHARES, ZakatEffect.COUNT, TextKey.ZAKAT_RULE_TRADING_FULL, iftaa("22181")),
    // ⚠️ «الأرباح بس»: الأرباح نفسها مش متسجلة في الأصول لسه ⇒ قيمة السهم على الزكاة صفر، والأرباح شغل جاي (HANDOVER)
    ZakatRule(ZakatCountry.EG, ZakatTopic.LONG_TERM_SHARES, ZakatEffect.EXEMPT, TextKey.ZAKAT_RULE_LONG_TERM_DIVIDENDS, iftaa("22181")),
    ZakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_STRONG, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null, pending = true)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.RECEIVABLE_DOUBTFUL, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null, pending = true)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.ROSCA_CREDIT, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null, pending = true)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.CRYPTO, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null)),
    ZakatRule(ZakatCountry.EG, ZakatTopic.PENSION, ZakatEffect.NO_RULING, TextKey.ZAKAT_RULE_NO_RULING, iftaa(null)),
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
