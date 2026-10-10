package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.ProjectKind
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.launch

/**
 * «المشاريع» (لوحة `Projects` — مدخلها من «المزيد»): شرح سطر · النشطة (النوع · «كم كلّفني؟» أو «كم كسبت منه؟» · صرفت وجاءك · آخر موعد ·
 * «تقريبي» لو فيه عمليات نوعها مش مؤكد) · المؤرشفة مطوية بـ«إرجاع» (مفيش مسح) · «+» ⇒ مشروع جديد (أول سؤال: شخصي ولا عمل).
 */
@Composable
internal fun ProjectsScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps) { mutableStateOf<Load<ProjectsUi>>(Load.Loading) }
    var archOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(deps, version, retry) {
        load = loadOf { projectsUi(deps.people.projects.list(), deps.space.currency, deps.shell.today()) { k, s -> deps.people.money.projectHeadline(k, s) } }
    }
    InnerScaffold(t(UiKey.PROJECTS_TITLE), actions = { SurfaceIconButton(Lucide.PLUS, t(UiKey.PROJECTS_NEW), { creating = true }) }) {
        item(key = "intro") { Note(t(UiKey.PROJECTS_INTRO)) }
        when (val l = load) {
            Load.Loading -> items(3) { Skeleton(Modifier.fillMaxWidth().height(130.dp)) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(UiKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val ui = l.value
                if (ui.active.isEmpty()) item(key = "empty") { EmptyState(t(UiKey.PROJECTS_EMPTY_TITLE), t(UiKey.PROJECTS_EMPTY_BODY)) }
                items(ui.active, key = { it.project.id }) { p -> ProjectCard(p) { nav.push(ProjectDetailRoute(p.project.id)) } }
                if (ui.archived.isNotEmpty()) item(key = "archived") {
                    ArchivedProjects(ui, archOpen, { archOpen = !archOpen }) { card ->
                        scope.launch {
                            runCatching { deps.people.projects.setArchived(card.project.id, false) }
                                .onSuccess { PeopleChanges.bump(); toaster.show(t(UiKey.PROJECTS_RESTORED, card.project.name)) }
                                .onFailure { toaster.show(it.message ?: "") }
                        }
                    }
                }
            }
        }
    }
    Sheet(creating, onDismiss = { creating = false }, title = t(UiKey.PROJECTS_NEW)) {
        ProjectForm(null, onCreated = { nav.push(ProjectDetailRoute(it.id)) }, done = { creating = false })
    }
}

@Composable
internal fun HeadlineText(h: Headline?, size: Int) {
    if (h == null) BasicText(t(UiKey.PPL_DASH), style = Type.of(size, FontWeight.Bold).copy(color = Ink.muted))
    else AmountText(h.line.minor, h.line.currency, size = size, tone = h.tone, color = if (h.colorExpense) Ink.expense else Ink.income)
}

@Composable
private fun ProjectCard(p: ProjectCardUi, onClick: () -> Unit) {
    FloatingCard(onClick = onClick, clickLabel = p.project.name) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(p.project.name, Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
                val work = p.project.kind == ProjectKind.WORK
                Pill(p.kindLabel, if (work) Ink.transfer else Ink.primary, if (work) PeopleInk.transferSoft else Ink.selected)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(p.question, style = Type.of(13).copy(color = Ink.muted))
                HeadlineText(p.headline, 20)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(p.line, style = Type.caption().copy(color = Ink.muted))
                BasicText(p.deadline, style = Type.caption().copy(color = Ink.muted))
            }
            p.approx?.let { Pill(it, Ink.focus, Ink.alertBg) }
        }
    }
}

@Composable
private fun ArchivedProjects(ui: ProjectsUi, open: Boolean, toggle: () -> Unit, restore: (ProjectCardUi) -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, motion(Springs.SNAPPY), label = "chev")
    FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        val press = rememberPress()
        val title = t(UiKey.PROJECTS_ARCHIVED, sentenceNumber(ui.archived.size))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(press).tap(press, label = title, onClick = toggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(title, style = Type.of(15, FontWeight.Bold).copy(color = Ink.muted))
            LucideIcon(Lucide.CHEVRON_DOWN, size = 18.dp, tint = Ink.muted, modifier = Modifier.graphicsLayer { rotationZ = turn })
        }
        if (open) {
            ui.archived.forEachIndexed { i, c ->
                Rowed(i == ui.archived.lastIndex) {
                    Column(Modifier.weight(1f)) {
                        BasicText(c.project.name, style = Type.bodyBold())
                        BasicText(joinLine(c.kindLabel, c.question), style = Type.caption().copy(color = Ink.muted))
                    }
                    HeadlineText(c.headline, 14)
                    TonalButton(t(UiKey.PROJECTS_RESTORE), onClick = { restore(c) })
                }
            }
            Note(t(UiKey.PROJECTS_ARCH_NOTE))
        }
    }
}
