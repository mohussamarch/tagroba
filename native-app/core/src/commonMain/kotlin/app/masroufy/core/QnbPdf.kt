package app.masroufy.core

/**
 * كشف QNB مصر PDF (OVERRIDES §40.3، اتعمل 2026-10-01 على كشف المالك — على جهازه بس).
 * الأعمدة `Date | Description | Debit | Credit | Balance`: المدين بالسالب، والخانة الفاضية `-`، ومفيش فواصل آلاف.
 * كل عملية سطر فيه التاريخ والمبالغ، ووصفها **متقسم** على سطر فوقه وسطر تحته ⇒ كل سطر وصف بيروح للعملية الأقرب ليه.
 * أماكن الأعمدة بتتقرا من عناوينها في كل صفحة (`Debit` · `Credit` · `Balance`) مش أرقام ثابتة.
 * سطرين «BALANCE BROUGHT/CARRIED FORWARD» مش عمليات: الأول رصيد البداية والتاني رصيد النهاية.
 *
 * 🔒 **الوصف بيتقص هنا** (`redactSms`: أي 5 أرقام ورا بعض أو أكتر ⇒ `••••` + آخر 4) — قرار المالك: «متعتمدش ان الحسابات
 * هتجيلك مقفول عليها لازم انت اللي عمل كدة بنفسك». طبقة التخزين بتقص تاني (`storeForm`)، بس القارئ ما بيطلّعش رقم كامل أصلًا.
 * ⚠️ النص العربي جوه الكشف بيطلع `?` من قارئ الكلمات — الأسماء العربي بتضيع، واللاتيني بيفضل.
 */
data class QnbPdfOutcome(
    val rows: List<ParsedRow>,
    val errors: List<RowError>,
    val pagesRead: Int,
    /** رصيد «BALANCE BROUGHT FORWARD» — بداية سلسلة الرصيد. */
    val openingBalanceMinor: Halalas?,
    /** رصيد «BALANCE CARRIED FORWARD». */
    val closingBalanceMinor: Halalas?,
)

private val QNB_DATE = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
private val QNB_AMOUNT = Regex("-?\\d+(?:\\.\\d{1,2})?")
private val CARD_TIME = Regex("\\d{2}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}-")
private val CARD_TAIL = Regex("\\d{4}-\\d{4}-\\d{2}-\\d{2}.*$")
private val QNB_NOISE = Regex("^FawryPF\\*|>\\S*|\\b(CAIRO|ALEX\\.?|GIZA|EG|ONUS)\\b", RegexOption.IGNORE_CASE)
private const val FORWARD = "BALANCE "

private class QnbColumns(val descriptionMin: Double, val debitMin: Double, val creditMin: Double, val balanceMin: Double)

/** من عناوين الصفحة نفسها؛ لو مش لاقيها، القيم اللي اتقاست على الكشف الحقيقي. */
private fun columnsOf(words: List<PositionedWord>): QnbColumns {
    fun header(name: String) = words.firstOrNull { JsText.trim(it.text) == name }?.x
    return QnbColumns(
        descriptionMin = (header("Description") ?: 111.0) - 5,
        debitMin = (header("Debit") ?: 439.0) - 5,
        creditMin = header("Credit") ?: 553.0,
        balanceMin = header("Balance") ?: 667.0,
    )
}

private data class QnbRow(val y: Double, val date: IsoDate, val debit: String?, val credit: String?, val balance: String?, val details: MutableList<Pair<Double, String>>)

private fun readMoney(text: String?): Halalas? = text?.takeIf { it != "-" && QNB_AMOUNT.matches(it) }?.let { tryParseMoney(it, Currency.EGP) }

/** اسم التاجر من «CARD PURCHASE dd/mm/yy hh:mm-<الاسم> <المدينة> <كارت-تاريخ>» — الفشل فاضي مش تخمين. */
internal fun qnbMerchantName(details: String): String {
    val time = CARD_TIME.find(details) ?: return ""
    var name = details.substring(time.range.last + 1).replace(CARD_TAIL, "")
    // كود الفرع لازق في الاسم («FUEL12345») مش جزء من اسم التاجر
    name = JsText.trim(JsText.collapseWhitespace(name.replace(QNB_NOISE, " ").replace(Regex("\\d{3,}"), " ")))
    if (name.length < 3 || name.none { it in 'A'..'Z' || it in 'a'..'z' } || Regex("\\d{5,}").containsMatchIn(name)) return ""
    return name.uppercase().take(40)
}

