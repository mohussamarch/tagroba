package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.dayMonth
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentStyle
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ImportPreview
import app.masroufy.usecase.SmsBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@Composable
internal fun PasteCard(text: String, onText: (String) -> Unit, busy: Boolean, onRead: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 132.dp).clip(RoundedCornerShape(16.dp)).background(Ink.text.copy(alpha = 0.05f)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                if (text.isEmpty()) BasicText(t(UiKey.SMS_PASTE_HINT), style = Type.of(14, lineHeight = 1.7).copy(color = Ink.faded))
                BasicTextField(text, onText, Modifier.fillMaxWidth(), textStyle = Type.of(14, lineHeight = 1.7), cursorBrush = SolidColor(Ink.primary))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(t(UiKey.SMS_PASTE_FROM_CLIP), { clipboard.getText()?.text?.let(onText) }, Modifier.weight(1f))
                PrimaryButton(t(UiKey.SMS_PASTE_READ), onRead, Modifier.weight(1f), enabled = text.isNotBlank(), loading = busy)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodCard(
    ranges: List<SmsRange>,
    range: SmsRangeKind,
    onRange: (SmsRangeKind) -> Unit,
    senders: List<String>,
    off: Set<String>,
    onToggle: (String) -> Unit,
    busy: Boolean,
    onRead: () -> Unit,
) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.SMS_PASTE_RANGE), style = Type.of(13, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in ranges) {
                    val label = when (r.kind) {
                        SmsRangeKind.MONTH -> t(UiKey.SMS_PASTE_RANGE_MONTH, dayMonth(r.from))
                        SmsRangeKind.WEEK -> t(UiKey.SMS_PASTE_RANGE_WEEK)
                        SmsRangeKind.DAYS30 -> t(UiKey.SMS_PASTE_RANGE_30)
                    }
                    SelectChip(label, r.kind == range, { onRange(r.kind) }, height = 44.dp)
                }
            }
            BasicText(t(UiKey.SMS_PASTE_BANKS), style = Type.of(13, FontWeight.Bold))
            if (senders.isEmpty()) BasicText(t(UiKey.SMS_PASTE_NO_SENDERS), style = Type.caption().copy(color = Ink.muted))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in senders) SelectChip(s, s !in off, { onToggle(s) }, height = 44.dp)
            }
            PrimaryButton(t(UiKey.SMS_PASTE_READ_PERIOD), onRead, Modifier.fillMaxWidth(), enabled = senders.any { it !in off }, loading = busy)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResultsBlock(
    result: SmsReadUi,
    period: Boolean,
    currency: Currency,
    wallets: List<Wallet>,
    wallet: Wallet?,
    onWallet: (Wallet) -> Unit,
    busy: Boolean,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val understood = t(UiKey.SMS_PASTE_SUMMARY, opsCount(result.rows.size), msgsCount(result.skippedCount))
        BasicText(if (period) t(UiKey.SMS_PASTE_SUMMARY_READ, msgsCount(result.messages), understood) else understood, style = Type.of(15, FontWeight.Bold))
        if (result.truncated) BasicText(t(UiKey.SMS_PASTE_TRUNCATED), style = Type.caption().copy(color = Ink.focus))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(t(UiKey.SMS_PASTE_WALLET_Q), style = Type.of(13, FontWeight.Bold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (w in wallets.filter { it.kind != "cash" }) SelectChip(w.name, w.id == wallet?.id, { onWallet(w) }, height = 44.dp)
            }
        }
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            result.rows.forEachIndexed { i, r ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(r.merchant, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
                        val dup = r.state == PastedState.DUPLICATE
                        AmountText(r.amountMinor, currency, tone = toneOf(r.direction), size = 14, color = if (dup) Ink.faded else null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (r.state) {
                            PastedState.NEW -> Tag(t(UiKey.SMS_PASTE_NEW), TagTone.NEW)
                            PastedState.DUPLICATE -> Tag(t(UiKey.SMS_PASTE_DUP), TagTone.MUTED)
                            PastedState.SIMILAR, PastedState.CONFLICT -> Tag(t(UiKey.SMS_PASTE_SIMILAR), TagTone.AMBER)
                            PastedState.PENDING -> Unit
                        }
                        BasicText(dayMonth(r.date), style = Type.caption().copy(color = Ink.muted))
                    }
                }
            }
        }
        if (result.skipped.isNotEmpty()) {
            BasicText(t(UiKey.SMS_PASTE_SKIPPED, msgsCount(result.skippedCount)), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                result.skipped.forEachIndexed { i, s ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(s.reason, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
                        BasicText(msgsCount(s.count), style = Type.caption().copy(color = Ink.muted))
                    }
                }
            }
        }
        val ready = wallet != null && result.toSave > 0
        PrimaryButton(t(UiKey.SMS_PASTE_SAVE, opsCount(result.toSave)), onSave, Modifier.fillMaxWidth(), enabled = ready, loading = busy)
        val note = when {
            wallet == null -> UiKey.SMS_PASTE_PICK_WALLET
            result.duplicates > 0 -> UiKey.SMS_PASTE_NOTE_DUPS
            else -> UiKey.SMS_PASTE_NOTE_NOTHING
        }
        BasicText(t(note), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
}
