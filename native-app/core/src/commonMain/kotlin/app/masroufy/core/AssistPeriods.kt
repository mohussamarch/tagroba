package app.masroufy.core

/**
 * الفترة اللي السؤال بيقصدها («الشهر ده» · «الأسبوع اللي فات» · «من أول السنة» · «آخر ٧ أيام» · «آخر ٣ شهور» · «أكتوبر» …) ويوم
 * المصروف المكتوب («امبارح» · «أول امبارح»). الافتراضي = الشهر المالي الحالي (من يوم الراتب). **الأسبوع بيبدأ السبت** في البلدين
 * (رد المالك في النافذة التالتة 2026-10-09 على فرع التصميم). **اسم الشهر = الشهر المالي اللي بيخلص فيه** (نفس النافذة: ٢٨ سبتمبر –
 * ٢٧ أكتوبر = «أكتوبر»). التاريخ اللي في المستقبل ما بيتقبلش.
 */
enum class AssistPeriodKind { CURRENT_FISCAL, PREVIOUS_FISCAL, THIS_WEEK, LAST_WEEK, TODAY, YESTERDAY, DAYS_AGO, THIS_YEAR, LAST_YEAR, LAST_DAYS, LAST_MONTHS, NAMED_MONTH }

/** [days] = عدد الأيام/الشهور («آخر ٧ أيام» · «آخر ٣ شهور» · «أول امبارح» = ٢)، أو رقم الشهر (١–١٢) لـ[AssistPeriodKind.NAMED_MONTH]. */
data class AssistPeriod(val kind: AssistPeriodKind, val days: Int = 0) {
    val fiscal: Boolean get() = kind == AssistPeriodKind.CURRENT_FISCAL || kind == AssistPeriodKind.PREVIOUS_FISCAL || kind == AssistPeriodKind.NAMED_MONTH
}

/** المدى الفعلي: من · لحد (لحد النهارده للفترة الجارية) · و[period] للشهر المالي (الرئيسية بتتقري بيه). */
data class AssistRange(val from: IsoDate, val to: IsoDate, val period: Period?)

private fun phrases(vararg p: String): List<List<String>> = p.map { assistTokens(assistNormalize(it)) }

private val PREVIOUS_MONTH = phrases("الشهر اللي فات", "الشهر الماضي", "الشهر السابق", "الشهر الفايت", "الشهر الفائت", "الشهر اللي راح", "الشهر الي فات", "last month", "previous month")
private val THIS_MONTH = phrases("الشهر ده", "هذا الشهر", "هالشهر", "الشهر هذا", "الشهر الحالي", "this month", "الشهر دا")
private val LAST_WEEK = phrases("الاسبوع اللي فات", "الاسبوع الماضي", "الاسبوع السابق", "الاسبوع الفايت", "الاسبوع الي فات", "last week")
private val THIS_WEEK = phrases("الاسبوع ده", "هذا الاسبوع", "هالاسبوع", "الاسبوع هذا", "الاسبوع الحالي", "this week", "الاسبوع دا")
private val LAST_YEAR = phrases("السنه اللي فاتت", "السنه الماضيه", "العام الماضي", "السنه الفايته", "last year")
private val THIS_YEAR = phrases(
    "السنه دي", "هذه السنه", "هالسنه", "السنه هذي", "من اول السنه", "منذ بدايه السنه", "من بدايه السنه", "هذا العام", "العام الحالي", "السنه الحاليه",
    "this year", "so far this year", "السنه كلها",
)
private val TODAY = phrases("النهارده", "اليوم", "النهاردا", "today", "الحين")
private val YESTERDAY = phrases("امبارح", "امس", "البارحه", "yesterday", "مبارح")
private val DAY_BEFORE_YESTERDAY = phrases("اول امبارح", "اول امس", "قبل امس", "قبل امبارح")
private val TOMORROW = phrases("بكره", "بكرا", "باكر", "بكرة", "tomorrow", "بعد بكره")
private val LAST_N = Regex("""(?:اخر|خلال|last|past)\s+(\d{1,3})\s+(?:يوم|ايام|days?)""")
private val LAST_N_MONTHS = Regex("""(?:اخر|خلال|last|past)\s+(\d{1,2})\s+(?:شهر|شهور|اشهر|months?)""")

/** أسامي الشهور (الميلادي بالعربي · الشامي · الإنجليزي) ⇒ رقم الشهر. «آب» مش هنا (= «أب»). */
private val MONTH_NAMES: Map<String, Int> = buildMap {
    fun put(m: Int, vararg w: String) = w.forEach { put(assistNormalize(it), m) }
    put(1, "يناير", "كانون الثاني", "january", "jan"); put(2, "فبراير", "شباط", "february", "feb"); put(3, "مارس", "اذار", "march")
    put(4, "ابريل", "نيسان", "april", "apr"); put(5, "مايو", "ايار"); put(6, "يونيو", "حزيران", "june", "jun")
    put(7, "يوليو", "تموز", "july", "jul"); put(8, "اغسطس", "august", "aug"); put(9, "سبتمبر", "ايلول", "september", "sep")
    put(10, "اكتوبر", "تشرين الاول", "october", "oct"); put(11, "نوفمبر", "تشرين الثاني", "november", "nov"); put(12, "ديسمبر", "كانون الاول", "december", "dec")
}

