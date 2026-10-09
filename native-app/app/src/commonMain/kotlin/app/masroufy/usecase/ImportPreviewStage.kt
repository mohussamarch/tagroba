package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.DedupeCandidate
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.ParseOutcome
import app.masroufy.core.ParsedRow
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
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
import app.masroufy.core.Direction
import app.masroufy.core.uiText

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

    // فهرس الموجود مسبقًا للحساب ده (`CrossSourcePreview.kt`) — سجل واحد لكل عملية
    val window = deps.crossSourceWindowDays
    val ledger = loadLedger(deps, request.accountIdentity)
    val index = buildDedupeIndex(existingRecordsOf(ledger, preferStatement = window != null))
    val catDeps = buildCategorizeDeps(deps)

    val scanned = mutableListOf<ImportPreviewLine>()
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
                    reason = uiText(TextKey.IMPORT_SAME_REFERENCE_IN_FILE, "$ref", "$priorLine"),
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

        scanned += ImportPreviewLine(
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
    // §75-10 (S4): الكشف والرسالة نفس الحركة ⇒ دمج. null = من غير دمج (ملفات المرجع بالحرف)
    val lines = if (window != null) applyCrossSource(deps, request, scanned, ledger, window) else scanned

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
    // سطر الدمج ما بيضيفش فلوس: العملية موجودة ومحسوبة خلاص
    for (line in lines.filter { it.selectedByDefault && it.mergeInto == null }) {
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
