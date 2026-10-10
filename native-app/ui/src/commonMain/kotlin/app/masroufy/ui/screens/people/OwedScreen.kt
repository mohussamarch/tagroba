package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.parseIsoDate
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «لك» و«عليك» بالكامل (لوحتي `OwedToYou` و`YouOwe`): المجموع فوق (لكل عملة لوحدها، من غير مقاصة مع الطرف التاني) والناس تحت
 * بالأهم: اللي فات موعده، ثم اللي قرّب. الضغط على أي حد ⇒ ملفه.
 */
private const val DUE_HORIZON_DAYS = 400

@Composable
internal fun OwedScreen(side: OwedSide) {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps, side) { mutableStateOf<Load<OwedUi>>(Load.Loading) }
    LaunchedEffect(deps, version, retry) {
        load = loadOf {
            val today = deps.shell.today()
            val rows = deps.people.people.listWithBalances()
            val overview = runCatching { deps.people.overview.forSpace(today, deps.space.id) }.getOrNull()
            val until = dayNumberToIso(toDayNumber(parseIsoDate(today)) + DUE_HORIZON_DAYS)
            val dues = runCatching { deps.people.dues.dueItems(today, until) }.getOrDefault(emptyList())
            owedUi(side, rows, overview, deps.space.id, dues, today)
        }
    }
    val owed = side == OwedSide.OWED_TO_YOU
    InnerScaffold(t(if (owed) UiKey.OWED_TITLE_OWED else UiKey.OWED_TITLE_OWE)) {
        when (val l = load) {
            Load.Loading -> item { Skeleton(Modifier.fillMaxWidth().height(140.dp), radius = 28.dp) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(UiKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val ui = l.value
                item(key = "total") { TotalHero(ui, owed) }
                item(key = "note") { BasicText(t(UiKey.OWED_ORDER_NOTE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted)) }
                item(key = "rows") {
                    FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        if (ui.rows.isEmpty()) {
                            BasicText(t(if (owed) UiKey.OWED_EMPTY_OWED else UiKey.OWED_EMPTY_OWE), Modifier.fillMaxWidth().height(56.dp), style = Type.body().copy(color = Ink.muted))
                        }
                        ui.rows.forEachIndexed { i, r -> OwedRow(r, owed, i == ui.rows.lastIndex) { nav.push(PersonProfileRoute(r.personId)) } }
                    }
                }
                item(key = "foot") { Note(t(UiKey.OWED_FOOT)) }
            }
        }
    }
}

@Composable
private fun TotalHero(ui: OwedUi, owed: Boolean) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(t(if (owed) UiKey.OWED_TOTAL_OWED else UiKey.OWED_TOTAL_OWE), style = Type.body().copy(color = Ink.onHeroMuted))
            val lines = ui.totals
            if (lines == null) HeroAmount(null, app.masroufy.core.Currency.SAR, Modifier.fillMaxWidth(), size = 34)
            else if (lines.isEmpty()) HeroAmount(0, LocalSpace.current.space.currency, Modifier.fillMaxWidth(), size = 34)
            else lines.forEach { HeroAmount(it.minor, it.currency, Modifier.fillMaxWidth(), size = 34) }
            val people = countOf(ui.people, Noun.PEOPLE)
            BasicText(t(if (owed) UiKey.OWED_SUB_OWED else UiKey.OWED_SUB_OWE, people), style = Type.caption().copy(color = Ink.onHeroMuted))
        }
    }
}

@Composable
private fun OwedRow(r: OwedRowUi, owed: Boolean, last: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Rowed(last, Modifier.pressScale(press).tap(press, label = r.name, onClick = onClick)) {
        InitialCircle(r.name, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(r.name, style = Type.bodyBold())
            BasicText(r.reason, style = Type.caption().copy(color = Ink.muted))
            r.signalText?.let { text ->
                val (ink, bg) = when (r.signal) {
                    OwedSignal.OVERDUE -> Ink.expense to PeopleInk.expenseSoft
                    OwedSignal.SOON -> Ink.primary to Ink.selected
                    else -> Ink.muted to PeopleInk.mutedSoft
                }
                Pill(text, ink, bg)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AmountText(r.amountMinor, r.currency, size = 16, showCurrency = false, color = if (owed) AmountTone.INCOME.color else AmountTone.EXPENSE.color)
            BasicText(r.due, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}
