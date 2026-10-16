package app.masroufy.ui.screens.character

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * التأكيد جوه الصفحة (مش لوحة النظام): «عندك تعديلات ما اتحفظتش» · «احفظ» · «اخرج من غير حفظ» · «كمّل تعديل». الدوس برا الكارت = «كمّل تعديل»
 * (ما بيخرجش أبدًا من غير قرار).
 */
@Composable
internal fun ExitConfirm(saving: Boolean, onChoice: (ExitChoice) -> Unit) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(none, indication = null) { onChoice(ExitChoice.STAY) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.surface)
                .clickable(none, indication = null) {}.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(t(UiKey.CHAR_UNSAVED_TITLE), style = Type.of(17, FontWeight.Bold))
            BasicText(t(UiKey.CHAR_UNSAVED_BODY), style = Type.of(13).copy(color = Ink.muted))
            PrimaryButton(t(UiKey.CHAR_UNSAVED_SAVE), onClick = { onChoice(ExitChoice.SAVE) }, loading = saving, enabled = !saving, height = 52.dp, modifier = Modifier.fillMaxWidth())
            TonalButton(t(UiKey.CHAR_UNSAVED_LEAVE), onClick = { onChoice(ExitChoice.LEAVE) }, height = 48.dp, modifier = Modifier.fillMaxWidth())
            TonalButton(t(UiKey.CHAR_UNSAVED_STAY), onClick = { onChoice(ExitChoice.STAY) }, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** فتح البنّاء ولقينا مسودة من مرة قبل كده: «كمّل من آخر تعديل» أو «ابدأ من المحفوظ». */
@Composable
internal fun DraftOffer(onResume: () -> Unit, onDiscard: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.alertBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(UiKey.CHAR_DRAFT_TITLE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
        BasicText(t(UiKey.CHAR_DRAFT_BODY), style = Type.of(12).copy(color = Ink.muted))
        PrimaryButton(t(UiKey.CHAR_DRAFT_RESUME), onClick = onResume, height = 46.dp, modifier = Modifier.fillMaxWidth())
        TonalButton(t(UiKey.CHAR_DRAFT_SAVED), onClick = onDiscard, height = 44.dp, modifier = Modifier.fillMaxWidth())
    }
}
