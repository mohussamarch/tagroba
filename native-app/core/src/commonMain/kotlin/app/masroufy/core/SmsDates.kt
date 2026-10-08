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

/** نفس قاعدة التطبيق الحالي (ملف المرجع `golden/sms.json`) + فاصل «\». */
internal fun saudiTransactionDate(body: String, receivedAt: String): IsoDate? {
    LONG_YMD.find(body)?.let { m ->
        val v = iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        return if (isValidIsoDate(v)) v else null
    }
    LONG_DMY.find(body)?.let { m ->
        val v = iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        return if (isValidIsoDate(v)) v else null
    }
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

/** «29/07» = يوم/شهر من غير سنة (QNB · بيت التمويل الكويتي). */
private val DAY_MONTH = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d|/)")

/** أي شكل تاريخ في الرسالة — لو مفيش خالص، تاريخ الوصول هو تاريخ العملية (§40.3-١). */
private val DATE_TOKEN = Regex("(?<![\\d.,])\\d{1,4}[-/\\\\]\\d{1,2}(?![\\d,])|(?<![\\d.,])\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?![\\d.,])")

internal fun hasDateToken(body: String): Boolean = DATE_TOKEN.containsMatchIn(body) || MONTH_NAME.containsMatchIn(body)

/** يوم/شهر من غير سنة: سنة الوصول أو اللي قبلها، والمقبول واحد بس. */
private fun withYear(month: Int, day: Int, receivedAt: String, received: Long): IsoDate? {
    val year = receivedAt.take(4).toIntOrNull() ?: return null
    val candidates = listOf(year, year - 1).map { iso(it, month, day) }.filter { isValidIsoDate(it) && inWindow(it, received) }
    return candidates.singleOrNull()
}

/**
 * مصر: الشهر بالاسم ⇒ سنة كاملة ⇒ سنتين أرقام ⇒ «يوم MM-DD» ⇒ يوم/شهر ⇒ ولا تاريخ خالص = يوم الوصول.
 * ⚠️ يوم الوصول = أول 10 حروف من `receivedAt` زي ما هو (نفس القارئ القديم) — طبقة أندرويد لازم تبعت التوقيت المحلي.
 */
internal fun egyptTransactionDate(body: String, receivedAt: String): IsoDate? {
    MONTH_NAME.find(body)?.let { m ->
        val v = iso(m.groupValues[3].toInt(), MONTHS.indexOf(m.groupValues[1].lowercase()) + 1, m.groupValues[2].toInt())
        return v.takeIf(::isValidIsoDate)
    }
    EG_LONG_YMD.find(body)?.let { m -> return iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf(::isValidIsoDate) }
    EG_LONG_DMY.find(body)?.let { m -> return iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()).takeIf(::isValidIsoDate) }
    if (!hasDateToken(body)) {
        // رسالة البطاقة مفيهاش تاريخ: بتوصل ساعة العملية، فتاريخ الوصول هو تاريخها
        val day = receivedAt.take(10)
        return if (isValidIsoDate(day)) day else null
    }
    val received = JsText.parseIsoMillis(receivedAt) ?: return null
    if (EG_SHORT.containsMatchIn(body)) return shortCandidates(EG_SHORT, body, received).singleOrNull()
    NBE_MONTH_DAY.find(body)?.let { m -> return withYear(m.groupValues[1].toInt(), m.groupValues[2].toInt(), receivedAt, received) }
    DAY_MONTH.find(body)?.let { m -> return withYear(m.groupValues[2].toInt(), m.groupValues[1].toInt(), receivedAt, received) }
    return null
}
