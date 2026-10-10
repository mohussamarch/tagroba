package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «الوقائع — ليست آراء» (`ZakatFactsSheet` جوه `Zakat`): لكل حاجة سؤالها واختيارين (أو العيار/النقاوة لو ناقصين). الاختيار بيتحفظ
 * على طول ([onAnswer] ⇒ `ManageZakat.setAssetFacts` / `setReceivableFact`) والحساب بيتعاد، و«تم» بيقفل اللوحة.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ZakatFactsSheet(visible: Boolean, questions: List<FactQuestion>, onDismiss: () -> Unit, onAnswer: (Id, FactAnswer) -> Unit) {
    val title = t(UiKey.ZAKAT_SCREEN_FACTS)
    Sheet(visible, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.ZAKAT_FACTS_INTRO), style = Type.of(13).copy(color = Ink.muted))
        if (questions.isEmpty()) BasicText(t(UiKey.ZAKAT_FACTS_EMPTY), style = Type.of(13).copy(color = Ink.muted))
        // الأسئلة ممكن تبقى كتير ⇒ بتتمرر جوه اللوحة (أقصاها ٤٨٠) والزرار تحت ثابت
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (q in questions) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(q.title, style = Type.bodyBold())
                    BasicText(q.ask, style = Type.caption().copy(color = if (q.missing) Ink.focus else Ink.muted))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (o in q.options) SelectChip(o.label, o.selected, { onAnswer(q.subjectId, o.answer) }, height = 44.dp)
                    }
                }
            }
        }
        PrimaryButton(t(UiKey.ZAKAT_FACTS_DONE), onDismiss, Modifier.fillMaxWidth())
    }
}
