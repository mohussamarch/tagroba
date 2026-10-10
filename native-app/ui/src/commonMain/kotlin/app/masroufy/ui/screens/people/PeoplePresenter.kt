package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import app.masroufy.core.BalanceDirection
import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ObligationKind
import app.masroufy.core.PeopleOverview
import app.masroufy.core.PeopleSection
import app.masroufy.core.PersonOutlineState
import app.masroufy.core.PersonOverviewRow
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.PersonRow

/**
 * من نتايج حالات الاستخدام لحالة شاشة «الأشخاص» و«لك» و«عليك» — **ترتيب وكلام بس، من غير أي حساب فلوس** (الأرقام جاية جاهزة:
 * `PeopleOverview.totals` و`PersonObligationRow.remainingMinor`).
 */

/** دايرة شخص في الشاشة. [chip] «لك 500.00» / «عليك 2,000.00» (أو null). */
internal data class PersonChip(val id: Id, val name: String, val initial: String, val chip: String?, val tone: AmountTone?)

/** مبلغ بعملته (سطر لكل عملية — مفيش جمع بين عملتين). */
internal data class MoneyLine(val minor: Halalas, val currency: Currency)

internal data class PeopleTabUi(
    /** فوق الكاركتر (أقصى ٥). */
    val orbit: List<PersonChip>,
    /** الباقيين في الشبكة (المؤرشف اللي عليه أو ليه فلوس هنا بس). */
    val rest: List<PersonChip>,
    val total: Int,
    /** null = الأرصدة ما اتحمّلتش ⇒ «غير متاح» (مش صفر). */
    val owed: List<MoneyLine>?,
    val owedPeople: Int,
    val owe: List<MoneyLine>?,
    val owePeople: Int,
    /** الأشخاص ظاهرين من غير أرقام لأن قراية الأرصدة فشلت (حالة الخطأ في النموذج). */
    val balancesFailed: Boolean = false,
)

internal const val ORBIT_SIZE = 5

/**
 * حالة الخطأ: `PeopleOverview` فشل، فالأسماء بس من `ManagePeople.listWithBalances` (من غير المؤرشفين ومن غير أي رقم) و«لك/عليك» «غير متاح».
 */
internal fun peopleWithoutBalances(rows: List<PersonRow>): PeopleTabUi {
    val active = rows.filter { !it.person.archived }.map { PersonChip(it.person.id, it.person.name, initialOf(it.person.name), null, null) }
    return PeopleTabUi(active.take(ORBIT_SIZE), active.drop(ORBIT_SIZE), active.size, null, 0, null, 0, balancesFailed = true)
}

/**
 * الترتيب: «لك عندهم» ثم «عليك لهم» ثم «مناسبات قريبة» ثم الباقي — زي أقسام `PeopleOverview` (من غير تكرار).
 * ⚠️ النموذج بيقول «المُعالون أولًا ثم الأكثر تعاملًا» — **مالوش حسبة في كوتلن** (missingLogic) ⇒ الترتيب ده لحد ما تتبني.
 */
internal fun peopleTabUi(o: PeopleOverview, spaceId: String, currency: Currency): PeopleTabUi {
    val byId = o.rows.associateBy { it.person.id }
    val order = (o.sections.flatMap { it.personIds } + o.rows.map { it.person.id }).distinct().mapNotNull { byId[it] }
    val orbit = order.filter { !it.person.archived }.take(ORBIT_SIZE)
    val rest = order.filter { it !in orbit }
    val totals = o.totals.filter { it.spaceId == spaceId }
    fun lines(pick: (app.masroufy.core.PeopleTotal) -> Halalas) =
        totals.map { MoneyLine(pick(it), it.currency) }.filter { it.minor != 0L }.ifEmpty { listOf(MoneyLine(0, currency)) }
    fun count(s: PeopleSection) = o.sections.firstOrNull { it.section == s }?.count ?: 0
    return PeopleTabUi(
        orbit = orbit.map { chipOf(it) },
        rest = rest.map { chipOf(it) },
        total = order.size,
        owed = lines { it.owedToYouMinor },
        owedPeople = count(PeopleSection.OWED_TO_YOU),
        owe = lines { it.youOweMinor },
        owePeople = count(PeopleSection.YOU_OWE),
    )
}

