package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.hashContent
import app.masroufy.core.importFingerprint
import app.masroufy.core.uiText

/**
 * ImportStatement — نقل `importStatement.ts`: قراءة الكشف، منع التكرار، إنشاء الدفعة.
 * التدفق مرحلتين صراحةً (spec/04): `preview()` من غير كتابة، و`commit()` ذري جوه UnitOfWork.
 */
class ImportStatement(private val deps: ImportStatementDeps) {
    suspend fun preview(request: ImportRequest): ImportPreview = runPreview(deps, request)

    /** المرحلة التانية: الالتزام الذري. `selectedLineNumbers` بتسمح بضم متشابه أو استبعاد جديد **بقرار صريح**. */
    private suspend fun commitPrepared(
        request: ImportRequest,
        previewResult: ImportPreview,
        selectedLineNumbers: List<Int>?,
        chosenCategories: Map<Int, Id>?,
    ): ImportBatch {
        val selection = selectedLineNumbers?.toSet()
            ?: previewResult.lines.filter { it.selectedByDefault }.map { it.row.lineNumber }.toSet()

        // التعارض ما بيتكتبش أبدًا من غير حسم صريح — لا استبدال صامت (spec/05)
        for (line in previewResult.lines) {
            if (line.row.lineNumber in selection && line.state == MatchingState.CONFLICT) {
                throw IllegalStateException(uiText(TextKey.IMPORT_ROW_CONFLICT, "${line.row.lineNumber}", "${line.reason}"))
            }
        }

        var context: RecordContext? = null
        // §75-10 (S4): سطور الدمج ما بتعملش عملية، وسجلها بيتكتب **بعد** ما الدفعة تتقفل (`MergeUndo.kt`) — فدفعة معلّقة عمرها ما بتشاور
        // على عملية دفعة تانية (التنظيف بعد الانقطاع كان هيمسحها)
        val merges = previewResult.lines.filter { it.mergeInto != null && it.row.lineNumber in selection }
        val committed = deps.uow.run {
            val batchId = deps.ids.next("batch")
            val now = deps.clock.nowIso()
            val transactions = mutableListOf<Transaction>()
            val records = mutableListOf<SourceRecord>()
            val includedLines = mutableListOf<ImportPreviewLine>()

            for (line in previewResult.lines) {
                val included = line.row.lineNumber in selection
                if (included && line.mergeInto != null) continue
                val txnId = if (included) deps.ids.next("txn") else null
                if (txnId != null) {
                    transactions += importLineTransaction(txnId, line, now, request, chosenCategories?.get(line.row.lineNumber))
                    includedLines += line
                }

                records += SourceRecord(
                    id = deps.ids.next("src"),
                    batchId = batchId,
                    accountIdentity = request.accountIdentity,
                    sourceReference = line.row.reference,
                    sourceHash = hashContent(line.row.raw),
                    originalRowIndex = line.row.lineNumber,
                    rawLine = line.row.raw,
                    transactionId = txnId,
                    matchingState = line.state,
                    reason = line.reason,
                )
            }

            /*
             * بروتوكول علامة الالتزام — «صفر أو كامل الدفعة» (spec/06):
             *   ١. الدفعة بتتكتب `staged` قبل أي حاجة
             *   ٢. **سجلات المصدر الأول** وبعدها العمليات — السجل هو الفهرس الوحيد
             *      اللي بيربط العملية بدفعتها؛ لو العمليات اتكتبت الأول واتقطع
             *      الاتصال، بتبقى يتيمة ومفيش طريق لتنظيفها
             *   ٣. التحويل لـ`committed` بكتابة واحدة ذرّية بطبيعتها
             */
            var batch = ImportBatch(
                id = batchId,
                sourceType = request.sourceType,
                fileHash = previewResult.fileHash,
                fileName = request.fileName,
                importedAt = now,
                state = ImportBatchState.STAGED,
                counts = ImportCounts(
                    total = previewResult.counts.total,
                    imported = transactions.size,
                    duplicates = previewResult.counts.duplicates,
                    similar = previewResult.counts.similar,
                    conflicts = previewResult.counts.conflicts,
                    invalid = previewResult.counts.invalid,
                ),
            )

            // قرارات زون التحويلات ⇒ الدخل المعروف ⇒ آثار وقت التسجيل (`RecordLines.kt` — نفس الطريق اللي «صلّح رسايل البنك القديمة» بيمشيه)
            val prepared = prepareRecordedLines(deps, request, batchId, now, includedLines, transactions.toList(), chosenCategories)
            transactions.clear()
            transactions += prepared.lineTransactions + prepared.extra.map { it.first }
            // عقد C0: الزيادة بتتحفظ مع الدفعة
            if (prepared.context != null) {
                records += prepared.extra.map { it.second } + prepared.context.claimed
                context = prepared.context
                // مراجعة S2: عدد الدفعة = كل اللي اتكتب فيها (ومعاه زيادة الآثار زي «رسوم بنكية») — نفس اللي التراجع بيمسحه والسجل بيعرضه
                batch = batch.copy(counts = batch.counts.copy(imported = transactions.size))
            }

            deps.batches.save(batch) // ١
            deps.sources.saveMany(records) // ٢ — الفهرس الأول
            deps.txns.saveMany(transactions) //     وبعده العمليات
            deps.batches.updateState(batchId, ImportBatchState.COMMITTED) // ٣

            batch.copy(state = ImportBatchState.COMMITTED)
        }
        // الدمج فشل (العملية اتمسحت بعد المعاينة مثلًا) ⇒ الآثار بتشتغل برضه والخطأ بيطلع بعدها: الرسالة بتفضل في الصندوق والسطر بيرجع جديد
        val mergeFailure = if (merges.isEmpty()) null else captureFailure { recordMerges(deps, request, committed.id, committed.importedAt, merges) }
        context?.let { ctx -> for (effect in deps.effects) afterCommitIsolated(effect, ctx) }
        mergeFailure?.let { throw it }
        return committed
    }

