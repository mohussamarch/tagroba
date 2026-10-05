package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.applyKnownPayerSalary
import app.masroufy.core.applyTransferVerdict
import app.masroufy.core.transferPartyOf
import app.masroufy.core.hashContent
import app.masroufy.core.importFingerprint
import app.masroufy.core.uiText

/**
 * ImportStatement — نقل `importStatement.ts`: قراءة الكشف، منع التكرار، إنشاء الدفعة.
 * التدفق مرحلتين صراحةً (spec/04): `preview()` من غير كتابة، و`commit()` ذري جوه UnitOfWork.
 */
class ImportStatement(private val deps: ImportStatementDeps) {
    suspend fun preview(request: ImportRequest): ImportPreview = runPreview(deps, request)

    private fun buildTransaction(id: Id, line: ImportPreviewLine, now: String, request: ImportRequest, chosenCategoryId: Id?): Transaction {
        // تصنيف اختاره المستخدم بنفسه وقت المراجعة = مؤكد، زي `categorizeTransactions.confirm`
        val categoryId = chosenCategoryId ?: line.categoryId
        return Transaction(
            id = id,
            occurredAt = line.row.date,
            datePrecision = "day",
            sourceOrder = line.row.lineNumber,
            // ⚠️ النوع الاقتصادي **ما بيستنتجش من اتجاه السيولة** (spec/02) — بيبدأ غير محدد
            economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false,
            observedDirection = line.row.direction,
            amountMinor = line.row.amountMinor,
            currency = request.currency,
            categoryConfirmed = chosenCategoryId != null,
            excludedFromBudget = false,
            reviewState = when {
                chosenCategoryId != null -> ReviewState.CONFIRMED
                line.categoryId != null -> ReviewState.SUGGESTED
                else -> ReviewState.NEEDS_REVIEW
            },
            isCashTagged = false,
            rawDescription = line.row.description,
            rawMerchantName = line.row.merchantName,
            createdAt = now,
            updatedAt = now,
            categoryId = categoryId,
            // الرصيد المعلن بيتحفظ زي ما ورد — هو مرجع المطابقة (OVERRIDES §7-ب)
            statedBalanceMinor = line.row.statedBalanceMinor,
            walletId = request.walletId,
            sourceCategory = line.row.sourceCategory,
            sourceOperationType = line.row.sourceOperationType,
        )
    }

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

        return deps.uow.run {
            val batchId = deps.ids.next("batch")
            val now = deps.clock.nowIso()
            val transactions = mutableListOf<Transaction>()
            val records = mutableListOf<SourceRecord>()

            for (line in previewResult.lines) {
                val included = line.row.lineNumber in selection
                val txnId = if (included) deps.ids.next("txn") else null
                if (txnId != null) transactions += buildTransaction(txnId, line, now, request, chosenCategories?.get(line.row.lineNumber))

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
            val batch = ImportBatch(
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

            val decided = deps.transferParties?.listAll()?.associateBy { it.key }.orEmpty()
            if (decided.isNotEmpty()) {
                for (i in transactions.indices) {
                    val party = transferPartyOf(transactions[i])?.let { decided[it.key] } ?: continue
                    transactions[i] = applyTransferVerdict(transactions[i], party, now)
                }
            }
            // بعد قرارات زون التحويلات («حسابي التاني» بيأكد النوع ⇒ ما بيتلمسش هنا)
            val payers = deps.incomeSources?.listAll().orEmpty().filter { it.payerKeys.isNotEmpty() }
            if (payers.isNotEmpty()) {
                for (i in transactions.indices) transactions[i] = applyKnownPayerSalary(transactions[i], payers, now)
            }

            deps.batches.save(batch) // ١
            deps.sources.saveMany(records) // ٢ — الفهرس الأول
            deps.txns.saveMany(transactions) //     وبعده العمليات
            deps.batches.updateState(batchId, ImportBatchState.COMMITTED) // ٣

            batch.copy(state = ImportBatchState.COMMITTED)
        }
    }

    private fun firstByLineNumber(lines: List<ImportPreviewLine>): Map<Int, ImportPreviewLine> {
        val out = HashMap<Int, ImportPreviewLine>(lines.size * 2)
        for (line in lines) out.getOrPut(line.row.lineNumber) { line }
        return out
    }

    private var committing = false

    /** `chosenCategories`: تصنيف اختاره المستخدم لسطر (رقم السطر ← التصنيف) — بيتحفظ مؤكد. */
    suspend fun commit(
        request: ImportRequest,
        previous: ImportPreview,
        selected: List<Int>? = null,
        chosenCategories: Map<Int, Id>? = null,
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
            if (fresh.previousBatch != null && addable.isEmpty()) return fresh.previousBatch
            for (number in selection) {
                val before = previousByNumber[number]
                val now = freshByNumber[number]
                if (before == null || now == null || before.row != now.row || now.state != before.state || now.matchedTransactionId != before.matchedTransactionId) {
                    throw IllegalStateException(uiText(TextKey.IMPORT_CHANGED_AFTER_PREVIEW))
                }
                if (now.state == MatchingState.DUPLICATE || now.state == MatchingState.INVALID) {
                    throw IllegalStateException(uiText(TextKey.IMPORT_LINE_NOT_ADDABLE))
                }
            }
            return commitPrepared(request, fresh, selection, chosenCategories)
        } finally {
            committing = false
        }
    }
}
