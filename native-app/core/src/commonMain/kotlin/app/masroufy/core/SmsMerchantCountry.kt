package app.masroufy.core

/**
 * **بلد المحل في آخر اسمه** (الجولة السابعة — §75-12 · اختيار (م)/(ع)): وصف المحل بصيغة شبكات الكروت «الاسم المدينة البلد»
 * («SAMPLE MALL DUBAI AE» · «SAMPLE SEATTLE US» · «SAMPLE.COM LONDON GBR» · «AMAZON.COM US») = شراء من **برّه البلد** حتى لو
 * المبلغ بالريال أو بالجنيه. الجولة السادسة كانت بتمسك سطر «Country:» و«سعر الصرف» بس، والبلد اللي في اسم المحل كانت بتعدّي
 * وتتسجل لوحدها. دلوقتي آخر كلمة في اسم المحل = كود بلد (حرفين أو تلاتة **كابيتال**) أو اسم بلد غير بلد القارئ ⇒ الرسالة بتفضل
 * مفهومة بس **تستنى** تأكيد المالك (زي «Country: GB»).
 *
 * القايمة **مقفولة** ومن غير الأكواد اللي هي كلمة إنجليزي عادية في آخر اسم محل («CO» شركة · «AL» · «IS» · «NO» · «BE» · «AT» · «TO» …).
 */

private val ALPHA2 = setOf(
    "AE", "US", "GB", "UK", "EG", "SA", "BH", "KW", "QA", "OM", "JO", "LB", "TR", "IN", "PK", "CN", "HK", "SG", "MY", "ID", "TH", "PH",
    "FR", "DE", "IT", "ES", "NL", "CH", "SE", "DK", "FI", "IE", "PT", "GR", "CA", "AU", "NZ", "JP", "KR", "ZA", "MA", "TN", "LU", "PL",
    "CZ", "HU", "RO", "RU", "UA", "BR", "MX", "CL", "IQ", "SY", "YE", "SD", "LY", "DZ", "ET", "KE", "NG", "BD", "LK", "NP", "VN", "TW",
    "MV", "CY", "MT", "EE", "LV", "LT", "SK", "SI", "HR", "RS", "BG", "AZ", "GE", "KZ", "UZ", "IE",
)

private val ALPHA3 = setOf(
    "USA", "GBR", "ARE", "EGY", "SAU", "KSA", "TUR", "IND", "PAK", "CHN", "HKG", "SGP", "MYS", "IDN", "THA", "PHL", "FRA", "DEU", "ITA",
    "ESP", "NLD", "BEL", "CHE", "AUT", "SWE", "NOR", "DNK", "FIN", "IRL", "PRT", "GRC", "CAN", "AUS", "NZL", "JPN", "KOR", "ZAF", "MAR",
    "TUN", "LUX", "POL", "CZE", "HUN", "ROU", "RUS", "UKR", "BRA", "MEX", "ARG", "CHL", "BHR", "KWT", "QAT", "OMN", "JOR", "LBN", "IRQ",
    "SYR", "YEM", "SDN", "LBY", "DZA", "ETH", "KEN", "NGA", "BGD", "LKA", "NPL", "VNM", "TWN", "MDV", "CYP", "MLT", "GEO", "AZE", "KAZ",
    "UZB", "UAE",
)

/** اسم البلد في آخر وصف المحل (حروف صغيرة بعد [shapeKey]). */
private val COUNTRY_NAME = Regex(
    "(?:^| )(?:united states|united kingdom|turkey|turkiye|india|france|germany|italy|spain|bahrain|kuwait|qatar|oman|jordan|lebanon" +
        "|egypt|saudi arabia|ksa|uae|emirates|الامارات|مصر|تركيا|البحرين|الكويت|قطر|عمان|الاردن|لبنان|السعوديه|السعودية|المملكة العربية السعودية)$",
)

/** بلد القارئ: السعودية ومصر بكل كتاباتهم. */
internal val SAUDI_TAIL: Set<String> = setOf("SA", "SAU", "KSA", "saudi arabia", "ksa", "السعوديه", "السعودية", "المملكة العربية السعودية")
internal val EGYPT_TAIL: Set<String> = setOf("EG", "EGY", "egypt", "مصر")

/** آخر كلمة (أو اسم البلد) في [merchant] = بلد **غير** [local] ⇒ شراء من برّه (§75-12) — الرسالة تستنى. */
internal fun foreignCountryTail(merchant: String, local: Set<String>): Boolean {
    val tokens = merchant.trim().split(' ', '\t').filter { it.isNotEmpty() }
    if (tokens.size < 2) return false // اسم من كلمة واحدة = المحل نفسه، مش «اسم + بلد»
    val last = tokens.last().trimEnd('.', ',', '،')
    if (last.all { it in 'A'..'Z' } && (last.length == 2 && last in ALPHA2 || last.length == 3 && last in ALPHA3)) return last !in local
    val key = shapeKey(merchant)
    val name = COUNTRY_NAME.find(key)?.value?.trim() ?: return false
    return name !in local
}
