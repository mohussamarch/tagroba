package app.masroufy.usecase

import app.masroufy.core.CategorizationSource
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ParsedRow
import app.masroufy.core.RowError
import app.masroufy.core.SchemaId
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/** أنواع ImportStatement المشتركة بين مرحلتي المعاينة والالتزام — نقل `importTypes.ts`. */

data class ImportPreviewLine(
    val row: ParsedRow,
    val state: MatchingState,
    /** سبب الحالة بلغة المستخدم — مفيش حالة بلا تفسير (spec/04). */
    val reason: String,
    val matchedTransactionId: String? = null,
    val categoryId: Id? = null,
    val categoryReason: String,
    /** مصدر التصنيف — «التاجر المؤكد» معناه إن المستخدم افتكره قبل كده (OVERRIDES §36). */
    val categorySource: CategorizationSource? = null,
    /** مختار للاستيراد افتراضيًا؟ الجديد أيوه، وما عداه محتاج قرار. */
    val selectedByDefault: Boolean,
)

data class ImportCountsPreview(
    val total: Int,
    val newCount: Int,
    val duplicates: Int,
    val similar: Int,
    val conflicts: Int,
    val invalid: Int,
)

/** مجموع أثر المحدد افتراضيًا على المحفظة والدخل والمصروف — spec/05. */
data class ImportImpact(
    val walletDeltaMinor: Halalas,
    val expenseMinor: Halalas,
    val incomeMinor: Halalas,
)

data class ImportPreview(
    val fileName: String,
    val fileHash: String,
    val schema: SchemaId,
    val accountIdentity: String,
    /** الدرجة ١: نفس الملف اتستورد قبل كده — الدفعة السابقة بتتعرض بدل الإضافة. */
    val previousBatch: ImportBatch?,
    val lines: List<ImportPreviewLine>,
    val errors: List<RowError>,
    val counts: ImportCountsPreview,
    val impact: ImportImpact,
)

data class ImportStatementDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    val merchants: MerchantRepository,
    val categories: CategoryRepository,
    val rules: RuleRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

data class ImportRequest(
    val fileName: String,
    val content: String,
    /** هوية الحساب/المصدر — نطاق تفرّد المرجع البنكي (spec/03). */
    val accountIdentity: String,
    val sourceType: ImportSourceType,
    /** المحفظة اللي الكشف ده بتاعها — بتربط العمليات بيها لمطابقة الرصيد. */
    val walletId: Id? = null,
    val schema: SchemaId? = null,
    /**
     * صفوف محلَّلة جاهزة — مسار الـPDF. لما تتبعت، التحليل بيتخطى وبقية الخط
     * (منع التكرار والتصنيف والحفظ على مرحلتين) بتشتغل زي ما هي بالظبط.
     */
    val parsedRows: List<ParsedRow>? = null,
)
