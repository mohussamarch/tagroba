package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
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
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.UiKey
import app.masroufy.core.ruleFor
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.more.EmployerFields
import app.masroufy.ui.screens.more.EmployerRetroBox
import app.masroufy.ui.screens.more.employerInput
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.AnswerUndo
import app.masroufy.usecase.EmployerView
import app.masroufy.usecase.IncomeAnswers
import app.masroufy.usecase.RetroPreview
import app.masroufy.usecase.SalaryAnswer
import kotlinx.coroutines.launch

/** نتيجة رد الداخل (البند 6): العنوان والسطر و«تراجع» ومعاينة «طبّق على اللي فات» لو فيه جهة. */
data class IncomeDone(val title: String, val body: String, val undo: AnswerUndo, val retro: RetroPreview? = null, val refund: Boolean = false)

/** «راتب الدوام»: «راتب من الجهة» · الحساب اتربط ولا لأ · من غير جهة ⇒ زي أي دخل. أسامي الأنواع زي ما هي ([ruleFor]). */
fun salaryDone(a: SalaryAnswer): IncomeDone {
    val name = a.employer?.name
    val title = name?.let { t(UiKey.INCOME_ANS_DONE_SALARY, it) } ?: t(UiKey.INCOME_ANS_DONE_INCOME, ruleFor(EconomicKind.SALARY).label)
    val body = when {
        name == null -> t(UiKey.INCOME_ANS_DONE_INCOME_BODY)
        a.party == null -> t(UiKey.INCOME_ANS_DONE_NO_PARTY)
        else -> t(UiKey.INCOME_ANS_DONE_SALARY_BODY, name)
    }
    return IncomeDone(title, body, a.undo, a.retro)
}

/**
 * أزرار ردود الداخل (البند 6): «ده راتب الدوام» (لوحة الجهة ⇒ `IncomeAnswers.markSalary`) · «عمل إضافي» (`FREELANCE`) · «بيعة»
 * (`PERSONAL_SALE`) ⇒ `markIncome` للتحويل ده بس. [withOther] = «نوع آخر» من `IncomeAnswers.OTHER_KINDS` (في تغيير نوع تحويل داخل).
 * null في الاعتمادات (المعاينة) ⇒ مفيش أزرار.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeAnswerChips(transactionId: Id, withOther: Boolean, withSalary: Boolean = true, onDone: (IncomeDone) -> Unit) {
    val answers = LocalSpace.current.operations.incomeAnswers ?: return
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun mark(kind: EconomicKind) = scope.launch {
        var undo: AnswerUndo? = null
        error = failureOf { undo = answers.markIncome(transactionId, kind) }
        undo?.let { onDone(IncomeDone(t(UiKey.INCOME_ANS_DONE_INCOME, ruleFor(kind).label), t(UiKey.INCOME_ANS_DONE_INCOME_BODY), it)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (withSalary) SelectChip(t(UiKey.INCOME_ANS_SALARY), false, { picking = true; error = null })
        SelectChip(t(UiKey.INCOME_ANS_SIDE_JOB), false, { mark(EconomicKind.FREELANCE) })
        SelectChip(t(UiKey.INCOME_ANS_SALE), false, { mark(EconomicKind.PERSONAL_SALE) })
        if (withOther) SelectChip(t(UiKey.INCOME_ANS_OTHER), other, { other = !other })
    }
    if (other) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in IncomeAnswers.OTHER_KINDS) SelectChip(ruleFor(k).label, false, { mark(k) })
        }
    }
    error?.let { FieldError(it) }
    EmployerPickSheet(picking, transactionId, onDismiss = { picking = false }) { picking = false; onDone(it) }
}

/** بعد الرد: كارت أخضر بـ«تراجع» (`IncomeAnswers.undo`) + «طبّق على اللي فات» لو الجهة ليها تحويلات قديمة. */
@Composable
fun IncomeDoneBox(done: IncomeDone, onUndone: () -> Unit, onChanged: () -> Unit) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    var error by remember(done) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(ChipInk.greenBg).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(done.title, style = Type.of(13, FontWeight.Bold).copy(color = ChipInk.green))
            BasicText(done.body, style = Type.of(13))
            TonalButton(t(UiKey.TRANSFER_PARTY_UNDO), {
                scope.launch {
                    val err = failureOf { deps.incomeAnswers?.undo(done.undo) }
                    if (err == null) onUndone() else error = err
                }
            }, height = 40.dp)
            error?.let { FieldError(it) }
        }
        val retro = deps.employerRetro
        val employerId = done.retro?.employerId
        if (retro != null && employerId != null) EmployerRetroBox(retro, employerId, done.retro, onChanged)
    }
}

