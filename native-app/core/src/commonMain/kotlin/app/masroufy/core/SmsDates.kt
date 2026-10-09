package app.masroufy.core

/**
 * تاريخ العملية من نص رسالة البنك. **ما بنخمّنش:** التاريخ اللي ممكن يتقري بطريقتين بيتقبل بس لو طريقة واحدة بتطلع يوم
 * من 60 يوم قبل وصول الرسالة لحد يوم بعده — وإلا الرسالة بتترفض «التاريخ مش واضح».
 */

private fun iso(y: Int, m: Int, d: Int) = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

// «\» اتضافت (الراجحي — إيداع حساب المواطن «26\09\14») · ملف المرجع مفيهوش «\» فما اتغيرش حاجة فيه
private val LONG_YMD = Regex("(?<!\\d)(\\d{4})[-/\\\\](\\d{1,2})[-/\\\\](\\d{1,2})(?!\\d)")
private val LONG_DMY = Regex("(?<!\\d)(\\d{1,2})[-/\\\\](\\d{1,2})[-/\\\\](\\d{4})(?!\\d)")
private val SHORT = Regex("(?<!\\d)(\\d{1,2})[-/\\\\](\\d{1,2})[-/\\\\](\\d{1,2})(?!\\d)")

private fun inWindow(value: IsoDate, received: Long): Boolean {
    val time = toDayNumber(parseIsoDate(value)).toLong() * DAY_MS
    return time <= received + DAY_MS && time >= received - 60 * DAY_MS
}

/** سنتين أرقام: «26/9/14» (سنة/شهر/يوم) أو «14/9/26» (يوم/شهر/سنة) — المقبول واحد بس. */
private fun shortCandidates(regex: Regex, body: String, received: Long): Set<String> {
    val candidates = LinkedHashSet<String>()
    for (m in regex.findAll(body)) {
        val (a, b, c) = m.destructured.toList().map { it.toInt() }
        for (value in listOf(iso(2000 + a, b, c), iso(2000 + c, b, a))) {
            if (isValidIsoDate(value) && inWindow(value, received)) candidates.add(value)
        }
    }
    return candidates
}

/**
 * التاريخ بسنة كاملة **مش بعد يوم من الوصول** (الجولة التانية من المراجعة): تاريخ في المستقبل = آخر موعد عرض أو ميعاد سداد
 * («حتى 31/03/2026» · «قبل 2026-03-25») مش عملية خلصت — كان بيبقى تاريخ العملية، وتاريخ بعد سنة كان بيتقبل.
 * الحد التاني (60 يوم قبل الوصول) **ما بيتطبقش** على التاريخ بسنة كاملة: ملف المرجع `golden/sms.json` بيقفل رسايل اتأخرت أكتر من
 * كده بتاريخها (سؤال مفتوح للمالك في OVERRIDES §75.1). وقت الوصول مش مفهوم ⇒ التاريخ زي ما هو (ملف المرجع).
 */
private fun notFuture(value: IsoDate, receivedAt: String): IsoDate? {
    if (!isValidIsoDate(value)) return null
    val received = JsText.parseIsoMillis(receivedAt) ?: return value
    return value.takeIf { toDayNumber(parseIsoDate(it)).toLong() * DAY_MS <= received + DAY_MS }
}

/**
 * التاريخ الهجري (الجولة التالتة): «1447/09/16هـ الموافق 2026-03-05» كانت بتتسجل سنة 1447 ميلادي. التاريخ بسنة كاملة لازم سنته
 * تبقى جنب سنة الوصول (السنة اللي قبلها لحد اللي بعدها — [notFuture] بيحكم الحد اللي فوق بالظبط)؛ الهجري (14xx) أو أي سنة بعيدة
 * بيتساب والتاريخ اللي بعده بيتجرب. ده ما بيلمسش قفل ملف المرجع (رسالة اتأخرت أكتر من 60 يوم في نفس السنة أو اللي قبلها).
 * (تخطي «هـ» وتفضيل «الموافق» اتجربوا واتشالوا: حد السنة بيعمل نفس الشغل — التحوير ما غيّرش ولا نتيجة.)
 */
private fun plausibleYear(year: Int, receivedAt: String): Boolean {
    val received = JsText.parseIsoMillis(receivedAt) ?: return true
    val receivedYear = parseIsoDate(dayNumberToIso(received.floorDiv(DAY_MS).toInt())).year
    return year in receivedYear - 1..receivedYear + 1
}

/** أول تاريخ بسنة كاملة سنته معقولة، أو null. */
private fun fullDate(regex: Regex, body: String, receivedAt: String, yearGroup: Int): MatchResult? =
    regex.findAll(body).firstOrNull { plausibleYear(it.groupValues[yearGroup].toInt(), receivedAt) }

