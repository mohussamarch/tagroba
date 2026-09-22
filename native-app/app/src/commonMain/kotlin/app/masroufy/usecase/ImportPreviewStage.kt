package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.DedupeCandidate
import app.masroufy.core.ExistingRecord
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.ParseOutcome
import app.masroufy.core.ParsedRow
import app.masroufy.core.SchemaId
import app.masroufy.core.addMoney
import app.masroufy.core.buildDedupeIndex
import app.masroufy.core.categorize
import app.masroufy.core.classifyCandidate
import app.masroufy.core.hashContent
import app.masroufy.core.importFingerprint
import app.masroufy.core.jsTrim
import app.masroufy.core.merchantIndex
import app.masroufy.core.normalizeText
import app.masroufy.core.parseCsv
import app.masroufy.core.parseRows
import app.masroufy.core.prepareRules
import app.masroufy.core.sourceAmountMinor
import app.masroufy.core.Direction

/**
 * مرحلة المعاينة من ImportStatement — نقل `importPreview.ts`.
 * استخراج ومراجعة **من غير أي كتابة**؛ آمن نداها كذا مرة.
 */

internal suspend fun buildCategorizeDeps(deps: ImportStatementDeps): CategorizeDeps {
    val merchants = deps.merchants.listAll()
    val categories = deps.categories.listAll()
    val rules = deps.rules.listAll()
    return CategorizeDeps(
        merchantsByNormalizedName = merchantIndex(merchants),
        categoryIdByName = categories.associate { normalizeText(it.name) to it.id },
        rules = prepareRules(rules),
    )
}

private fun toCandidate(row: ParsedRow, accountIdentity: String) = DedupeCandidate(
    accountIdentity = accountIdentity,
    sourceReference = row.reference,
    date = row.date,
    amountMinor = row.amountMinor,
    direction = row.direction,
    merchantName = row.merchantName,
    rowIndex = row.lineNumber,
    statedBalanceMinor = row.statedBalanceMinor,
)

/** بيبني فهرس الموجود مسبقًا للحساب ده. */
private suspend fun loadExisting(deps: ImportStatementDeps, accountIdentity: String): List<ExistingRecord> {
    val records = deps.sources.listByAccountIdentity(accountIdentity)
    val txnIds = records.mapNotNull { it.transactionId }
    val byId = deps.txns.findByIds(txnIds).associateBy { it.id }

    /*
     * **سجل واحد لكل عملية، مش لكل سجل مصدر.** العملية اللي اتسجلت من مصدرين
     * ليها أكتر من `sourceRecord`، وكانت بتتعد أكتر من مرة في فهرس التكرار
     * فتبتلع أكتر من صف وارد (اتشاف على بيانات المالك 2026-09-12).
     */
    val existing = mutableListOf<ExistingRecord>()
    val seenTransactions = mutableSetOf<String>()
    for (record in records) {
        val transactionId = record.transactionId ?: continue
        if (transactionId in seenTransactions) continue
        val txn = byId[transactionId] ?: continue
        seenTransactions.add(transactionId)
        existing += ExistingRecord(
            DedupeCandidate(
                accountIdentity = record.accountIdentity,
                sourceReference = record.sourceReference,
                date = txn.occurredAt,
                // المبلغ الأصلي من الكشف لو المستخدم عدّله (OVERRIDES §32) — فنفس السطر ما يتضافش تاني
                amountMinor = sourceAmountMinor(txn.amountMinor, txn.originalAmountMinor),
                // الاتجاه الملاحظ حقيقة بنكية محفوظة، مش بيستنتج من النوع الاقتصادي
                direction = txn.observedDirection,
                merchantName = txn.rawMerchantName ?: "",
                rowIndex = record.originalRowIndex,
                statedBalanceMinor = txn.statedBalanceMinor,
                smsSource = record.sourceReference?.startsWith("SMS:") == true,
            ),
            transactionId,
        )
    }
    return existing
}

