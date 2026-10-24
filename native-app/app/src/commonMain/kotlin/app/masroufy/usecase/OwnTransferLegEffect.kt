package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.MatchingState
import app.masroufy.core.R06Key
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.daysBetween
import app.masroufy.core.hashContent
import app.masroufy.core.shiftDays
import app.masroufy.core.smsLegsOf
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository
import kotlin.math.abs

/**
 * OVERRIDES §112-3 (قرار المالك 2026-10-23): التحويل لحسابك التاني والبنك التاني **مش بيبعت رسايل** ⇒ البرنامج بيسجّل دخول الفلوس في
 * الحساب التاني لوحده (**رِجل تلقائية** — معرّفها = معرّف الصادر + [OWN_LEG_SUFFIX]: «تلقائية» و«مربوطة بالصادر» من المعرّف نفسه، وبتتعدل
 * زي أي عملية). من غيرها «معك الآن» كان بيقل بمبلغ التحويل.
 * - **الصادر:** سطر رسالة بنك صادر نوعه «تحويل داخلي» مؤكد (أثر آخر 4 أرقام §75-11 أو قرار «حسابي التاني» §60) ولسه مالوش محفظة تانية
 *   ([Transaction.transferToWalletId] — السحب للكاش ليه طريقه) ⇒ الرِجل على المحفظة اللي آخر 4 أرقامها = الطرف (محفظة واحدة بالظبط).
 * - **ما بتتعملش** لو المحفظة التانية فيها داخل بنفس المبلغ في **يوم قبل أو بعد** ([ownLegAlreadyThere]) — رسالة بنكها سبقت.
 * - **الدمج:** رسالة البنك التاني لما توصل بعدين (داخل بنفس المبلغ ±يوم على المحفظة دي) ⇒ الرِجل التلقائية بتتشال ورسالته هي الرِجل
 *   (تحويل داخلي مؤكد) — عملية واحدة مش اتنين.
 * رسايل البنك بس — الكشف زي ما هو (المجاميع المرجعية ما بتتغيرش).
 * ⚠️ تراجع عن دفعة البنك التاني بعد الدمج ما بيرجّعش الرِجل التلقائية (HANDOVER).
 */
