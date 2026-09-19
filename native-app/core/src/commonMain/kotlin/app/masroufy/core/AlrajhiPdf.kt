package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * كشف الراجحي PDF — نقل `pdf/alrajhiPdfSchema.ts` + `arabicText.ts` + `merchantName.ts`.
 * بياخد **كلمات بإحداثياتها** مش ملف: قراية الملف نفسها خاصة بكل جهاز (PdfBox على أندرويد، PDFKit على الآيفون)،
 * والتحليل هنا واحد. ⚠️ لازم يتقارن على الكشف الحقيقي على الجهاز بس (ممنوع يترفع — ARCHITECTURE §9.5).
 */
data class PositionedWord(
    /** الحافة الشمال بالنقاط. */
    val x: Double,
    /** الارتفاع من تحت الصفحة. */
    val y: Double,
    val text: String,
)
data class PdfPage(val pageNumber: Int, val words: List<PositionedWord>)
data class AlrajhiPdfOutcome(val rows: List<ParsedRow>, val errors: List<RowError>, val pagesRead: Int)

private val MIRRORED = mapOf('(' to ')', ')' to '(', '[' to ']', ']' to '[', '{' to '}', '}' to '{')

fun hasArabic(text: String): Boolean = text.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF }

/** أشكال العرض ⇒ حروف عادية (`ﺍﻟﺮﺻﻴﺪ` ⇒ `الرصيد`). */
fun normalizeArabic(text: String): String = nfkc(text)

/** السطر اللي فيه عربي بيتقرا من اليمين (x الأكبر الأول)، واللاتيني الخالص من الشمال. */
fun joinLine(words: List<PositionedWord>): String {
    if (words.isEmpty()) return ""
    val normalized = words.map { it.copy(text = normalizeArabic(it.text)) }
    val rtl = normalized.any { hasArabic(it.text) }
    val ordered = if (rtl) normalized.sortedByDescending { it.x } else normalized.sortedBy { it.x }
    val joined = ordered.joinToString(" ") { w -> if (hasArabic(w.text)) w.text.map { MIRRORED[it] ?: it }.joinToString("") else w.text }
    return JsText.trim(JsText.collapseWhitespace(joined))
}

/** تنضيف للعرض وقص عند حد معلن — النص الكامل بيتحفظ من غير قص. */
fun tidy(text: String, maxLength: Int = 160): String {
    val clean = JsText.trim(JsText.collapseWhitespace(normalizeArabic(text)))
    return if (clean.length > maxLength) clean.take(maxLength - 1) + "…" else clean
}

/* ───────────────────────── اسم التاجر (أنماط استدلالية — الفشل null مش تخمين) ───────────────────────── */

private const val NAME = "[A-Za-z0-9&.'\\- ]"
private val ONLINE = Regex("Online Purchase from ($NAME+)")
private val AGREEMENT = Regex("payment_agreement_\\w+,$S*($NAME{2,45}?)$S*,")
private val AGENT = Regex("${B}ag_[0-9a-f]{6,},$S*($NAME{2,45}?)$S*,")
private val LOCAL = Regex("($NAME{3,}?),$S*([A-Za-z\\- ]+),$S*SA$B")
private val INTERNATIONAL = Regex("([A-Za-z0-9*.'&\\- ]{3,45}?)$S*:$S*\\d{4}\\*+\\d+")
private val BANK_PREFIX = Regex("^(TYB|Agmt\\)?|ARBS\\w*|SABS\\w*|PG\\d+|\\d{6,})[${S.substring(1, S.length - 1)}-]*", RegexOption.IGNORE_CASE)
private val BRANCH_CODE = Regex("$S*\\d{3}-\\d+$S*")
private val COUNTRY_SUFFIX = Regex("$S+(US|IE|GB|AE|NL|LU|CZ|SA|TR|EG)$", RegexOption.IGNORE_CASE)
private val TRAILING = Regex("[${S.substring(1, S.length - 1)},.\\-]+$")

private fun cleanName(name: String): String? {
    var out = JsText.trim(JsText.collapseWhitespace(name))
    out = out.replaceFirst(BANK_PREFIX, "")
    out = out.replace(BRANCH_CODE, " ")
    out = out.replaceFirst(COUNTRY_SUFFIX, "")
    out = JsText.trim(out.replaceFirst(TRAILING, ""))
    if (out.length < 3) return null
    if (out.none { it in 'A'..'Z' || it in 'a'..'z' }) return null
    return out.uppercase().take(40)
}

/** الأنماط الأدق الأول. */
fun extractMerchantName(details: String): String? {
    for (pattern in listOf(ONLINE, AGREEMENT, AGENT, LOCAL, INTERNATIONAL)) {
        val match = pattern.find(details) ?: continue
        cleanName(match.groupValues[1])?.let { return it }
    }
    return null
}

/* ───────────────────────── الكشف ───────────────────────── */

/** حدود الأعمدة بالنقاط (A4 عرضها 595، من اليمين للشمال) — متقاسة على كشف حقيقي (ARCHITECTURE §19.4). */
object AlrajhiColumns {
    const val BALANCE_MAX = 110.0
    const val CREDIT_MAX = 200.0
    const val DEBIT_MAX = 310.0
    const val DETAILS_MIN = 305.0
    const val DETAILS_MAX = 500.0
    const val DATE_MIN = 450.0
}

