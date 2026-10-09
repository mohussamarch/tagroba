package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
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
        ui?.project?.name ?: t(TextKey.PROJECTS_TITLE),
        actions = {
            if (ui != null) {
                SurfaceIconButton(PeopleIcons.PEN, t(TextKey.PROJECT_DETAIL_EDIT), { editing = true }, iconSize = 18.dp)
                if (!ui.project.archived) SurfaceIconButton(PeopleIcons.ARCHIVE, t(TextKey.PROJECT_DETAIL_ARCHIVE), {
                    act {
                        deps.people.projects.setArchived(ui.project.id, true)
                        toaster.show(t(TextKey.PROJECT_DETAIL_ARCHIVED, ui.project.name))
                        nav.pop()
                    }
                }, iconSize = 18.dp)
            }
        },
    ) {
        when (val l = load) {
            Load.Loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(if (it == 0) 160.dp else 220.dp)) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(TextKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val d = l.value
                item(key = "sub") { Note(d.sub) }
                item(key = "summary") { Summary(d) }
                item(key = "tabs") {
                    SegmentedTabs(
                        listOf(ProjectTab.OPS to t(TextKey.PROJECT_DETAIL_TAB_OPS, sentenceNumber(d.ops.size)), ProjectTab.RULES to t(TextKey.PROJECT_DETAIL_TAB_RULES, sentenceNumber(d.rules.size))),
                        tab, { tab = it }, Modifier.fillMaxWidth(), height = 44.dp,
                    )
                }
                if (tab == ProjectTab.OPS) item(key = "ops") { Ops(d) { txn -> act { deps.people.projects.setMember(txn, d.project.id, false) } } }
                else item(key = "rules") { Rules(d, { r -> act { deps.people.projects.setRuleEnabled(r.rule.id, !r.rule.enabled) } }, { ruling = true }) }
            }
        }
    }
    if (ui != null) {
        Sheet(editing, onDismiss = { editing = false }, title = t(TextKey.PROJECTS_EDIT_TITLE)) { ProjectForm(ui.project, onCreated = {}, done = { editing = false }) }
        Sheet(ruling, onDismiss = { ruling = false }, title = t(TextKey.PROJECT_DETAIL_RULE_TITLE)) { RuleForm(ui.project.id) { addedOld -> ruling = false; if (addedOld) tab = ProjectTab.OPS } }
    }
}

@Composable
private fun Summary(d: ProjectDetailUi) {
    FloatingCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(d.question, style = Type.of(13).copy(color = Ink.muted))
            HeadlineText(d.headline, 30)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    BasicText(t(TextKey.PROJECT_DETAIL_OUT), style = Type.caption().copy(color = Ink.muted))
                    AmountText(d.out.minor, d.out.currency, size = 15)
                }
                Column(Modifier.weight(1f)) {
                    BasicText(t(TextKey.PROJECT_DETAIL_IN), style = Type.caption().copy(color = Ink.muted))
                    AmountText(d.inn.minor, d.inn.currency, size = 15)
                }
            }
            Note(d.formula)
            d.approx?.let { Pill(it, Ink.focus, Ink.alertBg) }
        }
    }
}

@Composable
private fun Ops(d: ProjectDetailUi, remove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (d.ops.isEmpty()) EmptyState(t(TextKey.PROJECT_DETAIL_NO_OPS))
        else FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            d.ops.forEachIndexed { i, o ->
                Rowed(i == d.ops.lastIndex, minHeight = 64.dp) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(o.name, style = Type.bodyBold())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(o.date, style = Type.caption().copy(color = Ink.muted))
                            if (o.byRule) Pill(t(TextKey.PROJECT_DETAIL_SRC_RULE), Ink.transfer, PeopleInk.transferSoft)
                            else Pill(t(TextKey.PROJECT_DETAIL_SRC_MANUAL), Ink.muted, PeopleInk.chip)
                            if (o.unsure) Pill(t(TextKey.PROJECT_DETAIL_UNSURE), Ink.focus, Ink.alertBg)
                        }
                    }
                    AmountText(o.amountMinor, o.currency, size = 14, tone = o.tone)
                    SquareIcon(Lucide.X, t(TextKey.PROJECT_DETAIL_REMOVE_OP, o.name), { remove(o.txnId) })
                }
            }
        }
        Note(t(TextKey.PROJECT_DETAIL_ADD_HINT))
    }
}

@Composable
private fun Rules(d: ProjectDetailUi, toggle: (RuleUi) -> Unit, add: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(t(TextKey.PROJECT_DETAIL_RULES_INTRO))
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
                    ToggleSwitch(r.rule.enabled, t(TextKey.PROJECT_DETAIL_RULE_SWITCH, r.rule.matchText), { toggle(r) })
                }
            }
        }
        TonalButton(t(TextKey.PROJECT_DETAIL_NEW_RULE), onClick = add, modifier = Modifier.fillMaxWidth())
    }
}
