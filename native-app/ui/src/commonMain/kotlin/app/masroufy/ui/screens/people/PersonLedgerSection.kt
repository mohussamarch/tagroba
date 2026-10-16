package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/**
 * صفحة الشخص (جولة r05): الأرصدة (ليك · عليك · الصافي لكل عملة) · «السجل بينا» كله الأحدث الأول (السطر بيفتح العملية أو الدين) · الديون المفتوحة ·
 * اللي اتقفلت (مطوية). الصفحة الفاضية بس لو مفيش ولا حاجة فعلًا — بسطر بيقول تعمل إيه.
 */
internal fun LazyListScope.personLedgerItems(ui: LedgerUi, modifier: Modifier, onTxn: (Id) -> Unit, onDebt: (Id) -> Unit) {
    if (ui.totals.isNotEmpty()) item(key = "ledger-totals") {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tot in ui.totals) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalBox(t(UiKey.PLINK_TOTAL_LAK), tot.lakMinor, tot, AmountTone.INCOME, Modifier.weight(1f))
                TotalBox(t(UiKey.PLINK_TOTAL_ALEK), tot.alekMinor, tot, AmountTone.EXPENSE, Modifier.weight(1f))
                TotalBox(t(UiKey.PLINK_TOTAL_NET), tot.netMinor, tot, if (tot.netMinor >= 0L) AmountTone.INCOME else AmountTone.EXPENSE, Modifier.weight(1f))
            }
        }
    }
    item(key = "ledger-history") {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.PLINK_PAGE_HISTORY), style = Type.section())
            FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (ui.rows.isEmpty()) {
                    val line = if (ui.empty) UiKey.PLINK_PAGE_EMPTY else UiKey.PERSON_PAGE_HISTORY_EMPTY
                    BasicText(t(line), Modifier.padding(vertical = 16.dp), style = Type.body().copy(color = Ink.muted))
                }
                ui.rows.forEachIndexed { i, r ->
                    LineRow(r.title, r.sub, r.amountMinor, r.currency, r.tone, i == ui.rows.lastIndex) {
                        r.transactionId?.let(onTxn) ?: r.obligationId?.let(onDebt)
                    }
                }
            }
        }
    }
    if (ui.open.isNotEmpty() || ui.settled.isNotEmpty()) item(key = "ledger-debts") {
        var showSettled by remember { mutableStateOf(false) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.PLINK_PAGE_OPEN_DEBTS), style = Type.section())
            FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (ui.open.isEmpty()) BasicText(t(UiKey.PLINK_PAGE_NO_OPEN), Modifier.padding(vertical = 16.dp), style = Type.body().copy(color = Ink.muted))
                ui.open.forEachIndexed { i, d -> LineRow(d.title, d.sub, d.amountMinor, d.currency, d.tone, i == ui.open.lastIndex) { onDebt(d.obligationId) } }
            }
            if (ui.settled.isNotEmpty()) {
                ExpandRow(t(UiKey.PLINK_PAGE_SETTLED, app.masroufy.core.sentenceNumber(ui.settled.size)), showSettled, { showSettled = !showSettled })
                if (showSettled) FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ui.settled.forEachIndexed { i, d -> LineRow(d.title, d.sub, d.amountMinor, d.currency, d.tone, i == ui.settled.lastIndex) { onDebt(d.obligationId) } }
                }
            }
        }
    }
}

@Composable
private fun TotalBox(title: String, minor: Long, tot: LedgerTotalsUi, tone: AmountTone, modifier: Modifier) {
    FloatingCard(modifier, shape = RoundedCornerShape(Radius.control), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        BasicText(title, style = Type.caption().copy(color = Ink.muted))
        AmountText(minor, tot.currency, Modifier.fillMaxWidth(), size = 16, color = tone.color)
    }
}

@Composable
private fun LineRow(title: String, sub: String, minor: Long, currency: app.masroufy.core.Currency, tone: AmountTone, last: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Rowed(last, Modifier.pressScale(press).tap(press, label = title, onClick = onClick)) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Type.bodyBold())
            BasicText(sub, style = Type.caption().copy(color = Ink.muted))
        }
        AmountText(minor, currency, tone = tone, showCurrency = false)
        LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
    }
}
