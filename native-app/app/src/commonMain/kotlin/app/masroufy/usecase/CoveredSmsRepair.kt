package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.BankSmsReader
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.SchemaId
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsLegVerdict
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.smsLegRows
import app.masroufy.core.smsLegVerdict
import app.masroufy.core.toDayNumber
import app.masroufy.core.toParsedRow
import app.masroufy.core.uiText
import app.masroufy.port.WalletRepository

/**
 * سطر «الكشف غطّاها» في «صلّح رسايل البنك القديمة» (r041-B · OVERRIDES §91): رسالة القاعدة القديمة ما سجّلتهاش لأن يومها جوه أيام الكشف،
 * ومبلغها **مش** في الكشف. الشاشة بتعرض بس. [title] = اسم التاجر أو الطرف من قارئ الرسالة (مقصوص) — عمره ما بيبقى نص الرسالة.
 */
data class SmsUnrecordedLine(
    val recordId: Id,
    val walletId: Id,
    val date: IsoDate,
    val amountMinor: Halalas,
    val currency: Currency,
    val direction: Direction,
    val title: String,
)

/** معاينة الشاشة كلها: النقل (قبل ⇒ بعد) و«الكشف غطّاها». */
data class SmsRepairPreview(val moves: List<SmsRepairLine>, val unrecorded: List<SmsUnrecordedLine>)

/**
 * اللي اتكتب لما «الكشف غطّاها» اتسجلت — «تراجع» بيمسح الجديد ويرجّع السجلات والدفعات زي ما كانت بالحرف.
 * [createdStamps] = `updatedAt` لكل عملية اتعملت لحظة الكتابة: اتغيرت من ساعتها (تعديل · ربط) ⇒ «تراجع» بيترفض (النسخة هي البديل).
 */
internal class CoveredWrite(
    val lineCount: Int,
    val createdTransactionIds: List<Id>,
    val createdRecordIds: List<Id>,
    val recordsBefore: List<SourceRecord>,
    val batchesBefore: List<ImportBatch>,
    val createdStamps: Map<Id, String> = emptyMap(),
)

/**
 * r041-B: الرسايل اللي القاعدة القديمة علّمتها «الكشف غطّاها» (`MatchingState.DUPLICATE` من غير عملية) وهي **مش** في الكشف ⇒ بتتعرض، والمالك
 * يأكّد، وبتتسجل وعليها «مش في الكشف» بنفس بنّا التسجيل العادي (`RecordLines.kt`) — نفس آثار خط الرسايل ([withReturnEffects] زي `SmsLane.of`).
 * الحكم «جوه أيام الكشف» و«المبلغ في الكشف» هو نفس دوال التسجيل ([inCoveredDays] · [amountInStatement]) — القاعدتين ما يختلفوش.
 * ⚠️ آثار ما بعد الحفظ (`afterCommit` — سؤال «ده راتبك؟» · ميعاد الاشتراك) **ما بتتنداش هنا**: بتكتب برّا وحدة العمل و«تراجع» ما يقدرش يرجّعها.
 * عشان كده `ReturnedSmsEffect` **مش هنا** (ربطه بعملية متخزنة بيكمل في `afterCommit` وبيكتب على عملية متخزنة جوه `prepare`)، ورسايل «رجع»
 * وكل سطر ليه سبب انتظار (`waitReasonOf` — استرداد · سحب كاش · إيداع كاش · …) ما بتتعرضش في النسخة دي (OVERRIDES §91 — سؤال مفتوح).
 */
class CoveredSmsRepair(deps: ImportStatementDeps, private val wallets: WalletRepository, private val readers: List<BankSmsReader>) {
    private val deps = withReturnEffects(deps).let { d -> d.copy(effects = d.effects.filter { it !is ReturnedSmsEffect }) }

    internal class Candidate(val line: SmsUnrecordedLine, val record: SourceRecord, val batch: ImportBatch, val wallet: Wallet, val row: SmsRow)

    private class Cover(val days: Pair<IsoDate, IsoDate>, val statement: List<Transaction>)

    suspend fun preview(): List<SmsUnrecordedLine> = candidates().map { it.line }

