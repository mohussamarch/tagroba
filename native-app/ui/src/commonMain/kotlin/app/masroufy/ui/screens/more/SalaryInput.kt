package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * المرتب **مبلغ بالظبط** (قرار المالك 2026-10-16 — «انا محتاج اكتبه ونخلي الناس تكتبه»). القراية بالهللة بـ`tryParseMoney` (قراية مش حساب —
 * بتقبل الأرقام العربية وخانتين عشريتين بالكتير). فاضي ⇒ من غير غلط · حروف أو أكتر من خانتين ⇒ «اكتب المرتب بالأرقام» · صفر أو سالب ⇒
 * «لازم يكون أكبر من صفر» (نموذج التصميم 85388ca7).
 */
data class SalaryText(val minor: Halalas?, val error: UiKey?)

fun readSalaryText(text: String, currency: Currency): SalaryText {
    val raw = text.trim()
    if (raw.isEmpty()) return SalaryText(null, null)
    val minor = tryParseMoney(raw, currency) ?: return SalaryText(null, UiKey.ACC_SALARY_NOT_NUMBER)
    return if (validSalaryAmount(minor)) SalaryText(minor, null) else SalaryText(null, UiKey.ACC_SALARY_BAD)
}

/** المسودة اللي الخانة بتبدأ بيها: المبلغ المعروف من غير فواصل ولا رمز (عشان يتعدل)، والمجهول فاضي. */
fun salaryDraft(salary: SalaryState?): String = salary?.amountMinor?.let { formatAmount(it, salary.currency, grouping = false) }.orEmpty()

/**
 * خانة المرتب (لوحة «ملفك» وكارت «كمّل ملفك»): نفس خانة المبلغ بتاعة التطبيق (`MoneyField` زي رصيد البداية في `WalletAddSheet` — الرقم يمين
 * والعملة شمال) · سطر أصفر لو الإجابة القديمة كانت نطاق · [hint] (الكارت: «ممكن تغيّره بعدين من ملفك») · اسم جهة الشغل لو المبلغ هيتحفظ عليها ·
 * «احفظ» و«أفضّل ما أقولش» جنب بعض — «احفظ» مقفول لحد ما المبلغ يبقى صالح.
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
    hint: String? = null,
) {
    val read = readSalaryText(text, currency)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (salary?.wasRange == true) WarnBox(null, t(UiKey.ACC_SALARY_WAS_RANGE_LONG), Modifier.fillMaxWidth())
        MoneyField(text, onText, label = null, currency = currency, error = read.error?.let { t(it) }, big = true)
        if (hint != null) BasicText(hint, style = Type.caption().copy(color = Ink.muted))
        salary?.employer?.let { BasicText(t(UiKey.ACC_SALARY_ON_EMPLOYER, it.name), style = Type.caption().copy(color = Ink.muted)) }
        if (error != null) FieldError(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(UiKey.ACC_SALARY_SAVE), onClick = { read.minor?.let(onSave) }, enabled = enabled && read.minor != null, modifier = Modifier.weight(1f))
            TonalButton(t(UiKey.ACC_SAL_SKIP), onClick = onDecline, modifier = Modifier.weight(1f), muted = true, enabled = enabled)
        }
    }
}
