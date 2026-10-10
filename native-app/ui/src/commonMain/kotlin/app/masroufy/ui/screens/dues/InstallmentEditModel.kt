package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.INSTALLMENT_NAME_MAX
import app.masroufy.core.InstallmentKind
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.formatAmount
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.text.t
import app.masroufy.usecase.InstallmentInput
import app.masroufy.usecase.InstallmentView

/**
 * «خطة جديدة / تعديل الخطة» (`InstallmentEdit`): الخانات كنص زي ما المستخدم كتبها، والفحص هنا **قراية ومقارنة بس** (فاضي · مش رقم · صفر)
 * عشان الخطأ يبان جنب الخانة. باقي القواعد (الإجمالي أقل من الأصل · أكتر من ٣٦٠ قسط · المدفوع أكبر من الإجمالي · العملة والنوع بعد الربط)
 * بتيجي من `ManageInstallments.save` نفسها برسالتها.
 * ⚠️ النموذج بيسأل «عدد الأقساط» ويحسب الإجمالي = القسط × العدد، ولوحة «النتيجة» بتتحدّث وإنت بتكتب — الحسبتين دول مش في حالة استخدام
 * ⇒ الشاشة بتسأل «إجمالي ما ستدفعه» مباشرة (زي `SCREENS.md` و`InstallmentInput.totalMinor`)، و«النتيجة» ما بتظهرش.
 */
data class PlanForm(
    val name: String = "",
    val provider: String = "",
    val kind: InstallmentKind = InstallmentKind.FINANCING,
    val principal: String = "",
    val total: String = "",
    val installment: String = "",
    val cycleMonths: Int = 1,
    val firstDueAt: IsoDate? = null,
    val hasInterest: Boolean? = null,
) {
    companion object
}

/** الخانات بنص الخطة المحفوظة (المبالغ من غير فواصل عشان تتعدّل). */
fun PlanForm.Companion.of(v: InstallmentView): PlanForm {
    val p = v.plan
    fun plain(m: Long) = formatAmount(m, p.currency, grouping = false)
    return PlanForm(p.name, p.provider, p.kind, plain(p.principalMinor), plain(p.totalMinor), plain(p.installmentMinor), p.cycleMonths, p.firstDueAt, p.hasInterest)
}

enum class PlanField { NAME, PRINCIPAL, TOTAL, INSTALLMENT, FIRST }

data class PlanFormCheck(val errors: Map<PlanField, String>, val input: InstallmentInput?)

/** دورات الخطة المعروضة (كل شهر · شهرين · ٣ · ٦ · سنة). */
val PLAN_CYCLES = listOf(1, 2, 3, 6, 12)

fun checkPlanForm(f: PlanForm, id: String?, currency: Currency): PlanFormCheck {
    val errors = mutableMapOf<PlanField, String>()
    if (f.name.isBlank() || f.name.trim().length > INSTALLMENT_NAME_MAX) errors[PlanField.NAME] = t(TextKey.INSTALLMENT_NAME_LENGTH, INSTALLMENT_NAME_MAX.toString())
    fun money(field: PlanField, text: String): Long? {
        val v = tryParseMoney(text, currency)
        when {
            text.isBlank() || v == null -> errors[field] = t(TextKey.DUES_ERR_FORMAT)
            v <= 0 -> errors[field] = t(TextKey.DUES_ERR_POSITIVE)
        }
        return v
    }
    val principal = money(PlanField.PRINCIPAL, f.principal)
    val total = money(PlanField.TOTAL, f.total)
    val installment = money(PlanField.INSTALLMENT, f.installment)
    if (f.firstDueAt == null) errors[PlanField.FIRST] = t(TextKey.DUE_BAD_FIRST_DATE)
    val input = if (errors.isNotEmpty()) null else InstallmentInput(
        id = id,
        name = f.name.trim(),
        provider = f.provider.trim(),
        kind = f.kind,
        currency = currency,
        principalMinor = principal!!,
        totalMinor = total!!,
        installmentMinor = installment!!,
        cycleMonths = f.cycleMonths,
        firstDueAt = f.firstDueAt!!,
        hasInterest = f.hasInterest,
    )
    return PlanFormCheck(errors, input)
}

/** النوع والعملة بيتقفلوا بعد ما عمليات اتربطت بالخطة (قسط مدفوع أو المبلغ المستلم) — نفس قاعدة `save`. */
fun InstallmentView.locked(): Boolean = progress.paidMinor > 0 || plan.receivedTransactionId != null
