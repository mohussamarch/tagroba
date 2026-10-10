package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.RouteSheet
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** عدد الأشكال المؤقتة (نفس «اختر شكلك» في أول مرة — الكاركتر النهائي لسه بيتصمم §73). */
const val LOOK_COUNT = 6

/**
 * «اختر شكلك» (`LookSheet` — جوه «ملفك»): ٦ دواير ٧٦ (الشكل ٦٠ جواها) في ٣ أعمدة، المختار بحد أخضر 3 · «أشكال مؤقتة…» · «احفظ».
 * الاختيار بيظهر في كل الدواير ([LookChoice]). ⚠️ **حفظه مع الحساب ناقص في المنطق** (مفيش حقل للشكل في `UserProfile`).
 */
@Composable
internal fun LookSheet(close: () -> Unit) {
    var pick by remember { mutableStateOf(LookChoice.look ?: 1) }
    RouteSheet(t(UiKey.LOOK_SHEET_TITLE), close) { dismiss ->
        BasicText(t(UiKey.LOOK_SHEET_TITLE), style = Type.of(17, androidx.compose.ui.text.font.FontWeight.Bold))
        for (row in (1..LOOK_COUNT).chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (k in row) LookChoiceButton(k, pick == k) { pick = k }
            }
        }
        BasicText(t(UiKey.LOOK_SHEET_NOTE), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
        PrimaryButton(t(UiKey.LOOK_SHEET_SAVE), onClick = { LookChoice.look = pick; dismiss() }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LookChoiceButton(look: Int, selected: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val label = t(UiKey.LOOK_SHEET_ITEM, sentenceNumber(look))
    val ring = if (selected) Modifier.insetRing(CircleShape, 3.dp, Ink.primary) else Modifier
    Box(
        Modifier.size(76.dp).pressScale(press).clip(CircleShape).background(Glass.lensOnLight).then(ring)
            .tap(press, role = Role.RadioButton, label = label, onClick = onClick).semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { MeAvatar(60.dp, percent = 100, look = look) }
}
