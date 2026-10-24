package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.MAX_SAFE_HALALAS
import app.masroufy.core.R06Key
import app.masroufy.core.Settlement
import app.masroufy.core.TextKey
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import app.masroufy.port.WalletRepository

/**
 * سطر سداد في صفحة الدين. [day] و[walletId] من عملية السداد (null ⇒ سداد من غير عملية). [movable] = العملية يدوية ⇒ تاريخها وحسابها
 * بيتعدلوا من هنا؛ عملية من البنك تاريخها وحسابها من البنك. [fx] = سداد بعملة تانية (مبلغه من السعر ⇒ ما بيتعدلش).
 */
data class SettlementRow(
    val settlementId: Id,
    val obligationId: Id,
    val transactionId: Id?,
    val amountMinor: Halalas,
    val currency: Currency,
    val day: IsoDate?,
    val walletId: Id?,
    val walletName: String,
    val movable: Boolean,
    val fx: Boolean,
)

class SettlementEditError(message: String) : IllegalArgumentException(message)

class ManageSettlementsDeps(
    val settlements: SettlementRepository,
    val obligations: ObligationRepository,
    val txns: TransactionRepository,
    val wallets: WalletRepository,
    val uow: UnitOfWork,
    val clock: Clock,
)

/**
 * r06-links2 (فجوة ٥): «عدّل السداد» في صفحة الدين — المبلغ (من غير ما يعدّي أصل الدين ولا مبلغ العملية) والتاريخ والحساب (للعملية
 * اليدوية بس). «الغِه» = لوحة المسح الواحدة ([RemoveSettlement]) بتراجع. المبالغ هللات صحيحة.
 */
class ManageSettlements(private val d: ManageSettlementsDeps) {
    suspend fun list(obligationId: Id, personId: Id): List<SettlementRow> {
        val o = d.obligations.listByPerson(personId).firstOrNull { it.id == obligationId } ?: return emptyList()
        val list = d.settlements.listByObligations(listOf(obligationId))
        val txns = d.txns.findByIds(list.map { it.transactionId }.filter { it.isNotEmpty() }).associateBy { it.id }
        val wallets = d.wallets.listAll().associateBy { it.id }
        return list.map { s ->
            val t = txns[s.transactionId]
            SettlementRow(
                s.id, obligationId, t?.id, s.amountMinor, o.currency, t?.occurredAt?.take(10), t?.walletId,
                t?.walletId?.let { wallets[it]?.name }.orEmpty(), t?.sourceOrder == MANUAL_SOURCE_ORDER, s.fx != null,
            )
        }.sortedBy { it.day ?: "" }
    }

    suspend fun edit(obligationId: Id, personId: Id, settlementId: Id, amountMinor: Halalas, day: IsoDate?, walletId: Id?): Settlement {
        val o = d.obligations.listByPerson(personId).firstOrNull { it.id == obligationId } ?: throw SettlementEditError(uiText(R06Key.REMOVE_GONE))
        val all = d.settlements.listByObligations(listOf(obligationId))
        val s = all.firstOrNull { it.id == settlementId } ?: throw SettlementEditError(uiText(R06Key.REMOVE_GONE))
        if (s.fx != null && amountMinor != s.amountMinor) throw SettlementEditError(uiText(R06Key.L2_ST_FX_LOCKED))
        if (amountMinor <= 0 || amountMinor > MAX_SAFE_HALALAS) throw SettlementEditError(uiText(TextKey.AMOUNT_POSITIVE))
        val others = all.filter { it.id != settlementId }.sumOf { it.amountMinor }
        if (others + amountMinor > o.originalMinor) throw SettlementEditError(uiText(R06Key.L2_ST_OVER))
        val t = s.transactionId.takeIf { it.isNotEmpty() }?.let { d.txns.findByIds(listOf(it)).firstOrNull() }
        if (t != null && amountMinor > t.amountMinor) throw SettlementEditError(uiText(R06Key.L2_ST_OVER_OP))
        val moved = t != null && ((day != null && day != t.occurredAt.take(10)) || (walletId != null && walletId != t.walletId))
        if (moved && t!!.sourceOrder != MANUAL_SOURCE_ORDER) throw SettlementEditError(uiText(R06Key.L2_ST_BANK_LOCKED))
        if (day != null && !isValidIsoDate(day)) throw SettlementEditError(uiText(TextKey.TXN_DATE_INVALID))
        if (walletId != null && moved) {
            val w = d.wallets.findById(walletId) ?: throw SettlementEditError(uiText(TextKey.TXN_WALLET_REQUIRED))
            if (w.currency != t!!.currency) throw SettlementEditError(uiText(TextKey.TXN_TRANSFER_CURRENCY))
        }
        val updated = s.copy(amountMinor = amountMinor)
        d.uow.run {
            if (updated != s) d.settlements.saveMany(listOf(updated))
            if (moved) d.txns.saveMany(listOf(t!!.copy(occurredAt = day ?: t.occurredAt, walletId = walletId ?: t.walletId, updatedAt = d.clock.nowIso())))
        }
        return updated
    }
}
