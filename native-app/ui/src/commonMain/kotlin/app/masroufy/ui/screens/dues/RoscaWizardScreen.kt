package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.RoscaQuestion
import app.masroufy.core.TextKey
import app.masroufy.core.formatAmount
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «جمعية جديدة» (`RoscaWizard`): 8 أسئلة (سؤال في كل خطوة — الاسم · كم دورًا · القسط · كل كم · أول دفعة · كم سهمًا · دورك أو «غير معروف بعد» ·
 * قيمة الدور) فوق `RoscaSetup`، والخطأ جنب سؤاله · «السابق» والرجوع لأي إجابة من التحليل (بيمسح المتعارض بس) · **التحليل قبل الحفظ** ⇒
 * `ManageRoscas.createFromDraft`.
 */
@Composable
fun RoscaWizardScreen() {
    val space = LocalSpace.current
    val deps = space.dues
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val currency = space.space.currency
    val today = space.shell.today()
    var state by remember(deps) { mutableStateOf(deps.roscaSetup.begin(currency)) }
    var editing by remember { mutableStateOf<RoscaQuestion?>(null) }
    var inputs by remember { mutableStateOf(WizardInputs()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val plain = { m: Long -> formatAmount(m, currency, grouping = false) }
    val q: RoscaQuestion? = editing ?: state.prompt?.question
    val (step, total) = wizardStep(state, q)

    fun goTo(target: RoscaQuestion?) {
        editing = target
        inputs = inputs.from(state.draft, plain)
        error = null
    }

    fun next() {
        if (q == null) {
            saving = true
            scope.launch {
                try {
                    val r = deps.roscas.createFromDraft(state.draft)
                    toaster.show(t(UiKey.RW_SAVED, r.name))
                    saved = true
                    DuesChanges.bump()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    error = failText(e)
                } finally {
                    saving = false
                }
            }
            return
        }
        val (answer, problem) = answerOf(q, inputs, currency)
        if (answer == null) {
            error = problem
            return
        }
        try {
            state = deps.roscaSetup.answer(state.draft, answer)
            goTo(null)
        } catch (e: IllegalArgumentException) {
            error = failText(e)
        } catch (e: IllegalStateException) {
            error = failText(e)
        }
    }

    // الزرارين ثابتين تحت زي النموذج: «السابق» (لو فيه) · «التالي»/«احفظ الجمعية» — وبعد الحفظ «إلى الجمعيات»
    val bar: @Composable RowScope.() -> Unit = {
        if (saved) PrimaryButton(t(UiKey.RW_DONE), { nav.pop() }, Modifier.weight(1f), height = 52.dp)
        else {
            val prev = previousQuestion(state.draft, q)
            if (prev != null) SecondaryButton(t(UiKey.RW_PREV), { goTo(prev) }, Modifier.weight(1f), height = 52.dp)
            PrimaryButton(t(if (q == null) UiKey.RW_SAVE else UiKey.RW_NEXT), { next() }, Modifier.weight(2f), loading = saving, height = 52.dp)
        }
    }
    DuesScaffold(t(UiKey.RW_TITLE), bottomBar = bar) {
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    if (q == null) t(UiKey.RW_SUMMARY_STEP) else t(UiKey.RW_STEP, sentenceNumber(step), sentenceNumber(total)),
                    style = Type.caption().copy(color = Ink.muted),
                )
                CountBar(if (q == null) total else step - 1, total, onHero = false)
            }
        }
        if (q != null) {
            item(key = "q-${q.name}") {
                QuestionCard(q, state, inputs, today, error, onInputs = { inputs = it; error = null })
            }
        } else {
            wizardSummary(state, today)?.let { s -> item(key = "summary") { SummaryView(s, currency, onEdit = { goTo(it) }) } }
            error?.let { item(key = "save-error") { FieldError(it) } }
        }
    }
}
