package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.money
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.FixChoice
import app.masroufy.usecase.FixLine
import app.masroufy.usecase.FixPreview
import app.masroufy.usecase.FixResult
import app.masroufy.usecase.fixSummary
import kotlinx.coroutines.launch

/** «صلّح رصيدي» لمحفظة بنك (2026-10-16). */
data class FixMyBalanceRoute(val walletId: Id) : Route {
    override val name = "FixMyBalance"
}

/**
 * الأدوات الأربعة بالترتيب على شاشة واحدة (`FixMyBalance`): ١ الرسايل القديمة · ٢ المكرر · ٣ «كم معك؟» · ٤ قبل ⇒ بعد. كل سطر متعلّم ويتخطّى بضغطة،
 * و«طبّق» واحدة بنسخة قبلها و«تراجع عن الكل». الأرقام كلها من حالة الاستخدام (الشاشة ما بتحسبش).
 */
@Composable
fun FixMyBalanceScreen(walletId: Id) {
    val deps = LocalSpace.current
    val fix = deps.more.fixBalance
    val toaster = LocalToaster.current
    val changes = LocalDataChanges.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<FixPreview?>(null) }
    var failed by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(emptySet<Id>()) }
    var amountText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<FixResult?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(fix, tick) {
        val r = runCatching { fix?.preview(walletId, deps.shell.today()) }
        preview = r.getOrNull()
        failed = r.isFailure || fix == null
        skipped = emptySet()
    }
    InnerScaffold(t(UiKey.FIX_BAL_TITLE)) {
        val p = preview
        if (p == null) {
            item(key = "sk") { if (failed) BasicText(t(app.masroufy.core.TextKey.NOT_AVAILABLE), style = Type.body()) else Skeleton(Modifier.fillMaxWidth()) }
            return@InnerScaffold
        }
        val cur = p.currency
        val stated: Halalas? = amountText.trim().takeIf { it.isNotEmpty() }?.let { tryParseMoney(it, cur) }?.takeIf { it >= 0 }
        val choice = FixChoice(
            p.moves.map { it.key }.toSet() - skipped, p.unrecorded.map { it.key }.toSet() - skipped, p.pairs.map { it.key }.toSet() - skipped, stated,
        )
        item(key = "note") { BasicText(t(UiKey.FIX_BAL_NOTE), style = Type.of(13).copy(color = Ink.muted)) }
        val r = done
        if (r != null) {
            item(key = "done") {
                StepCard(t(UiKey.FIX_BAL_DONE)) {
                    SummaryRows(r.beforeMinor, r.afterMinor, r.unknownMinor, cur)
                    TonalButton(t(UiKey.FIX_BAL_UNDO), onClick = {
                        busy = true
                        scope.launch {
                            val failedCount = runCatching { fix?.undo(r.undo) }.getOrNull()
                            busy = false
                            toaster.show(if (failedCount == 0) t(UiKey.FIX_BAL_UNDONE) else t(UiKey.FIX_BAL_REFUSED, (failedCount ?: 1).toString()))
                            done = null; changes.changed(); tick++
                        }
                    }, height = 48.dp, modifier = Modifier.fillMaxWidth())
                }
            }
            return@InnerScaffold
        }
        item(key = "s1") {
            StepCard(t(UiKey.FIX_BAL_STEP1)) {
                val lines = p.moves + p.unrecorded
                StepLines(lines, skipped, cur, { skipped = toggle(skipped, it) }) { l ->
                    if (l in p.unrecorded) t(UiKey.FIX_BAL_NEW_LINE, dayMonth(l.date), money(l.amountMinor, cur))
                    else t(UiKey.FIX_BAL_MOVE_LINE, dayMonth(l.date), money(l.amountMinor, cur), l.toWalletName ?: "—")
                }
            }
        }
        item(key = "s2") {
            StepCard(t(UiKey.FIX_BAL_STEP2)) {
                StepLines(p.pairs, skipped, cur, { skipped = toggle(skipped, it) }) { l -> t(UiKey.FIX_BAL_PAIR_LINE, dayMonth(l.date), money(l.amountMinor, cur)) }
            }
        }
        item(key = "s3") {
            StepCard(t(UiKey.FIX_BAL_STEP3)) {
                TextInput(amountText, { amountText = it }, Modifier.fillMaxWidth(), placeholder = t(UiKey.FIX_BAL_STEP3_HINT))
            }
        }
        item(key = "s4") {
            val s = fixSummary(p, choice)
            StepCard(t(UiKey.FIX_BAL_STEP4)) {
                SummaryRows(s.beforeMinor, s.afterMinor, s.unknownMinor, cur)
                PrimaryButton(t(UiKey.FIX_BAL_APPLY), loading = busy, enabled = !busy, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true
                    scope.launch {
                        val res = runCatching { fix?.apply(p, choice) }
                        busy = false
                        val out = res.getOrNull()
                        when {
                            res.isFailure -> toaster.show(res.exceptionOrNull()?.message ?: t(UiKey.MORE_SAVE_FAILED), dark = true)
                            out == null -> toaster.show(t(UiKey.FIX_BAL_NOTHING))
                            else -> { done = out; changes.changed(); if (out.refused > 0) toaster.show(t(UiKey.FIX_BAL_REFUSED, out.refused.toString())) }
                        }
                    }
                })
            }
        }
    }
}

private fun toggle(set: Set<Id>, key: Id): Set<Id> = if (key in set) set - key else set + key

@Composable
private fun StepCard(title: String, content: @Composable () -> Unit) {
    FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(title, style = Type.of(15, FontWeight.Bold))
            content()
        }
    }
}

/** العدد وأثر المختار، وسطر لكل واحد بـ«تخطّي». */
@Composable
private fun StepLines(lines: List<FixLine>, skipped: Set<Id>, cur: Currency, onToggle: (Id) -> Unit, text: (FixLine) -> String) {
    if (lines.isEmpty()) {
        BasicText(t(UiKey.FIX_BAL_NONE), style = Type.of(13).copy(color = Ink.muted))
        return
    }
    val effect = app.masroufy.usecase.linesEffect(lines, skipped)
    BasicText(t(UiKey.FIX_BAL_COUNT_EFFECT, (lines.size - lines.count { it.key in skipped }).toString(), money(effect, cur)), style = Type.of(13, FontWeight.Bold))
    for (l in lines) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(text(l) + " · " + money(l.effectMinor, cur), Modifier.weight(1f), style = Type.of(12))
        val off = l.key in skipped
        SelectChip(t(if (off) UiKey.FIX_BAL_SKIPPED else UiKey.FIX_BAL_SKIP), off, { onToggle(l.key) })
    }
}

@Composable
private fun SummaryRows(before: Halalas, after: Halalas, unknown: Halalas?, cur: Currency) {
    BasicText(t(UiKey.FIX_BAL_BEFORE) + ": " + money(before, cur), style = Type.of(14))
    BasicText(t(UiKey.FIX_BAL_AFTER) + ": " + money(after, cur), style = Type.of(14, FontWeight.Bold))
    BasicText(unknown?.let { t(UiKey.FIX_BAL_UNKNOWN) + ": " + money(it, cur) } ?: t(UiKey.FIX_BAL_UNKNOWN_NONE), style = Type.of(13).copy(color = Ink.muted))
}
