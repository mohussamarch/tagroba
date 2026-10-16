package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.BalanceExplanation
import app.masroufy.core.BalanceItem
import app.masroufy.core.BalanceSource
import app.masroufy.core.DuplicatePair
import app.masroufy.core.Id
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.MergeRefusal
import app.masroufy.usecase.MergeResult
import app.masroufy.usecase.MergeTicket
import app.masroufy.usecase.UndoResult
import kotlinx.coroutines.launch

/** «افهم رصيدك» لمحفظة (بند المالك 3) — من «تفاصيل المحفظة». */
data class BalanceWhyRoute(val walletId: Id) : Route {
    override val name = "BalanceWhy"
}

private val SOURCE_LABEL = mapOf(
    BalanceSource.STATEMENT to UiKey.BWHY_SRC_STATEMENT, BalanceSource.SMS to UiKey.BWHY_SRC_SMS, BalanceSource.BOTH to UiKey.BWHY_SRC_BOTH,
    BalanceSource.MANUAL to UiKey.BWHY_SRC_MANUAL, BalanceSource.TRANSFER to UiKey.BWHY_SRC_TRANSFER, BalanceSource.ADJUSTMENT to UiKey.BWHY_SRC_ADJUSTMENT,
)

/**
 * رصيد البداية + المجاميع بالمصدر ⇒ الرصيد المحسوب (كل رقم من `ExplainBalance` — **مفيش حساب هنا**، CLAUDE.md #4) · «حاجات تستاهل تتراجع» ·
 * «راجع المكرر»: كل زوج جنب بعض، والدمج **بزرار المالك بس** («دول عملية واحدة») بنفس دمج الكشف والرسالة (`MergeDuplicates` — نسخة قبل الكتابة)،
 * و«تراجع» عن آخر دمج. مش متوصل ⇒ «غير متاح» (القاعدة 10).
 */
@Composable
fun BalanceWhyScreen(walletId: Id) {
    val deps = LocalSpace.current
    val more = deps.more
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var loaded by remember(deps) { mutableStateOf(false) }
    var why by remember(deps) { mutableStateOf<BalanceExplanation?>(null) }
    var showPairs by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastTicket by remember { mutableStateOf<MergeTicket?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        why = runCatching { more.balanceWhy?.explain(walletId, deps.shell.today()) }.getOrNull()
        loaded = true
    }
    fun refused(reason: MergeRefusal) = toaster.show(
        t(
            when (reason) {
                MergeRefusal.NO_BACKUP -> UiKey.BWHY_REFUSED_NO_BACKUP
                MergeRefusal.LINKED -> UiKey.BWHY_REFUSED_LINKED
                else -> UiKey.BWHY_REFUSED
            },
        ),
    )
    fun merge(p: DuplicatePair) {
        val merger = more.mergeDuplicates ?: return
        busy = true
        scope.launch {
            when (val r = runCatching { merger.merge(p.sms.transactionId, p.statement.transactionId) }.getOrNull()) {
                is MergeResult.Done -> { lastTicket = r.ticket; toaster.show(t(UiKey.BWHY_MERGED), dark = true) }
                is MergeResult.Refused -> refused(r.reason)
                null -> refused(MergeRefusal.CHANGED)
            }
            busy = false
            tick++
        }
    }
    fun undo(ticket: MergeTicket) {
        val merger = more.mergeDuplicates ?: return
        busy = true
        scope.launch {
            when (val r = runCatching { merger.undo(ticket) }.getOrNull()) {
                is UndoResult.Done -> { lastTicket = null; toaster.show(t(UiKey.BWHY_UNDONE), dark = true) }
                is UndoResult.Refused -> refused(r.reason)
                null -> refused(MergeRefusal.CHANGED)
            }
            busy = false
            tick++
        }
    }
    InnerScaffold(t(UiKey.BWHY_OPEN)) {
        val w = why
        if (!loaded) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(160.dp), radius = 26.dp, strong = true) }
            return@InnerScaffold
        }
        if (w == null) {
            item(key = "na") { EmptyState(t(UiKey.BWHY_UNAVAILABLE)) }
            return@InnerScaffold
        }
        item(key = "intro") { NoteBox(t(UiKey.BWHY_INTRO)) }
        item(key = "chain") {
            GroupCard {
                FactRow(t(UiKey.BWHY_OPENING, fullDate(w.openingAt) ?: w.openingAt), amountLabel(w.openingMinor, w.currency), last = false)
                w.totals.forEach { s ->
                    SourceRow(
                        t(SOURCE_LABEL.getValue(s.source)),
                        amountLabel(s.netMinor, w.currency),
                        t(UiKey.BWHY_SRC_LINE, s.count.toString(), amountLabel(s.inMinor, w.currency), amountLabel(s.outMinor, w.currency)),
                    )
                }
                val computed = w.computedMinor
                FactRow(t(UiKey.BWHY_COMPUTED), computed?.let { amountLabel(it, w.currency) } ?: t(UiKey.BWHY_UNAVAILABLE), last = true)
            }
        }
        if (w.computedMinor == null) item(key = "notopen") { NotYetLine(t(UiKey.BWHY_NOT_OPEN)) }
        item(key = "reviewTitle") { GroupTitle(t(UiKey.BWHY_REVIEW_TITLE)) }
        val r = w.review
        val rows = listOf(
            UiKey.BWHY_DUPES to r.duplicates.size, UiKey.BWHY_BEFORE to r.beforeOpening.size, UiKey.BWHY_OTHER_CURRENCY to r.otherCurrency.size,
            UiKey.BWHY_FOREIGN to r.foreignAmount.size, UiKey.BWHY_REVERSALS to r.reversals.size, UiKey.BWHY_ONE_SIDED to r.oneSidedTransfers.size,
            UiKey.BWHY_UNASSIGNED to r.unassigned.size,
        ).filter { it.second > 0 }
        item(key = "review") {
            if (rows.isEmpty()) NoteBox(t(UiKey.BWHY_REVIEW_EMPTY))
            else GroupCard { rows.forEachIndexed { i, (k, n) -> FactRow(t(k), t(UiKey.BWHY_COUNT, n.toString()), last = i == rows.lastIndex) } }
        }
        val merger = more.mergeDuplicates
        if (merger != null && (r.duplicates.isNotEmpty() || lastTicket != null)) {
            item(key = "pairsOpen") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r.duplicates.isNotEmpty()) TonalButton(t(UiKey.BWHY_DUPES_OPEN), onClick = { showPairs = !showPairs }, modifier = Modifier.fillMaxWidth())
                    lastTicket?.let { tk -> TonalButton(t(UiKey.BWHY_UNDO), onClick = { undo(tk) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), muted = true) }
                }
            }
        }
        if (merger != null && showPairs && r.duplicates.isNotEmpty()) {
            item(key = "pairsNote") { NoteBox(t(UiKey.BWHY_PAIR_NOTE)) }
            r.duplicates.forEach { p ->
                item(key = "pair-${p.sms.transactionId}-${p.statement.transactionId}") { PairCard(p, enabled = !busy) { merge(p) } }
            }
        }
    }
}

