package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * تفاصيل المشروع (لوحة `ProjectDetail`): الملخص (السؤال والرقم · صرفت (نصيبك) · جاءك · المعادلة · «تقريبي») · خانتين العمليات والقواعد ·
 * شيل عملية (بتفضل «مستبعدة» عشان قاعدة ما ترجعهاش) · تشغيل/قفل قاعدة · قاعدة جديدة ⇒ «نضيف السابق أيضًا؟» بعدده · تعديل الاسم والموعد · أرشفة.
 */
private enum class ProjectTab { OPS, RULES }

@Composable
internal fun ProjectDetailScreen(projectId: String) {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps, projectId) { mutableStateOf<Load<ProjectDetailUi>>(Load.Loading) }
    var tab by remember { mutableStateOf(ProjectTab.OPS) }
    var editing by remember { mutableStateOf(false) }
    var ruling by remember { mutableStateOf(false) }
    LaunchedEffect(deps, projectId, version, retry) {
        load = loadOf {
            val d = deps.people.projects.detail(projectId)
            projectDetailUi(d, deps.space.currency, deps.people.money.projectHeadline(d.project.kind, d.summary))
        }
    }
    val ui = (load as? Load.Ready)?.value
    fun act(block: suspend () -> Unit) = scope.launch { runCatching { block() }.onSuccess { PeopleChanges.bump() }.onFailure { toaster.show(it.message ?: "") } }
    InnerScaffold(
        ui?.project?.name ?: t(UiKey.PROJECTS_TITLE),
        actions = {
            if (ui != null) {
                SurfaceIconButton(PeopleIcons.PEN, t(UiKey.PROJECT_DETAIL_EDIT), { editing = true }, iconSize = 18.dp)
                if (!ui.project.archived) SurfaceIconButton(PeopleIcons.ARCHIVE, t(UiKey.PROJECT_DETAIL_ARCHIVE), {
                    act {
                        deps.people.projects.setArchived(ui.project.id, true)
                        toaster.show(t(UiKey.PROJECT_DETAIL_ARCHIVED, ui.project.name))
                        nav.pop()
                    }
                }, iconSize = 18.dp)
            }
        },
    ) {
        when (val l = load) {
            Load.Loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(if (it == 0) 160.dp else 220.dp)) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(UiKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val d = l.value
                item(key = "sub") { Note(d.sub) }
                item(key = "summary") { Summary(d) }
                item(key = "tabs") {
                    SegmentedTabs(
                        listOf(ProjectTab.OPS to t(UiKey.PROJECT_DETAIL_TAB_OPS, sentenceNumber(d.ops.size)), ProjectTab.RULES to t(UiKey.PROJECT_DETAIL_TAB_RULES, sentenceNumber(d.rules.size))),
                        tab, { tab = it }, Modifier.fillMaxWidth(), height = 44.dp,
                    )
                }
                if (tab == ProjectTab.OPS) item(key = "ops") { Ops(d) { txn -> act { deps.people.projects.setMember(txn, d.project.id, false) } } }
                else item(key = "rules") { Rules(d, { r -> act { deps.people.projects.setRuleEnabled(r.rule.id, !r.rule.enabled) } }, { ruling = true }) }
            }
        }
    }
    if (ui != null) {
        Sheet(editing, onDismiss = { editing = false }, title = t(UiKey.PROJECTS_EDIT_TITLE)) { ProjectForm(ui.project, onCreated = {}, done = { editing = false }) }
        Sheet(ruling, onDismiss = { ruling = false }, title = t(UiKey.PROJECT_DETAIL_RULE_TITLE)) { RuleForm(ui.project.id) { addedOld -> ruling = false; if (addedOld) tab = ProjectTab.OPS } }
    }
}

/** الملخص على البطاقة البترولية (زي اللوحة): السؤال · الرقم الكبير (العمل بإشارته) · صرفت (نصيبك) وجاءك · المعادلة · «تقريبي». */
@Composable
private fun Summary(d: ProjectDetailUi) {
    HeroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(d.question, style = Type.of(14).copy(color = Ink.onHeroMuted))
            val h = d.headline
            if (h == null) BasicText(t(UiKey.PPL_DASH), style = Type.of(32, FontWeight.Bold).copy(color = Ink.onPrimary))
            else AmountText(h.line.minor, h.line.currency, Modifier.fillMaxWidth(), size = 32, tone = h.tone, color = Ink.onPrimary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroCell(t(UiKey.PROJECT_DETAIL_OUT), d.out, Modifier.weight(1f))
                HeroCell(t(UiKey.PROJECT_DETAIL_IN), d.inn, Modifier.weight(1f))
            }
            BasicText(d.formula, style = Type.of(12).copy(color = Ink.onHeroMuted))
            d.approx?.let { Pill(it, Ink.focus, Ink.alertBg) }
        }
    }
}

@Composable
private fun HeroCell(label: String, line: MoneyLine, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        BasicText(label, style = Type.of(11).copy(color = Ink.onHeroMuted))
        AmountText(line.minor, line.currency, Modifier.fillMaxWidth(), size = 15, color = Ink.onPrimary)
    }
}

@Composable
private fun Ops(d: ProjectDetailUi, remove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (d.ops.isEmpty()) EmptyState(t(UiKey.PROJECT_DETAIL_NO_OPS))
        else FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            d.ops.forEachIndexed { i, o ->
                Rowed(i == d.ops.lastIndex, minHeight = 64.dp) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(o.name, style = Type.bodyBold())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(o.date, style = Type.caption().copy(color = Ink.muted))
                            if (o.byRule) Pill(t(UiKey.PROJECT_DETAIL_SRC_RULE), Ink.transfer, PeopleInk.transferSoft)
                            else Pill(t(UiKey.PROJECT_DETAIL_SRC_MANUAL), Ink.muted, PeopleInk.chip)
                            if (o.unsure) Pill(t(UiKey.PROJECT_DETAIL_UNSURE), Ink.focus, Ink.alertBg)
                        }
                    }
                    AmountText(o.amountMinor, o.currency, size = 14, tone = o.tone)
                    SquareIcon(Lucide.X, t(UiKey.PROJECT_DETAIL_REMOVE_OP, o.name), { remove(o.txnId) })
                }
            }
        }
        Note(t(UiKey.PROJECT_DETAIL_ADD_HINT))
    }
}

@Composable
private fun Rules(d: ProjectDetailUi, toggle: (RuleUi) -> Unit, add: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(t(UiKey.PROJECT_DETAIL_RULES_INTRO))
        if (d.rules.isNotEmpty()) FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            d.rules.forEachIndexed { i, r ->
                Rowed(i == d.rules.lastIndex, if (r.rule.enabled) Modifier else Modifier.alpha(0.6f), minHeight = 64.dp) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(r.mode, Ink.primary, Ink.selected)
                            BasicText(r.text, style = Type.bodyBold())
                        }
                        BasicText(r.direction, style = Type.caption().copy(color = if (r.rule.enabled) Ink.muted else Ink.focus))
                    }
                    ToggleSwitch(r.rule.enabled, t(UiKey.PROJECT_DETAIL_RULE_SWITCH, r.rule.matchText), { toggle(r) })
                }
            }
        }
        TonalButton(t(UiKey.PROJECT_DETAIL_NEW_RULE), onClick = add, modifier = Modifier.fillMaxWidth())
    }
}
