package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.hashContent
import app.masroufy.core.parseIsoDate
import app.masroufy.core.shiftDays
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import kotlin.math.abs

/**
 * §82-10 (المالك 2026-10-10): «رسالة رسوم لوحدها + نفس الرسوم في التحويل = **رسوم واحدة**» — أيًّا كانت اللي وصلت الأول.
 * - **رسالة الرسوم الأول** (كانت بتطلع رسومين): التحويل لما يتسجل بيدوّر على عملية رسوم من رسالة رسوم ([SmsKind.FEE][app.masroufy.core.SmsKind.FEE])
 *   بنفس المبلغ والمحفظة والعملة، يومها = يوم التحويل (أو يوم قبله/بعده لو الرسالة عدّت نص الليل — الأقرب الأول)، و**مش محسوبة لتحويل
 *   تاني** ⇒ هي رسوم التحويل: مفيش عملية «رسوم بنكية» جديدة، وسجل مصدر «#fee» بتاع التحويل بيتكتب **عليها** (علامة إنها اتحسبت — وتحويل
 *   تاني بنفس الرسوم في نفس اليوم ياخد رسومه لوحده). في نفس الدفعة (قراية التاريخ) نفس القاعدة على سطور الدفعة نفسها.
 * - **التحويل الأول**: رسالة الرسوم اللي بعده بتطلع «شبيهة» من منع التكرار (نفس المبلغ واليوم والمحفظة) ⇒ مستنية تأكيدك ومش بتتسجل
 *   لوحدها — مسار قراية الرسايل نفسه ما اتلمسش هنا.
 * التراجع عن أي دفعة من الاتنين: عملية الرسوم ليها سجلين ⇒ بتفضل («ليها مصدر تاني») — الرسوم ما بتضيعش ولا بتتكرر.
 */
internal class SmsFeeTwins(
    private val txns: TransactionRepository?,
    private val sources: SourceRecordRepository?,
    /** سطور رسايل الرسوم في الدفعة دي. */
    private val feeLines: List<RecordedLine>,
) {
    private val claimed = mutableSetOf<Id>()

    /**
     * رسوم [feeMinor] لعملية [main]: لو ليها توأم من رسالة رسوم ⇒ بيتعلّم عليه (سجل [feeReference]) ويرجّع معرّفه، وإلا null (اتعمل عملية رسوم
     * عادي).
     */
    suspend fun claim(ctx: RecordContext, main: Transaction, feeMinor: Halalas, feeReference: String, line: RecordedLine): Id? {
        val day = main.occurredAt.take(10)
        // نفس الدفعة الأول: سطر رسالة رسوم بنفس المبلغ والمحفظة واليوم — وبعدين اللي اتسجل قبل كده
        val twin = feeLines.map { it.transaction }.filter { twinOf(it, main, feeMinor) && it.id !in claimed }.nearest(day) ?: earlier(day, main, feeMinor) ?: return null
        claimed += twin.id
        sources?.saveMany(
            listOf(
                SourceRecord(
                    id = ctx.ids.next("src"), batchId = ctx.batchId, accountIdentity = ctx.request.accountIdentity, sourceReference = feeReference,
                    sourceHash = hashContent(line.line.row.raw), originalRowIndex = line.line.row.lineNumber, rawLine = line.line.row.raw,
                    transactionId = twin.id, matchingState = MatchingState.DUPLICATE, reason = uiText(UiKey.SMS_FEE_TWIN_REASON),
                ),
            ),
        )
        return twin.id
    }

    /** رسالة رسوم اتسجلت في دفعة قبل كده ولسه مش محسوبة لتحويل. */
    private suspend fun earlier(day: String, main: Transaction, feeMinor: Halalas): Transaction? {
        val t = txns ?: return null
        val s = sources ?: return null
        val near = t.listByDateRange(shiftDays(day, -1), shiftDays(day, 1)).filter { twinOf(it, main, feeMinor) && it.id !in claimed }
        if (near.isEmpty()) return null
        // عملية رسوم عليها «#fee» = رسوم تحويل (اتعملت منه أو اتحسبت له) ⇒ مش متاحة
        val taken = s.listByTransactionIds(near.map { it.id }).filter { it.sourceReference?.endsWith(SmsFeeEffect.FEE_REFERENCE_SUFFIX) == true }
            .mapNotNull { it.transactionId }.toSet()
        return near.filter { it.id !in taken }.nearest(day)
    }

    /** عملية رسوم من رسالة رسوم: «رسوم» صادرة بنوع العملية في المصدر «رسوم بنكية» · نفس المحفظة والعملة والمبلغ. */
    private fun twinOf(t: Transaction, main: Transaction, feeMinor: Halalas): Boolean =
        t.id != main.id && t.economicKind == EconomicKind.FEE && t.observedDirection == Direction.OUT && t.sourceOperationType == BankFeeCategory.NAME &&
            t.walletId == main.walletId && t.currency == main.currency && t.amountMinor == feeMinor

    private fun List<Transaction>.nearest(day: String): Transaction? {
        val target = toDayNumber(parseIsoDate(day))
        return minWithOrNull(compareBy({ abs(toDayNumber(parseIsoDate(it.occurredAt.take(10))) - target) }, { it.occurredAt }, { it.id }))
    }
}
