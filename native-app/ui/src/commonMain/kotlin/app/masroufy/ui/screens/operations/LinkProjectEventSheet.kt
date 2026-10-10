package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.EventRole
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.tabular
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EventDetail
import app.masroufy.usecase.ProjectMembership
import kotlinx.coroutines.launch

/**
 * «مشروع أو حدث» + لوحته (`LinkProjectEventSheet`): المشاريع علامة على العملية كلها (أكتر من مشروع — `ManageProjects.setMember`)، والحدث واحد
 * بس بنسبة 1..100 من المصروف (`EventGifts.link` دور «مصروف»). الحدث المربوط ما بيتغيرش لحدث تاني غير بعد «فك الربط». الوارد بيتربط بالحدث
 * نقطةً من صفحة الحدث. الربط **علامة** — ما بيغيّرش مصروف الشهر ولا التصنيف.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkProjectEventCard(v: DetailView, locked: Boolean) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    val out = v.flow != LinkFlow.TRANSFER_IN
    var reload by remember { mutableIntStateOf(0) }
    var projects by remember(v.id) { mutableStateOf<List<ProjectMembership>>(emptyList()) }
    var details by remember(v.id) { mutableStateOf<List<EventDetail>>(emptyList()) }
    LaunchedEffect(v.id, reload) {
        projects = attempt { deps.projects.membership(v.id) }.orEmpty()
        details = attempt { deps.events.list().let { it.active + it.archived }.map { deps.events.detail(it.event.id) } }.orEmpty()
    }
    val linked = eventLinkOf(details, v.id)
    val onProjects = projects.filter { it.member }
    var open by remember { mutableStateOf(false) }
    val has = onProjects.isNotEmpty() || linked != null
    val sub = when {
        locked -> t(UiKey.LINK_PROJECT_EVENT_LOCKED)
        has -> t(UiKey.LINK_PROJECT_EVENT_SUB_TAGS)
        else -> t(UiKey.LINK_PROJECT_EVENT_SUB_NONE)
    }
    LinkCard(Lucide.BRIEFCASE, t(UiKey.LINK_PROJECT_EVENT_CARD), sub, t(if (has) UiKey.OPERATION_DETAIL_CHANGE else UiKey.LINK_PERSON_OPEN), enabled = !locked, onOpen = { open = true }) {
        for (m in onProjects) TagLine(t(UiKey.LINK_PROJECT_EVENT_PROJECT_TAG), m.project.name, null)
        linked?.let { e ->
            val name = if (e.sharePercent < 100) t(UiKey.LINK_PROJECT_EVENT_NAME_SHARE, e.name, percentText(e.sharePercent)) else e.name
            TagLine(t(UiKey.LINK_PROJECT_EVENT_EVENT_TAG), name, amountLabel(e.shareMinor, v.currency))
        }
    }
    var picked by remember { mutableStateOf<Set<Id>>(emptySet()) }
    var event by remember { mutableStateOf<Id?>(null) }
    var unlinked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Id?>(null) }
    var shareText by remember { mutableStateOf("100") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        picked = onProjects.map { it.project.id }.toSet(); event = linked?.eventId; unlinked = false; pending = null
        shareText = (linked?.sharePercent ?: 100).toString(); error = null
    }
    val stillLinked = if (unlinked) null else linked
    val choices = eventChoices(details)
    val percent = parsePercent(shareText)
    Sheet(open, { open = false }, title = t(UiKey.LINK_PROJECT_EVENT_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(t(UiKey.LINK_PROJECT_EVENT_TITLE), style = Type.of(17, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(v.title, style = Type.of(13).copy(color = Ink.muted))
            AmountText(v.amountMinor, v.currency, tone = v.tone, size = 13)
        }
        Heading(t(UiKey.LINK_PROJECT_EVENT_PROJECTS), t(UiKey.LINK_PROJECT_EVENT_PROJECTS_HINT))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (projects.isEmpty()) BasicText(t(UiKey.LINK_PROJECT_EVENT_NO_PROJECTS), style = Type.of(13).copy(color = Ink.muted))
            for (m in projects) {
                val on = m.project.id in picked
                SelectChip(m.project.name, on, { picked = if (on) picked - m.project.id else picked + m.project.id; error = null }, height = 44.dp)
            }
        }
        Heading(t(UiKey.LINK_PROJECT_EVENT_EVENT), t(if (out) UiKey.LINK_PROJECT_EVENT_EVENT_HINT else UiKey.LINK_PROJECT_EVENT_EVENT_HINT_IN))
        if (out) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip(t(UiKey.LINK_PROJECT_EVENT_NONE), event == null && pending == null, { event = null; pending = null; if (linked != null) unlinked = true; error = null }, height = 44.dp)
                for (c in choices) SelectChip(c.name, event == c.id && pending == null, {
                    if (stillLinked != null && c.id != stillLinked.eventId) pending = c.id else {
                        event = c.id; pending = null
                        if (c.id == linked?.eventId && !unlinked) shareText = linked.sharePercent.toString()
                    }
                    error = null
                }, height = 44.dp)
            }
            val cur = choices.firstOrNull { it.id == event }
            BasicText(cur?.let { it.sub + if (it.id == stillLinked?.eventId) t(UiKey.LINK_PROJECT_EVENT_NOW_LINKED) else "" } ?: t(UiKey.LINK_PROJECT_EVENT_NOT_ON_ANY), style = Type.caption().copy(color = Ink.muted))
            if (pending != null && stillLinked != null) ConflictBox(stillLinked.name, choices.firstOrNull { it.id == pending }?.name.orEmpty()) {
                unlinked = true; event = pending; pending = null; shareText = "100"
            }
            if (event != null && pending == null) ShareBox(shareText, percent, choices.firstOrNull { it.id == event }?.name.orEmpty()) { shareText = it; error = null }
        }
        BasicText(t(UiKey.LINK_PROJECT_EVENT_NOTE), style = Type.of(12).copy(color = Ink.muted))
        error?.let { FieldError(it) }
        PrimaryButton(t(UiKey.LINK_PROJECT_EVENT_SAVE), loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
            error = projectEventError(pending != null, out && event != null, percent)
            if (error != null || saving) return@PrimaryButton
            saving = true
            scope.launch {
                val err = failureOf {
                    for (m in projects) if ((m.project.id in picked) != m.member) deps.projects.setMember(v.id, m.project.id, m.project.id in picked)
                    val plan = eventPlan(linked, event, unlinked, percent, out)
                    plan.unlinkOld?.let { deps.eventLinks.unlink(it, v.id) }
                    plan.linkTo?.let { deps.eventLinks.link(it, v.id, EventRole.SPEND, sharePercent = plan.share) }
                }
                saving = false
                reload++
                if (err != null) error = err else open = false
            }
        })
    }
}

@Composable
private fun TagLine(kind: String, name: String, amount: String?) {
    Divider()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(kind, style = Type.of(13).copy(color = Ink.muted))
        BasicText(name, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
        if (amount != null) BasicText(amount, style = Type.of(13, FontWeight.Bold).tabular())
    }
}

@Composable
private fun Heading(title: String, hint: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(title, style = Type.of(13, FontWeight.Bold))
        BasicText(hint, style = Type.caption().copy(color = Ink.muted))
    }
}

/** «مربوطة بـ«الحدث» مسبقًا» + «فك الربط من «الحدث»» (العملية بحدث واحد — `EVENT_TXN_ALREADY_LINKED`). */
@Composable
private fun ConflictBox(linkedName: String, wantedName: String, onUnlink: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.alertBg).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(UiKey.LINK_PROJECT_EVENT_CONFLICT_TITLE, linkedName), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        BasicText(t(UiKey.LINK_PROJECT_EVENT_CONFLICT_BODY, wantedName), style = Type.of(12))
        TonalButton(t(UiKey.LINK_PROJECT_EVENT_UNLINK, linkedName), onUnlink, height = 44.dp)
    }
}

