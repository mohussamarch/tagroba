package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.core.TextKey
import app.masroufy.core.buildPeriod
import app.masroufy.core.periodForDate
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.SpaceDeps
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.failureText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SuggestionSummary
import kotlinx.coroutines.launch

/** عمليات الشهر المختار (أو الحالي) واقتراحاتها — من `ReviewHistory.preview` (القراية شهر بشهر) ثم `SetEconomicKind.summarize`. */
internal class ReviewData(val rows: List<Transaction>, val summary: SuggestionSummary)

internal suspend fun loadReview(deps: SpaceDeps): ReviewData {
    val today = deps.shell.today()
    val payday = runCatching { deps.home.profile.load().payday }.getOrDefault(DEFAULT_PAYDAY)
    val current = periodForDate(today, payday)
    val period = PeriodChoice.of(deps.space.id)?.split("-")?.let { (y, m) -> buildPeriod(y.toInt(), m.toInt(), payday) } ?: current
    val to = if (period.end < today) period.end else today
    val rows = deps.home.history.preview(period.start, to).rows
    return ReviewData(rows, deps.home.kinds.summarize(rows))
}

/**
 * «المراجعة»: البطاقة البطلة («N عمليات نوعها غير مؤكد» + شريط اللي اتأكد) ⇒ المجموعات (المقترح بنوعه · الوارد الغامض · الصادر الغامض) — كل عملية:
 * الاسم والتاريخ والمبلغ · «المقترح: X ✓ تأكيد» · البدايل · سبب الاقتراح؛ بعد التأكيد «✓ X» + «تراجع» · «أكّد الـN» للقاطع ⇒ «طبّق قواعدك على السابق».
 * الحالات: بيحمّل (هيكل) · فاضي («لا شيء للمراجعة») · خطأ (شريط «تعذّر حفظ آخر تأكيد» + رسالة المنطق).
 */
@Composable
fun ReviewQueueScreen() {
    val deps = LocalSpace.current
    val scope = rememberCoroutineScope()
    var data by remember(deps) { mutableStateOf<ReviewData?>(null) }
    var loadFailed by remember(deps) { mutableStateOf(false) }
    var done by remember(deps) { mutableStateOf<Map<Id, EconomicKind>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deps) {
        runCatching { loadReview(deps) }.onSuccess { data = it }.onFailure { loadFailed = true }
    }
    fun confirm(item: ReviewItem, kind: EconomicKind) {
        scope.launch {
            runCatching { deps.home.kinds.setOne(item.id, kind) }
                .onSuccess { done = done + (item.id to kind); error = null }
                .onFailure { error = failureText(it, UiKey.REVIEW_QUEUE_SAVE_FAILED_BODY) }
        }
    }
    fun confirmGroup(group: ReviewGroup) {
        val d = data ?: return
        val ids = group.bulkIds(done.keys)
        scope.launch {
            runCatching { deps.home.kinds.confirmBulk(d.rows, ids) }
                .onSuccess { r ->
                    if (r.applied > 0) done = done + group.items.filter { it.id in ids && it.suggestion != null }.associate { it.id to it.suggestion!! }
                    error = null
                }
                .onFailure { error = failureText(it, UiKey.REVIEW_QUEUE_SAVE_FAILED_BODY) }
        }
    }
    InnerScaffold(t(UiKey.REVIEW_QUEUE_TITLE)) {
        error?.let { msg -> item(key = "error") { SaveErrorBanner(msg) } }
        val d = data
        when {
            loadFailed -> item(key = "failed") { EmptyState(t(UiKey.SHELL_LOAD_FAILED)) }
            d == null -> item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(120.dp), radius = 28.dp, strong = true)
                    Skeleton(Modifier.fillMaxWidth().height(160.dp))
                }
            }
            reviewTotal(d.summary) == 0 -> {
                item(key = "empty") { EmptyState(t(UiKey.REVIEW_QUEUE_EMPTY_TITLE), t(UiKey.REVIEW_QUEUE_EMPTY_BODY)) }
                item(key = "rules") { RulesCard() }
            }
            else -> {
                val total = reviewTotal(d.summary)
                item(key = "hero") { ReviewHero(total, done.size) }
                for (g in reviewGroups(d.summary)) item(key = g.key) {
                    GroupBlock(g, done, onAccept = ::confirm, onUndo = { done = done - it.id }, onBulk = { confirmGroup(g) })
                }
                item(key = "rules") { RulesCard() }
            }
        }
    }
}

@Composable
private fun ReviewHero(total: Int, done: Int) {
    val left = total - done
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(reviewHeroTitle(left), style = Type.of(20, FontWeight.Bold).copy(color = Ink.onPrimary))
            BasicText(
                if (left > 0) t(UiKey.REVIEW_QUEUE_HERO_BODY) else t(UiKey.REVIEW_QUEUE_DONE_BODY),
                style = Type.of(13).copy(color = Ink.onHeroMuted),
            )
            val pct = reviewDonePercent(total, done)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.heroTrack)) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.heroProgress))
            }
        }
    }
}

@Composable
private fun SaveErrorBanner(message: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(t(UiKey.REVIEW_QUEUE_SAVE_FAILED), style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
        BasicText(message, style = Type.caption().copy(color = Ink.focus))
    }
}

@Composable
private fun GroupBlock(g: ReviewGroup, done: Map<Id, EconomicKind>, onAccept: (ReviewItem, EconomicKind) -> Unit, onUndo: (ReviewItem) -> Unit, onBulk: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(UiKey.REVIEW_QUEUE_GROUP_COUNT, g.title, app.masroufy.core.sentenceNumber(g.items.size)), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            val bulk = g.bulkIds(done.keys)
            if (bulk.isNotEmpty()) TonalButton(t(UiKey.REVIEW_QUEUE_BULK, app.masroufy.core.sentenceNumber(bulk.size)), onClick = onBulk, height = 44.dp)
        }
        for (item in g.items) ReviewItemCard(item, done[item.id], onAccept, onUndo)
    }
}
