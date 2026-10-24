package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.R06Key
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.daysBetween
import app.masroufy.core.hashContent
import app.masroufy.core.shiftDays
import app.masroufy.core.uiText
import app.masroufy.port.TransactionRepository
import kotlin.math.abs

/**
 * r06-links2 (فجوة ١ — OVERRIDES §118-ج): التحويل لحسابك التاني **اليدوي** سطر واحد ([Transaction.transferToWalletId])، وبتاع رسايل البنك
 * رِجلين. من غير ده التحويل كان بيتحسب مرتين لما الاتنين يتسجلوا. القاعدة: نفس المبلغ والعملة · **يوم قبل أو بعد** · الرِجل على نفس
 * المحفظتين (صادر من محفظة اليدوي أو داخل على محفظته التانية) · الرِجل «تحويل داخلي» أو نوعها لسه مش متأكد.
 * - **اليدوي الأول ثم الرسالة** ([mergeManualIntoSms] — جوه أثر التسجيل): اليدوي بيتشال، والرسالة بتبقى رِجل مؤكدة، والرِجل التانية بتتعمل
 *   تلقائية (نفس [OWN_LEG_SUFFIX]) ⇒ رسالة البنك التاني لما توصل بتندمج فيها زي §112-3.
 * - **الرسالة الأول ثم اليدوي** ([manualOntoLegs] — جوه [AddTransaction]): مفيش سطر يدوي جديد؛ الرِجل الموجودة بتتأكد، والناقصة بتتعمل تلقائية.
 * المجموع على كل محفظة (و«معك الآن») واحد في الحالتين: ناقص المبلغ من الأولى وزايده على التانية مرة واحدة بس.
 */
const val MANUAL_SOURCE_ORDER = 9_000_000

/** سطر تحويل يدوي (من «+» أو المساعد) — اللي ممكن يندمج مع رِجل رسالة. */
internal fun isManualTransfer(t: Transaction): Boolean =
    t.transferToWalletId != null && t.economicKind == EconomicKind.INTERNAL_TRANSFER && t.sourceOrder == MANUAL_SOURCE_ORDER

/** [leg] (رِجل لسه مالهاش محفظة تانية) تبع التحويل من [from] لـ[to] بمبلغ [amount] حوالين [day]؟ */
internal fun legOfTransfer(leg: Transaction, from: Id, to: Id, amount: Halalas, currency: app.masroufy.core.Currency, day: String): Boolean {
    if (leg.transferToWalletId != null || leg.amountMinor != amount || leg.currency != currency) return false
    if (leg.economicKindConfirmed && leg.economicKind != EconomicKind.INTERNAL_TRANSFER) return false
    if (leg.linkedExpenseId != null || leg.linkedInflowMinor != null || leg.reversalOfId != null || leg.reversedById != null) return false
    if (abs(daysBetween(day, leg.occurredAt.take(10))) > 1) return false
    return (leg.walletId == from && leg.observedDirection == Direction.OUT) || (leg.walletId == to && leg.observedDirection == Direction.IN)
}

private fun confirmedLeg(t: Transaction, now: String) =
    t.copy(economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, categoryId = null, updatedAt = now)

/** اليدوي الأول: كل سطر رسالة بيطابق تحويل يدوي ⇒ اليدوي بيتشال والرِجل التانية بتتعمل تلقائية مع الدفعة. */
internal suspend fun mergeManualIntoSms(repo: TransactionRepository, ctx: RecordContext) {
    val smsLines = ctx.lines.filter { it.sms != null && it.transaction.walletId != null }
    if (smsLines.isEmpty()) return
    val days = smsLines.map { it.transaction.occurredAt.take(10) }
    val manual = repo.listByDateRange(shiftDays(days.min(), -1), shiftDays(days.max(), 1)).filter(::isManualTransfer).toMutableList()
    if (manual.isEmpty()) return
    for (line in smsLines) {
        val t = line.transaction
        val m = manual.firstOrNull { legOfTransfer(t, it.walletId!!, it.transferToWalletId!!, it.amountMinor, it.currency, it.occurredAt.take(10)) } ?: continue
        manual -= m
        repo.deleteMany(listOf(m.id))
        val merged = confirmedLeg(t, ctx.nowIso).copy(note = t.note ?: m.note)
        line.transaction = merged
        val otherWallet = if (t.observedDirection == Direction.OUT) m.transferToWalletId!! else m.walletId!!
        ctx.extra += ownLegOn(merged, otherWallet, ctx.nowIso) to SourceRecord(
            id = ctx.ids.next("src"), batchId = ctx.batchId, accountIdentity = ctx.request.accountIdentity,
            sourceReference = line.line.row.reference?.let { it + OWN_LEG_SUFFIX }, sourceHash = hashContent(line.line.row.raw),
            originalRowIndex = line.line.row.lineNumber, rawLine = line.line.row.raw, transactionId = merged.id + OWN_LEG_SUFFIX,
            matchingState = MatchingState.NEW, reason = uiText(R06Key.OWN_LEG_AUTO_REASON),
        )
    }
}

/**
 * الرسالة الأول: التحويل اليدوي [manual] (لسه ما اتحفظش) له رِجل أو رِجلين من رسايل؟ ⇒ بيتأكدوا والناقصة بتتعمل تلقائية، ويرجع الرِجل
 * الصادرة (أو الداخلة لو هي بس). مفيش ⇒ null والسطر اليدوي بيتحفظ عادي.
 */
internal suspend fun manualOntoLegs(repo: TransactionRepository, manual: Transaction, now: String): Transaction? {
    val from = manual.walletId ?: return null
    val to = manual.transferToWalletId ?: return null
    val day = manual.occurredAt.take(10)
    val near = repo.listByDateRange(shiftDays(day, -1), shiftDays(day, 1)).filter { legOfTransfer(it, from, to, manual.amountMinor, manual.currency, day) }
    val out = near.firstOrNull { it.walletId == from }
    val inc = near.firstOrNull { it.walletId == to }
    if (out == null && inc == null) return null
    val legs = listOfNotNull(out, inc).map { confirmedLeg(it, now).copy(note = it.note ?: manual.note) }
    val missing = when {
        out == null -> ownLegOn(legs.single(), from, now)
        inc == null -> ownLegOn(legs.single(), to, now)
        else -> null
    }
    repo.saveMany(legs + listOfNotNull(missing))
    return legs.first()
}