/** العبارة موجودة في الكلمات بالترتيب (الكلمة الأولى ممكن يبقى قبلها أداة: «بالشهر اللي فات»). */
fun containsPhrase(tokens: List<String>, phrase: List<String>): Boolean {
    if (phrase.isEmpty() || tokens.size < phrase.size) return false
    for (start in 0..tokens.size - phrase.size) {
        if (phrase.indices.all { k -> phraseWordAt(tokens[start + k], phrase[k], k == 0, phrase.size > 1) }) return true
    }
    return false
}

/**
 * كلمة العبارة: نفسها، أو (لو ٣ حروف أو أكتر وفي عبارة من كلمتين أو أكتر) بلاحقة بعدها («عيد ميلاده» = «عيد ميلاد»). الكلمة الأولى ممكن
 * قبلها أداة. الكلمات القصيرة («ده» · «في» · «من») والعبارة من كلمة واحدة («امس» مش «امسح») لازم تبقى نفسها بالظبط.
 */
private fun phraseWordAt(token: String, word: String, first: Boolean, suffix: Boolean): Boolean {
    val forms = if (first) cliticForms(token) else listOf(token)
    return forms.any { it == word || (suffix && word.length >= 3 && it.startsWith(word)) }
}

private fun hasAny(tokens: List<String>, list: List<List<String>>) = list.any { containsPhrase(tokens, it) }

/** «في اليوم» / «باليوم» = معدل يومي (المتاح يوميًا) مش «النهارده». */
private fun perDay(tokens: List<String>): Boolean = containsPhrase(tokens, listOf("في", "اليوم")) || tokens.any { it == "باليوم" || it == "لليوم" }

/** اسم شهر في الكلام (كلمة لوحدها أو بأداة: «في أكتوبر» · «لأكتوبر»؛ والاسم الشامي من كلمتين). */
private fun namedMonth(tokens: List<String>): Int? {
    for ((name, month) in MONTH_NAMES) {
        val words = name.split(' ')
        if (words.size > 1 && containsPhrase(tokens, words)) return month
    }
    return tokens.firstNotNullOfOrNull { t -> cliticForms(t).firstNotNullOfOrNull { f -> MONTH_NAMES[f]?.takeIf { f.length >= 4 || f == t } } }
}

/** الفترة في السؤال، أو null لو مفيش (⇒ الشهر المالي الحالي). */
fun detectAssistPeriod(normalized: String, tokens: List<String>): AssistPeriod? {
    LAST_N.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..366 }?.let { return AssistPeriod(AssistPeriodKind.LAST_DAYS, it) }
    LAST_N_MONTHS.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..24 }?.let { return AssistPeriod(AssistPeriodKind.LAST_MONTHS, it) }
    return when {
        hasAny(tokens, PREVIOUS_MONTH) -> AssistPeriod(AssistPeriodKind.PREVIOUS_FISCAL)
        hasAny(tokens, LAST_WEEK) -> AssistPeriod(AssistPeriodKind.LAST_WEEK)
        hasAny(tokens, THIS_WEEK) -> AssistPeriod(AssistPeriodKind.THIS_WEEK)
        hasAny(tokens, LAST_YEAR) -> AssistPeriod(AssistPeriodKind.LAST_YEAR)
        hasAny(tokens, THIS_YEAR) -> AssistPeriod(AssistPeriodKind.THIS_YEAR)
        hasAny(tokens, THIS_MONTH) -> AssistPeriod(AssistPeriodKind.CURRENT_FISCAL)
        hasAny(tokens, DAY_BEFORE_YESTERDAY) -> AssistPeriod(AssistPeriodKind.DAYS_AGO, 2)
        hasAny(tokens, YESTERDAY) -> AssistPeriod(AssistPeriodKind.YESTERDAY)
        hasAny(tokens, TODAY) && !perDay(tokens) -> AssistPeriod(AssistPeriodKind.TODAY)
        else -> namedMonth(tokens)?.let { AssistPeriod(AssistPeriodKind.NAMED_MONTH, it) }
    }
}

/** يوم المصروف المكتوب: 0 = النهارده، 1 = امبارح، 2 = أول امبارح، -1 = بكرة (مستقبل ⇒ سؤال)، null = ما اتذكرش. */
fun detectSpendDayOffset(raw: String, tokens: List<String>): Int? = when {
    hasAny(tokens, TOMORROW) || Regex("غدًا|غداً").containsMatchIn(raw) -> -1
    hasAny(tokens, DAY_BEFORE_YESTERDAY) -> 2
    hasAny(tokens, YESTERDAY) -> 1
    hasAny(tokens, TODAY) && !perDay(tokens) -> 0
    else -> null
}

/** السبت اللي بدأ فيه أسبوع [date] (ISO: 6 = السبت). */
fun weekStartSaturday(date: IsoDate): IsoDate = addDaysIso(date, -((isoWeekday(date) - 6).mod(7)))

