package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.DueFlow
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.ui.text.t
import app.masroufy.usecase.DuesView
import app.masroufy.usecase.InstallmentView
import app.masroufy.usecase.PersonRow
import app.masroufy.usecase.RecurringView
import app.masroufy.usecase.RoscaView

/**
 * خانة «المستحقات» (لوحة `Dues`): من نتيجة `LoadDues.load` (+ عدد الديون والجمعيات والأقساط والاشتراكات) لحالة الشاشة.
 * **مفيش أي جمع أو طرح هنا** — كل رقم جاي من حالة الاستخدام زي ما هو. مجموع «لك» ومجموع «عليك» **مش محسوبين في حالة الاستخدام**
 * (`DuesTotals` فيها الأجزاء بس) ⇒ `null` ⇒ «غير متاح» (القاعدة 10)، والأجزاء نفسها ظاهرة سطر سطر.
 */
data class DuesLineUi(val label: String, val minor: Halalas)

enum class DueKind { DEBT, SUBSCRIPTION, INSTALLMENT, ROSCA }

/** الصف أو المربع بيفتح إيه (نفس روابط النموذج). */
sealed interface DuesTarget {
    data class Debts(val side: DebtSide) : DuesTarget
    data object Subscriptions : DuesTarget
    data object Installments : DuesTarget
    data object Roscas : DuesTarget
}

data class AgendaRowUi(
    val key: String,
    val kind: DueKind,
    val title: String,
    val sub: String,
    val chip: Chip,
    val chipText: String,
    val amountMinor: Halalas,
    val currency: Currency,
    val incoming: Boolean,
    val dirText: String,
    val target: DuesTarget,
)

data class DuesTileUi(val label: String, val count: Int, val target: DuesTarget)

/** العدّ اللي في المربعات الأربعة (عدّ عناصر، مش فلوس). */
data class DuesCounts(val debts: Int, val roscas: Int, val installments: Int, val subscriptions: Int) {
    companion object {
        fun of(people: List<PersonRow>, roscas: List<RoscaView>, plans: List<InstallmentView>, recurring: RecurringView): DuesCounts =
            DuesCounts(people.sumOf { it.obligations.size }, roscas.size, plans.size, recurring.items.size)
    }
}

data class DuesPanelUi(
    val currency: Currency,
    val forYou: List<DuesLineUi>,
    val onYou: List<DuesLineUi>,
    /** null = غير متاح (مفيش حالة استخدام بتجمع الأجزاء). */
    val forYouTotal: Halalas?,
    val onYouTotal: Halalas?,
    val monthLabel: String,
    val monthPayMinor: Halalas,
    val monthReceiveMinor: Halalas,
    val agenda: List<AgendaRowUi>,
    val tiles: List<DuesTileUi>,
    /** أرباح التمويل اللي اتحققت في الفترة — null لو صفر (السطر ما بيظهرش). */
    val financingMinor: Halalas?,
    val empty: Boolean,
)

fun duesPanelUi(view: DuesView, counts: DuesCounts, period: Period, today: IsoDate, currency: Currency): DuesPanelUi {
    val totals = view.totals
    val forYou = buildList {
        add(DuesLineUi(t(UiKey.DUES_LINE_RECEIVABLE), totals.receivableMinor))
        if (totals.roscaSavedMinor > 0) add(DuesLineUi(t(UiKey.DUES_LINE_ROSCA_SAVED), totals.roscaSavedMinor))
    }
    val onYou = buildList {
        add(DuesLineUi(t(UiKey.DUES_LINE_LOANS), totals.payableLoanMinor))
        if (totals.payableCustodyMinor > 0) add(DuesLineUi(t(UiKey.DUES_LINE_CUSTODY), totals.payableCustodyMinor))
        if (totals.installmentsLeftMinor > 0) add(DuesLineUi(t(UiKey.DUES_LINE_INSTALLMENTS), totals.installmentsLeftMinor))
        if (totals.roscaOwedMinor > 0) add(DuesLineUi(t(UiKey.DUES_LINE_ROSCA_OWED), totals.roscaOwedMinor))
    }
    val agenda = view.agenda.map { agendaRow(it, today) }
    val tiles = listOf(
        DuesTileUi(t(UiKey.DUES_TILE_DEBTS), counts.debts, DuesTarget.Debts(DebtSide.ALL)),
        DuesTileUi(t(UiKey.DUES_TILE_ROSCAS), counts.roscas, DuesTarget.Roscas),
        DuesTileUi(t(UiKey.DUES_TILE_INSTALLMENTS), counts.installments, DuesTarget.Installments),
        DuesTileUi(t(UiKey.DUES_TILE_SUBSCRIPTIONS), counts.subscriptions, DuesTarget.Subscriptions),
    )
    val nothing = agenda.isEmpty() && counts.debts == 0 && counts.roscas == 0 && counts.installments == 0 && counts.subscriptions == 0
    return DuesPanelUi(
        currency = currency,
        forYou = forYou,
        onYou = onYou,
        forYouTotal = null,
        onYouTotal = null,
        monthLabel = monthName(parseIsoDate(period.end).month),
        monthPayMinor = view.month.toPayMinor,
        monthReceiveMinor = view.month.toReceiveMinor,
        agenda = agenda,
        tiles = tiles,
        financingMinor = view.financingCostMinor.takeIf { it > 0 },
        empty = nothing,
    )
}

/** صف ميعاد: «20 سبتمبر، منذ 17 يومًا» + الحالة + «ستستلم/ستدفع». */
internal fun agendaRow(item: DueItem, today: IsoDate): AgendaRowUi {
    val kind = when (item.source) {
        DueSource.DEBT -> DueKind.DEBT
        DueSource.RECURRING -> DueKind.SUBSCRIPTION
        DueSource.INSTALLMENT -> DueKind.INSTALLMENT
        DueSource.ROSCA_CONTRIBUTION, DueSource.ROSCA_PAYOUT -> DueKind.ROSCA
    }
    val incoming = item.flow == DueFlow.RECEIVE
    val tail = if (item.source == DueSource.ROSCA_PAYOUT) t(UiKey.DUES_ROW_YOUR_TURN) else relativeText(item.dueAt, today)
    val (chip, chipKey) = when (item.status) {
        DueStatus.OVERDUE -> Chip.OVERDUE to UiKey.DUES_CHIP_LATE
        DueStatus.SOON -> Chip.SOON to UiKey.DUES_CHIP_SOON
        DueStatus.UPCOMING -> Chip.UPCOMING to UiKey.DUES_CHIP_NEXT
    }
    val target = when (kind) {
        DueKind.DEBT -> DuesTarget.Debts(if (incoming) DebtSide.FOR_YOU else DebtSide.ON_YOU)
        DueKind.SUBSCRIPTION -> DuesTarget.Subscriptions
        DueKind.INSTALLMENT -> DuesTarget.Installments
        DueKind.ROSCA -> DuesTarget.Roscas
    }
    return AgendaRowUi(
        key = "${item.source.wire}-${item.sourceId}-${item.dueAt}",
        kind = kind,
        title = item.title,
        sub = t(UiKey.DUES_COMMA_JOIN, dateText(item.dueAt, today), tail),
        chip = chip,
        chipText = t(chipKey),
        amountMinor = item.amountMinor,
        currency = item.currency,
        incoming = incoming,
        dirText = t(if (incoming) UiKey.DUES_DIR_IN else UiKey.DUES_DIR_OUT),
        target = target,
    )
}
