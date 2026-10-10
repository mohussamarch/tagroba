package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import app.masroufy.core.Direction
import app.masroufy.core.EventRole
import app.masroufy.core.Id
import app.masroufy.core.LifeEventKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest
import kotlinx.coroutines.launch

/**
 * «سجّل النقوط» (لوحة `NuqootSheet` جوه تفاصيل الحدث): **نقوط كاش** = المحفظة + (الاسم والمبلغ) لكل سطر ⇒ عملية لكل اسم
 * (`EventGifts.recordGifts` — الكل أو ولا حاجة) · أو **عملية موجودة** تتربط نقطة (`EventGifts.link`). بتتصنّف «هدايا › نقوط» لوحدها.
 * الاسم المكتوب لو مش شخص موجود بيتضاف شخص جديد الأول (`addPerson` — ⚠️ مش في نفس الكتابة، missingLogic).
 */
private const val MAX_ROWS = 8
private const val QUICK_PEOPLE = 4

@Composable
internal fun NuqootButton(data: EventScreenData) {
    var open by remember { mutableStateOf(false) }
    val e = data.ui.event
    val gave = t(if (e.kind == LifeEventKind.CONDOLENCE) UiKey.EVENTS_GAVE_CONDOLENCE else UiKey.EVENTS_GAVE)
    val label = if (e.mine) t(UiKey.NUQOOT_OPEN_MINE) else t(UiKey.NUQOOT_OPEN_OTHER, gave)
    TonalButton(label, onClick = { open = true }, modifier = Modifier.fillMaxWidth())
    Sheet(open, onDismiss = { open = false }, title = if (e.mine) t(UiKey.EVENTS_GIFTS_IN) else gave) {
        NuqootForm(data, if (e.mine) t(UiKey.EVENTS_GIFTS_IN) else gave) { open = false }
    }
}

internal data class GiftLine(val name: String = "", val amount: String = "")

