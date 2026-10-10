package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.isValidIsoDate
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.IncomeSourceInput
import app.masroufy.usecase.JobChange
import app.masroufy.usecase.JobChangeResult
import kotlinx.coroutines.launch

/** اللوحة بثلاث أوضاع (`JobChangeSheet` في النموذج): «غيّرت عملي» · «أضف مصدر دخل» · «إنهاء «…»». */
sealed interface JobSheetMode {
    data object Change : JobSheetMode

    data object Add : JobSheetMode

    data class Close(val source: IncomeSource) : JobSheetMode
}

private enum class Step { CLOSE, NEW, ASK }

/**
 * «غيّرت عملي» (`ManageIncomeSources.changeJob` — القديم بيتقفل والجديد بيتفتح مع بعض) ⇒ «مبارك» على الجديد بس + أسئلته من النتيجة نفسها
 * (يوم الراتب ⇒ «تغيّر بداية شهرك؟» · الدورية للدوام الجزئي · المتوقع اختياري) وكلها تتخطّى. «إضافة» من غير «مبارك» ولا أسئلة. «إنهاء» من غير
 * أي كلمة (ممكن يكون اتفصل — §64). الأخطاء (اسم مكرر بفترة متداخلة …) من حالة الاستخدام نفسها جنب الزرار.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobChangeSheet(mode: JobSheetMode?, current: List<IncomeSource>, monthStart: Int, onClose: () -> Unit, onDone: () -> Unit) {
    var shown by remember { mutableStateOf<JobSheetMode?>(null) }
    if (mode != null) shown = mode
    val m = shown ?: return
    val deps = LocalSpace.current
    val sources = deps.more.incomeSources
    val today = deps.shell.today()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val changeable = current.filter { it.kind == IncomeSourceKind.JOB || it.kind == IncomeSourceKind.PART_TIME }.ifEmpty { current }
    var step by remember { mutableStateOf(Step.CLOSE) }
    var oldId by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf(today) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(IncomeSourceKind.JOB) }
    var start by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<JobChangeResult?>(null) }
    var queue by remember { mutableStateOf<List<IncomeFollowUp>>(emptyList()) }
    var done by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(mode) {
        if (mode == null) return@LaunchedEffect
        step = if (mode == JobSheetMode.Add) Step.NEW else Step.CLOSE
        oldId = (mode as? JobSheetMode.Close)?.source?.id ?: changeable.firstOrNull()?.id
        end = today; name = ""; kind = IncomeSourceKind.JOB; error = null; busy = false; result = null; queue = emptyList(); done = emptyList()
        start = if (mode == JobSheetMode.Change) plusDays(today, 1) else today
    }

    fun act(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure { error = it.message ?: t(TextKey.MORE_SAVE_FAILED) }
            busy = false
        }
    }

    val title = when (m) {
        JobSheetMode.Change -> t(TextKey.INCSRC_CHANGE)
        JobSheetMode.Add -> t(TextKey.INCSRC_ADD)
        is JobSheetMode.Close -> t(TextKey.JOB_CLOSE_TITLE, m.source.name)
    }
    Sheet(mode != null, onClose, title = title, closeLabel = t(TextKey.SHELL_CLOSE), corner = 28.dp, spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(
            t(
                when {
                    step == Step.ASK -> TextKey.JOB_SUB_ASK
                    step == Step.NEW && m == JobSheetMode.Add -> TextKey.JOB_SUB_ADD
                    step == Step.NEW -> TextKey.JOB_SUB_NEW
                    m is JobSheetMode.Close -> TextKey.JOB_SUB_CLOSE
                    else -> TextKey.JOB_SUB_CHANGE
                },
            ),
            style = Type.of(13).copy(color = Ink.muted),
        )
        when (step) {
            Step.CLOSE -> {
                if (m == JobSheetMode.Change && changeable.isNotEmpty()) {
                    Label(t(TextKey.JOB_WHICH_ENDED))
                    for (s in changeable) SelectChip(s.name, oldId == s.id, onClick = { oldId = s.id }, modifier = Modifier.fillMaxWidth(), height = 52.dp)
                }
                Label(t(TextKey.JOB_WHEN_ENDED))
                DateChoices(endDateChoices(today, monthStart), end, today) { end = it }
                PrimaryButton(
                    t(if (m is JobSheetMode.Close) TextKey.JOB_CLOSE_GO else TextKey.JOB_NEXT),
                    onClick = {
                        val id = oldId ?: return@PrimaryButton
                        if (m is JobSheetMode.Close) act { sources.close(id, end); onDone(); onClose() } else step = Step.NEW
                    },
                    enabled = oldId != null && isValidIsoDate(end) && !busy, loading = busy, modifier = Modifier.fillMaxWidth(),
                )
            }
            Step.NEW -> {
                TextInput(name, { name = it; error = null }, label = t(TextKey.JOB_NAME), placeholder = t(TextKey.JOB_NAME_PH))
                Label(t(TextKey.JOB_KIND))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in IncomeSourceKind.entries) FillChip(t(incomeKindLabel(k)), kind == k) { kind = k }
                }
                Label(t(TextKey.JOB_WHEN_STARTED))
                DateChoices(startDateChoices(today, monthStart, m == JobSheetMode.Change), start, today) { start = it }
                PrimaryButton(
                    t(if (m == JobSheetMode.Add) TextKey.JOB_ADD_GO else TextKey.MORE_SAVE),
                    onClick = {
                        val input = IncomeSourceInput(name.trim(), start, kind, deps.space.currency)
                        if (m == JobSheetMode.Add) act {
                            val added = sources.add(input)
                            toaster.show(t(TextKey.JOB_ADDED, added.name), dark = true)
                            onDone(); onClose()
                        } else act {
                            val r = sources.changeJob(JobChange(oldId, end, input))
                            result = r
                            queue = r.followUps
                            step = Step.ASK
                            onDone()
                        }
                    },
                    enabled = name.isNotBlank() && isValidIsoDate(start) && !busy, loading = busy, modifier = Modifier.fillMaxWidth(),
                )
            }
            Step.ASK -> {
                val r = result
                if (r != null) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.selected).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (r.congratulate && r.opened != null) BasicText(t(TextKey.JOB_CONGRATS, r.opened!!.name), style = Type.of(18, FontWeight.Bold).copy(color = Ink.primary))
                    r.closed?.let { c -> BasicText(t(TextKey.JOB_CLOSED_LINE, c.name, fullDate(c.endedAt) ?: c.endedAt.orEmpty()), style = Type.of(13)) }
                }
                val q = queue.firstOrNull()
                if (q != null) FollowUpQuestion(q, step = done.size + 1, total = done.size + queue.size, monthStart, busy,
                    onPayday = { id, day -> act { val more = sources.answerPayday(id, day); done = done + t(TextKey.JOB_DONE_PAYDAY, app.masroufy.core.sentenceNumber(day)); queue = nextQueue(queue, more) } },
                    onFrequency = { id, freq, day -> act { sources.answerPayFrequency(id, freq, day); done = done + payDone(freq, day); queue = nextQueue(queue, emptyList()) } },
                    onMonthStart = { day, yes -> act { if (yes) { sources.applyMonthStart(day); done = done + t(TextKey.ACC_PAYDAY_SAVED, app.masroufy.core.sentenceNumber(day)) }; queue = nextQueue(queue, emptyList()) } },
                    onExpected = { id, minor -> act { sources.answerExpectedSalary(id, minor); done = done + t(TextKey.JOB_DONE_EXPECTED, app.masroufy.ui.components.amountLabel(minor, deps.space.currency)); queue = nextQueue(queue, emptyList()) } },
                    onSkip = { queue = nextQueue(queue, emptyList()) },
                ) else {
                    BasicText(if (done.isEmpty()) t(TextKey.JOB_DONE_LATER) else done.joinToString("، "), style = Type.of(13).copy(color = Ink.muted))
                    PrimaryButton(t(TextKey.MORE_DONE), onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        error?.let { FieldError(it) }
    }
}

private fun payDone(freq: app.masroufy.core.PayFrequency, day: Int): String =
    if (freq == app.masroufy.core.PayFrequency.WEEKLY) t(TextKey.JOB_DONE_WEEKDAY, t(weekdayLabel(day))) else t(TextKey.JOB_DONE_PAYDAY, app.masroufy.core.sentenceNumber(day))

@Composable
private fun Label(text: String) = BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))

/** شرائح التاريخ + «تاريخ آخر» بخانة (سنة-شهر-يوم). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateChoices(choices: List<IsoDate>, selected: IsoDate, today: IsoDate, onPick: (IsoDate) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in choices) FillChip(dateChipLabel(d, today), !custom && selected == d) { custom = false; onPick(d) }
        FillChip(t(TextKey.JOB_OTHER_DATE), custom) { custom = true }
    }
    if (custom) {
        var text by remember { mutableStateOf(selected) }
        TextInput(text, { text = it.trim(); if (isValidIsoDate(text)) onPick(text) }, placeholder = today, ltr = true,
            error = if (text.isNotEmpty() && !isValidIsoDate(text)) t(TextKey.JOB_DATE_BAD) else null)
    }
}
