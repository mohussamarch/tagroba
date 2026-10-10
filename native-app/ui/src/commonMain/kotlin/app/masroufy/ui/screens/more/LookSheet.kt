package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «اختر شكلك» (`LookSheet` — من «ملفك»): الأشكال الستة المؤقتة (الكاركتر النهائي لسه بيتصمم — §73) في شبكة ٣ أعمدة. الحفظ بيعدّي على
 * [LookSetting] — `UserProfile` مالوش حقل للشكل لسه ⇒ «غير متاح بعد» والزرار مقفول (ما بنعملش حفظ شكلي بيضيع).
 */
@Composable
fun LookSheet(visible: Boolean, current: Int, onClose: () -> Unit) {
    val hook = LocalSpace.current.more.look
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var pick by remember { mutableIntStateOf(current) }
    LaunchedEffect(visible) { if (visible) pick = current }
    Sheet(visible, onClose, title = t(UiKey.LOOK_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, spacing = 12.dp) {
        BasicText(t(UiKey.LOOK_TITLE), style = Type.of(17, androidx.compose.ui.text.font.FontWeight.Bold))
        LookGrid(pick) { pick = it }
        BasicText(t(UiKey.LOOK_NOTE), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
        if (hook == null) NotYetLine(t(UiKey.LOOK_NOT_YET))
        PrimaryButton(
            t(UiKey.MORE_SAVE),
            onClick = { scope.launch { runCatching { hook?.choose(pick) }; toaster.show(t(UiKey.LOOK_SAVED), dark = true); onClose() } },
            enabled = hook != null, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** الشبكة نفسها (مشتركة مع «اختر شكلك» في أول مرة): دايرة 76 فيها `MeAvatar` 60، والمختار حلقة خضرا 3. */
@Composable
fun LookGrid(selected: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in (1..6).chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (k in row) {
                val press = rememberPress()
                val on = selected == k
                Box(
                    Modifier.size(76.dp).pressScale(press).clip(CircleShape).background(Glass.lensOnLight)
                        .then(if (on) Modifier.insetRing(CircleShape, 3.dp, Ink.primary) else Modifier)
                        .tap(press, role = Role.RadioButton, label = t(UiKey.LOOK_N, sentenceNumber(k)), onClick = { onPick(k) })
                        .semantics { this.selected = on },
                    contentAlignment = Alignment.Center,
                ) { MeAvatar(60.dp, 100, look = k) }
            }
        }
    }
}
