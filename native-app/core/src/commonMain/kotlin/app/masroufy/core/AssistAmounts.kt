package app.masroufy.core

/**
 * المبالغ في كلام المساعد ⇒ **أعداد صحيحة بالوحدة الصغرى** (CLAUDE.md #1 — نص بس، ولا `Double` في أي خطوة): «١٥» · «15.5» · «١٥٫٥» ·
 * «1,250» · «١٬٢٥٠» · «1.500» (فاصل آلاف) · «ب15» · «٢ ألف» · «ألفين» · كلمات الأرقام («خمسة وعشرين» · «ميتين» · «ألف وخمسمية»).
 * الرقم اللي **مش مبلغ** بيتشال: «آخر ٧ أيام» (مدة) · «على ٣» (عدد ناس في التقسيم) · «يوم ٢٨» · «الساعة ٥» · «٨٠٪» (نسبة).
 * «٥٠ هللة» و«٥٠ قرش» = وحدة صغرى. العملة المذكورة بالاسم أو الرمز بتطلع لوحدها — اللي **غير عملة البلد** بيخلّي المساعد يسأل بصراحة
 * من غير كارت. أكتر من خانتين بعد العلامة أو رقم أكبر من المسموح ⇒ [AssistMoneyScan.invalid] (ما بنقرّبش بصمت).
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
    /** رقم مكتوب كمبلغ بس مش صالح (أكتر من خانتين كسر · أكبر من المسموح). */
    val invalid: Boolean = false,
) {
    /** المبالغ المختلفة — «قهوة ١٥» = واحد، «١٥ و٢٠» = اتنين ⇒ سؤال. */
    val distinct: List<Halalas> get() = amounts.map { it.minor }.distinct()

    /** في البلد دي: العملة اللي اتذكرت مش عملتها (أو مجهولة) ⇒ أجنبي. */
    fun isForeign(spaceCurrency: Currency): Boolean = unknownCurrency || (currency != null && currency != spaceCurrency)
}

private val NUMBER = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:[.,]\d+)?""")
private val GROUPED = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?""")

private fun words(vararg w: String): Set<String> = w.map(::assistNormalize).filter { it.isNotBlank() }.toSet()

private val CURRENCY_WORDS: Map<String, Currency> = buildMap {
    for (w in words("ريال", "ريالات", "رس", "ر.س", "sar", "sr", "riyal", "riyals")) put(w, Currency.SAR)
    for (w in words("جنيه", "جنيهات", "جنية", "ج.م", "جم", "egp", "le")) put(w, Currency.EGP)
    for (w in words("دولار", "دولارات", "usd", "dollar", "dollars", "$")) put(w, Currency.USD)
    for (w in words("يورو", "eur", "euro", "euros", "€")) put(w, Currency.EUR)
    for (w in words("استرليني", "إسترليني", "gbp", "£")) put(w, Currency.GBP)
    for (w in words("درهم", "دراهم", "aed", "dirham", "dirhams")) put(w, Currency.AED)
}

/** الوحدة الصغرى بالاسم: «هللة» (الريال) و«قرش» (الجنيه). */
private val MINOR_WORDS: Map<String, Currency> = buildMap {
    for (w in words("هلله", "هللات", "halala", "halalas")) put(w, Currency.SAR)
    for (w in words("قرش", "قروش", "piasters", "piaster")) put(w, Currency.EGP)
}
private val UNKNOWN_CURRENCY = words("دينار", "دنانير", "ليره", "ليرات", "روبيه", "ين", "يوان", "bitcoin", "بيتكوين")
private val DURATION_AFTER = words(
    "يوم", "ايام", "يومين", "اسبوع", "اسابيع", "شهر", "شهور", "اشهر", "سنه", "سنين", "سنوات", "ساعه", "ساعات", "دقيقه", "دقايق", "day", "days", "week",
    "weeks", "month", "months", "year", "years", "hour", "hours", "مره", "مرات", "times", "قسط", "اقساط",
)
private val WEEKDAYS = words("السبت", "الاحد", "الحد", "الاثنين", "الاتنين", "الثلاثاء", "التلات", "الاربعاء", "الاربع", "الخميس", "الجمعه")
private val PEOPLE_AFTER = words("اشخاص", "افراد", "ناس", "انفار", "people", "persons", "ways")
private val DAY_BEFORE = words("يوم", "تاريخ", "الساعه", "ساعه", "day", "at", "قبل", "بعد", "اخر", "خلال", "last", "past")
private val THOUSAND = words("الف", "الاف", "الوف", "k", "thousand", "تلاف")

/**
 * كلمات الأرقام (فصحى · مصري · خليجي). «واحد» مش هنا عن قصد (عدد مش مبلغ: «قهوة واحد»). المضاعفات: «ألف» بعد رقم أصغر = ضرب.
 */