/** «ما نوع هذا المبلغ؟» على تحويل داخل اتقرر قبل كده (تعديل نوعه بنفس الردود + «نوع آخر»). */
@Composable
fun IncomeKindCard(transactionId: Id, onChanged: () -> Unit) {
    if (LocalSpace.current.operations.incomeAnswers == null) return
    var done by remember(transactionId) { mutableStateOf<IncomeDone?>(null) }
    val d = done
    if (d != null) {
        IncomeDoneBox(d, onUndone = { done = null; onChanged() }, onChanged = onChanged)
        return
    }
    // PLAN-0.6 §7: تحويل من حساب جهة الشغل مش راتب ⇒ «الفلوس دي إيه؟»
    if (payerAskShown(transactionId, onDone = { done = it; onChanged() }, onChanged = onChanged)) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(t(UiKey.INCOME_ANS_CHANGE_TITLE), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
        IncomeAnswerChips(transactionId, withOther = true) { done = it; onChanged() }
    }
}

/** «راتب من أي جهة؟»: جهة موجودة أو «جهة جديدة» (الاسم · من · إلى) ⇒ `markSalary` (الحساب المحوِّل بيتربط بيها في نفس الخطوة). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmployerPickSheet(visible: Boolean, transactionId: Id, onDismiss: () -> Unit, onDone: (IncomeDone) -> Unit) {
    val deps = LocalSpace.current.operations
    val answers = deps.incomeAnswers ?: return
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<EmployerView>>(emptyList()) }
    var party by remember { mutableStateOf<TransferPartyRef?>(null) }
    var picked by remember { mutableStateOf<Id?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        list = attempt { deps.employers?.list() }.orEmpty()
        party = attempt { answers.partyOf(transactionId) }
        isNew = list.isEmpty()
        error = null
    }
    Sheet(visible, onDismiss, title = t(UiKey.EMPLOYER_PICK_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(t(UiKey.EMPLOYER_PICK_TITLE), style = Type.of(17, FontWeight.Bold))
        BasicText(party?.let { t(UiKey.EMPLOYER_PICK_BODY, partyAccount(it)) } ?: t(UiKey.EMPLOYER_PICK_BODY_NO_PARTY), style = Type.of(13).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in list) SelectChip(e.source.name, !isNew && picked == e.source.id, { picked = e.source.id; isNew = false; error = null }, height = 44.dp)
            SelectChip(t(UiKey.EMPLOYER_PICK_NEW), isNew, { isNew = true; picked = null; error = null }, height = 44.dp)
        }
        if (isNew) EmployerFields(name, { name = it; error = null }, from, { from = it; error = null }, to, { to = it; error = null })
        error?.let { FieldError(it) }
        PrimaryButton(t(UiKey.EMPLOYER_LINK_SAVE), loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                saving = true
                var answer: SalaryAnswer? = null
                error = failureOf { answer = answers.markSalary(transactionId, picked.takeIf { !isNew }, if (isNew) employerInput(name, from, to) else null) }
                saving = false
                answer?.let { onDone(salaryDone(it)) }
            }
        })
    }
}
