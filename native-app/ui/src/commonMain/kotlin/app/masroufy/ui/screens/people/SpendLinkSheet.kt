package app.masroufy.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.EventRole
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.normalizeText
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EventDetail
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.ManageEvents
import kotlinx.coroutines.launch

/**
 * «اربط مصروفًا بنسبة» (لوحة `EventSpendLinkSheet` جوه تفاصيل الحدث): مصروف آخر ٣٠ يوم (الأحدث الأول) · بحث · لكل عملية مختارة نسبة 1–100
 * (المبدئي 100) ونصيب الحدث منها (`eventShareMinor` في `:wiring`) · المجموع اللي هيتضاف من غير «صافي».
 * الحفظ: `EventGifts.link(…, SPEND, نسبة)` لكل عملية — ⚠️ مش كتابة واحدة (missingLogic)؛ المربوطة بحدث تاني بتبان مقفولة باسمه (والحالة نفسها بترفض لو وصلت).
 */
internal const val RECENT_DAYS = 30

/** آخر [RECENT_DAYS] يوم لحد النهارده (فترة للقراية بس). */
internal fun recentPeriod(today: String): Period {
    val start = dayNumberToIso(toDayNumber(parseIsoDate(today)) - RECENT_DAYS)
    return Period("recent", start, today, RECENT_DAYS + 1)
}

/** الأحدث الأول، والبحث بالاسم (من غير تشكيل والهمزات) أو بأرقام المبلغ. */
internal fun filterRecent(txns: List<Transaction>, query: String, inbound: Boolean): List<Transaction> {
    val wanted = if (inbound) app.masroufy.core.Direction.IN else app.masroufy.core.Direction.OUT
    val q = normalizeText(query)
    val digits = q.filter { it in '0'..'9' }
    return txns.filter { it.observedDirection == wanted }
        .filter { q.isEmpty() || normalizeText(txnTitle(it)).contains(q) || (digits.isNotEmpty() && amountLabel(it.amountMinor, it.currency, showCurrency = false).filter { c -> c in '0'..'9' }.contains(digits)) }
        .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
}

/**
 * العمليات المربوطة بحدث **تاني** (العملية بتتربط بحدث واحد بس) ⇒ اسم الحدث ده، عشان تبان مقفولة بسببها بدل ما الحفظ يرفض بعدين.
 * قراية من `ManageEvents` بس (القايمة ثم تفاصيل كل حدث).
 */
internal suspend fun linkedElsewhere(events: ManageEvents, eventId: Id): Map<Id, String> {
    val lists = events.list()
    return otherEventLinks((lists.active + lists.archived).filter { it.event.id != eventId }.map { events.detail(it.event.id) }, eventId)
}

/** من تفاصيل الأحداث: معرّف العملية ⇒ اسم الحدث التاني المربوطة بيه. */
internal fun otherEventLinks(details: List<EventDetail>, eventId: Id): Map<Id, String> =
    details.filter { it.event.id != eventId }.flatMap { d -> d.transactions.map { it.transaction.id to d.event.name } }.toMap()

/** السطر التاني في صف العملية: مربوطة هنا · مربوطة بحدث تاني · التاريخ والمحفظة. */
internal fun spendRowLine(tx: Transaction, here: Boolean, otherEvent: String?, wallets: Map<Id, String>): String = when {
    here -> joinLine(dayMonth(tx.occurredAt), t(TextKey.SPEND_LINK_HERE))
    otherEvent != null -> t(TextKey.SPEND_LINK_OTHER_EVENT, otherEvent)
    else -> joinLine(dayMonth(tx.occurredAt), tx.walletId?.let(wallets::get))
}

@Composable
internal fun SpendLinkButton(data: EventScreenData) {
    var open by remember { mutableStateOf(false) }
    TonalButton(t(TextKey.SPEND_LINK_OPEN), onClick = { open = true }, modifier = Modifier.fillMaxWidth(), height = 44.dp)
    Sheet(open, onDismiss = { open = false }, title = t(TextKey.SPEND_LINK_TITLE, data.ui.event.name)) {
        SpendLinkForm(data) { open = false }
    }
}

