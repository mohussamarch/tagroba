package app.masroufy.core

/**
 * **بلد المحل في آخر اسمه** (الجولة السابعة — §75-12 · اختيار (م)/(ع)): وصف المحل بصيغة شبكات الكروت «الاسم المدينة البلد»
 * («SAMPLE MALL DUBAI AE» · «SAMPLE SEATTLE US» · «SAMPLE.COM LONDON GBR» · «AMAZON.COM US») = شراء من **برّه البلد** حتى لو
 * المبلغ بالريال أو بالجنيه ⇒ الرسالة بتفضل مفهومة بس **تستنى** تأكيد المالك (زي «Country: GB»).
 *
 * **الجولة التامنة** (المراجعة العدائية الرابعة): الأكواد **كلها** من ISO 3166 (حرفين وتلاتة — من غير الكلمات الإنجليزي العادية، اختيار (ذ)) ·
 * أسامي البلاد بالإنجليزي والعربي («AZERBAIJAN» · «MALAYSIA» · «جورجيا» · «بريطانيا») · **مدن** معروفة («TEST STORE DUBAI» · «… ISTANBUL» ·
 * «TEST HOTEL MAKKAH» في مصر) — `SmsCountryNames.kt`. والذيل بيتقسم على «-» و«/» و«,» و«.» كمان («LONDON-GB» · «ISTANBUL-TR» ·
 * «SAMPLE.CO.UK»)، والنقط بين الحروف بتتشال («U.A.E» = UAE). الكود بحروف صغيرة («Dubai Ae») بيتحسب لو قبله مدينة أو بلد.
 */

/** بلد القارئ: الأكواد والأسامي والمدن (بعد [placeKey] للأسامي). */
internal val SAUDI_TAIL: Set<String> =
    setOf("SA", "SAU", "KSA") + (listOf("saudi arabia", "ksa", "السعوديه", "المملكه العربيه السعوديه") + SAUDI_CITIES).map(::placeKey)
internal val EGYPT_TAIL: Set<String> = setOf("EG", "EGY") + (listOf("egypt", "مصر") + EGYPT_CITIES).map(::placeKey)

/** «U.A.E» · «U.S.A.» · «U.K.» ⇒ «UAE» · «USA» · «UK». */
private val DOTTED_CODE = Regex("(?<![A-Za-z])((?:[A-Za-z]\\.){2,}[A-Za-z]?)\\.?")
private val TAIL_SPLIT = Regex("[\\s\\-–—/,،.]+")

/** أطول ذيل (لحد 4 كلمات) اسمه بلد أو مدينة معروفة، أو null. */
private fun placeAtEnd(tokens: List<String>): String? {
    for (n in minOf(4, tokens.size) downTo 1) {
        val key = placeKey(tokens.takeLast(n).joinToString(" "))
        if (key in KNOWN_PLACES) return key
    }
    return null
}

/** آخر كلمة كود بلد (حرفين أو تلاتة من القايمة)، أو null. */
private fun countryCode(token: String): String? {
    val code = token.uppercase()
    return code.takeIf { it.length == 2 && it in ISO_ALPHA2 || it.length == 3 && it in ISO_ALPHA3 }
}

/** آخر كلمة (أو اسم البلد/المدينة) في [merchant] = مكان **غير** [local] ⇒ شراء من برّه (§75-12) — الرسالة تستنى. */
internal fun foreignCountryTail(merchant: String, local: Set<String>): Boolean {
    val folded = DOTTED_CODE.replace(merchant.trim()) { it.groupValues[1].replace(".", "") }
    val tokens = folded.split(TAIL_SPLIT).filter { it.isNotEmpty() }
    if (tokens.size < 2) return false // اسم من كلمة واحدة = المحل نفسه، مش «اسم + بلد»
    val last = tokens.last()
    val code = countryCode(last)
    // الكود بحروف كبيرة، أو بحروف صغيرة **وقبله مكان معروف** («Sample Store Dubai Ae»)
    if (code != null && (last.all { it in 'A'..'Z' } || placeAtEnd(tokens.dropLast(1)) != null)) return code !in local
    val place = placeAtEnd(tokens) ?: return false
    return place !in local
}
