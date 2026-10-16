package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.applyKnownPayerSalary
import app.masroufy.core.applyTransferVerdict
import app.masroufy.core.transferPartyOf

/**
 * r041-B: **بنّا العملية من سطر الاستيراد — مكان واحد** (اتنقل من `ImportStatement.buildTransaction`): `ImportStatement` بيستعمله، و«صلّح رسايل
 * البنك القديمة» (`CoveredSmsRepair`) بيستعمله هو هو للرسايل اللي «الكشف غطّاها» — عشان تصنيف الرسالة المتصلّحة ما يختلفش عن التسجيل العادي.
 */
internal fun importLineTransaction(id: Id, line: ImportPreviewLine, now: String, request: ImportRequest, chosenCategoryId: Id?): Transaction {
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
        notInStatement = true.takeIf { line.notInStatement },
    )
}

/**
 * العمليات بعد قرارات وقت التسجيل: [lineTransactions] = عملية لكل سطر بنفس ترتيب السطور · [extra] = اللي الآثار زودته (زي «رسوم بنكية»)
 * ومعاه سجل مصدره · [context] = سياق الآثار (null لو مفيش آثار) — `afterCommit` بيحتاجه.
 */
internal class PreparedLines(
    val lineTransactions: List<Transaction>,
    val extra: List<Pair<Transaction, SourceRecord>>,
    val context: RecordContext?,
    private val includedLines: List<ImportPreviewLine>,
) {
    /** عملية السطر رقم [lineNumber] بعد الآثار (الأثر ممكن يبدّل ترتيب السطور أو يشيل سطر) — null لو مالوش عملية. */
    fun transactionFor(lineNumber: Int): Transaction? = context?.lines?.firstOrNull { it.line.row.lineNumber == lineNumber }?.transaction
        ?: if (context == null) includedLines.indexOfFirst { it.row.lineNumber == lineNumber }.takeIf { it >= 0 }?.let { lineTransactions[it] } else null
}

/**
 * قرارات «زون التحويلات» (§60) ⇒ الدخل المعروف (§64) ⇒ آثار وقت التسجيل `prepare` بالترتيب (عقد C0) — نفس ترتيب `ImportStatement` بالظبط.
 * لازم يتنادى **جوه** وحدة العمل (الآثار ممكن تكتب، زي إنشاء تصنيف «رسوم بنكية»). [built] = [importLineTransaction] لكل سطر في [includedLines].
 */
internal suspend fun prepareRecordedLines(
    deps: ImportStatementDeps,
    request: ImportRequest,
    batchId: Id,
    now: String,
    includedLines: List<ImportPreviewLine>,
    built: List<Transaction>,
    chosenCategories: Map<Int, Id>?,
): PreparedLines {
    val transactions = built.toMutableList()
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
    if (deps.effects.isEmpty()) return PreparedLines(transactions, emptyList(), null, includedLines)
    // عقد C0: آثار وقت التسجيل بالترتيب (`RecordEffects.kt`) على العمليات **بعد** القرارين اللي فوق
    val lines = includedLines.indices.map { i -> RecordedLine(includedLines[i], request.smsRows[includedLines[i].row.lineNumber], transactions[i]) }
    val ctx = RecordContext(request, batchId, now, request.byOwner, chosenCategories.orEmpty(), lines.toMutableList(), mutableListOf(), deps.ids)
    for (effect in deps.effects) effect.prepare(ctx)
    return PreparedLines(ctx.lines.map { it.transaction }, ctx.extra.toList(), ctx, includedLines)
}
