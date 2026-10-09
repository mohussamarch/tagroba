package app.masroufy.core

/**
 * الكيانات اللي بتتخزن ومش داخلة في أي حساب — نقل باقي `src/domain/entities/types.ts`
 * و`budgetEntities.ts`. الحسابات نفسها في `Entities.kt` و`Ledger.kt`.
 * `wire` = الاسم المتخزن في فايربيز — ما يتغيرش.
 */

data class Person(val id: Id, val name: String, val archived: Boolean = false)

data class Tag(val id: Id, val normalizedName: String, val displayName: String)

/** ربط وسم بعملية — جدول مستقل لأن العملية بتاخد أكتر من وسم (spec/02: الربط ما يضاعفش المجموع). */
data class TransactionTag(val id: Id, val transactionId: Id, val tagId: Id)

enum class ImportSourceType(val wire: String) {
    CSV_PREVIEW("csv_preview"), CSV_LEGACY("csv_legacy"), PDF_ALRAJHI("pdf_alrajhi"), SMS("sms"),
    /** كشف QNB مصر PDF — التطبيق الجديد بس. */
    PDF_QNB("pdf_qnb");

    companion object {
        fun fromWire(wire: String): ImportSourceType = entries.first { it.wire == wire }
    }
}

enum class ImportBatchState(val wire: String) {
    STAGED("staged"), COMMITTED("committed"), REVERTED("reverted");

    companion object {
        fun fromWire(wire: String): ImportBatchState = entries.first { it.wire == wire }
    }
}

data class ImportCounts(
    val total: Int,
    val imported: Int,
    val duplicates: Int,
    val similar: Int,
    val conflicts: Int,
    val invalid: Int,
)

data class ImportBatch(
    val id: Id,
    val sourceType: ImportSourceType,
    /** بصمة الملف — الدرجة الأولى من منع التكرار (spec/05). */
    val fileHash: String,
    val fileName: String,
    val importedAt: String,
    val state: ImportBatchState,
    val counts: ImportCounts,
)

data class SourceRecord(
    val id: Id,
    val batchId: Id,
    /** هوية الحساب أو المصدر — نطاق تفرّد المرجع البنكي. */
    val accountIdentity: String,
    val sourceReference: String?,
    val sourceHash: String,
    val originalRowIndex: Int,
    /** نص الصف الأصلي زي ما ورد. */
    val rawLine: String,
    val transactionId: Id?,
    val matchingState: MatchingState,
    /**
     * سبب الحالة بلغة المستخدم — مفيش حالة بلا تفسير (spec/04).
     * ⚠️ بيتخزن كجملة جاهزة، فلغته بتتثبت وقت الحفظ (OVERRIDES §40.1 — يتحول لمفتاح مع طبقة البيانات).
     */
    val reason: String,
    /**
     * §75-10 (الشريحة S4): سجل **دمج** (سطر كشف أو رسالة اتدمج في عملية موجودة من المصدر التاني — `matchingState` = مكرر و[transactionId] =
     * العملية الموجودة) و**سطر الكشف غيّر** تاريخها ورصيدها المعلن ⇒ القيم القديمة عشان التراجع عن الدفعة يرجّعها. null = مفيش حاجة اتغيرت.
     */
    val mergeUndo: MergeRestore? = null,
)

/**
 * القيم اللي سطر الكشف كتب فوقها في العملية اللي اتدمج فيها (§75-10) — التراجع عن دفعته بيرجّعها زي ما كانت.
 * [mergedOccurredAt] و[mergedStatedBalanceMinor] = اللي سطر الكشف **كتبه** (مراجعة S4): التراجع بيرجّع كل حقل **بس لو لسه** فيه اللي الكشف
 * كتبه — تعديل المالك بعد الدمج (التاريخ مثلًا) بيفضل. ترتيب السطر المكتوب = `SourceRecord.originalRowIndex`. [mergedStatedBalanceMinor] null =
 * السطر ما كانش فيه رصيد (الرصيد ما اتغيرش). [mergedOccurredAt] null = سجل أقدم من المراجعة ⇒ بيرجع زي الأول (من غير شرط).
 */
data class MergeRestore(
    val occurredAt: IsoDate,
    val sourceOrder: Int,
    val statedBalanceMinor: Halalas?,
    val mergedOccurredAt: IsoDate? = null,
    val mergedStatedBalanceMinor: Halalas? = null,
)

data class Budget(
    /** مفتاح الفترة نفسه: «2026-09». فترة واحدة = ميزانية واحدة. */
    val id: Id,
    val periodKey: String,
    val periodStart: IsoDate,
    val periodEnd: IsoDate,
    /** السقف الإجمالي، أو null لو المستخدم حدد سقوف تصنيفات بس. */
    val totalLimitMinor: Halalas?,
    /** عتبة التنبيه بالمئة (80 = ٨٠٪)، أو null فمفيش تنبيه (spec/06). */
    val thresholdPercent: Int?,
    val createdAt: String,
    val updatedAt: String,
)

data class CategoryBudget(
    val id: Id,
    val budgetId: Id,
    val categoryId: Id,
    val limitMinor: Halalas,
    val notifyEnabled: Boolean,
    val thresholdPercent: Int?,
)
