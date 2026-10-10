package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.ImportBatch
import app.masroufy.core.MatchingState
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ImportImpact

/** البطاقة البطلة: الملف والمحفظة · «أثر المحدد» (أو «سُجّلت») · على المحفظة · المصروف · الدخل — الأرقام من المعاينة أو «غير متاح». */
@Composable
internal fun ImpactHero(fileName: String, walletName: String, selected: Int, impact: ImportImpact?, done: ImportBatch?, currency: Currency) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(UiKey.IMPORT_REVIEW_HERO_META, fileName, walletName), style = Type.caption().copy(color = Ink.onHeroMuted))
            val title = if (done != null) t(UiKey.IMPORT_REVIEW_DONE_TITLE, opsCount(done.counts.imported)) else t(UiKey.IMPORT_REVIEW_IMPACT, opsCount(selected))
            BasicText(title, style = Type.of(18, FontWeight.Bold).copy(color = Color.White))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 2.dp)) {
                val delta = impact?.walletDeltaMinor
                val rows = listOf(
                    Triple(UiKey.IMPORT_REVIEW_ON_WALLET, delta, if ((delta ?: 0L) < 0L) AmountTone.EXPENSE else AmountTone.INCOME),
                    Triple(UiKey.IMPORT_REVIEW_EXPENSE, impact?.expenseMinor, AmountTone.EXPENSE),
                    Triple(UiKey.IMPORT_REVIEW_INCOME, impact?.incomeMinor, AmountTone.INCOME),
                )
                rows.forEachIndexed { i, (label, minor, tone) ->
                    if (i > 0) HeroDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BasicText(t(label), style = Type.caption().copy(color = Ink.onHeroMuted))
                        val ink = if (tone == AmountTone.EXPENSE) Ink.rose else Ink.mint
                        AmountText(minor, currency, tone = if (minor == 0L) AmountTone.PLAIN else tone, color = if (minor == null) Ink.onHeroMuted else ink)
                    }
                }
            }
            if (impact == null && done == null) BasicText(t(UiKey.IMPORT_REVIEW_IMPACT_NA), style = Type.caption().copy(color = Ink.onHeroMuted))
        }
    }
}

/** العدّادات الخمسة — الضغط بيصفّي السطور بالحالة دي (وتاني ضغطة بترجّع الكل). */
@Composable
internal fun Counters(u: ImportReviewUi, filter: MatchingState?, onFilter: (MatchingState) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (state in MatchingState.entries) {
            val tone = stateTone(state)
            val active = filter == state
            val press = rememberPress()
            val shape = RoundedCornerShape(16.dp)
            val surface = if (active) Modifier.background(tone.bg).insetRing(shape, 1.5.dp, tone.ink) else Modifier.background(Color.White)
            Column(
                Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).pressScale(press).clip(shape).then(surface).tap(press, onClick = { onFilter(state) }).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(sentenceNumber(u.count(state)), style = Type.of(18, FontWeight.Bold).copy(color = tone.ink))
                BasicText(t(stateLabel(state)), style = Type.of(11).copy(color = tone.ink), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewLines(
    lines: List<ReviewLineUi>,
    categories: List<Category>,
    selection: Set<Int>,
    locked: Boolean,
    currency: Currency,
    onToggle: (Int) -> Unit,
    onCompare: (ReviewLineUi) -> Unit,
) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
        lines.forEachIndexed { i, l ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                if (l.selectable) CheckBox(l.lineNumber in selection, locked) { onToggle(l.lineNumber) }
                else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).background(stateTone(l.state).ink)) }
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(l.name, Modifier.weight(1f), style = Type.bodyBold(), maxLines = 1)
                        val dir = l.direction
                        AmountText(l.amountMinor, currency, tone = if (dir != null) toneOf(dir) else AmountTone.PLAIN, size = if (l.amountMinor == null) 12 else 14)
                    }
                    val row = t(UiKey.IMPORT_REVIEW_ROW, sentenceNumber(l.lineNumber))
                    BasicText(l.date?.let { t(UiKey.IMPORT_REVIEW_DATE_ROW, dayMonth(it), row) } ?: row, style = Type.caption().copy(color = Ink.muted))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Tag(t(stateLabel(l.state)), stateTone(l.state))
                        if (l.selectable) Tag(t(UiKey.IMPORT_REVIEW_CAT_SRC, categoryName(l.categoryId, categories), t(sourceLabel(l.source))), TagTone.MUTED, bold = false)
                    }
                    if (l.state != MatchingState.NEW) BasicText(l.reason, style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
                    if (l.canCompare && !locked) SmallAction(t(UiKey.IMPORT_REVIEW_COMPARE)) { onCompare(l) }
                }
            }
        }
    }
}

@Composable
private fun CheckBox(on: Boolean, locked: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(Modifier.size(44.dp).tap(press, !locked, role = Role.Checkbox, onClick = onClick), contentAlignment = Alignment.Center) {
        val box = if (on) Modifier.background(Ink.primary) else Modifier.background(Color.White).border(1.5.dp, Ink.faded, RoundedCornerShape(7.dp))
        Box(Modifier.size(22.dp).alpha(if (locked) 0.5f else 1f).clip(RoundedCornerShape(7.dp)).then(box), contentAlignment = Alignment.Center) {
            if (on) LucideIcon(Lucide.CHECK, size = 16.dp, tint = Color.White)
        }
    }
}