internal suspend fun runPreview(deps: ImportStatementDeps, request: ImportRequest): ImportPreview {
    val fileHash = importFingerprint(request.content, request.accountIdentity)

    // ─── الدرجة ١: بصمة ملف اتستورد قبل كده ───
    var previousBatch = deps.batches.findByFileHash(fileHash)
    if (previousBatch == null) {
        val legacy = deps.batches.findByFileHash(hashContent(request.content))
        if (legacy != null) {
            val records = deps.sources.listByBatch(legacy.id)
            if (records.isNotEmpty() && records.all { it.accountIdentity == request.accountIdentity }) previousBatch = legacy
        }
    }

    // الصفوف الجاهزة (مسار الـPDF) بتتخطى قارئ الـCSV وبس — كل اللي بعد كده مسار واحد
    val outcome = request.parsedRows?.let { ParseOutcome(request.schema ?: SchemaId.ALRAJHI_PDF, it, emptyList()) }
        ?: parseRows(parseCsv(request.content), request.schema)

    val index = buildDedupeIndex(loadExisting(deps, request.accountIdentity))
    val catDeps = buildCategorizeDeps(deps)

    val lines = mutableListOf<ImportPreviewLine>()
    // الدفعة بتتفحص ضد نفسها كمان: نفس المرجع مرتين في نفس الملف
    val seenInBatch = mutableMapOf<String, Int>()
    // كل سجل موجود بيبلع صف وارد واحد بس (اتشاف على بيانات المالك 2026-09-12)
    val balanceUsed = mutableMapOf<String, Int>()

    for (row in outcome.rows) {
        val candidate = toCandidate(row, request.accountIdentity).copy(smsSource = request.sourceType == app.masroufy.core.ImportSourceType.SMS)
        var verdict = classifyCandidate(candidate, index, balanceUsed)
        verdict.matchedBalanceKey?.let { key -> balanceUsed[key] = (balanceUsed[key] ?: 0) + 1 }

        val ref = candidate.sourceReference?.let { jsTrim(it) }
        if (verdict.state == MatchingState.NEW && !ref.isNullOrEmpty()) {
            val key = "${request.accountIdentity}|$ref"
            val priorLine = seenInBatch[key]
            if (priorLine != null) {
                verdict = verdict.copy(
                    state = MatchingState.DUPLICATE,
                    reason = "نفس المرجع «$ref» اتكرر في الملف ده نفسه (صف $priorLine)",
                    matchedTransactionId = null,
                    conflictFields = null,
                    matchedBalanceKey = null,
                )
            } else {
                seenInBatch[key] = row.lineNumber
            }
        }

        val categorization = categorize(
            CategorizationInput(
                currentConfirmed = false,
                merchantName = row.merchantName,
                description = row.description,
                sourceCategory = row.sourceCategory,
            ),
            catDeps,
        )

        lines += ImportPreviewLine(
            row = row,
            state = verdict.state,
            reason = verdict.reason,
            matchedTransactionId = verdict.matchedTransactionId,
            categoryId = categorization.categoryId,
            categoryReason = categorization.reason,
            categorySource = categorization.source,
            // «أضف الجديد فقط» — fixtures/README. المتشابه والتعارض محتاجين قرار
            selectedByDefault = verdict.state == MatchingState.NEW,
        )
    }

    val counts = ImportCountsPreview(
        total = outcome.rows.size + outcome.errors.size,
        newCount = lines.count { it.state == MatchingState.NEW },
        duplicates = lines.count { it.state == MatchingState.DUPLICATE },
        similar = lines.count { it.state == MatchingState.SIMILAR },
        conflicts = lines.count { it.state == MatchingState.CONFLICT },
        invalid = outcome.errors.size,
    )

    var walletDelta = 0L
    var expense = 0L
    var income = 0L
    for (line in lines.filter { it.selectedByDefault }) {
        if (line.row.direction == Direction.IN) {
            walletDelta = addMoney(walletDelta, line.row.amountMinor)
            income = addMoney(income, line.row.amountMinor)
        } else {
            walletDelta = addMoney(walletDelta, -line.row.amountMinor)
            expense = addMoney(expense, line.row.amountMinor)
        }
    }

    return ImportPreview(
        fileName = request.fileName,
        fileHash = fileHash,
        schema = outcome.schema,
        accountIdentity = request.accountIdentity,
        previousBatch = previousBatch,
        lines = lines,
        errors = outcome.errors,
        counts = counts,
        impact = ImportImpact(walletDeltaMinor = walletDelta, expenseMinor = expense, incomeMinor = income),
    )
}