    /** قراية بس. أول سجل لكل مرجع رسالة بس ممكن يبقى سطر (الباقي مكرر رسالة ⇄ رسالة ويفضل زي ما هو). */
    internal suspend fun candidates(): List<Candidate> {
        val all = wallets.listAll()
        val own = ownDigits(all)
        val notMine = notMineDigits(all)
        val smsBatches = deps.batches.listRecent(ALL_SMS_BATCHES)
            .filter { it.sourceType == ImportSourceType.SMS && it.state != ImportBatchState.STAGED && it.state != ImportBatchState.REVERTED }
            .sortedWith(compareBy({ it.importedAt }, { it.id }))
        val skipped = smsBatches.flatMap { b ->
            deps.sources.listByBatch(b.id).sortedBy { it.originalRowIndex }
                .filter { it.transactionId == null && it.matchingState == MatchingState.DUPLICATE && !it.sourceReference.isNullOrBlank() }.map { b to it }
        }
        if (skipped.isEmpty()) return emptyList()
        val sameRef = deps.sources.listBySourceReferences(skipped.map { it.second.sourceReference!! }.distinct()).groupBy { it.sourceReference }
        val covers = HashMap<String, Cover?>()
        val seen = HashSet<String>()
        val out = mutableListOf<Candidate>()
        for ((batch, record) in skipped) {
            val ref = record.sourceReference!!
            if (!seen.add(ref)) continue
            // اتسجلت بعدين بطريقة تانية · أو المالك مسحها بإيده (§91-12) ⇒ مش هنا
            if (sameRef[ref].orEmpty().any { it.transactionId != null || it.ownerDeletedAt != null }) continue
            val wallet = all.filter { it.name == record.accountIdentity }.singleOrNull() ?: continue
            val cover = covers.getOrPut(record.accountIdentity) { coverOf(record.accountIdentity) } ?: continue
            val row = rowOf(record, arrivalAnchors(batch.importedAt, cover.days.first), wallet, own, notMine)
                ?.takeIf { it.kind != SmsKind.RETURNED && waitReasonOf(it, targetOf(wallet, all, movesCash = false)) == null } ?: continue
            // المبلغ **في** الكشف ⇒ ما بتظهرش أبدًا (قاعدة المالك — بتفضل مدموجة/مغطّاة)
            if (!inCoveredDays(row.date, cover.days) || amountInStatement(cover.statement, row.date, row.amountMinor, row.direction)) continue
            if (alreadyRecorded(wallet.id, row)) continue
            out += Candidate(
                SmsUnrecordedLine(record.id, wallet.id, row.date, row.amountMinor, wallet.currency, row.direction, row.merchantName), record, batch, wallet, row,
            )
        }
        return out.sortedWith(compareBy({ it.line.date }, { it.line.recordId }))
    }

    /** مستندات النسخة المتأكدة: معرّفات ومراجع بس (عمرها ما بتشيل نص الرسالة) + عدادات كل دفعة هتتغير. */
    internal fun backupDocs(chosen: List<Candidate>): List<Map<String, Any?>> =
        chosen.map { c ->
            linkedMapOf(
                "kind" to "unrecorded", "recordId" to c.record.id, "batchId" to c.record.batchId, "transactionId" to null,
                "matchingState" to c.record.matchingState.name, "accountIdentity" to c.record.accountIdentity, "sourceReference" to c.record.sourceReference,
            )
        } + chosen.map { it.batch }.distinctBy { it.id }.map { b ->
            linkedMapOf("kind" to "batchCounts", "batchId" to b.id, "imported" to b.counts.imported, "duplicates" to b.counts.duplicates)
        }

    /**
     * التسجيل — **جوه وحدة العمل** ([RepairOldSms.applyAll]): عملية لكل سطر بنفس بنّا التسجيل وعليها «مش في الكشف»، وسجل المصدر بيشاور عليها
     * (نفس الحالة اللي التسجيل بيكتبها لسطر جديد)، وعداد الدفعة. سجل الأول وبعده العملية (بروتوكول `ImportStatement`).
     */
    internal suspend fun record(chosen: List<Candidate>, now: String): CoveredWrite {
        val catDeps = buildCategorizeDeps(deps)
        val lineTxns = mutableListOf<Transaction>()
        val extra = mutableListOf<Pair<Transaction, SourceRecord>>()
        val updated = mutableListOf<SourceRecord>()
        val batchesAfter = mutableListOf<ImportBatch>()
        for (group in chosen.groupBy { it.batch.id }.values) {
            var written = 0
            for (part in group.groupBy { it.wallet.id }.values) {
                val unique = part.distinctBy { it.row.lineNumber }
                val wallet = unique.first().wallet
                val rows = unique.map { it.row }
                val request = ImportRequest(
                    fileName = "bank-sms.json", content = "", accountIdentity = wallet.name, sourceType = ImportSourceType.SMS, walletId = wallet.id,
                    schema = SchemaId.SMS, parsedRows = rows.map { it.toParsedRow() }, currency = wallet.currency,
                    smsRows = rows.associateBy { it.lineNumber }, byOwner = true,
                )
                val lines = rows.map { r ->
                    val p = r.toParsedRow()
                    val cat = categorizeRow(p, catDeps)
                    ImportPreviewLine(
                        row = p, state = MatchingState.NEW, reason = uiText(UiKey.TXN_NOT_IN_STATEMENT_NOTE), categoryId = cat.categoryId,
                        categoryReason = cat.reason, categorySource = cat.source, selectedByDefault = true, notInStatement = true,
                    )
                }
                val built = lines.map { importLineTransaction(deps.ids.next("txn"), it, now, request, null) }
                val prepared = prepareRecordedLines(deps, request, group.first().batch.id, now, lines, built, null)
                for ((c, line) in unique.zip(lines)) {
                    val t = prepared.transactionFor(c.row.lineNumber) ?: throw IllegalStateException(uiText(UiKey.BWHY_REFUSED))
                    updated += c.record.copy(transactionId = t.id, matchingState = MatchingState.NEW, reason = line.reason)
                }
                lineTxns += prepared.lineTransactions.map { it.copy(notInStatement = true) }
                extra += prepared.extra
                written += prepared.lineTransactions.size + prepared.extra.size
            }
            val b = group.first().batch
            batchesAfter += b.copy(counts = b.counts.copy(imported = b.counts.imported + written, duplicates = (b.counts.duplicates - group.size).coerceAtLeast(0)))
        }
        val created = lineTxns + extra.map { it.first }
        val createdRecords = extra.map { it.second }
        deps.sources.saveMany(updated + createdRecords)
        deps.txns.saveMany(created)
        for (b in batchesAfter) deps.batches.save(b)
        return CoveredWrite(
            chosen.size, created.map { it.id }, createdRecords.map { it.id },
            chosen.map { it.record }, chosen.map { it.batch }.distinctBy { it.id }, created.associate { it.id to it.updatedAt },
        )
    }

