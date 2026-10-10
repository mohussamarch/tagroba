package app.masroufy.usecase

import app.masroufy.core.Currency
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
    /**
     * §75-10 (الشريحة S4): السطر ده **نفس الحركة** اللي اتسجلت من المصدر التاني (رسالة ⇄ كشف) ⇒ الالتزام ما بيعملش عملية جديدة، بيضيف
     * سجل مصدر تاني للعملية دي (`MergeUndo.kt`). null = سطر عادي. بيتملى بس لما `ImportStatementDeps.crossSourceWindowDays` متحدد.
     */
    val mergeInto: Id? = null,
    /**
     * §75-10 (مراجعة S4): السطر «شبه عملية» لأن ليه **أكتر من احتمال** من المصدر التاني (أو احتمال واحد سطر تاني بيتنافس عليه) ⇒ الاحتمالات
     * (الأقرب في التاريخ الأول). المالك يقدر يختار واحدة «هي دي» (`ImportStatement.commit(mergeChoices)`) فتتدمج بدل ما تتضاف مرة تانية.
     * فاضية = مفيش سؤال دمج على السطر.
     */
    val mergeCandidates: List<Id> = emptyList(),
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
    /**
     * قرارات «زون التحويلات» (§60): تحويل جديد لطرف اتقرر فيه بياخد القرار لوحده وهو بيتحفظ.
     * `null` = من غيرها (زي التطبيق الحالي — ملفات المرجع بتتعمل كده).
     */
    val transferParties: app.masroufy.port.TransferPartyRepository? = null,
    /**
     * مصادر الدخل (رد المالك §64): إيداع جديد من طرف اتأكد إنه بيحوّل المرتب بياخد «مرتب» مؤكد لوحده وهو بيتحفظ.
     * `null` = من غيرها (زي التطبيق الحالي).
     */
    val incomeSources: app.masroufy.port.IncomeSourceRepository? = null,
    /** عقد C0: آثار وقت التسجيل بالترتيب (`RecordEffects.kt`). فاضية = زي التطبيق الحالي (ملفات المرجع). */
    val effects: List<RecordEffect> = emptyList(),
    /** عقد C0 (§75-10 — الشريحة S4): الكشف والرسالة نفس العملية لو الفرق بالأيام دي أو أقل. null = من غير دمج (ملفات المرجع). */
    val crossSourceWindowDays: Int? = null,
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
    /**
     * عملة الكشف (عملة المحفظة). ⚠️ كانت مثبتة ريال جوه الاستيراد ⇒ كشف QNB مصر كان هيتسجل بالريال من غير رسالة
     * (اتكشف 2026-10-01). الافتراضي ريال عشان الراجحي والتطبيق الحالي ما يتغيروش.
     */
    val currency: Currency = Currency.SAR,
    /** عقد C0: صف رسالة البنك لكل رقم سطر (رسايل البنك بس) — الآثار اللي للرسايل بس بتبص عليه. فاضي = كشف. */
    val smsRows: Map<Int, app.masroufy.core.SmsRow> = emptyMap(),
    /** عقد C0: المالك هو اللي سجّل (الشاشة · التأكيد)؛ false = التسجيل التلقائي في الخلفية (§72). */
    val byOwner: Boolean = true,
)
