package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.operations.failureOf
import app.masroufy.ui.screens.operations.operationsCount
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EmployerInput
import app.masroufy.usecase.EmployerRetroApply
import app.masroufy.usecase.RetroPreview
import app.masroufy.usecase.RetroResult
import kotlinx.coroutines.launch

/** «من 2026-01-01 إلى …» أو «منذ …» — فترة الجهة زي ما اتسجلت. */
fun employerPeriod(s: IncomeSource): String =
    s.endedAt?.let { t(UiKey.EMPLOYER_PERIOD_RANGE, sentenceDigits(s.startedAt), sentenceDigits(it)) } ?: t(UiKey.EMPLOYER_PERIOD_SINCE, sentenceDigits(s.startedAt))

/** المكتوب في الخانات زي ما هو (من غير مسافات)، و«إلى» الفاضية = لسه شغال هناك. التحقق في `ManageEmployers.save`. */
fun employerInput(name: String, from: String, to: String): EmployerInput = EmployerInput(name.trim(), from.trim(), to.trim().ifEmpty { null })

/** خانات الجهة: الاسم · من تاريخ · إلى تاريخ (فاضي = لسه شغال). */
@Composable
fun EmployerFields(name: String, onName: (String) -> Unit, from: String, onFrom: (String) -> Unit, to: String, onTo: (String) -> Unit) {
    TextInput(name, onName, label = t(UiKey.EMPLOYER_NAME))
    TextInput(from, onFrom, label = t(UiKey.EMPLOYER_FROM), placeholder = t(UiKey.EMPLOYER_DATE_HINT), ltr = true)
    TextInput(to, onTo, label = t(UiKey.EMPLOYER_TO), placeholder = t(UiKey.EMPLOYER_DATE_HINT), ltr = true)
}

/**
 * «طبّق على اللي فات» (البند 7): المعاينة بالعدد (`EmployerRetroApply.preview` — أو [initial] الجاية من «راتب الدوام») ⇒ «طبّق (العدد)» =
 * التأكيد (`apply`: نسخة قبل التغيير الأول، ومن غيرها مفيش كتابة) ⇒ «تراجع» (`undo`). اللي حددت نوعه بإيدك ما بيتغيرش إلا لو اخترت.
 * لو التحويلات اتغيرت بعد المعاينة ⇒ رسالة الحالة والمعاينة من الأول.
 */
@Composable
fun EmployerRetroBox(retro: EmployerRetroApply, employerId: Id, initial: RetroPreview? = null, onChanged: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var preview by remember(employerId, initial) { mutableStateOf(initial) }
    var includeHand by remember(employerId) { mutableStateOf(false) }
    var done by remember(employerId) { mutableStateOf<RetroResult?>(null) }
    var undone by remember(employerId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember(employerId) { mutableStateOf<String?>(null) }
    fun run(onFail: () -> Unit = {}, block: suspend () -> Unit) = scope.launch {
        busy = true
        error = failureOf { block() }
        busy = false
        if (error != null) onFail()
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(t(UiKey.EMPLOYER_RETRO_TITLE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
        val p = preview
        val r = done
        when {
            undone -> BasicText(t(UiKey.EMPLOYER_RETRO_UNDONE), style = Type.of(13))
            r != null -> {
                BasicText(t(UiKey.EMPLOYER_RETRO_DONE, operationsCount(r.changed)), style = Type.of(13))
                TonalButton(t(UiKey.EMPLOYER_RETRO_UNDO), { run { retro.undo(r.undo); undone = true; onChanged() } }, height = 40.dp)
            }
            p == null -> TonalButton(t(UiKey.EMPLOYER_RETRO_PREVIEW), { run { preview = retro.preview(employerId) } }, height = 40.dp)
            p.isEmpty -> BasicText(t(UiKey.EMPLOYER_RETRO_NONE), style = Type.of(13).copy(color = Ink.muted))
            else -> {
                if (p.toSalaryIds.isNotEmpty()) BasicText(t(UiKey.EMPLOYER_RETRO_BODY, operationsCount(p.toSalaryIds.size)), style = Type.of(13))
                if (p.handSetIds.isNotEmpty()) {
                    BasicText(t(UiKey.EMPLOYER_RETRO_HAND, operationsCount(p.handSetIds.size)), style = Type.of(13).copy(color = Ink.muted))
                    SelectChip(t(UiKey.EMPLOYER_RETRO_INCLUDE_HAND, sentenceNumber(p.handSetIds.size)), includeHand, { includeHand = !includeHand })
                }
                val count = p.toSalaryIds.size + if (includeHand) p.handSetIds.size else 0
                if (count > 0) {
                    PrimaryButton(t(UiKey.EMPLOYER_RETRO_GO, sentenceNumber(count)), loading = busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        run(onFail = { preview = null }) { done = retro.apply(p, includeHand); onChanged() }
                    })
                }
            }
        }
        error?.let { FieldError(it) }
    }
}
