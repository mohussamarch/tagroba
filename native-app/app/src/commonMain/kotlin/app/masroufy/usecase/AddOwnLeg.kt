package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.shiftDays
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository

/**
 * OVERRIDES §120-5 — «اتأكد إن في طريقة أقدر أعمل الإجراء ده بنفسي لو ما اتعملش — خليه سهل أوي»: فلوس **دخلت** من حسابك التاني والرِجل
 * الخارجة منه ما اتعملتش لوحدها (الرسالة مفيهاش أرقام أو الأرقام مش مكتوبة على المحفظة) ⇒ من تفاصيل العملية «اخصمها من حسابي التاني» ⇒
 * تختار المحفظة ⇒ رِجل خارجة منها بنفس المبلغ واليوم (معرّفها = معرّف الداخل + [OWN_LEG_SUFFIX] — نفس رِجل §112-3، فرسالة الخصم لو وصلت
 * بعدين بتندمج فيها: `OwnTransferLegEffect.mergeIncoming`)، والداخل نفسه بيبقى «تحويل داخلي» مؤكد.
 * ما بتتعملش مرتين: الرِجل موجودة أو المحفظة فيها خارج بنفس المبلغ ±يوم ([ownLegAlreadyThere]) ⇒ [choices] فاضية و[add] بيرفض.
 */
class AddOwnLeg(
    private val txns: TransactionRepository,
    private val wallets: WalletRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    /** المحافظ اللي ينفع الفلوس تكون جت منها (مش كاش · نفس العملة · غير محفظة العملية). فاضية ⇒ الفعل ما بيظهرش. */
    suspend fun choices(transactionId: Id): List<Wallet> {
        val t = txns.findByIds(listOf(transactionId)).firstOrNull() ?: return emptyList()
        if (!eligible(t)) return emptyList()
        val near = around(t)
        if (near.any { it.id == t.id + OWN_LEG_SUFFIX }) return emptyList()
        val list = wallets.listAll().filter { w -> w.kind != "cash" && w.id != t.walletId && w.currency == t.currency }
        // تحويل داخلي رجله التانية موجودة خلاص (رسالة البنك التاني اندمجت) ⇒ مفيش فعل
        if (t.economicKind == EconomicKind.INTERNAL_TRANSFER && list.any { ownLegAlreadyThere(near, t, it.id) }) return emptyList()
        return list
    }

    /** الرِجل الخارجة من [walletId]. بيرجّع معرّفها. */
    suspend fun add(transactionId: Id, walletId: Id): Id {
        val t = txns.findByIds(listOf(transactionId)).firstOrNull()?.takeIf(::eligible) ?: throw TransferZoneError(uiText(TextKey.NOT_AVAILABLE))
        val target = wallets.listAll().firstOrNull { it.id == walletId && it.id != t.walletId && it.currency == t.currency }
            ?: throw TransferZoneError(uiText(TextKey.NOT_AVAILABLE))
        if (ownLegAlreadyThere(around(t), t, target.id)) throw TransferZoneError(uiText(TextKey.NOT_AVAILABLE))
        val now = clock.nowIso()
        val leg = ownLegOn(t, target.id, now)
        val base = t.copy(economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = now)
        uow.run { txns.saveMany(listOf(base, leg)) }
        return leg.id
    }

    private fun eligible(t: app.masroufy.core.Transaction): Boolean =
        t.observedDirection == Direction.IN && t.walletId != null && t.transferToWalletId == null && !t.id.endsWith(OWN_LEG_SUFFIX)

    private suspend fun around(t: app.masroufy.core.Transaction) =
        txns.listByDateRange(shiftDays(t.occurredAt.take(10), -1), shiftDays(t.occurredAt.take(10), 1))
}