/** الزوج جنب بعض: الرسالة يمين والكشف شمال (أو العكس حسب اتجاه اللغة) + الفرق بالأيام + زرار التأكيد. */
@Composable
private fun PairCard(p: DuplicatePair, enabled: Boolean, onMerge: () -> Unit) {
    GroupCard {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Side(t(UiKey.BWHY_PAIR_SMS, fullDate(p.sms.date) ?: p.sms.date), p.sms, Modifier.weight(1f))
            Side(t(UiKey.BWHY_PAIR_STATEMENT, fullDate(p.statement.date) ?: p.statement.date), p.statement, Modifier.weight(1f))
        }
        FactRow(t(UiKey.BWHY_PAIR_DAYS, p.days.toString()), "", last = true, muted = true)
        TonalButton(t(UiKey.BWHY_MERGE), onClick = onMerge, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(8.dp), height = 44.dp)
    }
}

@Composable
private fun Side(head: String, item: BalanceItem, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(head, style = Type.of(12).copy(color = Ink.muted))
        BasicText(amountLabel(item.amountMinor, item.currency), style = Type.of(15, FontWeight.Bold).copy(color = Ink.text))
        if (item.title.isNotBlank()) BasicText(item.title, style = Type.of(12).copy(color = Ink.muted), maxLines = 2)
    }
}

/**
 * طلب المالك ٤ (r05 — حل مؤقت لحد تصميم جلسة التصميم): اسم المصدر كان بيتكسر حرف حرف («من كش/ف/البن/ك») لأن سطر الأرقام واخد العرض كله.
 * دلوقتي الاسم سطر كامل والصافي جنبه، و«N عملية · دخل X · خرج Y» سطر تاني بيلف.
 */
@Composable
private fun SourceRow(label: String, net: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(label, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
            BasicText(net, style = Type.of(14, FontWeight.Bold).copy(color = Ink.text))
        }
        BasicText(detail, Modifier.fillMaxWidth(), style = Type.of(12).copy(color = Ink.muted))
    }
    RowRule()
}