class OwnTransferLegEffect(private val wallets: WalletRepository, private val txns: TransactionRepository?) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        if (ctx.lines.none { it.sms != null }) return
        // r06-links2: تحويل يدوي بنفس المبلغ ±يوم ⇒ بيندمج في الرسالة (OwnTransferManualMerge.kt)
        txns?.let { mergeManualIntoSms(it, ctx) }
        mergeIncoming(ctx)
        addLegs(ctx)
    }

    private suspend fun mergeIncoming(ctx: RecordContext) {
        val repo = txns ?: return
        val walletId = ctx.request.walletId ?: return
        val taken = mutableSetOf<Id>()
        // r06-links2: الاتجاهين — الرِجل التلقائية ممكن تبقى صادرة (التحويل اليدوي اتسجل على رسالة الداخل الأول)
        val incoming = ctx.lines.filter { l -> l.sms != null && l.transaction.walletId == walletId && l.transaction.transferToWalletId == null }
        if (incoming.isEmpty()) return
        // fix053: استعلام **واحد** للدفعة كلها (كان استعلام فايربيز لكل رسالة واردة ⇒ 1,000 استعلام في «اقرأ اللي فات» على 3,000 رسالة)
        val autoLegs = rangeOf(repo, incoming.map { it.transaction.occurredAt.take(10) }).filter { it.id.endsWith(OWN_LEG_SUFFIX) }
        if (autoLegs.isEmpty()) return
        for (line in incoming) {
            val t = line.transaction
            val day = t.occurredAt.take(10)
            val auto = autoLegs.firstOrNull { o ->
                o.id.endsWith(OWN_LEG_SUFFIX) && o.id !in taken && o.walletId == walletId && o.observedDirection == t.observedDirection && o.id != t.id + OWN_LEG_SUFFIX &&
                    o.amountMinor == t.amountMinor && o.currency == t.currency && withinADay(o.occurredAt, day)
            } ?: continue
            taken += auto.id
            repo.deleteMany(listOf(auto.id))
            line.transaction = t.copy(
                economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }

    private suspend fun addLegs(ctx: RecordContext) {
        val outs = ctx.lines.filter { l ->
            val t = l.transaction
            l.sms != null && t.observedDirection == Direction.OUT && t.economicKind == EconomicKind.INTERNAL_TRANSFER && t.economicKindConfirmed &&
                t.transferToWalletId == null && t.walletId != null
        }
        if (outs.isEmpty()) return
        val all = wallets.listAll()
        // fix053: العمليات حوالين أيام الدفعة مرة واحدة (مش استعلام لكل تحويل)
        var around: List<Transaction>? = null
        for (line in outs) {
            val t = line.transaction
            val sms = line.sms ?: continue
            val digits = buildSet {
                transferPartyOf(t)?.last4?.let(::add)
                if (sms.kind == SmsKind.OWN_TRANSFER) smsLegsOf(sms.raw).toLast4?.let(::add)
            } - setOfNotNull(sms.ownLast4)
            val target = all.filter { w -> w.id != t.walletId && w.currency == t.currency && walletLast4(w.accountLast4) in digits }.singleOrNull() ?: continue
            val day = t.occurredAt.take(10)
            val read = around ?: txns?.let { rangeOf(it, outs.map { l -> l.transaction.occurredAt.take(10) }) }.orEmpty().also { around = it }
            val near = read.filter { withinADay(it.occurredAt, day) } + ctx.lines.map { it.transaction } + ctx.extra.map { it.first }
            if (ownLegAlreadyThere(near, t, target.id)) continue
            val leg = ownInLegOf(t, target, ctx.nowIso)
            ctx.extra += leg to SourceRecord(
                id = ctx.ids.next("src"),
                batchId = ctx.batchId,
                accountIdentity = ctx.request.accountIdentity,
                sourceReference = line.line.row.reference?.let { it + OWN_LEG_SUFFIX },
                sourceHash = hashContent(line.line.row.raw),
                originalRowIndex = line.line.row.lineNumber,
                rawLine = line.line.row.raw,
                transactionId = leg.id,
                matchingState = MatchingState.NEW,
                reason = uiText(R06Key.OWN_LEG_AUTO_REASON),
            )
        }
    }
}

/** رِجل [t] على محفظة [target]: الاتجاه العكسي، نفس المبلغ واليوم، «تحويل داخلي» مؤكد (OVERRIDES §91-5 · §112-3). */
internal fun ownInLegOf(t: Transaction, target: Wallet, now: String): Transaction = ownLegOn(t, target.id, now)

internal fun ownLegOn(t: Transaction, targetId: Id, now: String): Transaction = t.copy(
    id = t.id + OWN_LEG_SUFFIX, walletId = targetId, observedDirection = if (t.observedDirection == Direction.OUT) Direction.IN else Direction.OUT,
    economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, categoryId = null,
    categoryConfirmed = false, statedBalanceMinor = null, linkedExpenseId = null, linkedInflowMinor = null, supportAskPending = null,
    transferToWalletId = null, createdAt = now, updatedAt = now,
)

/**
 * الرِجل موجودة خلاص: نفس المعرّف، أو المحفظة [targetId] فيها عملية بالاتجاه العكسي ونفس المبلغ والعملة في **يوم قبل أو بعد**
 * (§112-3 — البنك التاني ممكن يسجّل الوارد تاني يوم).
 */
internal fun ownLegAlreadyThere(all: List<Transaction>, t: Transaction, targetId: Id): Boolean {
    val direction = if (t.observedDirection == Direction.OUT) Direction.IN else Direction.OUT
    val day = t.occurredAt.take(10)
    return all.any { o ->
        o.id == t.id + OWN_LEG_SUFFIX ||
            (o.walletId == targetId && o.observedDirection == direction && o.amountMinor == t.amountMinor && o.currency == t.currency && withinADay(o.occurredAt, day))
    }
}

private fun withinADay(at: String, day: String): Boolean = abs(daysBetween(day, at.take(10))) <= 1

/** fix053: العمليات من يوم قبل أول يوم في [days] لحد يوم بعد آخر يوم — قراية واحدة للدفعة. */
private suspend fun rangeOf(repo: TransactionRepository, days: List<String>): List<Transaction> =
    repo.listByDateRange(shiftDays(days.min(), -1), shiftDays(days.max(), 1))
