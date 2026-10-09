package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.DuesTotals
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.normalizeText
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.PersonRow

/**
 * شاشة «الديون» (`DuesDebts`) و«تفاصيل الدين»: من `ManagePeople.listWithBalances` (المتبقي لكل التزام) + `LoadDues` (المجاميع
 * `DuesTotals` ومواعيد الديون `DueItem`) لحالة الشاشة. **مفيش جمع هنا**: مجموع «لك» = `receivableMinor` ومجموع «عليك» = `payableLoanMinor`
 * والأمانات لوحدها `payableCustodyMinor` (من غير مقاصة — spec/02). مع البحث مجموع المجموعة بيختفي (مفيش حالة استخدام بتجمع نتيجة البحث).
 */
data class DebtRowUi(
    val obligationId: String,
    val personId: String,
    val name: String,
    val initial: String,
    val reason: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val dueAt: IsoDate?,
    val dueText: String,
    val chip: Chip,
    /** null = مفيش إشارة (الدين من غير موعد — «منتظر منذ» مش متاح في حالة الاستخدام). */
    val chipText: String?,
    val forYou: Boolean,
    val kind: ObligationKind,
    val aria: String,
)

data class DebtGroupUi(val key: String, val title: String, val totalMinor: Halalas?, val order: String, val forYou: Boolean, val rows: List<DebtRowUi>)

data class DebtSideUi(val side: DebtSide, val label: String, val count: String, val totalMinor: Halalas, val hint: String)

data class DuesDebtsUi(
    val currency: Currency,
    val sides: List<DebtSideUi>,
    val groups: List<DebtGroupUi>,
    /** مفيش ولا دين مفتوح خالص (قبل البحث). */
    val empty: Boolean,
    val noHitsTitle: String?,
    val noHitsBody: String?,
)

/** أقرب ميعاد لسه ما اتدفعش لكل التزام (من `LoadDues.dueItems` — المتأخر الأول). */
internal fun debtDueMap(items: List<DueItem>): Map<String, DueItem> =
    items.filter { it.source == DueSource.DEBT }.groupBy { it.sourceId }.mapValues { (_, v) -> v.minBy { it.dueAt } }

internal fun reasonOf(o: Obligation): String = when {
    o.originTransactionId == null -> t(TextKey.DEBTS_REASON_OPENING)
    o.kind == ObligationKind.RECEIVABLE -> t(TextKey.DEBTS_REASON_RECEIVABLE)
    o.kind == ObligationKind.LOAN_PAYABLE -> t(TextKey.DEBTS_REASON_LOAN)
    else -> t(TextKey.DEBTS_REASON_CUSTODY)
}

/** إشارة الدين من ميعاده: فات موعدها ⇒ أحمر · قرّب (٣ أيام) ⇒ كهرماني · بعدين ⇒ هادي. */
internal fun debtSignal(due: DueItem?, today: IsoDate): Pair<Chip, String?> {
    if (due == null) return Chip.CALM to null
    val days = daysBetween(today, due.dueAt)
    return when (due.status) {
        DueStatus.OVERDUE -> Chip.OVERDUE to t(TextKey.DEBTS_SIG_OVERDUE, agoText(-days))
        DueStatus.SOON -> Chip.SOON to t(TextKey.DEBTS_SIG_SOON, afterText(days))
        DueStatus.UPCOMING -> Chip.CALM to afterText(days)
    }
}

internal fun debtRows(people: List<PersonRow>, dues: Map<String, DueItem>, today: IsoDate): List<DebtRowUi> = people.flatMap { p ->
    p.obligations.map { row ->
        val o = row.obligation
        val due = dues[o.id]
        val (chip, chipText) = debtSignal(due, today)
        val forYou = o.kind == ObligationKind.RECEIVABLE
        val amount = amountLabel(row.remainingMinor, o.currency)
        DebtRowUi(
            obligationId = o.id,
            personId = p.person.id,
            name = p.person.name,
            initial = initialOf(p.person.name),
            reason = reasonOf(o),
            amountMinor = row.remainingMinor,
            currency = o.currency,
            dueAt = due?.dueAt,
            dueText = due?.let { t(TextKey.DEBTS_DUE_ON, dateText(it.dueAt, today)) } ?: t(TextKey.DEBTS_NO_DUE),
            chip = chip,
            chipText = chipText,
            forYou = forYou,
            kind = o.kind,
            aria = t(if (forYou) TextKey.DEBTS_ARIA_FOR else TextKey.DEBTS_ARIA_ON, p.person.name, amount),
        )
    }
}

