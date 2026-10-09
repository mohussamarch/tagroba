package app.masroufy.usecase

import app.masroufy.core.CrossSourceExisting
import app.masroufy.core.CrossSourceRow
import app.masroufy.core.CrossSourceVerdict
import app.masroufy.core.DedupeCandidate
import app.masroufy.core.ExistingRecord
import app.masroufy.core.Id
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.hashContent
import app.masroufy.core.daysBetween
import app.masroufy.core.isSmsReference
import app.masroufy.core.jsTrim
import app.masroufy.core.matchCrossSource
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sourceAmountMinor
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import kotlin.math.abs

/**
 * المعاينة — الجزء الخاص بالدمج بين الكشف والرسالة (§75-10، الشريحة S4) + فهرس الموجود لمنع التكرار.
 * كله **قراية بس**؛ الكتابة في `MergeUndo.kt` بعد ما الدفعة تتقفل.
 */

/** سجلات المصدر بتاعة هوية الحساب + عملياتها (مرة واحدة لكل معاينة). */
internal class ExistingLedger(val records: List<SourceRecord>, val byId: Map<Id, Transaction>)

internal suspend fun loadLedger(deps: ImportStatementDeps, accountIdentity: String): ExistingLedger {
    val records = deps.sources.listByAccountIdentity(accountIdentity)
    return ExistingLedger(records, deps.txns.findByIds(records.mapNotNull { it.transactionId }).associateBy { it.id })
}

/**
 * **سجل واحد لكل عملية، مش لكل سجل مصدر.** العملية اللي اتسجلت من مصدرين ليها أكتر من `sourceRecord`، وكانت بتتعد أكتر من مرة في
 * فهرس التكرار فتبتلع أكتر من صف وارد (اتشاف على بيانات المالك 2026-09-12).
 * [preferStatement] (الدمج شغال — S4): العملية اللي ليها رسالة **وكشف** بيمثلها سجل الكشف — تاريخها ورصيدها بقوا بتوع الكشف، فإعادة
 * استيراد نفس الكشف بتتعرف «نفس سطر الكشف» بالرصيد. من غيره (ملفات المرجع) أول سجل بيمثلها زي التطبيق الحالي بالظبط.
 */
internal fun existingRecordsOf(ledger: ExistingLedger, preferStatement: Boolean): List<ExistingRecord> {
    val byTransaction = LinkedHashMap<String, MutableList<SourceRecord>>()
    for (record in ledger.records) {
        val transactionId = record.transactionId ?: continue
        if (transactionId !in ledger.byId) continue
        byTransaction.getOrPut(transactionId) { mutableListOf() }.add(record)
    }
    return byTransaction.map { (transactionId, group) ->
        val mixed = preferStatement && group.any { isSmsReference(it.sourceReference) } && group.any { !isSmsReference(it.sourceReference) }
        val record = if (mixed) group.first { !isSmsReference(it.sourceReference) } else group.first()
        val txn = ledger.byId.getValue(transactionId)
        ExistingRecord(
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
                smsSource = isSmsReference(record.sourceReference),
            ),
            transactionId,
        )
    }
}

private fun shift(date: IsoDate, days: Int): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(date)) + days)

private fun trimmedRef(reference: String?): String? = reference?.let(::jsTrim)?.takeIf { it.isNotEmpty() }

/**
 * العمليات اللي ممكن سطر يتدمج فيها أو يكون اتدمج فيها قبل كده: **نفس المحفظة** (لو الطلب ليه محفظة — كده الرسالة والكشف بيتقابلوا حتى لو
 * هويتهم مختلفة، زي محفظة اتغيّر اسمها) أو نفس هوية الحساب، نفس العملة، وجوه النافذة حوالين تواريخ السطور بنفس المبلغ والاتجاه — أو
 * مرجعها هو مرجع سطر وارد. سجلات المصدر بتتقري للمرشحين دول بس (مش لكل عمليات الفترة).
 */
