package app.masroufy.ui.screens.people

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.ProjectKind
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحتين صغيرين من صفحة الشخص (r06-person — شبكة «اعملوا سوا» اتشالت، OVERRIDES §116-ب):
 * - **مشروع مشترك** (من أيقونات «معاه»): بيعمل مشروع شخصي «مشروع مع {الاسم}» (`ManageProjects.create`) ويفتحه.
 * - **طلب اتصال** (علامة «مش متصل» شمال الصورة): محتاج ربط الحسابات (مرحلة جاية) ⇒ شرح بس، والزرار مقفول ومفيش أي اتصال بالنت.
 */
internal enum class TogetherAct(val title: TextRef, val body: TextRef, val go: TextRef) {
    PROJECT(UiKey.PERSON_PAGE_PROJECT_TITLE, UiKey.PERSON_PAGE_PROJECT_BODY, UiKey.PERSON_PAGE_PROJECT_GO),
    LINK(UiKey.PERSON_PAGE_LINK_TITLE, UiKey.PERSON_PAGE_LINK_BODY, UiKey.PERSON_PAGE_LINK_GO),
}

@Composable
internal fun TogetherSheet(act: TogetherAct?, name: String, close: () -> Unit) {
    Sheet(act != null, onDismiss = close, title = act?.let { t(it.title, name) } ?: "", veil = app.masroufy.ui.overlay.Veil.MENU) {
        if (act != null) TogetherBody(act, name, close)
    }
}

@Composable
private fun TogetherBody(act: TogetherAct, name: String, close: () -> Unit) {
    val space = LocalSpace.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(t(act.title, name), style = Type.section())
        BasicText(t(act.body, name), style = Type.of(14, lineHeight = 1.7).copy(color = Ink.muted))
        if (act == TogetherAct.LINK) Pill(t(UiKey.PERSON_PAGE_LINK_NOTE), Ink.focus, Ink.alertBg)
        ErrorLine(err)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                t(act.go),
                enabled = act == TogetherAct.PROJECT,
                loading = busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (act != TogetherAct.PROJECT) return@PrimaryButton
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
