package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SmsKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.parseIsoDate
import app.masroufy.core.shiftDays
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import kotlin.math.abs

/**
 * §82-10 · §88 (المالك 2026-10-11): رسالة رسوم لوحدها ([SmsKind.FEE]) وصلت **بعد** التحويل اللي رسومه اتسجلت منه ⇒ **بتنضم** لرسوم
 * التحويل دي لوحدها (رسوم واحدة) بدل ما تستنى «شبيهة». الاتجاه التاني (رسالة الرسوم الأول) في [SmsFeeTwins].
 *
 * بتنضم **بس لو واضح إنها هي** — غير كده السطر زي ما منع التكرار قاله بالظبط:
 * - رسالة رسوم صادرة، جديدة أو «شبيهة» ومش متدمجة في حاجة تانية · المحفظة معروفة.
 * - عملية «رسوم بنكية» ([BankFeeCategory]) صادرة في **نفس المحفظة والعملة** و**نفس المبلغ بالظبط**، يومها = يوم الرسالة أو يوم قبله/بعده
 *   (نفس نافذة [SmsFeeTwins] — الرسالة ممكن تعدّي نص الليل). الأقرب يوم الأول.
 * - العملية دي **رسوم تحويل** (عليها سجل «#fee») و**لسه مالهاش رسالة رسوم** (مفيش عليها أي سجل تاني) — رسالة رسوم واحدة لكل رسوم تحويل.
 *
 * الانضمام = دمج ([ImportPreviewLine.mergeInto] → `recordMerges`): سجل مصدر تاني على عملية الرسوم ومفيش عملية جديدة ولا أثر على الرصيد.
 * التراجع: دفعة رسالة الرسوم ⇒ سجلها بس بيتمسح والرسوم بتفضل (بتاعة التحويل) · دفعة التحويل ⇒ التحويل بيتمسح والرسوم بتفضل (ليها مصدر تاني
 * — رسالتها) — زي اتجاه «رسالة الرسوم الأول» بالظبط.
 */
internal suspend fun joinFeeMessages(deps: ImportStatementDeps, request: ImportRequest, lines: List<ImportPreviewLine>): List<ImportPreviewLine> {
    if (request.sourceType != ImportSourceType.SMS) return lines
    val wallet = request.walletId ?: return lines
    val feeLines = lines.filter { line ->
        request.smsRows[line.row.lineNumber]?.kind == SmsKind.FEE && line.row.direction == Direction.OUT && line.mergeInto == null &&
            (line.state == MatchingState.NEW || line.state == MatchingState.SIMILAR)
    }
    if (feeLines.isEmpty()) return lines
    val days = feeLines.map { it.row.date }
    val fees = deps.txns.listByDateRange(shiftDays(days.min(), -1), shiftDays(days.max(), 1)).filter {
        it.economicKind == EconomicKind.FEE && it.observedDirection == Direction.OUT && it.sourceOperationType == BankFeeCategory.NAME &&
            it.walletId == wallet && it.currency == request.currency
    }
    if (fees.isEmpty()) return lines
    val records = deps.sources.listByTransactionIds(fees.map { it.id }).groupBy { it.transactionId }
    val open = fees.filter { t -> records[t.id].orEmpty().let { own -> own.isNotEmpty() && own.all(::isTransferFee) } }.toMutableList()
    if (open.isEmpty()) return lines
    val joining = feeLines.map { it.row.lineNumber }.toSet()
    return lines.map { line ->
        if (line.row.lineNumber !in joining || line.mergeInto != null) return@map line
        val twin = open.filter { it.amountMinor == line.row.amountMinor && dayGap(it, line.row.date) <= 1 }
            .minWithOrNull(compareBy({ dayGap(it, line.row.date) }, { it.occurredAt }, { it.id })) ?: return@map line
        open -= twin
        line.copy(
            state = MatchingState.NEW, reason = uiText(UiKey.SMS_FEE_TWIN_REASON), matchedTransactionId = twin.id, mergeInto = twin.id,
            selectedByDefault = true, mergeCandidates = emptyList(),
        )
    }
}

/** سجل رسوم التحويل (`مرجع الرسالة#fee` — اتعملت منه أو اتحسبت له). */
private fun isTransferFee(record: SourceRecord): Boolean = record.sourceReference?.endsWith(SmsFeeEffect.FEE_REFERENCE_SUFFIX) == true

private fun dayGap(t: Transaction, day: String): Int = abs(toDayNumber(parseIsoDate(t.occurredAt.take(10))) - toDayNumber(parseIsoDate(day.take(10))))