    /**
     * صف الرسالة على محفظة سجلها. **اليوم من نص الرسالة بس**: القراية بكل وقت وصول ممكن ([arrivalAnchors]) لازم تدّي نفس اليوم — الرسالة اللي
     * مالهاش تاريخ يومها من وقت وصولها، وده مش محفوظ في السجل ⇒ ما تتعرضش (القاعدة 10). نفس قاعدة الرِّجل في التسجيل: حساب مش معروف ⇒ مكانها
     * «بانتظار تأكيدك» مش هنا · رِجلين بين محفظتين ⇒ مش هنا.
     */
    private fun rowOf(record: SourceRecord, anchors: List<String>, wallet: Wallet, own: Set<String>, notMine: Set<String>): SmsRow? {
        val reads = anchors.mapNotNull { parse(record.rawLine, it) }
        val row = reads.firstOrNull()?.takeIf { first -> reads.all { it.date == first.date } } ?: return null
        val leg = when (val verdict = smsLegVerdict(row, own, notMine)) {
            is SmsLegVerdict.Keep -> row
            is SmsLegVerdict.Into, is SmsLegVerdict.OutOf -> smsLegRows(row, verdict).single().takeIf { it.ownLast4 == walletLast4(wallet.accountLast4) }
            else -> null
        } ?: return null
        return leg.copy(lineNumber = record.originalRowIndex, reference = record.sourceReference)
    }

    private fun parse(body: String, receivedAt: String): SmsRow? =
        readers.firstNotNullOfOrNull { (it.parse(BankSmsMessage("", receivedAt, body), 1) as? SmsParseResult.Ok)?.row }

    private suspend fun coverOf(identity: String): Cover? {
        val ledger = loadLedger(deps, identity)
        val days = statementDays(deps.batches, ledger) ?: return null
        return Cover(days, statementTransactions(deps.batches, ledger))
    }

    /** حارس التسجيل مرتين: عملية في نفس المحفظة ونفس اليوم بنفس المبلغ والاتجاه (رسالة اتقرت تاني · إدخال يدوي) ⇒ مش هنا. */
    private suspend fun alreadyRecorded(walletId: Id, row: SmsRow): Boolean =
        deps.txns.listByDateRange(row.date, row.date + "T23:59:59.999Z").any {
            it.walletId == walletId && it.amountMinor == row.amountMinor && it.observedDirection == row.direction
        }
}

/**
 * مراجعة r041 B-1: أوقات الوصول الممكنة لرسالة سجلها مالوش وقت وصول. دفعة «اقرأ اللي فات» يومها يوم القراية (بعد الرسالة بشهور)، والقارئ
 * بيقبل التاريخ بسنتين أرقام («في:26-03-10») بس لو من 60 يوم قبل الوصول لحد يوم بعده — فيوم الدفعة لوحده كان بيرفض أغلب الرسايل القديمة
 * وكانت بتختفي من غير ما تتعد. دلوقتي: من أول يوم في الكشف [firstDay] كل 30 يوم لحد يوم الدفعة (أي يوم مغطّى ليه وقتين على الأقل جوه
 * نافذته) + يوم الدفعة واللي بعده (الرسالة وصلت قبل ما دفعتها تتكتب). الساعة 12 جرينتش = نفس اليوم في السعودية ومصر. يوم مش مفهوم ⇒ فاضية.
 */
internal fun arrivalAnchors(importedAt: String, firstDay: IsoDate): List<String> {
    val end = runCatching { toDayNumber(parseIsoDate(importedAt.take(10))) }.getOrNull() ?: return emptyList()
    val start = runCatching { toDayNumber(parseIsoDate(firstDay)) }.getOrNull() ?: end
    return ((start until end step ANCHOR_STEP_DAYS) + end + (end + 1)).map { dayNumberToIso(it) + "T12:00:00Z" }
}

/** أقل من نص نافذة القارئ (62 يوم) ⇒ كل تاريخ جوه الأيام ليه وقتين وصول بيقبلوه — اتنين بيتفقوا والرسالة اللي مالهاش تاريخ بيختلفوا. */
private const val ANCHOR_STEP_DAYS = 30

private const val ALL_SMS_BATCHES = 100_000
