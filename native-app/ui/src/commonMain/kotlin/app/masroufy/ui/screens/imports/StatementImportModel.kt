package app.masroufy.ui.screens.imports

import app.masroufy.core.IsoDate
import app.masroufy.core.SchemaId
import app.masroufy.usecase.PdfStatementResult

/** نوع الملف من أول بايتاته (مش من الاسم بس): PDF بيبدأ بـ`%PDF`، والباقي لو نص ⇒ CSV. */
enum class StatementKind { PDF, CSV }

/** null = مش PDF ولا نص (صورة · ملف تالف). */
fun statementKind(name: String, bytes: ByteArray): StatementKind? {
    if (bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
        return StatementKind.PDF
    }
    if (name.lowercase().endsWith(".pdf")) return null
    val head = bytes.copyOfRange(0, minOf(bytes.size, 4096))
    return if (head.any { it == 0.toByte() }) null else StatementKind.CSV
}

/** كشف اتقرا وجاهز للمراجعة: الملف · نوعه · الصفحات أو السطور · عدد العمليات والفترة (أول وآخر تاريخ — ترتيب تواريخ، مش حساب فلوس). */
data class StatementReadyUi(
    val fileName: String,
    val kind: StatementKind,
    /** القارئ (كشف الراجحي · QNB مصر) — null للـCSV. */
    val schema: SchemaId?,
    val pages: Int?,
    val lines: Int?,
    /** عدد العمليات المقروءة (PDF) — الـCSV بيتعد في المراجعة. */
    val operations: Int?,
    val from: IsoDate?,
    val to: IsoDate?,
)

fun pdfReadyUi(fileName: String, result: PdfStatementResult): StatementReadyUi {
    val dates = result.rows.map { it.date }
    return StatementReadyUi(fileName, StatementKind.PDF, result.schema, result.pagesRead, null, result.rows.size, dates.minOrNull(), dates.maxOrNull())
}

fun csvReadyUi(fileName: String, table: CsvTable?): StatementReadyUi =
    StatementReadyUi(fileName, StatementKind.CSV, null, null, table?.lines, null, null, null)

/** الكشوف اللي البلد دي بتعرف تقراها (من حزمتها) — للقايمة «الملفات المدعومة». */
fun pdfSchemas(schemas: List<SchemaId>): List<SchemaId> = schemas.filter { it == SchemaId.ALRAJHI_PDF || it == SchemaId.QNB_PDF }
