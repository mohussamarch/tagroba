package app.masroufy.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.EconomicKind
import app.masroufy.core.TextKey
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mintSheen
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.HistoryPreview
import kotlinx.coroutines.launch

/** كارت عملية: مفتوحة (المقترح + تأكيد + البدايل) أو اتأكدت («✓ X» + «تراجع» على أخضر فاتح بلمعة). */
@Composable
internal fun ReviewItemCard(item: ReviewItem, chosen: EconomicKind?, onAccept: (ReviewItem, EconomicKind) -> Unit, onUndo: (ReviewItem) -> Unit) {
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    BasicText(item.name, style = Type.bodyBold(), maxLines = 1)
                    BasicText(item.sub, style = Type.caption().copy(color = Ink.muted))
                }
                AmountText(item.amountMinor, item.currency, tone = item.tone)
            }
            if (chosen != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(t(TextKey.REVIEW_QUEUE_CHOSEN, kindLabel(chosen)), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold).copy(color = Ink.income))
                    TonalButton(t(TextKey.REVIEW_QUEUE_UNDO), onClick = { onUndo(item) }, height = 44.dp)
                }
            } else {
                BasicText(item.note, style = Type.caption().copy(color = Ink.muted))
                item.suggestion?.let { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.selected).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(t(TextKey.REVIEW_QUEUE_SUGGESTED), style = Type.caption().copy(color = Ink.muted))
                        BasicText(kindLabel(s), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold))
                        PrimaryButton(t(TextKey.REVIEW_QUEUE_ACCEPT), onClick = { onAccept(item, s) }, height = 44.dp)
                    }
                }
                if (item.alternatives.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (alt in item.alternatives) SelectChip(kindLabel(alt), selected = false, onClick = { onAccept(item, alt) }, height = 44.dp)
                    }
                }
            }
        }
    }
    if (chosen != null) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.selected).mintSheen(true).padding(horizontal = 14.dp, vertical = 12.dp)) { body() }
    } else {
        FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) { body() }
    }
}

/** «طبّق قواعدك على السابق» (`ReviewHistory`) — سنة ورا من النهارده. المؤكد بإيدك ما بيتلمسش (المنطق نفسه بيسيبه). */
private const val RULES_DAYS = 365

private sealed interface RulesState {
    data object Idle : RulesState

    data object Loading : RulesState

    data class Preview(val preview: HistoryPreview, val categories: List<Category>) : RulesState

    data class Done(val changed: Int) : RulesState

    data class Failed(val message: String) : RulesState
}

@Composable
internal fun RulesCard() {
    val deps = LocalSpace.current
    val scope = rememberCoroutineScope()
    var state by remember(deps) { mutableStateOf<RulesState>(RulesState.Idle) }
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(TextKey.REVIEW_QUEUE_RULES_TITLE), style = Type.of(15, FontWeight.Bold))
            when (val s = state) {
                RulesState.Idle, RulesState.Loading -> {
                    BasicText(t(TextKey.REVIEW_QUEUE_RULES_BODY), style = Type.of(13).copy(color = Ink.muted))
                    TonalButton(t(TextKey.REVIEW_QUEUE_RULES_PREVIEW), onClick = {
                        if (state == RulesState.Loading) return@TonalButton
                        state = RulesState.Loading
                        scope.launch {
                            state = runCatching {
                                val today = deps.shell.today()
                                val from = dayNumberToIso(toDayNumber(parseIsoDate(today)) - RULES_DAYS)
                                RulesState.Preview(deps.home.history.preview(from, today), deps.home.categories.list())
                            }.getOrElse { RulesState.Failed(it.message ?: t(TextKey.SHELL_LOAD_FAILED)) }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = state != RulesState.Loading)
                }
                is RulesState.Preview -> {
                    val rows = ruleRows(s.preview.categoryPlan, s.categories)
                    val count = s.preview.categoryPlan.changed.size
                    BasicText(
                        if (count == 0) t(TextKey.REVIEW_QUEUE_RULES_NONE) else t(TextKey.REVIEW_QUEUE_RULES_COUNT, sentenceNumber(count)),
                        style = Type.of(13).copy(color = Ink.muted),
                    )
                    rows.forEachIndexed { i, (name, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText(name, style = Type.of(13))
                            BasicText(sentenceNumber(n), style = Type.of(13, FontWeight.Bold))
                        }
                        if (i < rows.lastIndex) Divider()
                    }
                    if (count > 0) PrimaryButton(t(TextKey.REVIEW_QUEUE_RULES_APPLY, sentenceNumber(count)), onClick = {
                        scope.launch {
                            state = runCatching {
                                RulesState.Done(deps.home.history.applyCategories(s.preview.rows.map { it.id }, s.preview.categoryPlan).changed.size)
                            }.getOrElse { RulesState.Failed(it.message ?: t(TextKey.REVIEW_QUEUE_SAVE_FAILED_BODY)) }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
                is RulesState.Done -> BasicText(t(TextKey.REVIEW_QUEUE_RULES_DONE, sentenceNumber(s.changed)), style = Type.of(13).copy(color = Ink.income))
                is RulesState.Failed -> {
                    BasicText(s.message, style = Type.of(13).copy(color = Ink.expense))
                    TonalButton(t(TextKey.SHELL_RETRY), onClick = { state = RulesState.Idle }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
