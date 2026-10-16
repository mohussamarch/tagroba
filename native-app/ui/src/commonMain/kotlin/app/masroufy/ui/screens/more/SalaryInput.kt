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
import app.masroufy.core.PROFILE_SALARY_KEY
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

/** خانات الفورم: **خانة لكل جهة شغل شغالة** (رد المالك 2026-10-16) بمعرّفها، أو خانة الملف لوحدها لو مفيش جهات. */
fun salaryKeys(salary: SalaryState?): List<String> = salary?.employers?.map { it.id }?.ifEmpty { null } ?: listOf(PROFILE_SALARY_KEY)

/** المسودة: المبلغ المعروف لكل خانة من غير فواصل ولا رمز (عشان يتعدل)، والمجهول فاضي. */
fun salaryDraft(salary: SalaryState?): Map<String, String> {
    val known = salary?.employers?.associate { it.id to it.expectedMinor }.orEmpty().ifEmpty { mapOf(PROFILE_SALARY_KEY to salary?.amountMinor) }
    return salaryKeys(salary).associateWith { key -> known[key]?.let { formatAmount(it, salary!!.currency, grouping = false) }.orEmpty() }
}

/** كل الخانات صالحة ⇒ المبالغ بمفاتيحها (للحفظ)، وإلا null (زرار «احفظ» مقفول). */
fun readSalaryForm(texts: Map<String, String>, salary: SalaryState?, currency: Currency): Map<String, Halalas>? {
    val read = salaryKeys(salary).associateWith { readSalaryText(texts[it].orEmpty(), currency).minor }
    return if (read.values.all { it != null }) read.mapValues { it.value!! } else null
}

/**
 * خانة المرتب (لوحة «ملفك» وكارت «كمّل ملفك»): نفس خانة المبلغ بتاعة التطبيق (`MoneyField` زي رصيد البداية في `WalletAddSheet` — الرقم يمين
 * والعملة شمال) — **خانة لكل جهة** باسمها لو أكتر من واحدة · سطر أصفر لو الإجابة القديمة كانت نطاق · [hint] (الكارت) · «احفظ» و«مش عايز أقول»
 * جنب بعض — «احفظ» مقفول لحد ما كل الخانات تبقى صالحة.
 */
@Composable
fun SalaryInput(
    texts: Map<String, String>,
    onText: (String, String) -> Unit,
    currency: Currency,
    salary: SalaryState?,
    error: String?,
    enabled: Boolean,
    onSave: (Map<String, Halalas>) -> Unit,
    onDecline: () -> Unit,
    hint: String? = null,
) {
    val form = readSalaryForm(texts, salary, currency)
    val many = (salary?.employers?.size ?: 0) > 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (salary?.wasRange == true) WarnBox(null, t(UiKey.ACC_SALARY_WAS_RANGE_LONG), Modifier.fillMaxWidth())
        if (many) BasicText(t(UiKey.ACC_SALARY_EACH_JOB), style = Type.caption().copy(color = Ink.muted))
        for (key in salaryKeys(salary)) {
            val text = texts[key].orEmpty()
            val label = if (many) salary?.employers?.firstOrNull { it.id == key }?.name else null
            MoneyField(text, { onText(key, it) }, label = label, currency = currency, error = readSalaryText(text, currency).error?.let { t(it) }, big = !many)
        }
        if (hint != null) BasicText(hint, style = Type.caption().copy(color = Ink.muted))
        salary?.employer?.let { BasicText(t(UiKey.ACC_SALARY_ON_EMPLOYER, it.name), style = Type.caption().copy(color = Ink.muted)) }
        if (error != null) FieldError(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(UiKey.ACC_SALARY_SAVE), onClick = { form?.let(onSave) }, enabled = enabled && form != null, modifier = Modifier.weight(1f))
            TonalButton(t(UiKey.ACC_SAL_SKIP), onClick = onDecline, modifier = Modifier.weight(1f), muted = true, enabled = enabled)
        }
    }
}
