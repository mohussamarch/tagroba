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
    CSV_PREVIEW("csv_preview"), CSV_LEGACY("csv_legacy"), PDF_ALRAJHI("pdf_alrajhi"), SMS("sms");

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
