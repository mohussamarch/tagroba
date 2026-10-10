package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.DedupeCandidate
import app.masroufy.core.DedupeVerdict
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

/**
 * مراجعة S1 — §72 «الرسالة بتتسجل مرة واحدة مهما حصل»: منع التكرار مقيد بهوية الحساب، و§75-11 بيوزّع رسايل البنك على المحافظ بآخر 4 أرقام —
 * فرسالة اتسجلت في محفظة والجهاز وقع قبل ما تتشال من الصندوق، والمحاولة الجاية راحت محفظة تانية (المالك ربط البنك بمحفظة تانية · كتب أرقام
 * حساب) كانت بتتسجل **تاني**. مرجع الرسالة (`SMS:<بصمة>` = المرسل + وقت الوصول + النص) **فريد في البلد كلها** ⇒ لو اتسجل في أي محفظة تانية
 * (وعمليته لسه موجودة) = مكررة. رسايل البنك بس — الكشف زي ما هو.
 */
private suspend fun smsRecordedElsewhere(deps: ImportStatementDeps, request: ImportRequest, rows: List<ParsedRow>): Map<String, String> {
    if (request.sourceType != app.masroufy.core.ImportSourceType.SMS) return emptyMap()
    val refs = rows.mapNotNull { row -> row.reference?.let(::jsTrim)?.takeIf { it.startsWith("SMS:") } }.distinct()
    if (refs.isEmpty()) return emptyMap()
    val records = deps.sources.listBySourceReferences(refs).filter { it.accountIdentity != request.accountIdentity && it.transactionId != null }
    val alive = deps.txns.findByIds(records.mapNotNull { it.transactionId }.distinct()).map { it.id }.toSet()
    val out = LinkedHashMap<String, String>()
    for (record in records) {
        val txn = record.transactionId ?: continue
        if (txn in alive) out.getOrPut(jsTrim(record.sourceReference ?: continue)) { txn }
    }
    return out
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

    val elsewhere = smsRecordedElsewhere(deps, request, outcome.rows)
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
        // مراجعة S1: نفس رسالة البنك اتسجلت في محفظة تانية في البلد ⇒ مكررة (مش جديدة ولا «شبه») — بتتشال من الصندوق من غير تسجيل
        val recordedElsewhere = if (ref == null) null else elsewhere[ref]
        if (ref != null && recordedElsewhere != null && (verdict.state == MatchingState.NEW || verdict.state == MatchingState.SIMILAR)) {
            verdict = DedupeVerdict(MatchingState.DUPLICATE, uiText(TextKey.DEDUPE_SAME_REFERENCE, ref), matchedTransactionId = recordedElsewhere)
        }
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
    // §75-10 (S4): الكشف والرسالة نفس الحركة ⇒ دمج. null = من غير دمج (ملفات المرجع بالحرف). نص السطر = سطر الملف في الـCSV بس
    val textLines = request.parsedRows == null && (outcome.schema == SchemaId.PREVIEW || outcome.schema == SchemaId.LEGACY)
    val lines = if (window != null) applyCrossSource(deps, request, scanned, ledger, window, textLines) else scanned

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
