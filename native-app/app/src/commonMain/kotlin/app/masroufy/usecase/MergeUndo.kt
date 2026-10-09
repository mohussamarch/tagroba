package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.hashContent
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository

/**
 * §75-10 (الشريحة S4) — كتابة الدمج والتراجع عنه.
 *
 * **الدمج = سجل مصدر تاني للعملية الموجودة** (`matchingState` = مكرر — قيمة التطبيق القديم نفسها — و`transactionId` = العملية)، ومفيش عملية
 * جديدة. لو السطر الجديد **سطر كشف**: الكشف بيكسب في التاريخ وترتيب السطر والرصيد المعلن (سلسلة الرصيد بتطابق الكشف سطر بسطر —
 * CLAUDE.md معيار ٣)، والقيم القديمة بتتحفظ في السجل ([SourceRecord.mergeUndo]). أي حاجة تانية (التصنيف · الملاحظة · الروابط · المحل)
 * بتفضل بتاعة العملية اللي اتسجلت الأول. لو السطر الجديد رسالة: العملية ما بتتغيرش (الكشف كسب خلاص).
 *
 * بيتكتب **بعد** ما الدفعة تبقى `committed` (وحدة عمل لوحدها): الدفعة المعلّقة عمرها ما بتشاور على عملية دفعة تانية، فالتنظيف بعد الانقطاع
 * (`ResumeStagedBatch` هنا وفي التطبيق القديم) ما يمسحهاش. انقطاع بين الاتنين ⇒ السطر ما اتسجلش مصدر (الرسالة بتفضل في الصندوق وتتدمج
 * المرة الجاية؛ الكشف لو اتستورد تاني بيتدمج).
 */
internal suspend fun recordMerges(deps: ImportStatementDeps, request: ImportRequest, batchId: Id, nowIso: String, lines: List<ImportPreviewLine>) {
    val targets = deps.txns.findByIds(lines.mapNotNull { it.mergeInto }.distinct()).associateBy { it.id }
    val statement = request.sourceType != ImportSourceType.SMS
    val records = mutableListOf<SourceRecord>()
    val patched = mutableListOf<Transaction>()
    for (line in lines) {
        // العملية اتمسحت بعد المعاينة ⇒ ولا سجل (الكل أو لا شيء) — والسطر بيرجع «جديد» المرة الجاية
        val target = targets[line.mergeInto] ?: throw IllegalStateException(uiText(TextKey.IMPORT_CHANGED_AFTER_PREVIEW))
        records += SourceRecord(
            id = deps.ids.next("src"),
            batchId = batchId,
            accountIdentity = request.accountIdentity,
            sourceReference = line.row.reference,
            sourceHash = hashContent(line.row.raw),
            originalRowIndex = line.row.lineNumber,
            rawLine = line.row.raw,
            transactionId = target.id,
            matchingState = MatchingState.DUPLICATE,
            reason = line.reason,
            mergeUndo = if (statement) MergeRestore(target.occurredAt, target.sourceOrder, target.statedBalanceMinor) else null,
        )
        if (statement) {
            patched += target.copy(
                occurredAt = line.row.date,
                sourceOrder = line.row.lineNumber,
                // كشف من غير عمود رصيد ما بيمسحش رصيد موجود
                statedBalanceMinor = line.row.statedBalanceMinor ?: target.statedBalanceMinor,
                updatedAt = nowIso,
            )
        }
    }
    deps.uow.run {
        deps.sources.saveMany(records)
        if (patched.isNotEmpty()) deps.txns.saveMany(patched)
    }
}

/**
 * التراجع عن دفعة فيها دمج (`RevertDeps.undoers`): العملية **بتفضل** (ليها مصدر تاني — `RevertImportBatch` بيقرر ده)، وتاريخها وترتيبها
 * ورصيدها بيرجعوا زي ما كانوا قبل سطر الكشف. العملية اللي هتتمسح أصلًا ما بتتلمسش.
 * ⚠️ التطبيق القديم لو رجّع نفس الدفعة: بيسيب العملية (مصدر تاني) بس **ما بيرجّعش** التاريخ والرصيد — ما بيعرفش الحقل.
 */
class MergeUndo(private val txns: TransactionRepository, private val clock: Clock) : BatchUndo {
    override suspend fun undo(batchId: Id, records: List<SourceRecord>, deleting: List<Id>) {
        val skip = deleting.toSet()
        val restores = records.filter { it.batchId == batchId && it.mergeUndo != null && it.transactionId != null && it.transactionId !in skip }
        if (restores.isEmpty()) return
        val byId = txns.findByIds(restores.mapNotNull { it.transactionId }.distinct()).associateBy { it.id }
        val now = clock.nowIso()
        val restored = restores.mapNotNull { record ->
            val old = record.mergeUndo ?: return@mapNotNull null
            byId[record.transactionId]?.copy(occurredAt = old.occurredAt, sourceOrder = old.sourceOrder, statedBalanceMinor = old.statedBalanceMinor, updatedAt = now)
        }
        if (restored.isNotEmpty()) txns.saveMany(restored)
    }
}
