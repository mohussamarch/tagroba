package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «تفاصيل الجمعية» (`RoscaDetail`): البطاقة البترولية (موقفك · المرحلة · الأدوار والقسط · دفعت N من M · دورك) · الأدوار ومواعيدها (٤ حوالين
 * الجاي + دورك، و«كل الأدوار») · التحليل من `ManageRoscas.forecast` (مواعيد القبض · الربح · الإجمالي · قبل وبعد القبض · أكبر مبلغ وأكبر دين —
 * «غير متاح» لو دورك مش معروف).
 */
@Composable
fun RoscaDetailScreen(roscaId: String) {
    val space = LocalSpace.current
    val deps = space.dues
    val load = rememberLoad(deps, roscaId) {
        val today = space.shell.today()
        deps.roscas.list(today).firstOrNull { it.rosca.id == roscaId }?.let { roscaDetailUi(it, deps.roscas.forecast(roscaId), today) }
    }
    val ui = (load.value as? Load.Ready)?.value
    DuesScaffold(ui?.card?.name ?: t(TextKey.ROSCAS_TITLE)) {
        when (val s = load.value) {
            Load.Loading -> item { LoadingBlocks(190, 260) }
            Load.Failed -> item { LoadFailed(load::reload) }
            is Load.Ready -> {
                val d = s.value
                if (d == null) {
                    item { EmptyState(t(TextKey.ROSCA_NOT_FOUND)) }
                    return@DuesScaffold
                }
                item(key = "hero") { RoscaHero(d) }
                item(key = "turns") { Turns(d) }
                item(key = "analysis") { Analysis(d) }
                item(key = "kind") { BasicText(t(TextKey.ROSCA_KIND_NOTE), style = Type.caption().copy(color = Ink.muted)) }
            }
        }
    }
}

@Composable
private fun RoscaHero(d: RoscaDetailUi) {
    val c = d.card
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(d.heroLabel, Modifier.weight(1f), style = Type.body().copy(color = Ink.onHeroMuted))
                StatusChip(c.stage, c.stageChip, onHero = true)
            }
            HeroAmount(d.heroMinor, c.currency, Modifier.fillMaxWidth(), size = 34)
            BasicText(c.terms, style = Type.caption().copy(color = Ink.onHeroMuted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(c.progressText, style = Type.caption().copy(color = Color.White, fontWeight = FontWeight.Bold))
                BasicText(c.turnText, style = Type.caption().copy(color = Ink.onHeroMuted))
            }
            CountBar(c.paidCount, c.count, onHero = true)
        }
    }
}

@Composable
private fun Turns(d: RoscaDetailUi) {
    var all by rememberSaveable { mutableStateOf(false) }
    val rows = if (all) d.rows else d.focusRows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.ROSCA_TURNS_TITLE), style = Type.section())
        CardList {
            rows.forEachIndexed { i, r ->
                if (i > 0) Divider()
                TurnRow(r, d.card.currency)
            }
        }
        if (d.rows.size > d.focusRows.size) {
            TonalButton(
                if (all) t(TextKey.DUES_SHOW_LESS) else t(TextKey.ROSCA_ALL_TURNS, app.masroufy.core.sentenceNumber(d.rows.size)),
                { all = !all }, Modifier.fillMaxWidth(), height = 44.dp,
            )
        }
    }
}

@Composable
private fun TurnRow(r: TurnRowUi, currency: Currency) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val dot = Modifier.size(32.dp).clip(CircleShape)
        val (bg, ink) = when {
            r.mine -> Modifier.background(Brush.linearGradient(listOf(Ink.heroStart, Ink.heroEnd))) to Color.White
            r.paid -> Modifier.background(Ink.selected) to Ink.primary
            r.late -> Modifier.background(Ink.alertBg).insetRing(CircleShape, 1.5.dp, Ink.focus.copy(alpha = 0.45f)) to Ink.focus
            else -> Modifier.background(Color(0x0F193D33)) to Ink.muted
        }
        Box(dot.then(bg), contentAlignment = Alignment.Center) { BasicText(r.num, style = Type.of(13, FontWeight.Bold).copy(color = ink)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(r.date, style = Type.bodyBold())
            BasicText(
                r.status,
                style = Type.caption().copy(
                    color = if (r.mine) Ink.income else if (r.late) Ink.focus else Ink.muted,
                    fontWeight = if (r.mine || r.late) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AmountText(r.payMinor, currency, size = 14, tone = app.masroufy.ui.components.AmountTone.EXPENSE, showCurrency = false, color = if (r.paid) Ink.muted else Ink.expense)
            r.after?.let { BasicText(it, style = Type.of(11).copy(color = Ink.muted)) }
        }
    }
}

@Composable
private fun Analysis(d: RoscaDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.ROSCA_ANALYSIS), style = Type.section())
        CardList {
            d.stats.forEachIndexed { i, st ->
                if (i > 0) Divider()
                LabelValue(st.label, note = st.note.ifEmpty { null }) { StatValueView(st.value, d.card.currency) }
            }
        }
        BasicText(d.note, style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
internal fun StatValueView(v: StatValue, currency: Currency) {
    when (v) {
        StatValue.NA -> AmountText(null, currency, size = 15)
        is StatValue.Text -> ValueText(v.text)
        is StatValue.Money -> if (v.signed) GainValue(v.minor, currency, size = 15) else AmountText(v.minor, currency, size = 15)
    }
}
