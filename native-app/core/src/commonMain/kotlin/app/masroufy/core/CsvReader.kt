package app.masroufy.core

/**
 * قارئ CSV — نقل `src/infrastructure/import/csvReader.ts` (RFC 4180 + spec/05: BOM، فواصل جوه الاقتباس، CRLF).
 * نفس الحدود ونفس رسايل الأخطاء ونفس «النص الأصلي» لكل صف.
 */
data class CsvRow(
    /** رقم الصف في الملف زي ما المستخدم شايفه (الترويسة = 1). */
    val lineNumber: Int,
    val cells: List<String>,
    /** النص الأصلي للصف — بيتحفظ للمراجعة. */
    val raw: String,
)

data class CsvDocument(val header: List<String>, val rows: List<CsvRow>)

class CsvError(message: String) : IllegalArgumentException(message)

object CsvLimits {
    const val MAX_BYTES = 12 * 1024 * 1024
    const val MAX_ROWS = 100_000
    const val MAX_CELL_LENGTH = 20_000
}

fun stripBom(text: String): String = if (text.isNotEmpty() && text[0].code == 0xFEFF) text.substring(1) else text

fun parseCsv(input: String): CsvDocument {
    if (input.length > CsvLimits.MAX_BYTES) throw CsvError("الملف أكبر من الحد المسموح (12 ميجابايت)")
    val text = stripBom(input)
    val rows = mutableListOf<CsvRow>()
    var cells = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var lineNumber = 1
    var rawStart = 0

    fun pushRow(endIndex: Int) {
        cells.add(field.toString())
        // السطر الفاضي تمامًا بيتجاهل
        if (!(cells.size == 1 && cells[0].isEmpty())) {
            rows.add(CsvRow(lineNumber, cells, text.substring(rawStart, endIndex)))
            if (rows.size > CsvLimits.MAX_ROWS) throw CsvError("الملف فيه أكتر من ${CsvLimits.MAX_ROWS} صف")
        }
        cells = mutableListOf()
        field.clear()
        lineNumber++
        rawStart = endIndex + 1
    }

    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (inQuotes) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    field.append('"') // اقتباس مهروب جوه الخانة
                    i++
                } else {
                    inQuotes = false
                }
            } else {
                field.append(ch)
            }
            i++
            continue
        }
        when (ch) {
            '"' -> inQuotes = true
            ',' -> {
                cells.add(field.toString())
                field.clear()
            }
            '\r' -> if (!(i + 1 < text.length && text[i + 1] == '\n')) pushRow(i) // CRLF بيتعالج عند \n
            '\n' -> {
                val end = if (i > 0 && text[i - 1] == '\r') i - 1 else i
                pushRow(end)
                rawStart = i + 1
            }
            else -> {
                field.append(ch)
                if (field.length > CsvLimits.MAX_CELL_LENGTH) throw CsvError("خانة في السطر $lineNumber أطول من الحد المسموح")
            }
        }
        i++
    }

    if (inQuotes) throw CsvError("اقتباس مفتوح ولم يُغلق — الملف ناقص أو تالف عند السطر $lineNumber")
    if (field.isNotEmpty() || cells.isNotEmpty()) pushRow(text.length)
    if (rows.isEmpty()) throw CsvError("الملف فاضي")

    return CsvDocument(rows[0].cells.map { JsText.trim(it) }, rows.drop(1))
}