private val NUMBER_WORDS: Map<String, Long> = buildMap {
    fun put(v: Long, vararg w: String) = w.forEach { put(assistNormalize(it), v) }
    // الأشكال القصيرة («ست» · «خمس» · «تلات» · «اربع») مش هنا عن قصد: كلمات تانية («الست» · يوم «التلات» · «الاربع»)
    put(2, "اتنين", "اثنين", "اثنان"); put(3, "تلاته", "ثلاثه"); put(4, "اربعه"); put(5, "خمسه")
    put(6, "سته"); put(7, "سبعه"); put(8, "تمانيه", "ثمانيه"); put(9, "تسعه"); put(10, "عشره")
    put(11, "حداشر", "احدعشر"); put(12, "اتناشر", "اثنعشر"); put(15, "خمستاشر", "خمسطعش"); put(20, "عشرين", "عشرون"); put(25, "خمسه وعشرين")
    put(30, "تلاتين", "ثلاثين", "ثلاثون"); put(40, "اربعين", "اربعون"); put(50, "خمسين", "خمسون"); put(60, "ستين", "ستون")
    put(70, "سبعين", "سبعون"); put(80, "تمانين", "ثمانين", "ثمانون"); put(90, "تسعين", "تسعون")
    put(100, "ميه", "مية", "مائه", "مايه"); put(200, "ميتين", "مئتين", "مائتين", "مايتين"); put(300, "تلتميه", "ثلاثمائه", "تلاتمية", "ثلاثميه")
    put(400, "ربعميه", "اربعمائه", "اربعميه"); put(500, "خمسميه", "خمسمائه", "خمسمية"); put(1000, "الف"); put(2000, "الفين", "الفان")
}

/**
 * النص الرقمي ⇒ وحدة صغرى بالضرب والجمع الصحيح. «1.500» (٣ خانات بعد النقطة) = فاصل آلاف. أكتر من خانتين كسر غير كده ⇒ مش مبلغ
 * (null) — ما بنقرّبش بصمت.
 */
internal fun digitsToMinor(text: String, multiplier: Long = 1, minorUnits: Boolean = false): Halalas? {
    var clean = if (GROUPED.matches(text)) text.replace(",", "") else text.replace(',', '.')
    val dot = clean.split('.')
    if (dot.size == 2 && dot[1].length == 3 && dot[0].length in 1..3) clean = dot[0] + dot[1]
    val parts = clean.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].toLongOrNull() ?: return null
    val frac = parts.getOrNull(1) ?: ""
    if (frac.length > 2 || whole > 1_000_000_000_000L) return null
    if (minorUnits) return if (frac.isEmpty() && whole > 0) whole else null
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

/** الكلمة نفسها أو بعد «و»/«ب» بس — مش بعد «ال» («الاتنين» يوم في الأسبوع). */
private fun numberWord(token: String): Long? = NUMBER_WORDS[token] ?: NUMBER_WORDS[token.removePrefix("و")] ?: NUMBER_WORDS[token.removePrefix("ب")]

fun scanMoney(tokens: List<String>): AssistMoneyScan {
    val amounts = mutableListOf<AssistAmount>()
    var currency: Currency? = null
    var unknown = false
    var invalid = false
    var headCount: Int? = null
    var skipNext = false
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        val forms = cliticForms(token)
        forms.firstNotNullOfOrNull { CURRENCY_WORDS[it] }?.let { currency = currency ?: it }
        if (forms.any { it in UNKNOWN_CURRENCY }) unknown = true
        if ('$' in token) currency = currency ?: Currency.USD
        val after = tokens.getOrNull(i + 1)
        val before = tokens.getOrNull(i - 1)
        // كلمات الأرقام: «خمسه وعشرين» · «الف وخمسميه» · «تلات الاف»
        val wordValue = if (token.none { it.isDigit() }) numberWord(token) else null
        if (wordValue != null) {
            var total = 0L
            var j = i
            while (j < tokens.size) {
                val v = numberWord(tokens[j]) ?: break
                total = if (v == 1000L && total in 1..999) total * 1000 else total + v
                j++
            }
            if (j < tokens.size && cliticForms(tokens[j]).any { it in THOUSAND } && total in 1..999) { total *= 1000; j++ }
            val next = tokens.getOrNull(j)
            val notMoney = isDuration(next, tokens.getOrNull(j + 1)) || (next != null && cliticForms(next).any { it in PEOPLE_AFTER })
            if (!notMoney && total > 0) amounts += AssistAmount(total * 100, i)
            i = j
            continue
        }
        for (m in NUMBER.findAll(token)) {
            val glued = token.substring(m.range.last + 1)
            if (glued.isNotEmpty()) CURRENCY_WORDS[glued]?.let { currency = currency ?: it }
            if (isDuration(after, tokens.getOrNull(i + 2))) continue
            if (glued.startsWith('%') || after == "%" || '%' in token) continue
            if (before != null && cliticForms(before).any { it in DAY_BEFORE }) continue
            val people = after != null && cliticForms(after).any { it in PEOPLE_AFTER }
            val onCount = before != null && tokenIsWord(before, "علي") && (after == null || cliticForms(after).none { it in CURRENCY_WORDS })
            if (people || onCount) {
                m.value.toIntOrNull()?.takeIf { it in 2..50 }?.let { headCount = it; continue }
            }
            val minorCurrency = after?.let { a -> cliticForms(a).firstNotNullOfOrNull { MINOR_WORDS[it] } }
            if (minorCurrency != null) {
                currency = currency ?: minorCurrency
                digitsToMinor(m.value, minorUnits = true)?.let { amounts += AssistAmount(it, i) } ?: run { invalid = true }
                continue
            }
            val times = if (after != null && cliticForms(after).any { it in THOUSAND }) 1000L else 1L
            if (times == 1000L) skipNext = true
            val minor = digitsToMinor(m.value, times)
            if (minor == null) invalid = true else amounts += AssistAmount(minor, i)
        }
        // «٢ ألف»: كلمة «ألف» اتحسبت مع الرقم — ما تتعدّش مبلغ لوحدها
        i += if (skipNext) 2 else 1
        skipNext = false
    }
    return AssistMoneyScan(amounts, currency, unknown, headCount, invalid)
}