    private fun firstByLineNumber(lines: List<ImportPreviewLine>): Map<Int, ImportPreviewLine> {
        val out = HashMap<Int, ImportPreviewLine>(lines.size * 2)
        for (line in lines) out.getOrPut(line.row.lineNumber) { line }
        return out
    }

    private var committing = false

    /**
     * `chosenCategories`: تصنيف اختاره المستخدم لسطر (رقم السطر ← التصنيف) — بيتحفظ مؤكد.
     * `mergeChoices` (§75-10 — مراجعة S4): رد المالك على «أكتر من احتمال» — رقم السطر ← العملية اللي «هي دي» من
     * [ImportPreviewLine.mergeCandidates] ⇒ السطر بيتدمج فيها (مش محتاج يبقى في `selected`). اختيار مش من الاحتمالات ⇒ خطأ.
     */
    suspend fun commit(
        request: ImportRequest,
        previous: ImportPreview,
        selected: List<Int>? = null,
        chosenCategories: Map<Int, Id>? = null,
        mergeChoices: Map<Int, Id> = emptyMap(),
    ): ImportBatch {
        if (committing) throw IllegalStateException(uiText(TextKey.IMPORT_COMMIT_RUNNING))
        committing = true
        try {
            if (importFingerprint(request.content, request.accountIdentity) != previous.fileHash || request.accountIdentity != previous.accountIdentity) {
                throw IllegalStateException(uiText(TextKey.IMPORT_DATA_CHANGED))
            }
            val fresh = preview(request)
            val selection = selected ?: previous.lines.filter { it.selectedByDefault }.map { it.row.lineNumber }
            /*
             * الملف اتستورد قبل كده؟ بنرجّع الدفعة القديمة **بس لو مفيش حاجة جديدة تتضاف**
             * (قرار المالك 2026-09-12: الملف اللي اتستورد منه جزء لازم يكمل — منع التكرار
             * بيشتغل صف بصف أصلًا).
             */
            // فهرس برقم السطر بدل `find` جوه الحلقة: كان 20,000 صف × 20,000 = 6 ثواني حفظ (ScaleTest). أول سطر بالرقم يكسب — زي `find`
            val freshByNumber = firstByLineNumber(fresh.lines)
            val previousByNumber = firstByLineNumber(previous.lines)
            val addable = selection.filter { number ->
                val line = freshByNumber[number]
                line != null && line.state != MatchingState.DUPLICATE && line.state != MatchingState.INVALID
            }
            if (fresh.previousBatch != null && addable.isEmpty() && mergeChoices.isEmpty()) return fresh.previousBatch
            for (number in selection) {
                val before = previousByNumber[number]
                val now = freshByNumber[number]
                if (before == null || now == null || before.row != now.row || now.state != before.state || now.matchedTransactionId != before.matchedTransactionId ||
                    now.mergeInto != before.mergeInto
                ) {
                    throw IllegalStateException(uiText(TextKey.IMPORT_CHANGED_AFTER_PREVIEW))
                }
                if (now.state == MatchingState.DUPLICATE || now.state == MatchingState.INVALID) {
                    throw IllegalStateException(uiText(TextKey.IMPORT_LINE_NOT_ADDABLE))
                }
            }
            // «هي دي» (`MergeUndo.kt`): السطور اللي المالك اختار عمليتها بتتدمج فيها
            val lines = applyMergeChoices(request, previous, fresh, selection.toSet(), mergeChoices)
            return commitPrepared(request, fresh.copy(lines = lines), selection + mergeChoices.keys, chosenCategories)
        } finally {
            committing = false
        }
    }
}
