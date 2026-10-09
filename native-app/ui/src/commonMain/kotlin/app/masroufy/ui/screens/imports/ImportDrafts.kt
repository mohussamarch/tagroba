package app.masroufy.ui.screens.imports

import app.masroufy.core.ImportSourceType
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.toParsedRow
import app.masroufy.usecase.ImportRequest
import app.masroufy.usecase.PdfStatementResult
import app.masroufy.usecase.SmsBatch

/**
 * ملف اتقرا ومستني الخطوة الجاية — بيتنقل بين «كشف الحساب» و«تحديد الأعمدة» و«مراجعة الكشف» **في الذاكرة بس** (المسار بياخد الرقم).
 * التطبيق اتقفل في النص ⇒ المراجعة بتقول «اختر الملف من جديد» (مفيش حاجة اتسجلت قبل التأكيد).
 */
data class ImportDraft(
    val fileName: String,
    /** طلب الاستيراد الجاهز للمعاينة (محفظته متحددة) — null لسه (CSV أعمدته مش معروفة). */
    val request: ImportRequest? = null,
    val walletName: String? = null,
    /** نص ملف CSV (لتحديد الأعمدة يدويًا). */
    val csv: String? = null,
)

object ImportDrafts {
    private const val KEEP = 4
    private val drafts = LinkedHashMap<Long, ImportDraft>()
    private var next = 1L

    fun put(draft: ImportDraft): Long {
        val id = next++
        drafts[id] = draft
        while (drafts.size > KEEP) drafts.remove(drafts.keys.first())
        return id
    }

    operator fun get(id: Long): ImportDraft? = drafts[id]
}

/**
 * طلب الاستيراد لرسايل ملصوقة أو مقروءة (`SmsPaste`) — **نفس شكل** `ReviewSmsInbox` بالظبط (هوية الحساب = اسم المحفظة · مخطط الرسايل ·
 * الصفوف جاهزة · عملة المحفظة) عشان منع التكرار بين الرسايل والكشف يشتغل على نفس النطاق.
 */
fun smsRequest(batch: SmsBatch, wallet: Wallet) = ImportRequest(
    fileName = "bank-sms.json",
    content = batch.content,
    accountIdentity = wallet.name,
    sourceType = ImportSourceType.SMS,
    walletId = wallet.id,
    schema = SchemaId.SMS,
    parsedRows = batch.rows.map { it.toParsedRow() },
    currency = wallet.currency,
)

/** طلب كشف PDF (`ReadPdfStatement` اتقرا بقارئ البنك ده): الصفوف جاهزة والمحتوى القانوني للبصمة (زي شاشة الاستيراد في التطبيق الحالي). */
fun pdfRequest(fileName: String, result: PdfStatementResult, wallet: Wallet) = ImportRequest(
    fileName = fileName,
    content = result.content,
    accountIdentity = wallet.name,
    sourceType = result.sourceType,
    walletId = wallet.id,
    schema = result.schema,
    parsedRows = result.rows,
    currency = wallet.currency,
)

/** طلب ملف CSV — [schema] = null أول مرة (المعاينة بتتعرف عليه)، وبعدها اللي اتعرف عليه (المصدر بيتبعه). */
fun csvRequest(fileName: String, content: String, wallet: Wallet, schema: SchemaId? = null) = ImportRequest(
    fileName = fileName,
    content = content,
    accountIdentity = wallet.name,
    sourceType = if (schema == SchemaId.LEGACY) ImportSourceType.CSV_LEGACY else ImportSourceType.CSV_PREVIEW,
    walletId = wallet.id,
    schema = schema,
    currency = wallet.currency,
)
