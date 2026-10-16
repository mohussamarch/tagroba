package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.PersonDebtLine
import app.masroufy.core.PersonEntry
import app.masroufy.core.PersonEntryType
import app.masroufy.core.PersonLedger
import app.masroufy.core.UiKey
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.screens.operations.dayLabel
import app.masroufy.ui.screens.operations.meaningLabel
import app.masroufy.ui.text.t

/**
 * «السجل بينا» في صفحة الشخص (جولة r05) من `ManagePersonLinks.page.load` — كلام وترتيب بس، المبالغ والصافي جايين جاهزين من `personLedger`.
 * السطر بيفتح العملية (لو ليه عملية) أو الدين (دين قديم أو سداد بإيدك).
 */
internal data class LedgerRowUi(
    val key: String,
    val title: String,
    val sub: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val tone: AmountTone,
    val transactionId: Id?,
    val obligationId: Id?,
)

internal data class LedgerTotalsUi(val currency: Currency, val lakMinor: Halalas, val alekMinor: Halalas, val netMinor: Halalas)

internal data class DebtRowUi(val obligationId: Id, val title: String, val sub: String, val amountMinor: Halalas, val currency: Currency, val tone: AmountTone)

internal data class LedgerUi(
    val rows: List<LedgerRowUi>,
    val totals: List<LedgerTotalsUi>,
    val open: List<DebtRowUi>,
    val settled: List<DebtRowUi>,
    /** مفيش ولا حاجة بينكم ولا حساب مربوط ⇒ سطر يساعد بدل الصفحة الفاضية. */
    val empty: Boolean,
)

internal fun ledgerUi(l: PersonLedger, accounts: Int, today: IsoDate): LedgerUi = LedgerUi(
    rows = l.entries.mapIndexed { i, e -> rowOf(e, i, today) },
    totals = l.totals.map { LedgerTotalsUi(it.currency, it.owedToYouMinor, it.youOweMinor, it.netMinor) },
    open = l.open.map { debtRow(it, today, open = true) },
    settled = l.settled.map { debtRow(it, today, open = false) },
    empty = l.isEmpty && accounts == 0,
)

private fun debtSide(kind: ObligationKind?, lak: UiKey, alek: UiKey): String = t(if (kind == ObligationKind.RECEIVABLE) lak else alek)

internal fun entryTitle(e: PersonEntry): String = when (e.type) {
    PersonEntryType.LINK -> e.meaning?.let(::meaningLabel).orEmpty()
    PersonEntryType.DEBT -> debtSide(e.obligationKind, UiKey.PLINK_E_DEBT_LAK, UiKey.PLINK_E_DEBT_ALEK)
    PersonEntryType.OPENING_DEBT -> joinLine(t(UiKey.PLINK_E_OPENING), debtSide(e.obligationKind, UiKey.PLINK_DEBT_LAK, UiKey.PLINK_DEBT_ALEK))
    PersonEntryType.SETTLEMENT -> debtSide(e.obligationKind, UiKey.PLINK_E_SETTLEMENT_IN, UiKey.PLINK_E_SETTLEMENT_OUT)
    PersonEntryType.MANUAL_SETTLEMENT -> t(UiKey.PLINK_E_MANUAL_SETTLEMENT)
    PersonEntryType.ACCOUNT_TRANSFER -> t(if (e.direction == Direction.IN) UiKey.PLINK_E_TRANSFER_IN else UiKey.PLINK_E_TRANSFER_OUT)
}

private fun rowOf(e: PersonEntry, i: Int, today: IsoDate): LedgerRowUi {
    val day = if (e.date.isEmpty()) t(UiKey.PLINK_E_NO_DATE) else dayLabel(e.date, today)
    val net = t(UiKey.PLINK_NET_AFTER, amountLabel(e.netAfterMinor, e.currency))
    val sub = joinLine(day, e.title, net, if (e.auto) t(UiKey.PLINK_AUTO_BADGE) else null)
    val tone = when (e.direction) {
        Direction.IN -> AmountTone.INCOME
        Direction.OUT -> AmountTone.EXPENSE
        null -> AmountTone.PLAIN
    }
    return LedgerRowUi("${e.type}-${e.transactionId ?: e.obligationId}-$i", entryTitle(e), sub, e.amountMinor, e.currency, tone, e.transactionId, e.obligationId)
}

private fun debtRow(d: PersonDebtLine, today: IsoDate, open: Boolean): DebtRowUi {
    val o = d.obligation
    val title = debtSide(o.kind, UiKey.PLINK_DEBT_LAK, UiKey.PLINK_DEBT_ALEK)
    val state = if (open) t(UiKey.PLINK_DEBT_REMAINING, amountLabel(d.remainingMinor, o.currency), amountLabel(o.originalMinor, o.currency))
    else t(UiKey.PLINK_DEBT_DONE, amountLabel(o.originalMinor, o.currency))
    val day = if (d.date.isEmpty()) t(UiKey.PLINK_E_OPENING) else dayLabel(d.date, today)
    val tone = if (o.kind == ObligationKind.RECEIVABLE) AmountTone.INCOME else AmountTone.EXPENSE
    return DebtRowUi(o.id, title, joinLine(day, state), if (open) d.remainingMinor else o.originalMinor, o.currency, tone)
}
