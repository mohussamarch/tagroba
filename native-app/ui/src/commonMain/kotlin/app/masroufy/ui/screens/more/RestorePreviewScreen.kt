package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.FullBackupPlan
import kotlinx.coroutines.launch

private sealed interface Phase {
    data object Reading : Phase
    data class Error(val message: String) : Phase
    data class Plan(val plan: FullBackupPlan) : Phase
    data class Applying(val plan: FullBackupPlan) : Phase
    data class Done(val plan: FullBackupPlan, val added: Int) : Phase
}

/**
 * معاينة الاسترجاع (`RestorePreview` — §12 · §49 · §64-٩): الملف بيتقرا ويتقارن باللي عندك (`FullBackup.plan`) — لكل مجموعة جاي · هيتضاف ·
 * هيتساب، والبلاد (جديدة ⇒ بتتعمل · موجودة ⇒ بيتدمج فيها)، وملفك ما بيتكتبش فوقه. **مفيش حرف بيتكتب قبل «أكّد»** (`FullBackup.apply` بيعيد
 * الدمج على الموجود ساعتها). الملف القديم (إصدار ١) أو اللي اتعدّل ⇒ رسالة حالة الاستخدام نفسها، ومن غير كتابة.
 */
@Composable
fun RestorePreviewScreen(fileName: String, text: String) {
    val deps = LocalSpace.current
    val backup = deps.more.fullBackup
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var phase by remember(deps, text) { mutableStateOf<Phase>(Phase.Reading) }
    var open by remember { mutableStateOf(setOf(0)) }
    LaunchedEffect(deps, text) {
        phase = if (backup == null) Phase.Error(t(UiKey.BAK_NOT_WIRED))
        else runCatching { backup.plan(text) }.fold({ Phase.Plan(it) }, { Phase.Error(it.message ?: t(UiKey.RST_BAD_FILE)) })
    }
    InnerScaffold(t(UiKey.RST_TITLE)) {
        item(key = "file") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x99FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                BasicText(fileName, Modifier.fillMaxWidth(), style = Type.of(13, FontWeight.Bold).copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.End))
                val plan = (phase as? Phase.Plan)?.plan ?: (phase as? Phase.Done)?.plan ?: (phase as? Phase.Applying)?.plan
                plan?.let { p -> fullDate(p.file.exportedAt)?.let { BasicText(t(UiKey.RST_MADE_ON, it), style = Type.caption().copy(color = Ink.muted)) } }
            }
        }
        when (val p = phase) {
            Phase.Reading -> item(key = "reading") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(t(UiKey.RST_READING), style = Type.of(13).copy(color = Ink.muted))
                    Skeleton(Modifier.fillMaxWidth().height(120.dp), radius = 24.dp)
                    Skeleton(Modifier.fillMaxWidth().height(260.dp))
                }
            }
            is Phase.Error -> item(key = "error") {
                WarnBox(t(UiKey.RST_BAD_FILE), p.message) {
                    BasicText(t(UiKey.RST_NOTHING_WRITTEN), style = Type.caption().copy(color = Color(0xFF6B4600)))
                    TonalButton(t(UiKey.RST_OTHER_FILE), onClick = { nav.pop() }, height = 44.dp)
                }
            }
            else -> {
                val plan = (p as? Phase.Plan)?.plan ?: (p as? Phase.Applying)?.plan ?: (p as Phase.Done).plan
                val view = planView(plan)
                val done = p as? Phase.Done
                item(key = "hero") {
                    HeroCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText(
                                if (done != null) t(UiKey.RST_ADDED_N, countText(done.added, RECORD_WORDS)) else t(UiKey.RST_WILL_ADD_N, countText(view.totalToAdd, RECORD_WORDS)),
                                style = Type.of(20, FontWeight.Bold).copy(color = Ink.onPrimary),
                            )
                            val body = when {
                                done != null -> t(UiKey.RST_DONE_BODY)
                                p is Phase.Applying -> t(UiKey.RST_APPLYING)
                                else -> t(UiKey.RST_PLAN_BODY, sentenceNumber(view.incoming))
                            }
                            BasicText(body, style = Type.of(13).copy(color = Ink.onHeroMuted))
                        }
                    }
                }
                item(key = "profile") { NoteBox(t(view.profileLine)) }
                view.spaces.forEachIndexed { i, s ->
                    item(key = "space$i") { PlanSpaceCard(s, done != null, i in open) { open = if (i in open) open - i else open + i } }
                }
                item(key = "notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in view.notes) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LucideIcon(Lucide.CHECK, size = 14.dp, tint = Ink.income)
                            BasicText(n, style = Type.caption().copy(color = Ink.muted))
                        }
                    }
                }
                item(key = "acts") {
                    if (done != null) PrimaryButton(t(UiKey.MORE_DONE), onClick = { nav.pop() }, modifier = Modifier.fillMaxWidth())
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            if (p is Phase.Applying) t(UiKey.RST_ADDING) else t(UiKey.RST_CONFIRM, countText(view.totalToAdd, RECORD_WORDS)),
                            onClick = {
                                val b = backup ?: return@PrimaryButton
                                phase = Phase.Applying(plan)
                                scope.launch {
                                    phase = runCatching { b.apply(plan.file) }.fold({ Phase.Done(plan, it.totalAdded) }, { Phase.Error(it.message ?: t(UiKey.RST_APPLY_FAILED)) })
                                }
                            },
                            enabled = p is Phase.Plan, loading = p is Phase.Applying, modifier = Modifier.fillMaxWidth(),
                        )
                        TonalButton(t(UiKey.RST_CANCEL), onClick = { nav.pop() }, enabled = p is Phase.Plan, muted = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSpaceCard(s: PlanSpace, done: Boolean, open: Boolean, onToggle: () -> Unit) {
    GroupCard(horizontal = 16.dp) {
        val press = rememberPress()
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).tap(press, onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(s.title, style = Type.of(15, FontWeight.Bold))
                    s.isNew?.let { Badge(t(if (it) UiKey.RST_SPACE_NEW else UiKey.RST_SPACE_MERGE), BadgeKind.INFO) }
                }
                BasicText(planSpaceLine(s, done), style = Type.caption().copy(color = Ink.muted))
            }
            LucideIcon(if (open) Lucide.CHEVRON_UP else Lucide.CHEVRON_DOWN, size = 18.dp, tint = Ink.muted)
        }
        if (open && s.rows.isNotEmpty()) {
            RowRule()
            PlanRowLine(t(UiKey.RST_COL_GROUP), t(UiKey.RST_COL_IN), t(UiKey.RST_COL_ADD), t(UiKey.RST_COL_SKIP), header = true)
            s.rows.forEachIndexed { i, r ->
                PlanRowLine(r.label, sentenceNumber(r.incoming), if (r.toAdd > 0) sentenceNumber(r.toAdd) else "—", sentenceNumber(r.skipped), added = r.toAdd > 0)
                if (i != s.rows.lastIndex) RowRule()
            }
            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlanRowLine(label: String, incoming: String, add: String, skip: String, header: Boolean = false, added: Boolean = false) {
    val base = if (header) Type.of(11, FontWeight.Bold).copy(color = Ink.muted) else Type.of(13)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = if (header) base else base.copy(fontWeight = FontWeight.Bold))
        for ((v, strong) in listOf(incoming to false, add to added, skip to false)) {
            BasicText(v, Modifier.width(52.dp), style = base.copy(textAlign = TextAlign.Center, color = if (strong) Ink.income else if (header) Ink.muted else Ink.muted, fontWeight = if (strong || header) FontWeight.Bold else FontWeight.Normal))
        }
    }
}