fun dayBefore(date: IsoDate): IsoDate = addDaysIso(date, -1)

/** اليوم بعد/قبل [days] يوم (للاستخدامات — `addDaysIso` داخلي في القلب). */
fun shiftDays(date: IsoDate, days: Int): IsoDate = addDaysIso(date, days)

/** الشهر المالي اللي **بيخلص** في الشهر [month] (رد المالك: اسم الشهر = اللي بيخلص فيه) — الأقرب لورا من الحالي (لحد سنة). */
fun fiscalPeriodNamed(month: Int, today: IsoDate, payday: Int): Period {
    var p = periodForDate(today, payday)
    repeat(13) {
        if (parseIsoDate(p.end).month == month) return p
        p = periodForDate(dayBefore(p.start), payday)
    }
    return p
}

/** المدى من الفترة + النهارده + يوم الراتب. الشهر المالي بنفس `periodForDate` اللي الرئيسية والميزانية بيستعملوه. */
fun resolveAssistRange(p: AssistPeriod?, today: IsoDate, payday: Int): AssistRange {
    val current = periodForDate(today, payday)
    fun clip(period: Period) = AssistRange(period.start, minOf(period.end, maxOf(period.start, today)), period)
    return when (p?.kind ?: AssistPeriodKind.CURRENT_FISCAL) {
        AssistPeriodKind.CURRENT_FISCAL -> AssistRange(current.start, current.end, current)
        AssistPeriodKind.PREVIOUS_FISCAL -> periodForDate(dayBefore(current.start), payday).let { AssistRange(it.start, it.end, it) }
        AssistPeriodKind.NAMED_MONTH -> fiscalPeriodNamed(p!!.days, today, payday).let { if (it == current) AssistRange(it.start, it.end, it) else clip(it) }
        AssistPeriodKind.THIS_WEEK -> AssistRange(weekStartSaturday(today), today, null)
        AssistPeriodKind.LAST_WEEK -> weekStartSaturday(today).let { AssistRange(addDaysIso(it, -7), addDaysIso(it, -1), null) }
        AssistPeriodKind.TODAY -> AssistRange(today, today, null)
        AssistPeriodKind.YESTERDAY -> dayBefore(today).let { AssistRange(it, it, null) }
        AssistPeriodKind.DAYS_AGO -> addDaysIso(today, -p!!.days).let { AssistRange(it, it, null) }
        AssistPeriodKind.THIS_YEAR -> AssistRange(today.take(4) + "-01-01", today, null)
        AssistPeriodKind.LAST_YEAR -> (today.take(4).toInt() - 1).let { AssistRange("$it-01-01", "$it-12-31", null) }
        AssistPeriodKind.LAST_DAYS -> AssistRange(addDaysIso(today, -(maxOf(1, p!!.days) - 1)), today, null)
        AssistPeriodKind.LAST_MONTHS -> {
            var start = current
            repeat(maxOf(1, p!!.days) - 1) { start = periodForDate(dayBefore(start.start), payday) }
            AssistRange(start.start, today, null)
        }
    }
}

/** اسم الفترة في الرد («هذا الشهر» · «الأسبوع الماضي» · «أكتوبر» …). */
fun assistPeriodLabel(p: AssistPeriod?): String = when (p?.kind ?: AssistPeriodKind.CURRENT_FISCAL) {
    AssistPeriodKind.CURRENT_FISCAL -> uiText(TextKey.ASSIST_PERIOD_THIS_MONTH)
    AssistPeriodKind.PREVIOUS_FISCAL -> uiText(TextKey.ASSIST_PERIOD_LAST_MONTH)
    AssistPeriodKind.NAMED_MONTH -> uiText(TextKey.ASSIST_PERIOD_IN, assistMonthName(p!!.days))
    AssistPeriodKind.THIS_WEEK -> uiText(TextKey.ASSIST_PERIOD_THIS_WEEK)
    AssistPeriodKind.LAST_WEEK -> uiText(TextKey.ASSIST_PERIOD_LAST_WEEK)
    AssistPeriodKind.TODAY -> uiText(TextKey.ASSIST_PERIOD_TODAY)
    AssistPeriodKind.YESTERDAY -> uiText(TextKey.ASSIST_PERIOD_YESTERDAY)
    AssistPeriodKind.DAYS_AGO -> uiText(TextKey.ASSIST_PERIOD_DAY_BEFORE)
    AssistPeriodKind.THIS_YEAR -> uiText(TextKey.ASSIST_PERIOD_THIS_YEAR)
    AssistPeriodKind.LAST_YEAR -> uiText(TextKey.ASSIST_PERIOD_LAST_YEAR)
    AssistPeriodKind.LAST_DAYS -> uiText(TextKey.ASSIST_PERIOD_LAST_DAYS, p!!.days.toString())
    AssistPeriodKind.LAST_MONTHS -> uiText(TextKey.ASSIST_PERIOD_LAST_MONTHS, p!!.days.toString())
}

/** اسم الشهر من جدول النصوص (ميلادي). */
fun assistMonthName(month: Int): String = uiText(TextKey.ASSIST_MONTHS).split('|').getOrElse(month - 1) { month.toString() }