fun parseQnbPdf(pages: List<PdfPage>): QnbPdfOutcome {
    val rows = mutableListOf<ParsedRow>()
    val errors = mutableListOf<RowError>()
    var opening: Halalas? = null
    var closing: Halalas? = null
    var lineNumber = 0
    for (page in pages) {
        val cols = columnsOf(page.words)
        val words = page.words.filter { JsText.trim(it.text).isNotEmpty() }
        // سطر العملية = فيه تاريخ في أول عمود
        val txRows = words.filter { it.x < cols.descriptionMin && QNB_DATE.matches(JsText.trim(it.text)) }
            .map { it.y }.distinct().sortedDescending()
            .map { y ->
                val line = words.filter { kotlin.math.abs(it.y - y) < 1.5 }
                val iso = JsText.trim(line.first { it.x < cols.descriptionMin && QNB_DATE.matches(JsText.trim(it.text)) }.text)
                fun col(min: Double, max: Double) = line.filter { it.x >= min && it.x < max }.joinToString("") { JsText.trim(it.text) }.ifEmpty { null }
                QnbRow(y, iso, col(cols.debitMin, cols.creditMin), col(cols.creditMin, cols.balanceMin), col(cols.balanceMin, Double.MAX_VALUE), mutableListOf())
            }
        if (txRows.isEmpty()) continue
        val rowYs = txRows.map { it.y }.toSet()
        // كل كلمة وصف (حتى اللي على نفس سطر العملية) ⇒ العملية الأقرب ليها رأسيًا، في حدود نص المسافة بين عمليتين
        for (w in words) {
            if (w.x < cols.descriptionMin || w.x >= cols.debitMin) continue
            val nearest = txRows.minBy { kotlin.math.abs(it.y - w.y) }
            if (kotlin.math.abs(nearest.y - w.y) > 12 && w.y !in rowYs) continue
            nearest.details += w.y to w.text
        }
        for (r in txRows) {
            // من فوق لتحت، ومن الشمال لليمين جوه السطر
            val details = r.details.sortedByDescending { it.first }.joinToString(" ") { it.second }.let { JsText.trim(JsText.collapseWhitespace(it)) }
            val balance = readMoney(r.balance)
            if (details.startsWith(FORWARD)) {
                if (details.contains("BROUGHT")) opening = opening ?: balance else closing = balance
                continue
            }
            lineNumber += 1
            // نص الدليل بيتخزن مع العملية، فبيفضل بلغة الكشف نفسه ومايتترجمش
            val raw = "صفحة ${page.pageNumber} · ${r.date}"
            val debit = readMoney(r.debit)?.let { kotlin.math.abs(it) }
            val credit = readMoney(r.credit)
            if (!isValidIsoDate(r.date)) {
                errors += RowError(lineNumber, "التاريخ", uiText(TextKey.ROW_DATE_INVALID, r.date), raw)
                continue
            }
            if ((debit != null && debit > 0) == (credit != null && credit > 0)) {
                errors += RowError(lineNumber, "المبلغ", if (debit != null && debit > 0) uiText(TextKey.PDF_DEBIT_AND_CREDIT) else uiText(TextKey.PDF_ZERO_AMOUNT), raw)
                continue
            }
            val isDebit = debit != null && debit > 0
            rows += ParsedRow(
                lineNumber = lineNumber, date = r.date,
                amountMinor = if (isDebit) debit!! else credit!!,
                direction = if (isDebit) Direction.OUT else Direction.IN,
                merchantName = qnbMerchantName(details),
                reference = null, sourceName = "كشف QNB",
                description = tidy(redactSms(details), 400), raw = raw,
                sourceOperationType = details.substringBefore('-').let { tidy(it, 80) }.ifEmpty { null },
                statedBalanceMinor = balance,
            )
        }
    }
    return QnbPdfOutcome(rows, errors, pages.size, opening, closing)
}
