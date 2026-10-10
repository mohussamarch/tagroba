package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.MatchingState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayMonth
import app.masroufy.core.jsTrim
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.CancellationException

/**
 * «قارن» (`ImportDuplicateSheet`): السطر الجديد من الكشف والعملية الموجودة جنب بعض · الفرق (المبلغ أو التاريخ) · سبب الاشتباه (جملة منع التكرار) ·
 * القرار: الشبيه «هي نفسها — تجاهلها» / «لا، أضفها» · التعارض «أبقِ القديم» (ما بنستبدلش من غير قرارك) · المكرر «حسنًا».
 * [onDecide] بياخد `true` = ضمّها للتسجيل، `false` = شيلها، `null` = من غير تغيير.
 */
@Composable
fun ImportDuplicateSheet(
    line: ReviewLineUi?,
    load: suspend (String) -> Transaction,
    currency: Currency,
    onDecide: (ReviewLineUi, Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    var old by remember(line) { mutableStateOf<Transaction?>(null) }
    LaunchedEffect(line) {
        val id = line?.matchedTransactionId ?: return@LaunchedEffect
        old = try {
            load(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val state = line?.state ?: MatchingState.SIMILAR
    val title = t(
        when (state) {
            MatchingState.CONFLICT -> UiKey.IMPORT_DUP_TITLE_CONFLICT
            MatchingState.DUPLICATE -> UiKey.IMPORT_DUP_TITLE_DUP
            else -> UiKey.IMPORT_DUP_TITLE_SIMILAR
        },
    )
    Sheet(line != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        val l = line ?: return@Sheet
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f), style = Type.of(17, FontWeight.Bold))
            Tag(t(stateLabel(state)), stateTone(state))
        }
        val o = old
        val diff = o?.let { diffOf(l.amountMinor, l.date, it.amountMinor, it.occurredAt) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Side(t(UiKey.IMPORT_DUP_NEW_SIDE), l.name, l.amountMinor, l.direction, l.date, t(UiKey.IMPORT_DUP_ROW_IN_FILE, t(UiKey.IMPORT_REVIEW_ROW, app.masroufy.core.sentenceNumber(l.lineNumber))), currency, diff, true, Modifier.weight(1f))
            if (o != null) {
                Side(t(UiKey.IMPORT_DUP_OLD_SIDE), jsTrim(o.rawMerchantName ?: o.rawDescription ?: ""), o.amountMinor, o.observedDirection, o.occurredAt, null, o.currency, diff, false, Modifier.weight(1f))
            }
        }
        if (diff != null) {
            BasicText(t(UiKey.IMPORT_DUP_DIFF, t(if (diff == DiffField.AMOUNT) UiKey.IMPORT_DUP_DIFF_AMOUNT else UiKey.IMPORT_DUP_DIFF_DATE)), style = Type.captionBold().copy(color = Ink.focus))
        }
        BasicText(
            l.reason,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp),
            style = Type.of(13, lineHeight = 1.7),
        )
        if (state == MatchingState.CONFLICT) BasicText(t(UiKey.IMPORT_DUP_CONFLICT_NOTE), style = Type.caption().copy(color = Ink.muted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                MatchingState.SIMILAR -> {
                    QuietButton(t(UiKey.IMPORT_DUP_SKIP), { onDecide(l, false) }, Modifier.weight(1f))
                    PrimaryButton(t(UiKey.IMPORT_DUP_ADD), { onDecide(l, true) }, Modifier.weight(1f))
                }
                MatchingState.CONFLICT -> PrimaryButton(t(UiKey.IMPORT_DUP_KEEP), { onDecide(l, null) }, Modifier.weight(1f))
                else -> PrimaryButton(t(UiKey.IMPORT_DUP_OK), { onDecide(l, null) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Side(
    label: String,
    name: String,
    amount: Halalas?,
    direction: Direction?,
    date: IsoDate?,
    source: String?,
    currency: Currency,
    diff: DiffField?,
    isNew: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (isNew) Ink.primary.copy(alpha = 0.06f) else Ink.text.copy(alpha = 0.04f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = Type.of(11, FontWeight.Bold).copy(color = Ink.muted))
        BasicText(name, style = Type.bodyBold(), maxLines = 2)
        val amountBox = if (diff == DiffField.AMOUNT) Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.alertBg).padding(horizontal = 6.dp) else Modifier
        AmountText(amount, currency, amountBox, tone = direction?.let(::toneOf) ?: app.masroufy.ui.components.AmountTone.PLAIN)
        if (date != null) {
            val dateBox = if (diff == DiffField.DATE) Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.alertBg).padding(horizontal = 6.dp) else Modifier
            BasicText(dayMonth(date), dateBox, style = Type.caption().copy(color = if (diff == DiffField.DATE) Ink.focus else Ink.muted))
        }
        if (source != null) BasicText(source, style = Type.of(11).copy(color = Ink.muted))
    }
}