private fun statusRank(r: DebtRowUi) = when (r.chip) {
    Chip.OVERDUE -> 0
    Chip.SOON -> 1
    else -> if (r.dueAt != null) 2 else 3
}

fun duesDebtsUi(people: List<PersonRow>, totals: DuesTotals, dueItems: List<DueItem>, today: IsoDate, currency: Currency, side: DebtSide, query: String): DuesDebtsUi {
    val all = debtRows(people, debtDueMap(dueItems), today)
    val q = normalizeText(query.trim())
    val hits = if (q.isEmpty()) all else all.filter { normalizeText(it.name).contains(q) }
    val searching = q.isNotEmpty()
    val forYou = hits.filter { it.forYou }.sortedWith(compareBy<DebtRowUi>({ statusRank(it) }, { it.dueAt ?: "9999" }))
    val loans = hits.filter { it.kind == ObligationKind.LOAN_PAYABLE }.sortedBy { it.dueAt ?: "9999" }
    val custody = hits.filter { it.kind == ObligationKind.CUSTODY_PAYABLE }.sortedBy { it.dueAt ?: "9999" }
    val groups = buildList {
        if (side != DebtSide.ON_YOU && forYou.isNotEmpty()) {
            add(DebtGroupUi("for", t(TextKey.DUES_FOR_YOU), totals.receivableMinor.takeUnless { searching }, t(TextKey.DEBTS_ORDER_FOR_YOU), true, forYou))
        }
        if (side != DebtSide.FOR_YOU && loans.isNotEmpty()) {
            add(DebtGroupUi("on", t(TextKey.DUES_ON_YOU), totals.payableLoanMinor.takeUnless { searching }, t(TextKey.DEBTS_ORDER_ON_YOU), false, loans))
        }
        if (side != DebtSide.FOR_YOU && custody.isNotEmpty()) {
            add(DebtGroupUi("custody", t(TextKey.DEBTS_CUSTODY_TITLE), totals.payableCustodyMinor.takeUnless { searching }, t(TextKey.DEBTS_ORDER_CUSTODY), false, custody))
        }
    }
    val forYouPeople = all.filter { it.forYou }.map { it.personId }.distinct().size
    val onYouPeople = all.filter { it.kind == ObligationKind.LOAN_PAYABLE }.map { it.personId }.distinct().size
    val sides = listOf(
        DebtSideUi(
            DebtSide.FOR_YOU, t(TextKey.DUES_FOR_YOU),
            countText(forYouPeople, TextKey.DEBTS_AT_ONE, TextKey.DEBTS_AT_TWO, TextKey.DEBTS_AT_FEW, TextKey.DEBTS_AT_MANY),
            totals.receivableMinor, t(TextKey.DEBTS_FOR_YOU_HINT),
        ),
        DebtSideUi(
            DebtSide.ON_YOU, t(TextKey.DUES_ON_YOU),
            countText(onYouPeople, TextKey.DEBTS_TO_ONE, TextKey.DEBTS_TO_TWO, TextKey.DEBTS_TO_FEW, TextKey.DEBTS_TO_MANY),
            totals.payableLoanMinor, t(TextKey.DEBTS_ON_YOU_HINT),
        ),
    )
    val none = groups.isEmpty()
    val sideName = t(if (side == DebtSide.FOR_YOU) TextKey.DUES_FOR_YOU else TextKey.DUES_ON_YOU)
    return DuesDebtsUi(
        currency = currency,
        sides = sides,
        groups = groups,
        empty = all.isEmpty(),
        noHitsTitle = if (!none) null else if (searching) t(TextKey.DEBTS_NO_HITS_NAME, query.trim()) else t(TextKey.DEBTS_NO_HITS),
        noHitsBody = if (!none) null else if (side != DebtSide.ALL) t(TextKey.DEBTS_NO_HITS_IN_SIDE, sideName) else t(TextKey.DEBTS_NO_HITS_TRY),
    )
}
