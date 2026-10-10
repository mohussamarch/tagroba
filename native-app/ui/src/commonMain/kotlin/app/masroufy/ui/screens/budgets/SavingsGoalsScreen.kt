package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «خطط الادخار» (`SavingsGoals`): لكل خطة المدّخر والنسبة والحالة والأربع أرقام · نجمة واحدة بس · «التفاصيل» (`GoalDetail`) · المؤرشفة ·
 * «خطة جديدة». الحالات: عادي · فاضي · غير متاح (رصيد الحساب المربوط مش معروف ⇒ «غير متاح» مكان كل رقم) · بيحمّل · خطأ.
 */
@Composable
fun SavingsGoalsScreen() {
    val space = LocalSpace.current
    val deps = space.budgets
    val today = space.shell.today()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var wallets by remember(space) { mutableStateOf<List<Wallet>>(emptyList()) }
    val state = rememberLoad(space, reload) {
        wallets = space.shell.withYouNow().wallets.map { it.wallet }
        loadGoals(deps, today, wallets)
    }
    var openId by remember { mutableStateOf<String?>(null) }
    var newOpen by remember { mutableStateOf(false) }
    var archivedOpen by remember { mutableStateOf(false) }
    val ui = (state as? Load.Ready)?.value
    DetailScaffold(
        title = t(UiKey.GOALS_TITLE),
        actions = { NewGoalButton { newOpen = true } },
    ) {
        when (state) {
            Load.Loading -> item { Skeleton(Modifier.fillMaxWidth().height(260.dp)) }
            is Load.Failed -> item { ErrorCard(t(UiKey.SHELL_LOAD_FAILED), state.message ?: t(UiKey.BUDGETS_ERROR_BODY), { reload++ }) }
            is Load.Ready -> {
                val goals = state.value
                if (goals.active.isEmpty() && goals.archived.isEmpty()) {
                    item {
                        EmptyState(t(UiKey.GOALS_EMPTY_TITLE), t(UiKey.GOALS_EMPTY_BODY)) {
                            PrimaryButton(t(UiKey.GOALS_NEW), { newOpen = true }, Modifier.padding(top = 10.dp))
                        }
                    }
                } else {
                    item { BasicText(t(UiKey.GOALS_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
                    for (card in goals.active) item(key = card.id) {
                        GoalCard(card, onOpen = { openId = card.id }) {
                            scope.launch {
                                val error = attempt(t(UiKey.SHELL_LOAD_FAILED)) { deps.goals.star(card.id, !card.starred) }
                                toaster.show(error ?: if (card.starred) t(UiKey.GOALS_UNSTARRED) else t(UiKey.GOALS_STARRED, card.name))
                                if (error == null) reload++
                            }
                        }
                    }
                    if (goals.archived.isNotEmpty()) item(key = "archived") { ArchivedSection(goals.archived, archivedOpen) { archivedOpen = !archivedOpen } }
                    item { BasicText(t(UiKey.GOALS_METHOD), style = Type.caption().copy(color = Ink.muted)) }
                }
            }
        }
    }
    GoalDetailSheet(ui?.active?.firstOrNull { it.id == openId }, deps, today, onDismiss = { openId = null }) { reload++ }
    GoalNewSheet(newOpen, deps, today, space.space.currency, space.space.id, wallets, onDismiss = { newOpen = false }) { reload++ }
}

/** «+» أخضر 48 في الرأس — «خطة جديدة». */
@Composable
private fun NewGoalButton(onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(48.dp).pressScale(press).clip(RoundedCornerShape(Radius.control)).background(Glass.primary)
            .tap(press, label = t(UiKey.GOALS_NEW), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.PLUS, size = 22.dp, tint = Ink.onPrimary) }
}

@Composable
private fun GoalCard(card: GoalCardUi, onOpen: () -> Unit, onStar: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StarButton(card.starred, onStar)
                Column(Modifier.weight(1f)) {
                    BasicText(card.name, style = Type.of(16, FontWeight.Bold), maxLines = 1)
                    BasicText(card.sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
                }
                if (card.chip != null) StatusPill(card.chip, card.chipTone)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                AmountText(card.savedMinor, card.currency, size = 18)
                BasicText(card.ofText, style = Type.caption().copy(color = Ink.muted))
            }
            ProgressBar(card.percent ?: 0, if (card.reached) Ink.income else Ink.primary)
            StatsGrid(card.stats)
            TonalButton(t(if (card.linked) UiKey.GOALS_OPEN_LINKED else UiKey.GOALS_OPEN_MANUAL), onOpen, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

/** الأربع أرقام 2×2. */
@Composable
fun StatsGrid(stats: List<GoalStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (pair in stats.chunked(2)) {
            Gap8Row(Modifier.fillMaxWidth()) {
                for (s in pair) StatTile(s.label, s.value, Modifier.weight(1f), statColor(s.tone))
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** النجمة (44): على خطة واحدة بس — `ManageSavingsGoals.star` بيشيلها من أي خطة تانية. */
@Composable
private fun StarButton(starred: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(Radius.lensSmall)).background(if (starred) Ink.alertBg else Color(0x0A193D33))
            .tap(press, label = t(if (starred) UiKey.GOALS_STAR_REMOVE else UiKey.GOALS_STAR_ADD), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LucideIcon(BudgetsIcons.STAR, size = 20.dp, tint = if (starred) Ink.focus else Ink.faded) }
}

@Composable
private fun ArchivedSection(list: List<ArchivedUi>, open: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TonalButton(t(UiKey.GOALS_ARCHIVED, app.masroufy.core.sentenceNumber(list.size)), onToggle, height = 44.dp, muted = true)
        if (open) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.control)).background(Color(0x0A193D33)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                for (a in list) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(a.name, style = Type.of(13).copy(color = Ink.muted))
                        BasicText(a.text, style = Type.of(13).copy(color = Ink.muted, fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
    }
}

/** الحشوة القياسية للكروت اللي فيها صفوف بفواصل. */
internal val rowsPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/** تشغيل [block] مرة واحدة لما [trigger] يبقى true (الاحتفال بالخطة اللي كملت بعد إيداع). */
@Composable
internal fun OnceWhen(trigger: Boolean, block: () -> Unit) {
    LaunchedEffect(trigger) { if (trigger) block() }
}
