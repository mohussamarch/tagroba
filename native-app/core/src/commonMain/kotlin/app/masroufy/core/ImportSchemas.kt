package app.masroufy.core

/**
 * مخططات الاستيراد — نقل `src/infrastructure/import/schemas.ts` (spec/05).
 * كل مخطط له كاشف ومحوّل، والناتج شكل واحد (`ParsedRow`). الصف الفاسد بيروح لقايمة الأخطاء برقمه ونصه،
 * ومش بيوقّف باقي الملف. **مفيش تخمين لأعمدة مالية.**
 */
enum class SchemaId(val wire: String) {
    PREVIEW("preview"), LEGACY("legacy"), ALRAJHI_PDF("alrajhi_pdf"), SMS("sms");

    companion object {
        fun fromWire(wire: String): SchemaId = entries.first { it.wire == wire }
    }
}

data class ParsedRow(
    val lineNumber: Int,
    val date: IsoDate,
    /** موجب دايمًا؛ الاتجاه منفصل. */
    val amountMinor: Halalas,
    val direction: Direction,
    val merchantName: String,
    val reference: String?,
    val sourceName: String,
    val description: String,
    val raw: String,
    /** تصنيف الملف — دليل مش حكم. */
    val sourceCategory: String? = null,
    val sourceOperationType: String? = null,
    val statedBalanceMinor: Halalas? = null,
)

data class RowError(val lineNumber: Int, val field: String, val message: String, val raw: String)

data class ParseOutcome(val schema: SchemaId, val rows: List<ParsedRow>, val errors: List<RowError>)

class SchemaError(message: String) : IllegalArgumentException(message)

private val PREVIEW_HEADER = listOf("date", "name", "amount", "type", "source", "reference")
private val LEGACY_HEADER = listOf("التاريخ", "مدين", "دائن", "الرصيد", "التاجر", "التصنيف", "نوع العملية", "التفاصيل")

private fun headerMatches(header: List<String>, expected: List<String>): Boolean =
    header.size >= expected.size && expected.withIndex().all { (i, name) -> JsText.trim(header[i]).lowercase() == name.lowercase() }

fun detectSchema(doc: CsvDocument): SchemaId {
    if (headerMatches(doc.header, PREVIEW_HEADER)) return SchemaId.PREVIEW
    if (headerMatches(doc.header, LEGACY_HEADER)) return SchemaId.LEGACY
    throw SchemaError(
        uiText(TextKey.SCHEMA_UNKNOWN_COLUMNS, doc.header.joinToString(" , "), PREVIEW_HEADER.joinToString(" , "), LEGACY_HEADER.joinToString(" , ")),
    )
}

private val LEGACY_DATE = Regex("^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})$")

