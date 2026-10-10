package app.masroufy.ui.screens.people

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.ProjectKind
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «معًا» في ملف الشخص: دعوة لجمعية · مشروع مشترك · طلب اتصال · تقسيم فاتورة — كل واحد بلوحة صغيرة.
 * - **مشروع مشترك** شغّال: بيعمل مشروع شخصي «مشروع مع {الاسم}» (`ManageProjects.create`) ويفتحه.
 * - **طلب اتصال** محتاج ربط الحسابات (مرحلة جاية — مكتوب في اللوحة زي النموذج).
 * - **الجمعية مع شخص** و**تقسيم فاتورة من غير عملية** مالهمش حالة استخدام ⇒ الزرار مقفول وجنبه السبب (missingLogic).
 */
private enum class Act(val label: TextRef, val icon: Lucide, val title: TextRef, val body: TextRef, val go: TextRef, val amount: TextRef?) {
    ROSCA(UiKey.PERSON_PAGE_ACT_ROSCA, Lucide.COINS, UiKey.PERSON_PAGE_ROSCA_TITLE, UiKey.PERSON_PAGE_ROSCA_BODY, UiKey.PERSON_PAGE_ROSCA_GO, UiKey.PERSON_PAGE_ROSCA_AMOUNT),
    PROJECT(UiKey.PERSON_PAGE_ACT_PROJECT, Lucide.BRIEFCASE, UiKey.PERSON_PAGE_PROJECT_TITLE, UiKey.PERSON_PAGE_PROJECT_BODY, UiKey.PERSON_PAGE_PROJECT_GO, null),
    LINK(UiKey.PERSON_PAGE_ACT_LINK, PeopleIcons.USER_PLUS, UiKey.PERSON_PAGE_LINK_TITLE, UiKey.PERSON_PAGE_LINK_BODY, UiKey.PERSON_PAGE_LINK_GO, null),
    SPLIT(UiKey.PERSON_PAGE_ACT_SPLIT, PeopleIcons.SPLIT_BILL, UiKey.PERSON_PAGE_SPLIT_TITLE, UiKey.PERSON_PAGE_SPLIT_BODY, UiKey.PERSON_PAGE_SPLIT_GO, UiKey.PERSON_PAGE_SPLIT_AMOUNT),
}

@Composable
internal fun Together(ui: PersonPageUi, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<Act?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.PERSON_PAGE_TOGETHER), style = Type.section())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in Act.entries) {
                FloatingCard(
                    Modifier.weight(1f).heightIn(min = 84.dp),
                    shape = RoundedCornerShape(Radius.control),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    onClick = { open = a },
                    clickLabel = t(a.label),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Ink.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                            LucideIcon(a.icon, size = 20.dp, tint = Ink.primary)
                        }
                        BasicText(t(a.label), style = Type.of(12, FontWeight.Bold, 1.4).copy(textAlign = TextAlign.Center))
                    }
                }
            }
        }
        Note(t(UiKey.PERSON_PAGE_FUTURE, ui.name))
    }
    val act = open
    Sheet(act != null, onDismiss = { open = null }, title = act?.let { t(it.title, ui.name) } ?: "", veil = app.masroufy.ui.overlay.Veil.MENU) {
        if (act != null) TogetherSheet(act, ui.name) { open = null }
    }
}

@Composable
private fun TogetherSheet(act: Act, name: String, close: () -> Unit) {
    val space = LocalSpace.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(t(act.title, name), style = Type.section())
        BasicText(t(act.body, name), style = Type.of(14, lineHeight = 1.7).copy(color = Ink.muted))
        act.amount?.let { NumberInput(amount, { amount = it }, label = t(it), currency = space.space.currency) }
        when (act) {
            Act.LINK -> Pill(t(UiKey.PERSON_PAGE_LINK_NOTE), Ink.focus, Ink.alertBg)
            Act.ROSCA, Act.SPLIT -> Pill(t(UiKey.PPL_NOT_BUILT), Ink.focus, Ink.alertBg)
            Act.PROJECT -> Unit
        }
        ErrorLine(err)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                t(act.go),
                enabled = act == Act.PROJECT,
                loading = busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (act != Act.PROJECT) return@PrimaryButton
                    scope.launch {
                        busy = true
                        val made = runCatching { space.people.projects.create(t(UiKey.PERSON_PAGE_PROJECT_TITLE, name), ProjectKind.PERSONAL) }
                        busy = false
                        made.onSuccess { PeopleChanges.bump(); close(); nav.push(ProjectDetailRoute(it.id)) }.onFailure { err = it.message }
                    }
                },
            )
            SecondaryButton(t(UiKey.PPL_CANCEL), onClick = close, modifier = Modifier.weight(1f))
        }
    }
}
