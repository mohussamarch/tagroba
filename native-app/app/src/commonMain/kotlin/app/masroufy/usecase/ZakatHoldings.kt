package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatHolding
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatMetal
import app.masroufy.core.computePosition
import app.masroufy.core.remainingOfObligation
import app.masroufy.core.roscaOwnMoneyReceipts
import app.masroufy.core.roscaStatus
import app.masroufy.core.walletBalancesOn

/** اللي بتملكه يوم الحساب + المحافظ والعمليات (للحول). */
internal data class GatheredHoldings(val holdings: List<ZakatHolding>, val wallets: List<Wallet>, val transactions: List<Transaction>)

/**
 * بيجمع «اللي بتملكه» من بياناتك نفسها يوم [asOf] (OVERRIDES §62): أرصدة المحافظ · الأصول بالكمية والسعر · الديون ليك وعليك ·
 * الأمانات · الجمعيات. **بعملة الحساب بس** — المساحة لكل بلد (§41)، والمبالغ ما بتتجمعش بين عملتين. الوقائع من `zakatFacts`.
 * [collectedAfter] بداية السنة: كل تسوية على دين ليك عمليتها **بعدها لحد [asOf]** بتطلع «دين اتحصّل» (§62 — 4399 و§3.4)؛
 * null = من غير (اقتراح الميعاد). الحد ده بيخلّي التحصيل يتعد في سنة واحدة بس: السنة اللي قبلها بتقف عند يوم ميعادها.
 */
internal class ZakatHoldingsReader(private val deps: ManageZakatDeps) {
    suspend fun read(asOf: IsoDate, collectedAfter: IsoDate? = null): GatheredHoldings {
        val currency: Currency = deps.currency
        val facts: Map<Id, ZakatFact> = deps.facts.listAll().associateBy { it.subjectId }
        val wallets = deps.wallets.listAll().filter { it.currency == currency }
        val from = wallets.minOfOrNull { it.openingAt }
        val txns = if (from == null || from > asOf) emptyList() else deps.txns.listByDateRange(from, asOf).filter { it.currency == currency }
        val out = mutableListOf<ZakatHolding>()

        val balances = walletBalancesOn(wallets, txns, asOf)
        for (w in wallets) out += ZakatHolding.Cash(w.id, w.name, balances[w.id], w.openingAt)

        val lots = deps.lots.listAll().filter { it.purchasedAt <= asOf }.groupBy { it.assetId }
        val sales = deps.sales.listAll().filter { it.soldAt <= asOf }.groupBy { it.assetId }
        val prices = deps.prices.listAll().associateBy { it.assetId }
        for (a in deps.assets.listAll()) {
            if (a.archived || a.currency != currency) continue
            val mine = lots[a.id].orEmpty()
            val position = computePosition(a.id, mine, sales[a.id].orEmpty(), prices[a.id], asOf)
            if (position.heldQuantity <= 0) continue
            val since = mine.minOfOrNull { it.purchasedAt }
            val fact = facts[a.id]
            out += when (a.kind) {
                "gold", "silver" -> ZakatHolding.Metal(
                    a.id, a.name, if (a.kind == "gold") ZakatMetal.GOLD else ZakatMetal.SILVER, position.heldQuantity,
                    fact?.karat, fact?.fineness, fact?.purpose, prices[a.id]?.pricePerUnitMinor, since,
                )
                "stock", "fund" -> ZakatHolding.Security(
                    a.id, a.name, if (a.kind == "stock") ZakatLineKind.STOCKS else ZakatLineKind.FUNDS, position.marketValueMinor, fact?.holding, since,
                    fact?.saudiCompany,
                )
                "digital" -> ZakatHolding.Digital(a.id, a.name, position.marketValueMinor)
                else -> ZakatHolding.Other(a.id, a.name, position.marketValueMinor)
            }
        }

        for (person in deps.people.listAll()) {
            val obligations = deps.obligations.listByPerson(person.id).filter { it.currency == currency }
            if (obligations.isEmpty()) continue
            val settled = deps.settlements.listByObligations(obligations.map { it.id })
            val dates = deps.txns.findByIds((obligations.mapNotNull { it.originTransactionId } + settled.map { it.transactionId }).distinct())
                .associate { it.id to it.occurredAt }
            for (o in obligations) {
                if (collectedAfter != null && o.kind == ObligationKind.RECEIVABLE) {
                    for (s in settled.filter { it.obligationId == o.id }) {
                        val on = dates[s.transactionId] ?: continue
                        if (on > collectedAfter && on <= asOf) out += ZakatHolding.CollectedReceivable(s.id, o.id, person.name, s.amountMinor, on, facts[o.id]?.collectability)
                    }
                }
                val remaining = remainingOfObligation(o, settled)
                if (remaining <= 0) continue
                when (o.kind) {
                    ObligationKind.RECEIVABLE -> out += ZakatHolding.Receivable(o.id, person.name, remaining, facts[o.id]?.collectability, o.originTransactionId?.let(dates::get))
                    ObligationKind.LOAN_PAYABLE -> out += ZakatHolding.Debt(o.id, person.name, remaining)
                    // الأمانة: المرجع ما حددش في البلدين (§62) ⇒ سطر لوحده «ما حددش»، ومش بتتخصم من الكاش
                    ObligationKind.CUSTODY_PAYABLE -> out += ZakatHolding.Custody(o.id, person.name, remaining)
                }
            }
        }

        for (r in deps.roscas.listAll()) {
            if (r.currency != currency) continue
            val entries = deps.roscaEntries.listByRosca(r.id)
            val position = roscaStatus(r, entries, asOf).positionMinor
            if (position > 0) out += ZakatHolding.RoscaCredit(r.id, r.name, position, r.firstDueAt)
            if (position < 0) out += ZakatHolding.Debt(r.id, r.name, -position)
            // القبض جوه السنة: الجزء اللي كان من فلوسك (مصر — زي الدين اللي اتحصّل، 4399). حركة من غير عملية معروفة ⇒ مالهاش تاريخ ⇒ بتتساب
            if (collectedAfter != null) {
                val dates = deps.txns.findByIds(entries.map { it.transactionId }.distinct()).associate { it.id to it.occurredAt }
                val dated = entries.mapNotNull { e -> dates[e.transactionId]?.let { e to it } }
                for (receipt in roscaOwnMoneyReceipts(dated)) {
                    if (receipt.date > collectedAfter && receipt.date <= asOf) {
                        out += ZakatHolding.CollectedRosca(receipt.entryId, r.id, r.name, receipt.ownMinor, receipt.date)
                    }
                }
            }
        }
        return GatheredHoldings(out, wallets, txns)
    }
}