@Composable
private fun NuqootForm(data: EventScreenData, title: String, done: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val e = data.ui.event
    val currency = space.space.currency
    val hostName = data.ui.hostName.orEmpty()
    var link by remember { mutableStateOf(false) }
    var wallet by remember { mutableStateOf(data.wallets.firstOrNull { it.kind == "cash" }?.id ?: data.wallets.firstOrNull()?.id) }
    var rows by remember { mutableStateOf(if (e.mine) listOf(GiftLine(), GiftLine()) else listOf(GiftLine(name = hostName))) }
    var cand by remember { mutableStateOf<Id?>(null) }
    var who by remember { mutableStateOf(if (e.mine) null else e.hostPersonId) }
    var candidates by remember { mutableStateOf<List<Transaction>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(link) {
        if (link && candidates == null) candidates = runCatching {
            val all = space.people.transactions.load(LoadTransactionsScreenRequest(period = recentPeriod(space.shell.today()))).transactions
            filterRecent(all, "", inbound = e.mine).filter { it.id !in data.ui.linkedTxnIds }
        }.getOrDefault(emptyList())
    }
    val plan = checkGiftLines(rows, data.people, currency)
    val walletName = data.wallets.firstOrNull { it.id == wallet }?.name.orEmpty()
    Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetHeading(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(t(UiKey.NUQOOT_MODE_CASH), !link, { link = false; err = null }, Modifier.weight(1f), filled = true)
            Choice(t(UiKey.NUQOOT_MODE_LINK), link, { link = true; err = null }, Modifier.weight(1f), filled = true)
        }
        if (!link) {
            FieldTitle(t(UiKey.NUQOOT_WALLET))
            ChoiceFlow { for (w in data.wallets) Choice(w.name, wallet == w.id, { wallet = w.id }, filled = true, textSize = 13) }
            FieldTitle(t(UiKey.NUQOOT_ROWS))
            rows.forEachIndexed { i, r ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(r.name, { v -> rows = rows.mapIndexed { j, x -> if (j == i) x.copy(name = v.take(80)) else x }; err = null }, Modifier.weight(1.4f), placeholder = t(UiKey.NUQOOT_NAME))
                    NumberInput(r.amount, { v -> rows = rows.mapIndexed { j, x -> if (j == i) x.copy(amount = v) else x }; err = null }, Modifier.weight(1f), placeholder = "0.00")
                    SquareIcon(Lucide.X, t(UiKey.PPL_REMOVE_ROW), { rows = rows.filterIndexed { j, _ -> j != i } }, enabled = rows.size > 1)
                }
            }
            ChoiceFlow {
                Choice(t(UiKey.NUQOOT_ADD_ROW), false, { if (rows.size < MAX_ROWS) rows = rows + GiftLine() }, textSize = 13)
                val used = rows.map { it.name.trim() }.toSet()
                for (p in data.people.filter { !it.archived && it.name !in used }.take(QUICK_PEOPLE)) {
                    Choice(p.name, false, {
                        val empty = rows.indexOfFirst { it.name.isBlank() }
                        rows = if (empty >= 0) rows.mapIndexed { j, x -> if (j == empty) x.copy(name = p.name) else x } else if (rows.size < MAX_ROWS) rows + GiftLine(p.name) else rows
                    }, textSize = 13)
                }
            }
            val ok = plan as? GiftPlan.Ready
            Note(if (ok != null && ok.amounts.isNotEmpty()) t(UiKey.NUQOOT_SUM, amountLabel(space.people.money.total(ok.amounts), currency), countOf(ok.amounts.size, Noun.OPS), walletName) else t(UiKey.NUQOOT_EACH))
        } else {
            val list = candidates
            if (list == null) app.masroufy.ui.components.Skeleton(Modifier.fillMaxWidth().heightIn(min = 96.dp))
            else if (list.isEmpty()) Note(t(UiKey.NUQOOT_NO_CANDIDATES))
            list.orEmpty().forEach { tx ->
                val on = cand == tx.id
                PickRow(on, txnTitle(tx), { cand = tx.id; err = null }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(txnTitle(tx), Modifier.weight(1f), style = Type.bodyBold())
                        AmountText(tx.amountMinor, tx.currency, size = 14, tone = if (e.mine) AmountTone.INCOME else AmountTone.EXPENSE)
                    }
                    BasicText(joinLine(dayMonth(tx.occurredAt), data.wallets.firstOrNull { it.id == tx.walletId }?.name), style = Type.caption().copy(color = Ink.muted))
                }
            }
            FieldTitle(t(UiKey.PROFILE_DEBT_WHO))
            ChoiceFlow { for (p in data.people.filter { !it.archived }) Choice(p.name, who == p.id, { who = p.id; err = null }, filled = true, textSize = 13) }
        }
        Note(t(UiKey.NUQOOT_KIND_NOTE))
        ErrorLine(err)
        val count = (plan as? GiftPlan.Ready)?.amounts?.size ?: 0
        PrimaryButton(
            when { link -> t(UiKey.NUQOOT_SAVE_LINK); count > 0 -> t(UiKey.NUQOOT_SAVE_N, countOf(count, Noun.GIFTS_OBJ)); else -> t(UiKey.NUQOOT_SAVE) },
            loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            onClick = {
                val gifts = space.people.gifts
                if (link) {
                    val tx = cand ?: run { err = t(UiKey.NUQOOT_NEED_TXN); return@PrimaryButton }
                    val person = who ?: run { err = t(UiKey.NUQOOT_NEED_WHO); return@PrimaryButton }
                    scope.launch {
                        busy = true
                        val r = runCatching { gifts.link(e.id, tx, if (e.mine) EventRole.GIFT_IN else EventRole.GIFT_OUT, person) }
                        busy = false
                        r.onSuccess { PeopleChanges.bump(); toaster.show(t(UiKey.NUQOOT_LINKED)); done() }.onFailure { err = it.message }
                    }
                    return@PrimaryButton
                }
                val w = wallet ?: run { err = t(UiKey.NUQOOT_NO_WALLET); return@PrimaryButton }
                when (plan) {
                    is GiftPlan.Bad -> err = plan.message
                    is GiftPlan.Ready -> scope.launch {
                        busy = true
                        val r = runCatching { saveGifts(space.people, e.id, if (e.mine) Direction.IN else Direction.OUT, w, space.shell.today(), plan) }
                        busy = false
                        PeopleChanges.bump()
                        r.onSuccess { toaster.show(t(UiKey.NUQOOT_DONE, countOf(plan.amounts.size, Noun.GIFTS), walletName)); done() }.onFailure { err = it.message }
                    }
                }
            },
        )
    }
}
