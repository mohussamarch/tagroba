package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.RoscaAnswer
import app.masroufy.core.RoscaDraft
import app.masroufy.core.RoscaForecast
import app.masroufy.core.RoscaPrompt
import app.masroufy.core.applyRoscaAnswer
import app.masroufy.core.nextRoscaQuestion
import app.masroufy.core.roscaForecast
import app.masroufy.core.roscaFromDraft
import app.masroufy.core.roscaPrompt
import app.masroufy.core.roscaQuestionProgress

/**
 * شاشة «جمعية جديدة» بالأسئلة (OVERRIDES §50): سؤال سؤال، وبعد آخر سؤال **التحليل بيظهر قبل الحفظ**
 * (هتقبض إمتى، هتدفع كام، هتفضل مديون قد إيه) — والحفظ بعدها بـ`ManageRoscas.createFromDraft`.
 * مفيش تخزين هنا: المسودة في إيد الشاشة، فالرجوع وتغيير إجابة ببلاش.
 */
data class RoscaSetupState(
    val draft: RoscaDraft,
    /** null = الأسئلة خلصت. */
    val prompt: RoscaPrompt?,
    val step: Int,
    val totalSteps: Int,
    /** التحليل — بيظهر لما الأسئلة تخلص بس. */
    val preview: RoscaForecast?,
)

class RoscaSetup {
    fun begin(currency: Currency): RoscaSetupState = stateOf(RoscaDraft(currency))

    /** الإجابة الغلط بترمي `RoscaError` بسبب مكتوب، والمسودة اللي قبلها بتفضل زي ما هي في إيد الشاشة. */
    fun answer(draft: RoscaDraft, answer: RoscaAnswer): RoscaSetupState = stateOf(applyRoscaAnswer(draft, answer))

    private fun stateOf(d: RoscaDraft): RoscaSetupState {
        val question = nextRoscaQuestion(d)
        val (step, total) = roscaQuestionProgress(d)
        return RoscaSetupState(
            draft = d,
            prompt = question?.let { roscaPrompt(d, it) },
            step = step,
            totalSteps = total,
            preview = if (question == null) roscaForecast(roscaFromDraft(d, "preview", "")) else null,
        )
    }
}
