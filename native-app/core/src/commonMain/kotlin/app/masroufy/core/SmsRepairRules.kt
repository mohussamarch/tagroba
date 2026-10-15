package app.masroufy.core

import kotlin.math.abs

/**
 * sms-fix (HANDOVER §6.5-3) — **تصليح السطور القديمة بتأكيد المالك** (عمره ما بيتعمل لوحده): قواعد نقية للشاشة «صلّح رسايل البنك القديمة».
 *
 * (١) [smsRepairTarget]: عملية اتسجلت من رسالة قبل الإصلاح ⇒ اتجاهها ومحفظتها بالقاعدة الجديدة (`SmsAccountLegs.kt`) من نص الرسالة المحفوظ.
 * العملية في محفظة من رجلين التحويل ⇒ المحفظة زي ما هي واتجاهها اتجاه رِجلها. في محفظة تانية ⇒ تتنقل لمحفظة الرِّجل الوحيدة (داخل أو خارج).
 * مفيش قرار أكيد (رقم مش لأي محفظة · رجلين والمحفظة مش منهم) ⇒ null — ما بنخمّنش.
 *
 * (٢) [smsStatementDuplicates]: إعادة مطابقة **مرة واحدة** للعمليات اللي من رسالة بس مع سطور الكشف بس، في الأيام اللي الكشف بيغطيها في نفس
 * المحفظة: نفس المبلغ جوه النافذة (الشراء الدولي/الأونلاين أوسع)، ونفس الاتجاه — أو **عكسه** لتحويل بين حساباتك (الغلط القديم: اتسجل خارج
 * والكشف داخل). بيطلع أزواج لقايمة «راجع المكرر» (مسار balance-why) — المالك بيقرر.
 */
fun smsRepairTarget(row: SmsRow, own: Set<String>, currentWalletLast4: String?): Pair<String, Direction>? =
    when (val v = smsLegVerdict(row, own)) {
        is SmsLegVerdict.Into -> v.last4 to Direction.IN
        is SmsLegVerdict.OutOf -> v.last4 to Direction.OUT
        is SmsLegVerdict.Between -> when (currentWalletLast4) {
            v.fromLast4 -> v.fromLast4 to Direction.OUT
            v.toLast4 -> v.toLast4 to Direction.IN
            else -> null
        }
        else -> null
    }

/** حركة في الدفاتر للمطابقة: مصدرها رسالة ولا كشف ولا الاتنين · تحويل بين حساباتك · شراء دولي/أونلاين. */
data class LedgerMove(
    val transactionId: Id,
    val walletId: Id?,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val fromSms: Boolean,
    val fromStatement: Boolean,
    val ownTransfer: Boolean = false,
    val wide: Boolean = false,
)

/** زوج مكرر محتمل: العملية اللي من الرسالة بس · سطر الكشف · [oppositeDirection] = اتجاههم عكس بعض (تحويل بين حساباتك اتقرا غلط). */
data class SmsStatementPair(val smsTransactionId: Id, val statementTransactionId: Id, val oppositeDirection: Boolean)

/** [lastStatementDay] = آخر يوم الكشف بيغطيه لكل محفظة. كل سطر كشف بيتاخد في زوج واحد بس (الأقرب في التاريخ). */
fun smsStatementDuplicates(
    moves: List<LedgerMove>,
    lastStatementDay: Map<Id, IsoDate>,
    windowDays: Int = CROSS_SOURCE_WINDOW_DAYS,
): List<SmsStatementPair> {
    val statement = moves.filter { it.fromStatement && !it.fromSms }
    val taken = mutableSetOf<Id>()
    val out = mutableListOf<SmsStatementPair>()
    val smsOnly = moves.filter { m ->
        val last = m.walletId?.let(lastStatementDay::get)
        m.fromSms && !m.fromStatement && last != null && m.date <= last
    }.sortedWith(compareBy({ it.date }, { it.transactionId }))
    for (sms in smsOnly) {
        val window = if (sms.wide && sms.direction == Direction.OUT) maxOf(windowDays, CROSS_SOURCE_WIDE_WINDOW_DAYS) else windowDays
        val match = statement.filter { s ->
            s.transactionId !in taken && s.walletId == sms.walletId && s.amountMinor == sms.amountMinor &&
                (s.direction == sms.direction || sms.ownTransfer) && abs(daysBetween(sms.date, s.date)) <= window
        }.minWithOrNull(compareBy({ if (it.direction == sms.direction) 0 else 1 }, { abs(daysBetween(sms.date, it.date)) }, { it.transactionId })) ?: continue
        taken += match.transactionId
        out += SmsStatementPair(sms.transactionId, match.transactionId, match.direction != sms.direction)
    }
    return out
}
