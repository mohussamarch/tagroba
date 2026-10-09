package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.transferPartyOf
import app.masroufy.port.WalletRepository

/**
 * **§75-11 «… وكمان معرفة «حسابي التاني» منها»** (قرار المالك 2026-10-08): تحويل في **رسالة بنك** طرفه التاني آخر 4 أرقامه = آخر 4 أرقام
 * محفظة **تانية** من محافظ المالك في نفس البلد ⇒ **تحويل داخلي** مؤكد وهو بيتسجل (زي قرار «حسابي التاني» في زون التحويلات §60 — من
 * غير سؤال). الطرف = `transferPartyOf` (الاسم + آخر 4)، ولـ«بين حساباتك» ([SmsKind.OWN_TRANSFER]) كمان أي رقم متقص في الرسالة غير
 * رقم حسابك (`SmsRow.ownLast4`). رسايل البنك بس (الكشف زي ما هو)، والعملية اللي نوعها اتأكد قبل كده (قرار من الزون) ما بتتلمسش.
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
            val candidates = buildSet {
                transferPartyOf(t)?.last4?.let(::add)
                if (sms.kind == SmsKind.OWN_TRANSFER) MASKED_ACCOUNT.findAll(sms.raw).forEach { add(it.groupValues[1]) }
            } - setOfNotNull(sms.ownLast4)
            if (candidates.none { it in own }) continue
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