private val AMOUNT = Regex("([\\d,]+\\.\\d{2})$S*SAR")
private val DATE = Regex("(\\d{4})/(\\d{2})/(\\d{2})")
private val LONG_NUMBER = Regex("\\d{8,}")
private val NOT_A_NAME = Regex("الوقت|ملاحظة|Agmt|FRACCT|TOACCT", RegexOption.IGNORE_CASE)

private data class Line(val y: Double, val words: List<PositionedWord>)
private data class AmountLine(val balanceMinor: Long, val creditMinor: Long, val debitMinor: Long, val date: IsoDate)

private fun toLines(words: List<PositionedWord>): List<Line> {
    val buckets = LinkedHashMap<Double, MutableList<PositionedWord>>()
    for (w in words) {
        if (JsText.trim(w.text).isEmpty()) continue
        buckets.getOrPut(JsText.round(w.y)) { mutableListOf() }.add(w)
    }
    return buckets.entries.sortedByDescending { it.key }.map { Line(it.key, it.value) }
}

/** سطر عملية = التلات مبالغ والتاريخ مع بعض — الشرط الصارم بيمنع ترويسة أو مجموع يتقري كعملية. */
private fun readAmountLine(line: Line): AmountLine? {
    var balance: Long? = null
    var credit: Long? = null
    var debit: Long? = null
    var date: IsoDate? = null
    for (w in line.words) {
        val text = latinizeDigits(JsText.trim(w.text))
        val amount = AMOUNT.matchEntire(text)
        if (amount != null) {
            val minor = tryParseMoney(amount.groupValues[1]) ?: continue
            when {
                w.x < AlrajhiColumns.BALANCE_MAX -> balance = minor
                w.x < AlrajhiColumns.CREDIT_MAX -> credit = minor
                w.x < AlrajhiColumns.DEBIT_MAX -> debit = minor
            }
            continue
        }
        val parts = DATE.matchEntire(text)
        if (parts != null && w.x >= AlrajhiColumns.DATE_MIN) {
            val iso = "${parts.groupValues[1]}-${parts.groupValues[2]}-${parts.groupValues[3]}"
            if (isValidIsoDate(iso)) date = iso
        }
    }
    if (balance == null || credit == null || debit == null || date == null) return null
    return AmountLine(balance, credit, debit, date)
}

/** نص عمود التفاصيل — التاريخ بيتشال بالنمط مش بالإحداثي (العمودين لازقين). */
private fun detailsOf(line: Line): String = joinLine(
    line.words.filter { it.x >= AlrajhiColumns.DETAILS_MIN && it.x <= AlrajhiColumns.DETAILS_MAX && !DATE.matches(latinizeDigits(JsText.trim(it.text))) },
)

private fun usableAsName(text: String) = text.length >= 3 && !LONG_NUMBER.containsMatchIn(text) && !NOT_A_NAME.containsMatchIn(text)

/** التاجر الأول، وإلا نوع العملية لو صالح، وإلا فاضي — الاسم المخترع مش مقبول. */
private fun merchantNameFor(description: String, typeLine: String): String {
    extractMerchantName(description)?.let { return it }
    val fallback = tidy(typeLine, 60)
    return if (usableAsName(fallback)) fallback else ""
}

/** العملية = سطر فيه المبالغ والتاريخ؛ نوعها في السطر اللي **فوقه**، وتفاصيلها **تحته** لحد العملية الجاية. */
fun parseAlrajhiPdf(pages: List<PdfPage>): AlrajhiPdfOutcome {
    val rows = mutableListOf<ParsedRow>()
    val errors = mutableListOf<RowError>()
    var lineNumber = 0
    for (page in pages) {
        val lines = toLines(page.words)
        val hits = lines.mapIndexedNotNull { index, line -> readAmountLine(line)?.let { index to it } }
        hits.forEachIndexed { order, (index, amounts) ->
            lineNumber += 1
            val raw = "صفحة ${page.pageNumber} · ${amounts.date}"
            val isDebit = amounts.debitMinor > 0
            val isCredit = amounts.creditMinor > 0
            if (isDebit == isCredit) {
                errors += RowError(lineNumber, "المبلغ", if (isDebit) "العملية فيها مدين ودائن مع بعض — مش واضح اتجاهها" else "العملية مبلغها صفر في المدين والدائن", raw)
                return@forEachIndexed
            }
            val typeLine = if (index > 0) detailsOf(lines[index - 1]) else ""
            val stop = if (order + 1 < hits.size) hits[order + 1].first - 1 else lines.size
            val description = (index until stop).map { detailsOf(lines[it]) }.filter { it.isNotEmpty() }.joinToString(" | ")
            rows += ParsedRow(
                lineNumber = lineNumber, date = amounts.date,
                amountMinor = if (isDebit) amounts.debitMinor else amounts.creditMinor,
                direction = if (isDebit) Direction.OUT else Direction.IN,
                merchantName = merchantNameFor(description, typeLine), reference = null, sourceName = "كشف الراجحي",
                description = tidy(description, 400), raw = raw,
                // نوع العملية دليل مش حكم — ممكن ينزلق لسطر جنبه
                sourceOperationType = tidy(typeLine, 80).ifEmpty { null },
                statedBalanceMinor = amounts.balanceMinor,
            )
        }
    }
    return AlrajhiPdfOutcome(rows, errors, pages.size)
}