private suspend fun loadCrossSource(deps: ImportStatementDeps, request: ImportRequest, lines: List<ImportPreviewLine>, ledger: ExistingLedger, window: Int): List<CrossSourceExisting> {
    val from = shift(lines.minOf { it.row.date }, -window)
    val to = shift(lines.maxOf { it.row.date }, window)
    val inWallet = request.walletId?.let { w -> deps.txns.listByDateRange(from, to).filter { it.walletId == w } }.orEmpty()
    // نفس هوية الحساب ومن غير محفظة تانية (بيانات قديمة من غير محفظة)
    val byIdentity = ledger.byId.values.filter { it.occurredAt in from..to && (it.walletId == null || it.walletId == request.walletId) }
    val near = (inWallet + byIdentity).distinctBy { it.id }.filter { it.currency == request.currency }
    val byAmount = near.groupBy { sourceAmountMinor(it.amountMinor, it.originalAmountMinor) to it.observedDirection }
    val relevant = LinkedHashMap<Id, Transaction>()
    for (line in lines) {
        for (t in byAmount[line.row.amountMinor to line.row.direction].orEmpty()) if (abs(daysBetween(line.row.date, t.occurredAt)) <= window) relevant[t.id] = t
    }
    val refs = lines.mapNotNull { trimmedRef(it.row.reference) }.toSet()
    for (record in ledger.records) {
        val id = record.transactionId ?: continue
        if (trimmedRef(record.sourceReference) in refs) ledger.byId[id]?.let { relevant[id] = it }
    }
    if (relevant.isEmpty()) return emptyList()
    val records = deps.sources.listByTransactionIds(relevant.keys.toList())
    val batches = records.map { it.batchId }.distinct().associateWith { deps.batches.findById(it) }
    // **الدفعة المقفولة بس:** عملية دفعة معلّقة (انقطاع) التنظيف هيمسحها ⇒ الدمج فيها كان هيضيّع الرسالة. ومصدر السجل من نوع دفعته
    // (رسالة ولا كشف) مش من شكل المرجع — أي سجل زيادة لأثر وقت التسجيل (الرسوم مثلًا) بياخد نوع دفعته. دفعة مش موجودة (بيانات قديمة) ⇒ شكل المرجع
    val live = records.filter { batches[it.batchId]?.state != ImportBatchState.STAGED && batches[it.batchId]?.state != ImportBatchState.REVERTED }.groupBy { it.transactionId }
    fun sms(record: SourceRecord) = batches[record.batchId]?.let { it.sourceType == ImportSourceType.SMS } ?: isSmsReference(record.sourceReference)
    return relevant.values.mapNotNull { t ->
        val own = live[t.id] ?: return@mapNotNull null
        CrossSourceExisting(
            transactionId = t.id, date = t.occurredAt, amountMinor = sourceAmountMinor(t.amountMinor, t.originalAmountMinor), direction = t.observedDirection,
            fromSms = own.any { sms(it) }, fromStatement = own.any { !sms(it) },
            // بصمات سجلات الكشف بس (مراجعة S4): نص الرسالة عمره ما بيحكم إن رسالة تانية «متسجلة خلاص»
            references = own.mapNotNull { trimmedRef(it.sourceReference) }.toSet(), hashes = own.filter { !sms(it) }.map { it.sourceHash },
        )
    }
}

/**
 * §75-10 على سطور المعاينة: دمج (جديد + [ImportPreviewLine.mergeInto] — مختار افتراضيًا وأثره صفر) · أكتر من احتمال ⇒ «شبه عملية» ويسأل
 * ومعاه الاحتمالات ([ImportPreviewLine.mergeCandidates]) · مرجعه أو نصه اتدمج قبل كده ⇒ مكرر · سطر كشف مفيش فيه حاجة تحسم جنب عملية اتدمجت
 * بنفس الحركة ⇒ «شبه عملية» ويسأل. الباقي زي ما هو.
 * [textLines] = نص كل سطر هو سطر الملف نفسه (CSV) — الـPDF نصه «صفحة N · التاريخ» بس، فما بيتقارنش بالنص (مراجعة S4).
 */
internal suspend fun applyCrossSource(
    deps: ImportStatementDeps,
    request: ImportRequest,
    lines: List<ImportPreviewLine>,
    ledger: ExistingLedger,
    window: Int,
    textLines: Boolean,
): List<ImportPreviewLine> {
    if (lines.isEmpty()) return lines
    val fromSms = request.sourceType == ImportSourceType.SMS
    val existing = loadCrossSource(deps, request, lines, ledger, window)
    if (existing.isEmpty()) return lines
    // النص بالحرف بيحكم بس لسطر كشف CSV من غير مرجع ولا رصيد (المرجع والرصيد بيحسموا من غيره)
    fun hashOf(line: ImportPreviewLine): String? =
        if (!fromSms && textLines && trimmedRef(line.row.reference) == null && line.row.statedBalanceMinor == null) hashContent(line.row.raw) else null
    val rows = lines.map {
        CrossSourceRow(it.row.lineNumber, it.row.date, it.row.amountMinor, it.row.direction, fromSms, it.row.reference, it.state, it.matchedTransactionId, hashOf(it), it.row.statedBalanceMinor)
    }
    val verdicts = matchCrossSource(rows, existing, window)
    if (verdicts.isEmpty()) return lines
    val byId = existing.associateBy { it.transactionId }
    // المصدر التاني = اللي العملية الموجودة اتسجلت منه
    val other = uiText(if (fromSms) TextKey.MATCH_SOURCE_STATEMENT else TextKey.MATCH_SOURCE_SMS)
    return lines.map { line ->
        when (val v = verdicts[line.row.lineNumber]) {
            null -> line
            is CrossSourceVerdict.Merge -> line.copy(
                state = MatchingState.NEW, reason = uiText(TextKey.MATCH_MERGE, other, byId.getValue(v.transactionId).date),
                matchedTransactionId = v.transactionId, mergeInto = v.transactionId, selectedByDefault = true,
            )
            is CrossSourceVerdict.Ambiguous -> line.copy(
                state = MatchingState.SIMILAR, reason = uiText(TextKey.MATCH_AMBIGUOUS, other, window.toString()),
                matchedTransactionId = v.transactionId, mergeInto = null, selectedByDefault = false, mergeCandidates = v.candidates,
            )
            is CrossSourceVerdict.AlreadyMerged -> line.copy(
                state = MatchingState.DUPLICATE, reason = uiText(TextKey.MATCH_ALREADY_MERGED, other),
                matchedTransactionId = v.transactionId, mergeInto = null, selectedByDefault = false,
            )
            // مفيش حاجة في السطر تحسم ⇒ بيسأل: «ضيفها» = عملية جديدة، «تجاهل» = هي اللي اتدمجت (التشابه ما بيمسحش)
            is CrossSourceVerdict.MaybeMerged -> line.copy(
                state = MatchingState.SIMILAR, reason = uiText(TextKey.MATCH_MAYBE_MERGED, other, byId.getValue(v.transactionId).date),
                matchedTransactionId = v.transactionId, mergeInto = null, selectedByDefault = false,
            )
        }
    }
}
