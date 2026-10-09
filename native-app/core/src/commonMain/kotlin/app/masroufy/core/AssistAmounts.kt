package app.masroufy.core

/**
 * المبالغ في كلام المساعد ⇒ **أعداد صحيحة بالوحدة الصغرى** (CLAUDE.md #1 — ولا `Double` في أي خطوة): «١٥» · «15.5» · «1,500» ·
 * «1.500» (فاصل آلاف) · «٢ ألف» · «ألفين». الرقم اللي **مش مبلغ** بيتشال: «آخر ٧ أيام» (مدة) · «على ٣» (عدد ناس في التقسيم) ·
 * «يوم ٢٨» (يوم في الشهر) · «٨٠٪» (نسبة). العملة المذكورة بالاسم أو الرمز بتطلع لوحدها — اللي **غير عملة البلد** بيخلّي المساعد
 * يسأل بصراحة من غير كارت.
 */
data class AssistAmount(val minor: Halalas, val tokenIndex: Int)

data class AssistMoneyScan(
    /** المبالغ بالترتيب (من غير المدد والأعداد والأيام والنسب). */
    val amounts: List<AssistAmount>,
    /** عملة اتذكرت بالاسم أو الرمز (null = ما اتذكرتش). */
    val currency: Currency?,
    /** عملة اتذكرت ومش من العملات اللي التطبيق بيعرفها (دينار · ليرة …) ⇒ أجنبي أكيد. */
    val unknownCurrency: Boolean,
    /** «على ٣» في التقسيم: عدد الناس (مع صاحب الحساب). */
    val headCount: Int?,
) {
    /** المبالغ المختلفة — «قهوة ١٥» = واحد، «١٥ و٢٠» = اتنين ⇒ سؤال. */
    val distinct: List<Halalas> get() = amounts.map { it.minor }.distinct()

    /** في البلد دي: العملة اللي اتذكرت مش عملتها (أو مجهولة) ⇒ أجنبي. */
    fun isForeign(spaceCurrency: Currency): Boolean = unknownCurrency || (currency != null && currency != spaceCurrency)
}

private val NUMBER = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:[.,]\d+)?""")
private val GROUPED = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?""")

private fun words(vararg w: String): Set<String> = w.map(::assistNormalize).toSet()

private val CURRENCY_WORDS: Map<String, Currency> = buildMap {
    for (w in words("ريال", "ريالات", "رس", "ر.س", "sar", "sr", "riyal", "riyals")) put(w, Currency.SAR)
    for (w in words("جنيه", "جنيهات", "جنية", "ج.م", "جم", "egp", "le")) put(w, Currency.EGP)
    for (w in words("دولار", "دولارات", "usd", "dollar", "dollars", "$")) put(w, Currency.USD)
    for (w in words("يورو", "eur", "euro", "euros", "€")) put(w, Currency.EUR)
    for (w in words("استرليني", "إسترليني", "gbp", "£")) put(w, Currency.GBP)
    for (w in words("درهم", "دراهم", "aed", "dirham", "dirhams")) put(w, Currency.AED)
}
private val UNKNOWN_CURRENCY = words("دينار", "دنانير", "ليره", "ليرات", "روبيه", "ين", "يوان", "bitcoin", "بيتكوين")
private val DURATION_AFTER = words(
    "يوم", "ايام", "يومين", "اسبوع", "اسابيع", "شهر", "شهور", "اشهر", "سنه", "سنين", "سنوات", "day", "days", "week", "weeks", "month", "months",
    "year", "years", "مره", "مرات", "times",
)
private val WEEKDAYS = words("السبت", "الاحد", "الحد", "الاثنين", "الاتنين", "الثلاثاء", "التلات", "الاربعاء", "الاربع", "الخميس", "الجمعه")
private val PEOPLE_AFTER = words("اشخاص", "افراد", "ناس", "انفار", "people", "persons", "ways")
private val DAY_BEFORE = words("يوم", "تاريخ", "الساعه", "ساعه", "day", "at")
private val THOUSAND = words("الف", "الاف", "الوف", "k", "thousand")
private val TWO_THOUSAND = assistNormalize("ألفين")

/**
 * النص الرقمي ⇒ وحدة صغرى بالضرب والجمع الصحيح. «1.500» (٣ خانات بعد النقطة) = فاصل آلاف. أكتر من خانتين كسر غير كده ⇒ مش مبلغ
 * (null) — ما بنقرّبش بصمت.
 */
internal fun digitsToMinor(text: String, multiplier: Long = 1): Halalas? {
    var clean = if (GROUPED.matches(text)) text.replace(",", "") else text.replace(',', '.')
    val dot = clean.split('.')
    if (dot.size == 2 && dot[1].length == 3 && dot[0].length in 1..3) clean = dot[0] + dot[1]
    val parts = clean.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].toLongOrNull() ?: return null
    val frac = parts.getOrNull(1) ?: ""
    if (frac.length > 2 || whole > 1_000_000_000_000L) return null
    val cents = (frac + "00").take(2).toLong()
    val minor = (whole * 100 + cents) * multiplier
    if (minor <= 0 || minor > MAX_SAFE_HALALAS) return null
    return minor
}

private fun isDuration(after: String?, afterNext: String?): Boolean {
    if (after == null || cliticForms(after).none { it in DURATION_AFTER }) return false
    // «١٥ يوم الجمعة» = مبلغ وبعده يوم في الأسبوع، مش مدة
    return afterNext == null || cliticForms(afterNext).none { it in WEEKDAYS }
}

fun scanMoney(tokens: List<String>): AssistMoneyScan {
    val amounts = mutableListOf<AssistAmount>()
    var currency: Currency? = null
    var unknown = false
    var headCount: Int? = null
    for ((i, token) in tokens.withIndex()) {
        val forms = cliticForms(token)
        forms.firstNotNullOfOrNull { CURRENCY_WORDS[it] }?.let { currency = currency ?: it }
        if (forms.any { it in UNKNOWN_CURRENCY }) unknown = true
        if ('$' in token) currency = currency ?: Currency.USD
        if (forms.any { it == TWO_THOUSAND }) {
            amounts += AssistAmount(200_000, i)
            continue
        }
        for (m in NUMBER.findAll(token)) {
            val after = tokens.getOrNull(i + 1)
            val before = tokens.getOrNull(i - 1)
            val glued = token.substring(m.range.last + 1)
            CURRENCY_WORDS[glued]?.let { currency = currency ?: it }
            if (isDuration(after, tokens.getOrNull(i + 2))) continue
            if (glued.startsWith('%') || after == "%" || '%' in token) continue
            if (before != null && cliticForms(before).any { it in DAY_BEFORE }) continue
            val people = after != null && cliticForms(after).any { it in PEOPLE_AFTER }
            val onCount = before != null && tokenIsWord(before, "علي") && (after == null || cliticForms(after).none { it in CURRENCY_WORDS })
            if (people || onCount) {
                m.value.toIntOrNull()?.takeIf { it in 2..50 }?.let { headCount = it; continue }
            }
            val times = if (after != null && cliticForms(after).any { it in THOUSAND }) 1000L else 1L
            digitsToMinor(m.value, times)?.let { amounts += AssistAmount(it, i) }
        }
    }
    return AssistMoneyScan(amounts, currency, unknown, headCount)
}