/** نفس قاعدة التطبيق الحالي (ملف المرجع `golden/sms.json`) + فاصل «\» + التاريخ بسنة مش في المستقبل ولا هجري. */
internal fun saudiTransactionDate(body: String, receivedAt: String): IsoDate? {
    fullDate(LONG_YMD, body, receivedAt, 1)?.let { m ->
        return notFuture(iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), receivedAt)
    }
    fullDate(LONG_DMY, body, receivedAt, 3)?.let { m ->
        return notFuture(iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()), receivedAt)
    }
    if (LONG_YMD.containsMatchIn(body) || LONG_DMY.containsMatchIn(body)) return null // تاريخ بسنة كاملة بس هجري أو بعيد
    val received = JsText.parseIsoMillis(receivedAt) ?: return null
    return shortCandidates(SHORT, body, received).singleOrNull()
}

// ── مصر ──────────────────────────────────────────────────────────────────

private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
private val MONTH_NAME = Regex("(?<![A-Za-z])(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?[ \\t]+(\\d{1,2}),?[ \\t]+(\\d{4})", RegexOption.IGNORE_CASE)
private val EG_LONG_YMD = Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)")
private val EG_LONG_DMY = Regex("(?<![\\d.,])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})(?!\\d)")

/** سنتين أرقام بأي فاصل: «14/09/26» (التجاري الدولي) · «26-09-14» (فودافون كاش) · «14.09.26» (بريدفاست). */
private val EG_SHORT = Regex("(?<![\\d.,])(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2})(?![\\d.,])")

/** الأهلي المصري: «يوم 09-14» = شهر-يوم من غير سنة (البحث: «MM-DD with NO year»). */
private val NBE_MONTH_DAY = Regex("يوم[ \\t]*(\\d{1,2})-(\\d{1,2})(?![-/.\\d])")

/** كلمة قبل التاريخ في القوالب: «on 29/07» (QNB · بيت التمويل) · «dated» · «يوم» · «في» · «بتاريخ». */
private const val DATE_WORD = "(?:(?<![A-Za-z])(?:on|dated)|(?<![\\u0600-\\u06FF])(?:يوم|في|فى|بتاريخ))"

/**
 * «29/07» = يوم/شهر من غير سنة (QNB · بيت التمويل الكويتي) — **في مكان التاريخ بس**: بعد كلمة تاريخ أو قبل الساعة على طول.
 * «PHARMA 24/7» في اسم المحل مش تاريخ (مراجعة جلسة 33: كانت بتتقري 24 يوليو وتتسجل في صمت).
 */
private val DAY_MONTH = Regex(
    "$DATE_WORD[ \\t]*[:：]?[ \\t]*(\\d{1,2})/(\\d{1,2})(?![\\d/])|(?<![\\d.,])(\\d{1,2})/(\\d{1,2})(?![\\d,/])(?=[ \\t]+(?:at[ \\t]+)?\\d{1,2}:\\d{2})",
    RegexOption.IGNORE_CASE,
)

/** تاريخ بسنة في أي مكان (أي فاصل) — أو يوم/شهر في مكان التاريخ ([DAY_MONTH] و«يوم MM-DD»). */
private val FULL_DATE_TOKEN = Regex(
    "(?<![\\d.,])\\d{1,4}[-/\\\\]\\d{1,2}[-/\\\\]\\d{1,4}(?![\\d,])|(?<![\\d.,])\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?![\\d.,])",
)
private val PARTIAL_DATE_IN_PLACE = Regex(
    "$DATE_WORD[ \\t]*[:：]?[ \\t]*\\d{1,2}[-/\\\\]\\d{1,2}(?![\\d,])|(?<![\\d.,])\\d{1,2}[-/\\\\]\\d{1,2}(?![\\d,/\\\\-])(?=[ \\t]+(?:at[ \\t]+)?\\d{1,2}:\\d{2})",
    RegexOption.IGNORE_CASE,
)

/** أي تاريخ في الرسالة — لو مفيش خالص، تاريخ الوصول هو تاريخ العملية (§40.3-١). «24/7» في اسم محل مش تاريخ. */
internal fun hasDateToken(body: String): Boolean =
    FULL_DATE_TOKEN.containsMatchIn(body) || PARTIAL_DATE_IN_PLACE.containsMatchIn(body) || MONTH_NAME.containsMatchIn(body)

/**
 * الرسالة اللي مفيهاش تاريخ بتاخد يوم الوصول **بس** لو فيها عبارة عملية خلصت (تم … · إيداع · received · credited · has been …
 * · Successful). رمز أو عرض أو طلب أو «pending» من غير تاريخ ما يتسجلش بتاريخ النهارده (مراجعة جلسة 33).
 */
private val DONE_PHRASE = Regex(
    "(?<![\\u0600-\\u06FF])و?تم(?![\\u0600-\\u06FF])|إيداع|ايداع|(?<![A-Za-z])(?:has|have)[ \\t]+been(?![A-Za-z])" +
        "|(?<![A-Za-z])(?:received|credited|debited|deducted|returned|refunded|recharged|charged)(?![A-Za-z])|(?<![A-Za-z])successful",
    RegexOption.IGNORE_CASE,
)

