package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.EconomicKind
import app.masroufy.core.IsoDate
import app.masroufy.core.PendingAsk
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.Transaction
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.transferPartyOf
import app.masroufy.port.AskSource
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransferPartyRepository
import app.masroufy.port.WalletRepository

/**
 * **§75-11 «… وكمان معرفة «حسابي التاني» منها»** (قرار المالك 2026-10-08): تحويل في **رسالة بنك** طرفه التاني آخر 4 أرقامه = آخر 4 أرقام
 * محفظة **تانية** من محافظ المالك في نفس البلد ⇒ **تحويل داخلي** مؤكد وهو بيتسجل (زي قرار «حسابي التاني» في زون التحويلات §60 — من
 * غير سؤال). الطرف = `transferPartyOf` (الاسم + آخر 4)، ولـ«بين حساباتك» ([SmsKind.OWN_TRANSFER]) كمان أي رقم متقص في الرسالة غير
 * رقم حسابك (`SmsRow.ownLast4`). رسايل البنك بس (الكشف زي ما هو)، والعملية اللي نوعها اتأكد قبل كده (قرار من الزون) ما بتتلمسش.
 * **مراجعة S1:** التأكيد لوحده بس لما الطرف **أرقام بس** أو الرسالة نفسها بتقول «بين حساباتك». طرف **باسم** («إلى: SAMPLE PERSON» +
 * آخر 4) ممكن يبقى شخص تاني آخر أرقام حسابه زي حسابك بالصدفة — كان المصروف بيختفي من الصرف ومحفظتك التانية ما بيوصلهاش حاجة. دلوقتي
 * العملية بتفضل زي ما هي (مش متصنفة) وبيتسأل مرة «ده حسابك التاني؟» ([OwnAccountAskSource])، والرد قرار الزون (§60) بيتحفظ للطرف.
 */
class OwnAccountByLast4Effect(private val wallets: WalletRepository) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        if (ctx.lines.none { it.sms != null }) return
        val own = wallets.listAll().filter { it.id != ctx.request.walletId }.mapNotNull { walletLast4(it.accountLast4) }.toSet()
        if (own.isEmpty()) return
        for (line in ctx.lines) {
            val sms = line.sms ?: continue
            val t = line.transaction
            if (t.economicKindConfirmed) continue
            val party = transferPartyOf(t)
            val betweenYourAccounts = sms.kind == SmsKind.OWN_TRANSFER
            val candidates = buildSet {
                party?.last4?.let(::add)
                if (betweenYourAccounts) MASKED_ACCOUNT.findAll(sms.raw).forEach { add(it.groupValues[1]) }
            } - setOfNotNull(sms.ownLast4)
            if (candidates.none { it in own }) continue
            if (!betweenYourAccounts && party != null && party.named) continue // بيتسأل مرة (`OwnAccountAskSource`)
            line.transaction = t.copy(
                economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = ctx.nowIso,
            )
        }
    }

    private companion object {
        /** رقم حساب متقص («**2222» · «••••2222» · «xx2222») — مش مبلغ ولا تاريخ. */
        val MASKED_ACCOUNT = Regex("[*•xX#]+[ \\t]?\\d*?(\\d{4})(?!\\d)")
    }
}

/** الطرف فيه اسم (مش آخر 4 أرقام بس — مفتاح الطرف اللي أرقام بس بيبدأ بـ«#»). */
private val TransferPartyRef.named: Boolean get() = !key.startsWith("#")

/**
 * مراجعة S1 — سؤال «ده حسابك التاني؟» ([AskKind.OWN_ACCOUNT_CHECK]) لعدّ «محتاجة تأكيد» والتذكير الأسبوعي (§75-15): عملية من **رسالة
 * بنك** (مرجعها `SMS:`) لسه نوعها ما اتأكدش، طرفها **باسم** وآخر 4 أرقامه = آخر 4 أرقام محفظة تانية من محافظك في البلد، والطرف لسه مالوش
 * قرار في الزون. **سؤال واحد لكل طرف** (على آخر عملية معاه في النافذة). الرد: `ManageTransfers.markOwnAccount` (ويصلّح القديم) ·
 * `markPerson` · `dismiss` ⇒ ما بيتسألش تاني. مفيش إشعار جديد.
 */
class OwnAccountAskSource(
    private val spaceId: String,
    private val txns: TransactionRepository,
    private val sources: SourceRecordRepository,
    private val wallets: WalletRepository,
    private val parties: TransferPartyRepository,
) : AskSource {
    override suspend fun pending(from: IsoDate, to: IsoDate): List<PendingAsk> {
        // محفظة واحدة بأرقام تكفي (زي الأثر): التحويل ممكن يبقى من محفظة مكتوبش أرقامها لطرف آخره زي المحفظة دي
        val digits = wallets.listAll().mapNotNull { w -> walletLast4(w.accountLast4)?.let { w.id to it } }
        if (digits.isEmpty()) return emptyList()
        val decided = parties.listAll().map { it.key }.toSet()
        val candidates = txns.listByDateRange(from, to).mapNotNull { t ->
            if (t.economicKindConfirmed) return@mapNotNull null
            val party = transferPartyOf(t)?.takeIf { it.named && it.key !in decided } ?: return@mapNotNull null
            val last4 = party.last4 ?: return@mapNotNull null
            if (digits.none { it.first != t.walletId && it.second == last4 }) null else party to t
        }
        if (candidates.isEmpty()) return emptyList()
        val fromSms = sources.listByTransactionIds(candidates.map { it.second.id })
            .filter { it.sourceReference?.startsWith("SMS:") == true }.mapNotNull { it.transactionId }.toSet()
        val latest = LinkedHashMap<String, Transaction>()
        for ((party, t) in candidates) {
            if (t.id !in fromSms) continue
            val before = latest[party.key]
            if (before == null || t.occurredAt >= before.occurredAt) latest[party.key] = t
        }
        return latest.values.sortedBy { it.occurredAt }.map { PendingAsk(AskKind.OWN_ACCOUNT_CHECK, spaceId, transactionId = it.id, date = it.occurredAt) }
    }
}
