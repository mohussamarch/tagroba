package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.usecase.AddedProjectRule
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحة المشروع (جوه `Projects` و`ProjectDetail`): **جديد** = أول سؤال «شخصي ولا عمل؟» (§47) وبعده الاسم وآخر موعد اختياري ·
 * **تعديل** = الاسم والموعد (`rename` + `setDeadline`). الاسم المكرر بيترفض برسالة `ManageProjects`.
 */
@Composable
internal fun ProjectForm(existing: Project?, onCreated: (Project) -> Unit, done: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = space.shell.today()
    var kind by remember { mutableStateOf(existing?.kind) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var deadline by remember { mutableStateOf<IsoDate?>(existing?.deadline) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val k = kind
        if (k == null) {
            SheetHeading(t(UiKey.PROJECTS_ASK_KIND))
            for (option in ProjectKind.entries) {
                FloatingCard(Modifier.fillMaxWidth(), onClick = { kind = option }, clickLabel = kindLabel(option)) {
                    BasicText(kindLabel(option), style = Type.of(16, FontWeight.Bold))
                    BasicText(questionOf(option), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
                    BasicText(t(if (option == ProjectKind.WORK) UiKey.PROJECTS_EX_WORK else UiKey.PROJECTS_EX_PERSONAL), style = Type.caption().copy(color = Ink.muted))
                }
            }
            return@Column
        }
        SheetHeading(t(if (existing != null) UiKey.PROJECTS_EDIT_TITLE else UiKey.PROJECTS_NEW))
        Pill(joinLine(kindLabel(k), questionOf(k)), if (k == ProjectKind.WORK) Ink.transfer else Ink.primary, if (k == ProjectKind.WORK) PeopleInk.transferSoft else Ink.selected)
        TextInput(name, { name = it.take(60); err = null }, label = t(UiKey.PROJECTS_NAME), placeholder = t(UiKey.PROJECTS_NAME_PH), error = err)
        FieldTitle(t(UiKey.PROJECTS_DEADLINE))
        DateField(deadline, today, { deadline = it })
        if (deadline != null) TonalButton(t(UiKey.PROJECTS_NO_DEADLINE), onClick = { deadline = null }, muted = true)
        Note(t(UiKey.PROJECTS_DEADLINE_NOTE))
        PrimaryButton(
            t(if (existing != null) UiKey.PPL_SAVE else UiKey.PROJECTS_CREATE),
            enabled = name.isNotBlank(), loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    val p = space.people.projects
                    val r = runCatching {
                        if (existing != null) {
                            if (name.trim() != existing.name) p.rename(existing.id, name)
                            if (deadline != existing.deadline) p.setDeadline(existing.id, deadline)
                            null
                        } else p.create(name, k).also { made -> if (deadline != null) p.setDeadline(made.id, deadline) }
                    }
                    busy = false
                    r.onSuccess { made ->
                        PeopleChanges.bump()
                        toaster.show(if (made != null) t(UiKey.PROJECTS_CREATED, made.name) else t(UiKey.PPL_SAVED))
                        done()
                        if (made != null) onCreated(made)
                    }.onFailure { err = it.message }
                }
            },
        )
    }
}

/**
 * «قاعدة للمشروع»: النص (من حرفين لـ60) · طريقة المطابقة · الاتجاه ⇒ «التالي» بيحفظ القاعدة (`addRule`) ويرجّع عدد العمليات القديمة
 * (آخر 3 سنين) اللي بتطابقها ⇒ «نضيف السابق أيضًا؟» (`applyRuleToOld`) أو الجديد بس. ⚠️ أسامي العمليات القديمة نفسها مش متاحة — العدد بس.
 * [done] بياخد «اتضاف قديم؟» عشان الشاشة تفتح خانة العمليات.
 */
@Composable
internal fun RuleForm(projectId: String, done: (Boolean) -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RuleMatchMode.CONTAINS) }
    var dir by remember { mutableStateOf("out") }
    var added by remember { mutableStateOf<AddedProjectRule?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val a = added
        if (a == null) {
            SheetHeading(t(UiKey.PROJECT_DETAIL_RULE_TITLE))
            val short = text.trim().length == 1
            TextInput(text, { text = it.take(60); err = null }, label = t(UiKey.PROJECT_DETAIL_RULE_TEXT), placeholder = t(UiKey.PROJECT_DETAIL_RULE_PH), error = err ?: if (short) t(UiKey.PROJECT_DETAIL_RULE_ERR) else null)
            FieldTitle(t(UiKey.PROJECT_DETAIL_MODE_LABEL))
            SegmentedTabs(RuleMatchMode.entries.map { it to modeLabel(it) }, mode, { mode = it }, Modifier.fillMaxWidth(), height = 44.dp)
            FieldTitle(t(UiKey.PROJECT_DETAIL_DIR_LABEL))
            SegmentedTabs(listOf("out", "in", "any").map { it to dirLabel(it) }, dir, { dir = it }, Modifier.fillMaxWidth(), height = 44.dp)
            PrimaryButton(t(UiKey.PROJECT_DETAIL_NEXT), enabled = text.trim().length >= 2, loading = busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true
                    val r = runCatching { space.people.projects.addRule(projectId, text, mode, dir) }
                    busy = false
                    r.onSuccess { added = it; PeopleChanges.bump() }.onFailure { err = it.message }
                }
            })
        } else {
            SheetHeading(t(UiKey.PROJECT_DETAIL_OLD_TITLE))
            Note(if (a.oldMatches > 0) t(UiKey.PROJECT_DETAIL_OLD_BODY, countOf(a.oldMatches, Noun.OPS)) else t(UiKey.PROJECT_DETAIL_OLD_NONE), color = Ink.text)
            if (a.oldMatches > 0) PrimaryButton(t(UiKey.PROJECT_DETAIL_ADD_OLD), loading = busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true
                    val r = runCatching { space.people.projects.applyRuleToOld(a.rule.id) }
                    busy = false
                    PeopleChanges.bump()
                    r.onSuccess { n -> toaster.show(t(UiKey.PROJECT_DETAIL_OLD_ADDED, countOf(n, Noun.OPS))); done(n > 0) }.onFailure { err = it.message }
                }
            })
            SecondaryButton(t(if (a.oldMatches > 0) UiKey.PROJECT_DETAIL_NEW_ONLY else UiKey.PROJECT_DETAIL_OK), onClick = { done(false) }, modifier = Modifier.fillMaxWidth())
            ErrorLine(err)
        }
    }
}