/**
 * نصيب الحدث: الخانة (%) + النسب السريعة + «على «الحدث»» و«الباقي خارج الحدث» **بالنسبة** — المبلغ بالريال بيتحسب في حالة الاستخدام وقت الربط
 * وبيظهر على الكارت بعد الحفظ (معاينة المبلغ قبل الحفظ محتاجة حالة استخدام — ناقصة).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareBox(text: String, percent: Int?, eventName: String, onText: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.LINK_PROJECT_EVENT_SHARE), style = Type.of(13, FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(text, { if (it.length <= 3) onText(it) }, Modifier.width(112.dp), ltr = true, keyboard = KeyboardType.Number,
                trailing = { BasicText(t(UiKey.LINK_PROJECT_EVENT_PERCENT_SIGN), Modifier.padding(end = 14.dp), style = Type.of(15).copy(color = Ink.muted)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (q in QUICK_SHARES) SelectChip(percentText(q), percent == q, { onText(q.toString()) }, height = 44.dp)
            }
        }
        val split = sharePercents(percent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(androidx.compose.ui.graphics.Color(0x0F08634F)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (split == null) SplitLine(t(UiKey.LINK_PROJECT_EVENT_RANGE, sentenceNumber(1), sentenceNumber(100)), null, Ink.muted)
            else {
                SplitLine(t(UiKey.LINK_PROJECT_EVENT_ON, eventName), percentText(split.first), Ink.expense)
                SplitLine(t(UiKey.LINK_PROJECT_EVENT_OUTSIDE), percentText(split.second), Ink.muted)
            }
        }
    }
}

@Composable
private fun SplitLine(label: String, value: String?, ink: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(label, Modifier.weight(1f), style = Type.of(13))
        if (value != null) BasicText(value, style = Type.of(13, FontWeight.Bold).copy(color = ink))
    }
}
