package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SalaryEmployer
import kotlinx.coroutines.launch

/** «تراجع» عن اختيار جهة الراتب: الجهات قبل التغيير. */
private data class SalaryUndo(val text: String, val before: List<IncomeSource>)

/**
 * «راتب من أي جهة؟» (جولة r05 — البند 9) على راتب داخل: راتب من غير حساب مُحوِّل والجهة الوحيدة الشغالة يومه ⇒ بتتحدد لوحدها و«مش دي» بيلغيها ·
 * جهتين أو مفيش ⇒ الاختيار هنا. أي اختيار بـ«تراجع» 8 ثواني (`ManageEmployers.attachTransaction` · `declineAuto` · `restore`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SalaryEmployerCard(transactionId: Id, reload: Int, onChanged: () -> Unit) {
    val deps = LocalSpace.current.operations
    val employers = deps.employers ?: return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var state by remember(transactionId) { mutableStateOf<SalaryEmployer?>(null) }
    var undo by remember { mutableStateOf<SalaryUndo?>(null) }
    val window = undoWindowFor(undo) { undo = null }
    LaunchedEffect(transactionId, reload) { state = attempt { employers.employerFor(transactionId) } }
    val s = state ?: return
    fun change(text: String, block: suspend () -> List<IncomeSource>) = scope.launch {
        var before: List<IncomeSource> = emptyList()
        val err = failureOf { before = block() }
        if (err != null) toaster.show(err) else undo = SalaryUndo(text, before)
        onChanged()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        undo?.let { u ->
            UndoStrip(u.text, window) {
                undo = null
                scope.launch { toaster.show(failureOf { employers.restore(u.before) } ?: t(UiKey.SALEMP_UNDONE)); onChanged() }
            }
        }
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            val current = s.current
            BasicText(if (current == null) t(UiKey.SALEMP_TITLE_PICK) else t(UiKey.SALEMP_FROM, current.name), style = Type.of(14, FontWeight.Bold))
            if (current != null && s.auto) {
                BasicText(t(UiKey.SALEMP_AUTO), style = Type.caption().copy(color = Ink.muted))
                SmallAction(t(UiKey.SALEMP_NOT_THIS)) { change(t(UiKey.SALEMP_TITLE_PICK)) { employers.declineAuto(current.id, s.transactionId) } }
            }
            if (s.choices.isEmpty()) BasicText(t(UiKey.SALEMP_NONE), style = Type.caption().copy(color = Ink.muted))
            if (current == null || !s.auto) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in s.choices) SelectChip(e.name, current?.id == e.id, {
                    if (current?.id != e.id) change(t(UiKey.SALEMP_SET, e.name)) { employers.attachTransaction(e.id, s.transactionId) }
                }, height = 40.dp)
            }
        }
    }
}
