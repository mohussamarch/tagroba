package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.SalaryState
import app.masroufy.core.UiKey
import app.masroufy.core.formatAmount
import app.masroufy.core.tryParseMoney
import app.masroufy.core.validSalaryAmount
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.dues.MoneyField
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * المرتب **مبلغ بالظبط** (قرار المالك 2026-10-16 — «انا محتاج اكتبه ونخلي الناس تكتبه»). القراية بالهللة بـ`tryParseMoney` (قراية مش حساب).
 * فاضي ⇒ null (مش غلط) · مكتوب ومش مبلغ صالح (صفر أو سالب أو حروف) ⇒ [SalaryText.bad].
 */
data class SalaryText(val minor: Halalas?, val bad: Boolean)

fun readSalaryText(text: String, currency: Currency): SalaryText {
    val raw = text.trim()
    if (raw.isEmpty()) return SalaryText(null, false)
    val minor = tryParseMoney(raw, currency)
    return if (validSalaryAmount(minor)) SalaryText(minor, false) else SalaryText(null, true)
}

/** المسودة اللي الخانة بتبدأ بيها: المبلغ المعروف من غير فواصل ولا رمز (عشان يتعدل)، والمجهول فاضي. */
fun salaryDraft(salary: SalaryState?): String = salary?.amountMinor?.let { formatAmount(it, salary.currency, grouping = false) }.orEmpty()

/**
 * خانة المرتب (لوحة «ملفك» وكارت «كمّل ملفك»): نفس خانة المبلغ بتاعة التطبيق (`MoneyField` — رمز العملة والأرقام من الشمال) ·
 * «كنت مختار نطاق» لو كان فيه نطاق قديم · لو المبلغ هيتحفظ على جهة الشغل اسمها مكتوب · «حفظ» + «أفضّل ألا أقول».
 */
@Composable
fun SalaryInput(
    text: String,
    onText: (String) -> Unit,
    currency: Currency,
    salary: SalaryState?,
    error: String?,
    enabled: Boolean,
    onSave: (Halalas) -> Unit,
    onDecline: () -> Unit,
) {
    val read = readSalaryText(text, currency)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (salary?.wasRange == true) BasicText(t(UiKey.ACC_SALARY_WAS_RANGE), style = Type.bodyBold().copy(color = Ink.primary))
        MoneyField(text, onText, label = null, currency = currency, error = if (read.bad) t(UiKey.ACC_SALARY_BAD) else null, big = true)
        salary?.employer?.let { BasicText(t(UiKey.ACC_SALARY_ON_EMPLOYER, it.name), style = Type.caption().copy(color = Ink.muted)) }
        if (error != null) FieldError(error)
        PrimaryButton(t(UiKey.MORE_SAVE), onClick = { read.minor?.let(onSave) }, enabled = enabled && read.minor != null, modifier = Modifier.fillMaxWidth())
        TonalButton(t(UiKey.ACC_SAL_SKIP), onClick = onDecline, modifier = Modifier.fillMaxWidth(), muted = true, enabled = enabled)
    }
}
