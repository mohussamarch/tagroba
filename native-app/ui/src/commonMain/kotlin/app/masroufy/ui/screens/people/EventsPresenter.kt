package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.EventRole
import app.masroufy.core.EventSummary
import app.masroufy.core.EVENT_SHARE_WHOLE
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Occasion
import app.masroufy.core.PrepItem
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.eventNeedsPrep
import app.masroufy.core.prepAllowed
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.EventDetail
import app.masroufy.usecase.EventLists
import app.masroufy.usecase.EventRow

/**
 * «الأحداث» و«تفاصيل الحدث» من `ManageEvents.list`/`detail` — ترتيب وكلام بس. 🔒 **مفيش «صافي»** (§64): المصروف رقم، والنقوط رقم تاني
 * للمعلومية (اللي جاتلك في حدثك بس — في حدث حد تاني مش بتظهر خالص، مش صفر).
 */
internal data class EventStat(val label: String, val amounts: List<MoneyLine>, val tone: AmountTone, val sub: String?, val emptyText: String?)

internal data class EventCardUi(
    val id: Id,
    val name: String,
    val kind: LifeEventKind,
    val meta: String,
    val owner: String,
    val mine: Boolean,
    val stats: List<EventStat>,
)

internal data class NextEventUi(val id: Id, val name: String, val rel: String)

internal data class EventsUi(val next: NextEventUi?, val active: List<EventCardUi>, val archived: List<EventCardUi>)

internal fun ownerOf(e: LifeEvent, hostName: String?): String =
    if (e.mine) t(TextKey.PPL_MINE) else hostName?.let { t(TextKey.PPL_HOST_OF, it) } ?: t(TextKey.EVENTS_OTHER_OWNER)

private fun lines(s: EventSummary, pick: (app.masroufy.core.EventCurrencyTotals) -> Halalas?): List<MoneyLine> =
    s.totals.mapNotNull { c -> pick(c)?.let { MoneyLine(it, c.currency) } }

/** إحصاءات الكارت: المصروف دايمًا · النقوط اللي جاتلك (حدثك بس، لو فيه) · اللي إنت نقّطته (حدث حد تاني). */
internal fun eventStats(e: LifeEvent, s: EventSummary): List<EventStat> {
    val out = mutableListOf(
        EventStat(
            t(TextKey.EVENTS_SPEND), if (s.spendCount > 0) lines(s) { it.spentMinor } else emptyList(), AmountTone.PLAIN,
            if (s.spendCount > 0) countOf(s.spendCount, Noun.OPS) else null, if (s.spendCount > 0) null else t(TextKey.EVENTS_NOT_LINKED),
        ),
    )
    val giftsIn = s.giftInCount ?: 0
    if (e.mine && giftsIn > 0) out += EventStat(t(TextKey.EVENTS_GIFTS_IN), lines(s) { it.giftsInMinor }, AmountTone.INCOME, countOf(giftsIn, Noun.GIFTS), null)
    if (!e.mine) {
        val label = t(if (e.kind == LifeEventKind.CONDOLENCE) TextKey.EVENTS_GAVE_CONDOLENCE else TextKey.EVENTS_GAVE)
        out += if (s.giftOutCount > 0) EventStat(label, lines(s) { it.giftsOutMinor }, AmountTone.EXPENSE, countOf(s.giftOutCount, Noun.OPS), null)
        else EventStat(label, emptyList(), AmountTone.EXPENSE, null, t(TextKey.EVENTS_NOT_YET))
    }
    return out
}

/** النشطة: الجاية بالأقرب ثم اللي فاتت بالأحدث (زي النموذج) · المؤرشفة لوحدها · «أقرب حدث» = أول جاي. */
internal fun eventsUi(lists: EventLists, hostNames: Map<Id, String>, today: IsoDate): EventsUi {
    fun card(r: EventRow, archived: Boolean): EventCardUi {
        val e = r.event
        val owner = ownerOf(e, e.hostPersonId?.let(hostNames::get))
        val meta = if (archived) joinLine(e.kind.label, dayMonth(e.date), owner) else joinLine(e.kind.label, dayMonth(e.date), relativeDays(daysBetween(today, e.date)))
        return EventCardUi(e.id, e.name, e.kind, meta, owner, e.mine, eventStats(e, r.summary))
    }
    val upcoming = lists.active.filter { it.event.date >= today }.sortedBy { it.event.date }
    val past = lists.active.filter { it.event.date < today }.sortedByDescending { it.event.date }
    val ordered = upcoming + past
    val next = upcoming.firstOrNull()?.event?.let { NextEventUi(it.id, it.name, relativeDays(daysBetween(today, it.date))) }
    return EventsUi(next, ordered.map { card(it, false) }, lists.archived.map { card(it, true) })
}

// ── تفاصيل الحدث

