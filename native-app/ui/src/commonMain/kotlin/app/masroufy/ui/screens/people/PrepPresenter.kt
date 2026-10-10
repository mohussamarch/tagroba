package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.EventRole
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.PrepItem
import app.masroufy.core.PrepSummary
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.eventNeedsPrep
import app.masroufy.core.prepAllowed
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.EventDetail

/**
 * «التجهيزات» (لوحة `EventPrep`) من `ManageEventPrep.summary`/`suggestions` و`ManageEvents.detail` (لمصروف الحدث اللي مش على بند).
 * المخطط الإجمالي `null` ⇒ «غير متاح» (ولا بند ليه مبلغ — مش صفر). شريط البند من `PeopleMoney.usedTenthPercent` (من `core`).
 */
internal enum class PrepBlock { CONDOLENCE, PAST }

internal data class PrepItemUi(val item: PrepItem, val sub: String, val usedTenth: Long?, val over: Boolean, val spentMinor: Halalas)

internal data class LooseUi(val txnId: Id, val name: String, val sub: String, val amountMinor: Halalas, val currency: Currency)

internal data class PrepUi(
    val event: LifeEvent,
    val eventLine: String,
    val block: PrepBlock?,
    /** null = الملخص ما اتحمّلش ⇒ «غير متاح» (مش صفر). */
    val spent: MoneyLine?,
    val currency: Currency,
    val planned: MoneyLine?,
    val plannedSub: String,
    val leftLine: String?,
    val looseLine: String?,
    val otherCurrencyLine: String?,
    val suggestions: List<String>,
    val items: List<PrepItemUi>,
    val loose: List<LooseUi>,
)

internal fun prepUi(
    detail: EventDetail,
    summary: PrepSummary?,
    suggestions: List<String>,
    currency: Currency,
    today: IsoDate,
    usage: (planned: Halalas, spent: Halalas) -> Long,
): PrepUi {
    val e = detail.event
    val line = joinLine(e.kind.label, dayMonth(e.date), relativeDays(daysBetween(today, e.date)))
    val block = when {
        !prepAllowed(e) -> PrepBlock.CONDOLENCE
        !eventNeedsPrep(e, today) -> PrepBlock.PAST
        else -> null
    }
    val s = summary
    val items = s?.items.orEmpty().map { st ->
        val planned = st.item.plannedMinor
        val over = planned != null && st.spentMinor > planned
        val sub = joinLine(
            planned?.let { t(TextKey.EVENT_PREP_PLANNED_OF, amountLabel(it, currency, showCurrency = false)) } ?: t(TextKey.EVENT_PREP_NO_AMOUNT),
            if (st.spentMinor > 0) t(TextKey.EVENT_PREP_SPENT_OF, amountLabel(st.spentMinor, currency, showCurrency = false)) else t(TextKey.EVENT_PREP_NOT_SPENT),
        ) + if (over) " — " + t(TextKey.EVENT_PREP_OVER) else ""
        PrepItemUi(st.item, sub, planned?.let { usage(it, st.spentMinor) }, over, st.spentMinor)
    }
    val known = items.map { it.item.id }.toSet()
    val loose = detail.transactions
        .filter { it.link.role == EventRole.SPEND && (it.link.prepItemId == null || it.link.prepItemId !in known) && it.transaction.currency == currency }
        .map { LooseUi(it.transaction.id, txnTitle(it.transaction), dayMonth(it.transaction.occurredAt), it.shareMinor, it.transaction.currency) }
    val count = items.size
    val plannedSub = when {
        count == 0 -> t(TextKey.EVENT_PREP_NO_ITEMS)
        s?.plannedTotalMinor == null -> t(TextKey.EVENT_PREP_NO_PRICED)
        s.unpricedCount > 0 -> t(TextKey.EVENT_PREP_UNPRICED, countOf(s.unpricedCount, Noun.ITEMS))
        else -> t(TextKey.EVENT_PREP_ALL_PRICED)
    }
    return PrepUi(
        event = e,
        eventLine = line,
        block = block,
        spent = s?.let { MoneyLine(it.spentMinor, currency) },
        currency = currency,
        planned = s?.plannedTotalMinor?.let { MoneyLine(it, currency) },
        plannedSub = plannedSub,
        leftLine = if (count == 0) null else if ((s?.remainingCount ?: 0) > 0) t(TextKey.EVENT_PREP_LEFT, countOf(s!!.remainingCount, Noun.ITEMS)) else t(TextKey.EVENT_PREP_ALL_DONE),
        looseLine = s?.unassignedSpentMinor?.takeIf { it > 0 }?.let { t(TextKey.EVENT_PREP_LOOSE_LINE, amountLabel(it, currency, showCurrency = false)) },
        otherCurrencyLine = s?.otherCurrencyCount?.takeIf { it > 0 }?.let { t(TextKey.EVENT_PREP_OTHER_CURRENCY, countOf(it, Noun.OPS)) },
        suggestions = if (count == 0) suggestions else emptyList(),
        items = items,
        loose = if (count == 0) emptyList() else loose,
    )
}