@Composable
private fun SpendLinkForm(data: EventScreenData, done: () -> Unit) {
    val space = LocalSpace.current
    val money = space.people.money
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var txns by remember { mutableStateOf<List<Transaction>?>(null) }
    var elsewhere by remember { mutableStateOf<Map<Id, String>>(emptyMap()) }
    LaunchedEffect(space) {
        txns = runCatching { space.people.transactions.load(LoadTransactionsScreenRequest(period = recentPeriod(space.shell.today()))).transactions }.getOrDefault(emptyList())
        elsewhere = runCatching { linkedElsewhere(space.people.events, data.ui.event.id) }.getOrDefault(emptyMap())
    }
    val wallets = data.wallets.associate { it.id to it.name }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Map<Id, String>>(emptyMap()) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val shown = filterRecent(txns.orEmpty(), query, inbound = false)
    val chosen = txns.orEmpty().filter { it.id in picked }
    val pcts = chosen.associate { it.id to intOf(picked.getValue(it.id))?.takeIf { p -> p in 1..100 } }
    val allOk = pcts.values.all { it != null }
    val shares: List<Halalas> = chosen.mapNotNull { tx -> pcts[tx.id]?.let { money.eventShare(tx.amountMinor, it) } }
    val currency = space.space.currency
    Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeading(t(TextKey.SPEND_LINK_TITLE, data.ui.event.name))
        Note(t(TextKey.SPEND_LINK_INTRO))
        TextInput(query, { query = it }, placeholder = t(TextKey.SPEND_LINK_SEARCH), trailing = { LucideIcon(Lucide.SEARCH, size = 18.dp, tint = Ink.muted, modifier = Modifier.padding(end = 14.dp)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(TextKey.SPEND_LINK_LIST), style = Type.captionBold())
            Note(t(TextKey.SPEND_LINK_HINT))
        }
        when {
            txns == null -> app.masroufy.ui.components.Skeleton(Modifier.fillMaxWidth().heightIn(min = 120.dp))
            txns!!.none { it.observedDirection == app.masroufy.core.Direction.OUT } -> Note(t(TextKey.SPEND_LINK_NONE))
            shown.isEmpty() -> Note(t(TextKey.SPEND_LINK_NO_MATCH, query.trim()))
        }
        shown.forEachIndexed { i, tx ->
            val here = tx.id in data.ui.linkedTxnIds
            val other = elsewhere[tx.id]
            val locked = here || other != null
            val on = tx.id in picked
            Column {
                Rowed(i == shown.lastIndex && !on) {
                    val press = rememberPress()
                    Row(
                        Modifier.weight(1f).pressScale(press, !locked).tap(press, !locked, label = txnTitle(tx), onClick = {
                            picked = if (on) picked - tx.id else picked + (tx.id to "100")
                            err = null
                        }),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(on || locked, filled = on)
                        Column(Modifier.weight(1f)) {
                            BasicText(txnTitle(tx), style = Type.bodyBold().copy(color = if (locked) Ink.muted else Ink.text))
                            BasicText(spendRowLine(tx, here, other, wallets), style = Type.caption().copy(color = if (locked) Ink.primary else Ink.muted))
                        }
                        AmountText(tx.amountMinor, tx.currency, size = 14, tone = AmountTone.EXPENSE, color = if (locked) Ink.faded else null)
                    }
                }
                if (on) PercentRow(tx, picked.getValue(tx.id), { picked = picked + (tx.id to it); err = null }, pcts[tx.id]?.let { money.eventShare(tx.amountMinor, it) })
            }
        }
        Note(t(TextKey.SPEND_LINK_TAG_NOTE))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PeopleInk.tonalSoft).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.SPEND_LINK_TOTAL), style = Type.of(13, FontWeight.Bold))
                when {
                    chosen.isEmpty() -> BasicText(t(TextKey.SPEND_LINK_NOT_PICKED), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
                    allOk -> AmountText(money.total(shares), currency, tone = AmountTone.EXPENSE)
                    else -> AmountText(null, currency)
                }
            }
            Note(when {
                chosen.isEmpty() -> t(TextKey.SPEND_LINK_PICK_HINT)
                allOk -> t(TextKey.SPEND_LINK_EACH, countOf(chosen.size, Noun.OPS))
                else -> t(TextKey.SPEND_LINK_BAD_PCT_SUB)
            })
        }
        ErrorLine(err)
        PrimaryButton(
            if (chosen.isEmpty()) t(TextKey.SPEND_LINK_SAVE_EMPTY) else t(TextKey.SPEND_LINK_SAVE, countOf(chosen.size, Noun.OPS_OBJ)),
            loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (chosen.isEmpty()) { err = t(TextKey.SPEND_LINK_NEED_ONE); return@PrimaryButton }
                if (!allOk) { err = t(TextKey.SPEND_LINK_NEED_PCT); return@PrimaryButton }
                scope.launch {
                    busy = true
                    var linked = 0
                    val failure = runCatching { for (tx in chosen) { space.people.gifts.link(data.ui.event.id, tx.id, EventRole.SPEND, null, pcts.getValue(tx.id)!!); linked++ } }.exceptionOrNull()
                    busy = false
                    if (linked > 0) PeopleChanges.bump()
                    if (failure != null) err = failure.message
                    else {
                        toaster.show(t(TextKey.SPEND_LINK_DONE, countOf(linked, Noun.OPS), data.ui.event.name, amountLabel(money.total(shares), currency)))
                        done()
                    }
                }
            },
        )
    }
}

/** النسبة (١–١٠٠) + ١٠٠/٧٥/٥٠/٢٥ + نصيب الحدث. */
@Composable
private fun PercentRow(tx: Transaction, text: String, onText: (String) -> Unit, share: Halalas?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberInput(text, { onText(it.take(3)) }, Modifier.width(96.dp), placeholder = "100", decimal = false)
            BasicText(t(TextKey.PPL_PERCENT_SIGN), style = Type.bodyBold())
            for (v in listOf(100, 75, 50, 25)) Choice(t(TextKey.PPL_PERCENT, sentenceNumber(v)), intOf(text) == v, { onText(v.toString()) }, textSize = 13)
        }
        val p = intOf(text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Note(when {
                share == null -> t(TextKey.SPEND_LINK_RANGE)
                p == 100 -> t(TextKey.SPEND_LINK_WHOLE)
                else -> t(TextKey.SPEND_LINK_PART)
            })
            AmountText(share, tx.currency, size = 13, tone = AmountTone.EXPENSE)
        }
    }
}

/** مربع الاختيار 18 (زاوية 6). */
@Composable
internal fun CheckBox(on: Boolean, filled: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(18.dp).clip(shape).background(if (filled) Ink.primary else if (on) Ink.selected else PeopleInk.chip),
        contentAlignment = Alignment.Center,
    ) { if (on) LucideIcon(Lucide.CHECK, size = 14.dp, tint = if (filled) Ink.onPrimary else Ink.primary) }
}