/** الشريحة تحت الاسم بلون الإطار (`outlineState`): عليك > لك. */
internal fun chipOf(row: PersonOverviewRow): PersonChip {
    val (direction, key, tone) = when (row.state) {
        PersonOutlineState.YOU_OWE -> Triple(BalanceDirection.YOU_OWE, UiKey.PEOPLE_CHIP_OWE, AmountTone.EXPENSE)
        PersonOutlineState.OWED_TO_YOU -> Triple(BalanceDirection.OWED_TO_YOU, UiKey.PEOPLE_CHIP_OWED, AmountTone.INCOME)
        else -> return PersonChip(row.person.id, row.person.name, initialOf(row.person.name), null, null)
    }
    val line = row.balances.first { it.direction == direction }
    val text = t(key, amountLabel(line.amountMinor, line.currency, showCurrency = false))
    return PersonChip(row.person.id, row.person.name, initialOf(row.person.name), text, tone)
}

// ── «لك» و«عليك» (OwedToYou · YouOwe) ──

internal enum class OwedSide(val kinds: Set<ObligationKind>) {
    OWED_TO_YOU(setOf(ObligationKind.RECEIVABLE)),
    YOU_OWE(setOf(ObligationKind.LOAN_PAYABLE, ObligationKind.CUSTODY_PAYABLE)),
}

/** إشارة السطر: فات موعده > قرّب (٧ أيام) > عادي. ⚠️ «منتظر من زمان» محتاجة تاريخ الدين — مش متاحة (missingLogic). */
internal enum class OwedSignal { OVERDUE, SOON, CALM }

internal data class OwedRowUi(
    val personId: Id,
    val obligationId: Id,
    val name: String,
    val initial: String,
    val reason: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val due: String,
    val signal: OwedSignal?,
    val signalText: String?,
)

internal data class OwedUi(val totals: List<MoneyLine>?, val people: Int, val rows: List<OwedRowUi>)

/**
 * [totals] من `PeopleOverview.totals` للبلد دي (null = ما اتحمّلش ⇒ «غير متاح»). المواعيد من `LoadDues.dueItems` (أقرب قسط لسه ما اتدفعش).
 * الترتيب: اللي فات موعده (الأقدم الأول) · اللي قرّب · اللي ليه موعد بعدين · اللي من غير موعد.
 */
internal fun owedUi(side: OwedSide, rows: List<PersonRow>, overview: PeopleOverview?, spaceId: String, dues: List<DueItem>, today: IsoDate): OwedUi {
    val nextDue = dues.filter { it.source == DueSource.DEBT }.groupBy { it.sourceId }.mapValues { (_, v) -> v.minBy { it.dueAt } }
    val items = rows.flatMap { r ->
        r.obligations.filter { it.obligation.kind in side.kinds && it.remainingMinor > 0 }.map { o -> Triple(r, o, nextDue[o.obligation.id]) }
    }.sortedWith(compareBy({ rank(it.third) }, { it.third?.dueAt ?: "9999" }))
    val out = items.map { (r, o, due) ->
        val reason = when {
            o.obligation.originTransactionId == null -> t(UiKey.OWED_REASON_OPENING)
            o.obligation.kind == ObligationKind.RECEIVABLE -> t(UiKey.OWED_REASON_RECEIVABLE)
            o.obligation.kind == ObligationKind.LOAN_PAYABLE -> t(UiKey.OWED_REASON_LOAN)
            else -> t(UiKey.OWED_REASON_CUSTODY)
        }
        val (signal, text) = signalOf(due, today)
        OwedRowUi(
            r.person.id, o.obligation.id, r.person.name, initialOf(r.person.name), reason, o.remainingMinor, o.obligation.currency,
            due?.let { t(UiKey.OWED_DUE_ON, dayMonth(it.dueAt)) } ?: t(UiKey.OWED_NO_DUE), signal, text,
        )
    }
    val totals = overview?.totals?.filter { it.spaceId == spaceId }?.map {
        MoneyLine(if (side == OwedSide.OWED_TO_YOU) it.owedToYouMinor else it.youOweMinor, it.currency)
    }?.filter { it.minor != 0L }
    return OwedUi(totals, out.map { it.personId }.distinct().size, out)
}

private fun rank(due: DueItem?): Int = when (due?.status) {
    DueStatus.OVERDUE -> 0
    DueStatus.SOON -> 1
    DueStatus.UPCOMING -> 2
    null -> 3
}

private fun signalOf(due: DueItem?, today: IsoDate): Pair<OwedSignal?, String?> {
    due ?: return null to null
    val days = daysBetween(today, due.dueAt)
    return when (due.status) {
        DueStatus.OVERDUE -> OwedSignal.OVERDUE to t(UiKey.OWED_SIGNAL_OVERDUE, countOf(-days, Noun.DAYS))
        DueStatus.SOON -> OwedSignal.SOON to t(UiKey.OWED_SIGNAL_SOON, relativeDays(days))
        DueStatus.UPCOMING -> OwedSignal.CALM to relativeDays(days)
    }
}
