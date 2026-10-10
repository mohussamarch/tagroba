package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.ImportBatch
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.RevertPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * «إرجاع «الملف»» (`RevertBatchSheet`): معاينة من غير كتابة (`RevertImportBatch.plan`) ⇒ سيُحذف كام · سيبقى كام · اللي هيفضل وليه (تسوية · تقسيم ·
 * مستحقات · نقوط · استثمار · مصدر آخر — الجملة من المنطق) · أول 4 من اللي هيتمسح · الروابط اللي هتتشال ⇒ «أرجِع الدفعة» (`execute` ذري).
 * المعرّفات تالفة ⇒ «تعذّر إرجاع هذه الدفعة» بسببه.
 */
@Composable
fun RevertBatchSheet(batch: ImportBatch?, deps: ImportsDeps, onDone: (RevertPlan) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ui by remember(batch) { mutableStateOf<RevertUi?>(null) }
    var error by remember(batch) { mutableStateOf<String?>(null) }
    var running by remember(batch) { mutableStateOf(false) }
    LaunchedEffect(batch) {
        val b = batch ?: return@LaunchedEffect
        try {
            val plan = deps.batches.plan(b.id)
            val details = revertDetailIds(plan).mapNotNull { id -> runCatching { deps.transactions.load(id).transaction }.getOrNull()?.let { id to it } }.toMap()
            ui = revertUi(plan, details)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: t(UiKey.IMPORTS_LOAD_FAILED)
        }
    }
    val title = batch?.let { t(UiKey.REVERT_SHEET_TITLE, if (it.sourceType == app.masroufy.core.ImportSourceType.SMS) t(UiKey.BANK_SMS_TITLE) else it.fileName) }.orEmpty()
    Sheet(batch != null, { if (!running) onDismiss() }, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        val u = ui
        when {
            error != null -> {
                TintedPanel(PanelTone.AMBER, radius = 16.dp) {
                    PanelTitle(t(UiKey.REVERT_SHEET_FAILED), PanelTone.AMBER)
                    BasicText(error.orEmpty(), style = Type.of(13, lineHeight = 1.7))
                }
                QuietButton(t(UiKey.IMPORT_DUP_OK), onDismiss, Modifier.fillMaxWidth())
            }
            u == null -> {
                BasicText(t(UiKey.REVERT_SHEET_LOADING), style = Type.of(13).copy(color = Ink.muted))
                Skeleton(Modifier.fillMaxWidth().height(64.dp), radius = 16.dp)
                Skeleton(Modifier.fillMaxWidth().height(140.dp), radius = 16.dp)
            }
            u.blocked -> {
                TintedPanel(PanelTone.AMBER, radius = 16.dp) {
                    PanelTitle(t(UiKey.REVERT_SHEET_FAILED), PanelTone.AMBER)
                    BasicText(t(TextKey.REVERT_BLOCKED), style = Type.of(13, lineHeight = 1.7))
                }
                QuietButton(t(UiKey.IMPORT_DUP_OK), onDismiss, Modifier.fillMaxWidth())
            }
            else -> PlanBody(u)
        }
        if (u != null && !u.blocked && error == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(t(UiKey.STATEMENT_IMPORT_CANCEL), onDismiss, Modifier.weight(1f), enabled = !running)
                QuietButton(t(if (running) UiKey.REVERT_SHEET_RUNNING else UiKey.REVERT_SHEET_CONFIRM), {
                    val b = batch ?: return@QuietButton
                    if (running) return@QuietButton
                    running = true
                    scope.launch {
                        try {
                            onDone(deps.batches.execute(b.id))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: t(UiKey.IMPORTS_LOAD_FAILED)
                        }
                        running = false
                    }
                }, Modifier.weight(1f), danger = true, enabled = !running)
            }
        }
    }
}

@Composable
private fun PlanBody(u: RevertUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountTile(t(UiKey.REVERT_SHEET_WILL_DELETE), opsCount(u.deleteCount), Ink.expense, Ink.expense.copy(alpha = 0.08f), Modifier.weight(1f))
        CountTile(t(UiKey.REVERT_SHEET_WILL_KEEP), if (u.kept.isEmpty()) t(UiKey.REVERT_SHEET_NOTHING) else opsCount(u.kept.size), Ink.primary, Ink.selected, Modifier.weight(1f))
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (u.kept.isNotEmpty()) {
            BasicText(t(UiKey.REVERT_SHEET_KEPT_TITLE), style = Type.of(13, FontWeight.Bold))
            for (k in u.kept) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(k.txn.name ?: t(TextKey.NOT_AVAILABLE), Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
                        TxnAmount(k.txn)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Tag(t(keptLabel(k.decision)), TagTone.NEW)
                        BasicText(k.reason, Modifier.weight(1f), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
                    }
                }
            }
        } else {
            TintedPanel(PanelTone.MINT, radius = 14.dp) { BasicText(t(UiKey.REVERT_SHEET_CLEAN), style = Type.of(13).copy(color = Ink.primary)) }
        }
        if (u.deleted.isNotEmpty()) {
            BasicText(t(UiKey.REVERT_SHEET_DELETE_TITLE), style = Type.of(13, FontWeight.Bold))
            u.deleted.forEachIndexed { i, d ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(d.name ?: t(TextKey.NOT_AVAILABLE), Modifier.weight(1f), style = Type.of(13), maxLines = 1)
                    TxnAmount(d)
                }
            }
            if (u.more > 0) BasicText(t(UiKey.REVERT_SHEET_MORE, opsCount(u.more)), style = Type.caption().copy(color = Ink.muted))
        }
        BasicText(
            if (u.unlink > 0) t(UiKey.REVERT_SHEET_UNLINK, countText(u.unlink, UiKey.REVERT_SHEET_LINKS_ONE, UiKey.REVERT_SHEET_LINKS_TWO, UiKey.REVERT_SHEET_LINKS_FEW, UiKey.REVERT_SHEET_LINKS_MANY))
            else t(UiKey.REVERT_SHEET_UNTOUCHED),
            style = Type.caption().copy(color = Ink.muted),
        )
    }
}

@Composable
private fun TxnAmount(d: RevertTxnUi) {
    val currency = d.currency
    if (currency == null) BasicText(t(TextKey.NOT_AVAILABLE), style = Type.caption().copy(color = Ink.muted))
    else AmountText(d.amountMinor, currency, tone = d.direction?.let(::toneOf) ?: app.masroufy.ui.components.AmountTone.PLAIN, size = 13)
}

@Composable
private fun CountTile(label: String, value: String, ink: Color, bg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
        BasicText(label, style = Type.of(11).copy(color = ink))
        BasicText(value, style = Type.of(17, FontWeight.Bold).copy(color = ink))
    }
}