internal data class LinkedRowUi(val txnId: Id, val name: String, val sub: String, val amountMinor: Halalas, val currency: Currency, val share: String?, val tone: AmountTone)

internal data class EventDetailUi(
    val event: LifeEvent,
    val hostName: String?,
    val kindLine: String,
    val whenLine: String,
    val owner: String,
    /** null = لسه ما اتربطش مصروف (نص بدل الرقم). */
    val spend: List<MoneyLine>?,
    val giftLabel: String,
    val gifts: List<MoneyLine>?,
    val giftEmpty: String,
    val heroNote: String,
    val spends: List<LinkedRowUi>,
    val giftRows: List<LinkedRowUi>,
    val hasGiftsIn: Boolean,
    val canPrep: Boolean,
    val prepLine: String,
    val noPrep: String?,
    /** تذكير حدثك السنوي (null = مقفول). */
    val reminder: Occasion?,
    val linkedTxnIds: Set<Id>,
)

internal fun eventDetailUi(d: EventDetail, wallets: Map<Id, String>, prep: List<PrepItem>, reminder: Occasion?, today: IsoDate): EventDetailUi {
    val e = d.event
    val s = d.summary
    val condolence = e.kind == LifeEventKind.CONDOLENCE
    val gaveWord = t(if (condolence) TextKey.EVENTS_GAVE_CONDOLENCE else TextKey.EVENTS_GAVE)
    val giftRole = if (e.mine) EventRole.GIFT_IN else EventRole.GIFT_OUT
    val spends = d.transactions.filter { it.link.role == EventRole.SPEND }.map { l ->
        val txn = l.transaction
        val share = if (l.link.sharePercent == EVENT_SHARE_WHOLE) t(TextKey.EVENT_DETAIL_WHOLE)
        else t(TextKey.EVENT_DETAIL_SHARE_OF, sentenceNumber(l.link.sharePercent), amountLabel(txn.amountMinor, txn.currency, showCurrency = false))
        LinkedRowUi(txn.id, txnTitle(txn), joinLine(dayMonth(txn.occurredAt), txn.walletId?.let(wallets::get)), l.shareMinor, txn.currency, share, AmountTone.EXPENSE)
    }
    val giftRows = d.transactions.filter { it.link.role == giftRole }.map { l ->
        val txn = l.transaction
        LinkedRowUi(txn.id, l.personName ?: txnTitle(txn), joinLine(txn.walletId?.let(wallets::get), dayMonth(txn.occurredAt)), l.shareMinor, txn.currency, null, if (e.mine) AmountTone.INCOME else AmountTone.EXPENSE)
    }
    val giftCount = if (e.mine) s.giftInCount ?: 0 else s.giftOutCount
    val done = prep.count { it.done }
    val prepLine = joinLine(countOf(prep.size, Noun.ITEMS), if (done > 0) t(TextKey.EVENT_DETAIL_PREP_DONE, sentenceNumber(done)) else t(TextKey.EVENT_DETAIL_PREP_NONE_DONE))
    val canPrep = eventNeedsPrep(e, today)
    return EventDetailUi(
        event = e,
        hostName = d.hostName,
        kindLine = joinLine(e.kind.label, dayMonth(e.date)),
        whenLine = relativeDays(daysBetween(today, e.date)),
        owner = if (e.mine) t(TextKey.PPL_MINE) else d.hostName?.let { t(TextKey.EVENT_DETAIL_HOST, it) } ?: t(TextKey.EVENTS_OTHER_OWNER),
        spend = if (s.spendCount > 0) lines(s) { it.spentMinor } else null,
        giftLabel = if (e.mine) t(TextKey.EVENTS_GIFTS_IN) else gaveWord,
        gifts = if (giftCount > 0) lines(s) { if (e.mine) it.giftsInMinor else it.giftsOutMinor } else null,
        giftEmpty = t(if (e.mine) TextKey.EVENT_DETAIL_GIFTS_NONE else TextKey.EVENTS_NOT_YET),
        heroNote = if (e.mine) t(TextKey.EVENT_DETAIL_NOTE_MINE) else t(TextKey.EVENT_DETAIL_NOTE_OTHER, d.hostName ?: t(TextKey.EVENTS_OTHER_OWNER)),
        spends = spends,
        giftRows = giftRows,
        hasGiftsIn = e.mine && (s.giftInCount ?: 0) > 0,
        canPrep = canPrep,
        prepLine = prepLine,
        noPrep = if (canPrep) null else t(if (!prepAllowed(e)) TextKey.EVENT_DETAIL_NO_PREP_CONDOLENCE else TextKey.EVENT_DETAIL_NO_PREP_PAST),
        reminder = reminder,
        linkedTxnIds = d.transactions.map { it.transaction.id }.toSet(),
    )
}