private const val HOUR_MS = 3_600_000L

/** آخر [weekday] في الشهر (الأحد = 0). رقم اليوم 0 = 1970-01-01 = خميس. */
private fun lastWeekday(year: Int, month: Int, lastDay: Int, weekday: Int): Int {
    val last = toDayNumber(DateParts(year, month, lastDay))
    return last - (last + 4 - weekday).mod(7)
}

/**
 * يوم الوصول بتوقيت القاهرة. `receivedAt` من أندرويد = `Instant.toString()` بتوقيت جرينتش، فـ«أول 10 حروف» كانت بتسجل رسالة
 * وصلت بعد نص الليل بتوقيت القاهرة على اليوم اللي قبله (مراجعة جلسة 33). مصر +2، والتوقيت الصيفي (+3، من 2023) من أول آخر جمعة
 * في أبريل لحد آخر خميس في أكتوبر.
 */
internal fun cairoDayOf(receivedAt: String): IsoDate? {
    if (receivedAt.length == 10) return receivedAt.takeIf(::isValidIsoDate)
    val standard = (JsText.parseIsoMillis(receivedAt) ?: return null) + 2 * HOUR_MS
    val year = parseIsoDate(dayNumberToIso(standard.floorDiv(DAY_MS).toInt())).year
    val summer = year >= 2023 &&
        standard >= lastWeekday(year, 4, 30, 5) * DAY_MS &&
        standard < (lastWeekday(year, 10, 31, 4) + 1) * DAY_MS - HOUR_MS
    return dayNumberToIso((if (summer) standard + HOUR_MS else standard).floorDiv(DAY_MS).toInt())
}

/** يوم/شهر من غير سنة: سنة الوصول (بتوقيت القاهرة) أو اللي قبلها، والمقبول واحد بس. */
private fun withYear(month: Int, day: Int, receivedAt: String, received: Long): IsoDate? {
    val year = cairoDayOf(receivedAt)?.take(4)?.toIntOrNull() ?: return null
    val candidates = listOf(year, year - 1).map { iso(it, month, day) }.filter { isValidIsoDate(it) && inWindow(it, received) }
    return candidates.singleOrNull()
}

/**
 * مصر: الشهر بالاسم ⇒ سنة كاملة ⇒ سنتين أرقام ⇒ «يوم MM-DD» ⇒ يوم/شهر في مكان التاريخ ⇒ ولا تاريخ خالص = يوم الوصول بتوقيت
 * القاهرة (لو الرسالة فيها عبارة عملية خلصت — [DONE_PHRASE]).
 */
internal fun egyptTransactionDate(body: String, receivedAt: String): IsoDate? {
    MONTH_NAME.find(body)?.let { m ->
        return notFuture(iso(m.groupValues[3].toInt(), MONTHS.indexOf(m.groupValues[1].lowercase()) + 1, m.groupValues[2].toInt()), receivedAt)
    }
    fullDate(EG_LONG_YMD, body, receivedAt, 1)?.let { m ->
        return notFuture(iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()), receivedAt)
    }
    fullDate(EG_LONG_DMY, body, receivedAt, 3)?.let { m ->
        return notFuture(iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()), receivedAt)
    }
    if (EG_LONG_YMD.containsMatchIn(body) || EG_LONG_DMY.containsMatchIn(body)) return null // هجري أو سنة بعيدة
    // رسالة البطاقة مفيهاش تاريخ: بتوصل ساعة العملية، فيوم الوصول هو يومها
    if (!hasDateToken(body)) return if (DONE_PHRASE.containsMatchIn(body)) cairoDayOf(receivedAt) else null
    val received = JsText.parseIsoMillis(receivedAt) ?: return null
    if (EG_SHORT.containsMatchIn(body)) return shortCandidates(EG_SHORT, body, received).singleOrNull()
    NBE_MONTH_DAY.find(body)?.let { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        // الجولة الخامسة: «يوم MM-DD» شهر-يوم في جمل الأهلي المصري بس — مرسل تاني «يوم 08-10» ممكن يبقى يوم-شهر: لو القرايتين
        // ممكنين (ومختلفين) ⇒ التاريخ مش واضح (كانت بتتسجل 10 أغسطس بدل 8 أكتوبر)
        if (isNbeSentence(body)) return withYear(a, b, receivedAt, received)
        val monthDay = withYear(a, b, receivedAt, received)
        val dayMonth = withYear(b, a, receivedAt, received)
        return if (monthDay != null && dayMonth != null && monthDay != dayMonth) null else monthDay ?: dayMonth
    }
    DAY_MONTH.find(body)?.let { m ->
        val day = (m.groups[1] ?: m.groups[3])!!.value.toInt()
        val month = (m.groups[2] ?: m.groups[4])!!.value.toInt()
        return withYear(month, day, receivedAt, received)
    }
    return null
}
