package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.DueItem
import app.masroufy.core.DuesTotals
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.PersonRow

/** اللي الشاشة بتحمّله مرة واحدة — والفلتر والبحث بيتطبقوا عليه من غير ما يحمّل تاني. */
internal class DebtsData(val people: List<PersonRow>, val totals: DuesTotals, val dues: List<DueItem>, val today: IsoDate)

/**
 * «الديون» (`DuesDebts` — من مربع «الديون» وكارتي «لك/عليك» في المستحقات): الكارتين فوق بيصفّوا (الضغط تاني يرجّع الكل) · بحث بالاسم ·
 * «لك» الأهم أولًا (فات موعده ثم الأقرب) · «عليك» بالموعد · الأمانات لوحدها · كل دين بيفتح تفاصيله. مفيش زرار تذكير (قرار المالك — التذكير إشعار).
 */
@Composable
fun DuesDebtsScreen(initialSide: DebtSide) {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    var side by rememberSaveable { mutableStateOf(initialSide) }
    var query by rememberSaveable { mutableStateOf("") }
    val load = rememberLoad(deps) {
        val today = space.shell.today()
        val view = deps.loadDues.load(today, deps.period(today), space.space.currency, null)
        DebtsData(deps.people.listWithBalances(), view.totals, debtDueItems(deps, today), today)
    }
    DuesScaffold(t(TextKey.DEBTS_TITLE), t(TextKey.DEBTS_SUB)) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks() }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val d = s.value
                val ui = duesDebtsUi(d.people, d.totals, d.dues, d.today, space.space.currency, side, query)
                if (ui.empty) {
                    item {
                        EmptyState(t(TextKey.DEBTS_EMPTY_TITLE), t(TextKey.DEBTS_EMPTY_BODY), action = {
                            PrimaryButton(t(TextKey.DEBTS_EMPTY_GO), { nav.switchTab(Tab.PEOPLE) })
                        })
                    }
                    return@DuesScaffold
                }
                item(key = "sides") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (sd in ui.sides) SideFilter(Modifier.weight(1f), sd, side == sd.side, ui.currency) { side = if (side == sd.side) DebtSide.ALL else sd.side }
                    }
                }
                item(key = "netting") { BasicText(t(TextKey.DUES_NO_NETTING), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center)) }
                item(key = "search") {
                    TextInput(
                        query, { query = it }, placeholder = t(TextKey.DEBTS_SEARCH_HINT), imeAction = ImeAction.Search, height = 52.dp,
                        trailing = { if (query.isNotEmpty()) ClearButton { query = "" } },
                    )
                }
                for (g in ui.groups) item(key = "g-${g.key}") { Group(g, ui.currency) { nav.push(DebtDetailRoute(it)) } }
                if (ui.noHitsTitle != null) item(key = "nohits") {
                    EmptyState(ui.noHitsTitle, ui.noHitsBody, action = {
                        TonalButton(t(TextKey.DUES_SHOW_ALL), { query = ""; side = DebtSide.ALL }, height = 44.dp)
                    })
                }
                item(key = "foot") { BasicText(t(TextKey.DEBTS_FOOT), style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
}

@Composable
private fun SideFilter(modifier: Modifier, sd: DebtSideUi, on: Boolean, currency: Currency, onClick: () -> Unit) {
    val forYou = sd.side == DebtSide.FOR_YOU
    val shape = RoundedCornerShape(22.dp)
    FloatingCard(
        modifier.then(if (on) Modifier.insetRing(shape, 2.dp, if (forYou) Ink.income else Ink.expense) else Modifier),
        contentPadding = PaddingValues(14.dp), onClick = onClick, clickLabel = sd.label,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(sd.label, style = Type.bodyBold())
                BasicText(sd.count, style = Type.of(11).copy(color = Ink.muted))
            }
            AmountText(sd.totalMinor, currency, size = 20, color = if (forYou) Ink.income else Ink.expense)
            BasicText(sd.hint, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun Group(g: DebtGroupUi, currency: Currency, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(g.title, style = Type.of(17, FontWeight.Bold))
            if (g.totalMinor != null) AmountText(g.totalMinor, currency, color = if (g.forYou) Ink.income else Ink.expense)
        }
        BasicText(g.order, style = Type.caption().copy(color = Ink.muted))
        CardList {
            g.rows.forEachIndexed { i, row ->
                if (i > 0) Divider()
                DebtRow(row) { onOpen(row.obligationId) }
            }
        }
    }
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    val press = rememberPress()
    val label = t(TextKey.DEBTS_SEARCH_CLEAR)
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted) }
}
