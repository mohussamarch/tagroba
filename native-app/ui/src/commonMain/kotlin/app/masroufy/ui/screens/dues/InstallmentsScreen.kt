package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «الأقساط» (`Installments`): البطاقة البترولية بالمتبقي عليك في الأقساط (`DuesTotals.installmentsLeftMinor`) · كارت لكل خطة (الجهة والنوع ·
 * القسط · دفعت N من M · القادم · المبلغ المستلم للتمويل أو «لم يُربط بعد») ⇒ تفاصيل الخطة · «+ خطة جديدة» ⇒ `InstallmentEdit`.
 */
@Composable
fun InstallmentsScreen() {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val load = rememberLoad(deps) {
        val today = space.shell.today()
        val totals = deps.loadDues.load(today, deps.period(today), space.space.currency, null).totals
        installmentsUi(deps.installments.list(today), totals, today, space.space.currency)
    }
    DuesScaffold(t(TextKey.INSTALLMENTS_TITLE), actions = { TonalButton(t(TextKey.INSTALLMENTS_NEW), { nav.push(InstallmentEditRoute()) }, height = 44.dp) }) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(130, 200) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val ui = s.value
                if (ui.cards.isEmpty()) {
                    item {
                        EmptyState(t(TextKey.INSTALLMENTS_EMPTY_TITLE), t(TextKey.INSTALLMENTS_EMPTY_BODY), action = {
                            PrimaryButton(t(TextKey.INSTALLMENTS_NEW), { nav.push(InstallmentEditRoute()) })
                        })
                    }
                    return@DuesScaffold
                }
                item(key = "hero") {
                    HeroCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(t(TextKey.INSTALLMENTS_LEFT), style = Type.body().copy(color = Ink.onHeroMuted))
                            HeroAmount(ui.leftMinor, ui.currency, Modifier.fillMaxWidth(), size = 30)
                            BasicText(ui.plansCount, style = Type.caption().copy(color = Ink.onHeroMuted))
                        }
                    }
                }
                for (c in ui.cards) item(key = c.planId) { PlanCard(c) { nav.push(InstallmentDetailRoute(c.planId)) } }
                item(key = "note") { BasicText(t(TextKey.INSTALLMENTS_NOTE), style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
}

@Composable
private fun PlanCard(c: PlanCardUi, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), onClick = onClick, clickLabel = c.name) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(c.name, style = Type.of(15, FontWeight.Bold))
                    BasicText(c.kindText, style = Type.caption().copy(color = Ink.muted))
                }
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(c.installmentMinor, c.currency)
                    BasicText(c.cycle, style = Type.of(11).copy(color = Ink.muted))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(c.progressText, style = Type.caption().copy(color = Ink.muted))
                BasicText(c.nextText, style = Type.caption().copy(color = if (c.late) Ink.focus else Ink.muted, fontWeight = if (c.late) FontWeight.Bold else FontWeight.Normal))
            }
            CountBar(c.paidCount, c.count, onHero = false, fill = CategoryInk.transport)
            if (c.financing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(TextKey.INSTALLMENTS_RECEIVED), style = Type.caption().copy(color = Ink.muted))
                if (c.receivedMinor != null) AmountText(c.receivedMinor, c.currency, size = 13)
                else BasicText(t(TextKey.INST_NOT_LINKED), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
            }
        }
    }
}