/** YYYY/MM/DD (المخطط القديم) ⇒ ISO، والمستحيل بيترفض. */
fun legacyDateToIso(value: String): IsoDate? {
    val m = LEGACY_DATE.find(JsText.trim(latinizeDigits(value))) ?: return null
    val iso = formatIsoDate(DateParts(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
    return if (isValidIsoDate(iso)) iso else null
}

/** زي `${x}` في جافاسكربت لما الخانة ناقصة: بتطلع «undefined». */
private fun shown(value: String?) = value ?: "undefined"

private class RowFail(val error: RowError) : Exception()

private fun parsePreviewRow(row: CsvRow): ParsedRow {
    val c = row.cells
    fun fail(field: String, message: String): Nothing = throw RowFail(RowError(row.lineNumber, field, message, row.raw))
    val dateRaw = c.getOrNull(0)
    val date = JsText.trim(dateRaw ?: "")
    if (!isValidIsoDate(date)) fail("date", uiText(TextKey.ROW_DATE_INVALID, shown(dateRaw)))
    val amountRaw = c.getOrNull(2)
    val amountMinor = tryParseMoney(amountRaw ?: "") ?: fail("amount", uiText(TextKey.ROW_AMOUNT_INVALID, shown(amountRaw)))
    if (amountMinor < 0) fail("amount", uiText(TextKey.ROW_AMOUNT_NEGATIVE, shown(amountRaw)))
    if (amountMinor == 0L) fail("amount", uiText(TextKey.ROW_AMOUNT_ZERO))
    val typeRaw = c.getOrNull(3)
    val type = JsText.trim(typeRaw ?: "").lowercase()
    if (type !in listOf("expense", "income", "transfer")) {
        fail("type", uiText(TextKey.ROW_TYPE_UNKNOWN, shown(typeRaw)))
    }
    // العمود ده بيحدد **اتجاه السيولة** بس — «transfer» مش معناه تحويل داخلي تلقائي
    val name = JsText.trim(c.getOrNull(1) ?: "")
    val reference = JsText.trim(c.getOrNull(5) ?: "")
    return ParsedRow(
        lineNumber = row.lineNumber, date = date, amountMinor = amountMinor,
        direction = if (type == "income") Direction.IN else Direction.OUT,
        merchantName = name, reference = reference.ifEmpty { null }, sourceName = JsText.trim(c.getOrNull(4) ?: ""),
        description = name, raw = row.raw,
    )
}

private fun parseLegacyRow(row: CsvRow): ParsedRow {
    val c = row.cells
    fun fail(field: String, message: String): Nothing = throw RowFail(RowError(row.lineNumber, field, message, row.raw))
    val dateRaw = c.getOrNull(0)
    val date = legacyDateToIso(dateRaw ?: "") ?: fail("التاريخ", uiText(TextKey.ROW_LEGACY_DATE_INVALID, shown(dateRaw)))
    val debitRaw = c.getOrNull(1)
    val creditRaw = c.getOrNull(2)
    val debit = tryParseMoney(debitRaw ?: "0")
    val credit = tryParseMoney(creditRaw ?: "0")
    if (debit == null) fail("مدين", uiText(TextKey.ROW_DEBIT_INVALID, shown(debitRaw)))
    if (credit == null) fail("دائن", uiText(TextKey.ROW_CREDIT_INVALID, shown(creditRaw)))
    if (debit < 0 || credit < 0) fail("المبلغ", uiText(TextKey.ROW_DEBIT_CREDIT_NEGATIVE))
    if (debit > 0 && credit > 0) fail("المبلغ", uiText(TextKey.ROW_DEBIT_AND_CREDIT))
    if (debit == 0L && credit == 0L) fail("المبلغ", uiText(TextKey.ROW_NO_DEBIT_NO_CREDIT))
    // الرصيد اختياري: غيابه ما بيمنعش الاستيراد، بس بيمنع مقارنة السطر ده
    val balanceRaw = c.getOrNull(3)
    val balance = if (balanceRaw == null || JsText.trim(balanceRaw).isEmpty()) null else tryParseMoney(balanceRaw)
    return ParsedRow(
        lineNumber = row.lineNumber, date = date,
        amountMinor = if (credit > 0) credit else debit,
        direction = if (credit > 0) Direction.IN else Direction.OUT,
        merchantName = JsText.trim(c.getOrNull(4) ?: ""),
        // المخطط القديم من غير عمود مرجع
        reference = null, sourceName = "",
        description = JsText.trim(c.getOrNull(7) ?: ""), raw = row.raw,
        sourceCategory = JsText.trim(c.getOrNull(5) ?: "").ifEmpty { null },
        sourceOperationType = JsText.trim(c.getOrNull(6) ?: "").ifEmpty { null },
        statedBalanceMinor = balance,
    )
}

/** مستند CSV ⇒ صفوف موحدة. أي مخطط غير المعاينة بيتقرا بمحوّل المخطط القديم (زي الحالي). */
fun parseRows(doc: CsvDocument, schema: SchemaId? = null): ParseOutcome {
    val detected = schema ?: detectSchema(doc)
    val rows = mutableListOf<ParsedRow>()
    val errors = mutableListOf<RowError>()
    for (row in doc.rows) {
        try {
            rows += if (detected == SchemaId.PREVIEW) parsePreviewRow(row) else parseLegacyRow(row)
        } catch (fail: RowFail) {
            errors += fail.error
        }
    }
    return ParseOutcome(detected, rows, errors)
}
