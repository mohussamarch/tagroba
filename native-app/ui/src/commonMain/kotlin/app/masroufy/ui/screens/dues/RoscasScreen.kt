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
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «الجمعيات» (`Roscas`): كارت لكل جمعية (الأدوار والقسط · المرحلة · دفعت N من M · دورك · دفعت/قبضت/الربح — الربح «غير متاح» لو دورك
 * مش معروف · موقفك) ⇒ تفاصيل الجمعية · «جمعية جديدة» ⇒ الأسئلة (`RoscaWizard`).
 */
@Composable
fun RoscasScreen() {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val load = rememberLoad(deps) { deps.roscas.list(space.shell.today()).map(::roscaCard) }
    DuesScaffold(t(TextKey.ROSCAS_TITLE)) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(200, 200) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                if (s.value.isEmpty()) item { EmptyState(t(TextKey.ROSCAS_EMPTY_TITLE), t(TextKey.ROSCAS_EMPTY_BODY)) }
                for (c in s.value) item(key = c.roscaId) { RoscaCard(c) { nav.push(RoscaDetailRoute(c.roscaId)) } }
                item(key = "new") { PrimaryButton(t(TextKey.ROSCAS_NEW), { nav.push(RoscaWizardRoute) }, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun RoscaCard(c: RoscaCardUi, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), onClick = onClick, clickLabel = c.name) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(c.name, style = Type.of(16, FontWeight.Bold))
                    BasicText(c.terms, style = Type.caption().copy(color = Ink.muted))
                }
                StatusChip(c.stage, c.stageChip)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(c.progressText, style = Type.caption().copy(color = Ink.muted))
                    BasicText(c.turnText, style = Type.captionBold())
                }
                CountBar(c.paidCount, c.count, onHero = false, fill = CategoryInk.savings)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact(Modifier.weight(1f), t(TextKey.ROSCAS_PAID)) { AmountText(c.paidMinor, c.currency, size = 14, showCurrency = false) }
                Fact(Modifier.weight(1f), t(TextKey.ROSCAS_GOT)) {
                    if (c.gotMinor == null) BasicText(t(TextKey.DUES_DASH), style = Type.of(14, FontWeight.Bold).copy(color = Ink.muted))
                    else AmountText(c.gotMinor, c.currency, size = 14, showCurrency = false)
                }
                Fact(Modifier.weight(1f), t(TextKey.ROSCAS_GAIN)) { GainValue(c.gainMinor, c.currency, size = 13) }
            }
            BasicText(c.position, style = Type.of(13, FontWeight.Bold).copy(color = if (c.positionPositive) Ink.income else Ink.muted))
        }
    }
}

@Composable
private fun Fact(modifier: Modifier, label: String, value: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = Type.of(11).copy(color = Ink.muted))
        value()
    }
}

/** الربح: «غير متاح» (دورك مش معروف) · «لا شيء» · +/− بلونه. */
@Composable
internal fun GainValue(gain: Halalas?, currency: Currency, size: Int = 16) {
    when {
        gain == null -> AmountText(null, currency, size = size)
        gain == 0L -> BasicText(t(TextKey.ROSCA_GAIN_NONE), style = Type.of(size, FontWeight.Bold).copy(color = Ink.muted))
        else -> AmountText(gain, currency, size = size, tone = if (gain > 0) AmountTone.INCOME else AmountTone.EXPENSE, showCurrency = false)
    }
}
