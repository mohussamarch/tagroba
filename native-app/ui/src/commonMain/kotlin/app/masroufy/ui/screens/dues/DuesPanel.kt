package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** صفوف «حسب الموعد» الظاهرة قبل «اعرض الكل» (النموذج بيعرض ٥). */
private const val AGENDA_SHOWN = 6

/**
 * خانة «المستحقات» (تالت خانة في مبدّل «العمليات» — `Slots.DUES`، لوحة `Dues`): «لك» و«عليك» **من غير مقاصة** · سطر الشهر ·
 * كل اللي ليه ميعاد بالترتيب (متأخر ⇒ قريب ⇒ قادم) · المربعات الأربعة · أرباح التمويل للعلم. بتترسم جوه قايمة العمليات (`Column` مش قايمة).
 */
@Composable
fun DuesPanel() {
    val space = LocalSpace.current
    val deps = space.dues
    val load = rememberLoad(deps) {
        val today = space.shell.today()
        coroutineScope {
            val period = deps.period(today)
            val view = async { deps.loadDues.load(today, period, space.space.currency, null) }
            val people = async { deps.people.listWithBalances() }
            val roscas = async { deps.roscas.list(today) }
            val plans = async { deps.installments.list(today) }
            val recurring = async { deps.recurring.load(today) }
            duesPanelUi(view.await(), DuesCounts.of(people.await(), roscas.await(), plans.await(), recurring.await()), period, today, space.space.currency)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val s = load.value) {
            Load.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Skeleton(Modifier.weight(1f).height(120.dp))
                    Skeleton(Modifier.weight(1f).height(120.dp))
                }
                Skeleton(Modifier.fillMaxWidth().height(280.dp))
            }
            Load.Failed -> LoadFailed(load::reload)
            is Load.Ready -> if (s.value.empty) EmptyState(t(TextKey.DUES_EMPTY_TITLE), t(TextKey.DUES_EMPTY_BODY)) else DuesMain(s.value)
        }
    }
}

@Composable
private fun DuesMain(ui: DuesPanelUi) {
    val nav = LocalNavigator.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideCard(Modifier.weight(1f), t(TextKey.DUES_FOR_YOU), ui.forYouTotal, ui.forYou, ui.currency, forYou = true) { nav.push(DuesDebtsRoute(DebtSide.FOR_YOU)) }
        SideCard(Modifier.weight(1f), t(TextKey.DUES_ON_YOU), ui.onYouTotal, ui.onYou, ui.currency, forYou = false) { nav.push(DuesDebtsRoute(DebtSide.ON_YOU)) }
    }
    BasicText(t(TextKey.DUES_NO_NETTING), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.selected).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(ui.monthLabel, style = Type.of(13, FontWeight.Bold))
        BasicText(
            t(TextKey.DUES_MONTH_LINE, amountLabel(ui.monthPayMinor, ui.currency), amountLabel(ui.monthReceiveMinor, ui.currency)),
            Modifier.weight(1f), style = Type.of(13).copy(textAlign = TextAlign.End),
        )
    }
    if (ui.agenda.isNotEmpty()) AgendaList(ui.agenda, nav)
    Tiles(ui.tiles, nav)
    ui.financingMinor?.let { BasicText(t(TextKey.DUES_FINANCING, amountLabel(it, ui.currency)), style = Type.caption().copy(color = Ink.muted)) }
}

@Composable
private fun SideCard(modifier: Modifier, title: String, total: Long?, lines: List<DuesLineUi>, currency: Currency, forYou: Boolean, onClick: () -> Unit) {
    FloatingCard(modifier, contentPadding = PaddingValues(14.dp), onClick = onClick, clickLabel = title) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title, style = Type.bodyBold())
            AmountText(total, currency, size = 20, color = if (total == null) null else if (forYou) Ink.income else Ink.expense, showCurrency = true)
            for (l in lines) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(l.label, Modifier.weight(1f), style = Type.of(11).copy(color = Ink.muted), maxLines = 1)
                AmountText(l.minor, currency, size = 11, weight = FontWeight.Normal, showCurrency = false, color = Ink.muted)
            }
        }
    }
}

@Composable
private fun AgendaList(rows: List<AgendaRowUi>, nav: Navigator) {
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) rows else rows.take(AGENDA_SHOWN)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.DUES_LIST_TITLE), style = Type.section())
        CardList {
            shown.forEachIndexed { i, row ->
                if (i > 0) Divider()
                DueRow(row) { open(nav, row.target) }
            }
        }
        if (rows.size > AGENDA_SHOWN) TonalButton(t(if (all) TextKey.DUES_SHOW_LESS else TextKey.DUES_SHOW_ALL), { all = !all }, Modifier.fillMaxWidth(), height = 44.dp)
    }
}

@Composable
private fun Tiles(tiles: List<DuesTileUi>, nav: Navigator) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pair in tiles.chunked(2)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (tile in pair) FloatingCard(
                Modifier.weight(1f).heightIn(min = 84.dp), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(12.dp),
                onClick = { open(nav, tile.target) }, clickLabel = tile.label,
            ) {
                BasicText(sentenceNumber(tile.count), style = Type.of(20, FontWeight.Bold).copy(color = Ink.primary))
                BasicText(tile.label, style = Type.bodyBold())
            }
        }
    }
}

internal fun open(nav: Navigator, target: DuesTarget) = when (target) {
    is DuesTarget.Debts -> nav.push(DuesDebtsRoute(target.side))
    DuesTarget.Subscriptions -> nav.push(SubscriptionsRoute)
    DuesTarget.Installments -> nav.push(InstallmentsRoute)
    DuesTarget.Roscas -> nav.push(RoscasRoute)
}
