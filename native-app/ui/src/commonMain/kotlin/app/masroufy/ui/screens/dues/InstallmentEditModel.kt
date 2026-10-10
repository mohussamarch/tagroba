package app.masroufy.ui.screens.dues

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.INSTALLMENT_NAME_MAX
import app.masroufy.core.InstallmentKind
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.formatAmount
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.InstallmentInput
import app.masroufy.usecase.InstallmentView

/**
 * «خطة جديدة / تعديل الخطة» (`InstallmentEdit`): الخانات كنص زي ما المستخدم كتبها، والفحص هنا **قراية ومقارنة بس** (فاضي · مش رقم · صفر)
 * عشان الخطأ يبان جنب الخانة. باقي القواعد (الإجمالي أقل من الأصل · أكتر من 360 قسط · المدفوع أكبر من الإجمالي · العملة والنوع بعد الربط)
 * بتيجي من `ManageInstallments.save` نفسها برسالتها.
 * §82-20: «عدد الأقساط» ⇒ الإجمالي = القسط × العدد **من حالة الاستخدام** (`ManageInstallments.countPreview` و`saveCounted`)؛ من غير عدد
 * الشاشة بتسأل «إجمالي ما ستدفعه» زي الأول. «اشتري دلوقتي وادفع بعدين» (تقسيط مشتريات جديد): أول ميعاد = يوم الشراء، والسعر اختياري.
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
    /** §82-20: عدد الأقساط كنص (فاضي = الإجمالي مكتوب بإيدك). */
    val count: String = "",
    /** §82-20: «اشتري دلوقتي وادفع بعدين» (تابي · تمارا). */
    val payLater: Boolean = false,
    /** عملية أول قسط اللي المالك اختارها (null = «لسه ما ظهرتش»). */
    val firstTxnId: String? = null,
) {
    companion object
}

/** الخانات بنص الخطة المحفوظة (المبالغ من غير فواصل عشان تتعدّل). */
fun PlanForm.Companion.of(v: InstallmentView): PlanForm {
    val p = v.plan
    fun plain(m: Long) = formatAmount(m, p.currency, grouping = false)
    return PlanForm(p.name, p.provider, p.kind, plain(p.principalMinor), plain(p.totalMinor), plain(p.installmentMinor), p.cycleMonths, p.firstDueAt, p.hasInterest)
}

enum class PlanField { NAME, PRINCIPAL, TOTAL, INSTALLMENT, FIRST, COUNT }

/** [count] = عدد الأقساط لو اتكتب ⇒ الحفظ بـ`saveCounted` (الإجمالي من حالة الاستخدام). */
data class PlanFormCheck(val errors: Map<PlanField, String>, val input: InstallmentInput?, val count: Int? = null)

/** عدد الأقساط المكتوب (0-9 بعد التوحيد) — فاضي ⇒ null · مش رقم ⇒ -1 (خطأ جنب الخانة). */
internal fun countOf(text: String): Int? {
    val clean = app.masroufy.core.normalizeDigits(text).trim()
    if (clean.isEmpty()) return null
    return clean.toIntOrNull() ?: -1
}

/** دورات الخطة المعروضة (كل شهر · شهرين · 3 · 6 · سنة). */
val PLAN_CYCLES = listOf(1, 2, 3, 6, 12)

fun checkPlanForm(f: PlanForm, id: String?, currency: Currency): PlanFormCheck {
    val errors = mutableMapOf<PlanField, String>()
    if (f.name.isBlank() || f.name.trim().length > INSTALLMENT_NAME_MAX) errors[PlanField.NAME] = t(TextKey.INSTALLMENT_NAME_LENGTH, INSTALLMENT_NAME_MAX.toString())
    fun money(field: PlanField, text: String): Long? {
        val v = tryParseMoney(text, currency)
        when {
            text.isBlank() || v == null -> errors[field] = t(UiKey.DUES_ERR_FORMAT)
            v <= 0 -> errors[field] = t(UiKey.DUES_ERR_POSITIVE)
        }
        return v
    }
    val count = countOf(f.count)
    if (count != null && count !in 1..app.masroufy.core.DUE_MAX_INSTALLMENTS) errors[PlanField.COUNT] = t(UiKey.INST_ERR_COUNT, app.masroufy.core.DUE_MAX_INSTALLMENTS.toString())
    // «ادفع بعدين» من غير سعر ⇒ صفر وحالة الاستخدام بتحط السعر = الإجمالي · بالعدد ⇒ الإجمالي من حالة الاستخدام (صفر هنا)
    val payLater = f.payLater && f.kind == InstallmentKind.PURCHASE_PLAN && count != null
    val principal = if (payLater && f.principal.isBlank()) 0L else money(PlanField.PRINCIPAL, f.principal)
    val total = if (count != null) 0L else money(PlanField.TOTAL, f.total)
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
    return PlanFormCheck(errors, input, count?.takeIf { it > 0 && PlanField.COUNT !in errors })
}

/** اللي المعاينة محتاجاه من الخانات (القسط بالوحدة الصغرى · العدد · ادفع بعدين) — null لو لسه ناقص أو غلط. قراية بس، الحساب في حالة الاستخدام. */
fun PlanForm.previewInputs(currency: Currency): Triple<Long, Int, Boolean>? {
    val minor = tryParseMoney(installment, currency)?.takeIf { it > 0 } ?: return null
    val n = countOf(count)?.takeIf { it in 1..app.masroufy.core.DUE_MAX_INSTALLMENTS } ?: return null
    return Triple(minor, n, payLater && kind == InstallmentKind.PURCHASE_PLAN)
}

/** «اشتري دلوقتي وادفع بعدين» بيبان لتقسيط المشتريات في خطة جديدة بس (الخطة القديمة جدولها اتحدد خلاص). */
fun PlanForm.payLaterShown(isNew: Boolean): Boolean = isNew && kind == InstallmentKind.PURCHASE_PLAN

/** سطر المعاينة من حالة الاستخدام: «الإجمالي 1,000 = 4 × 250» و(ادفع بعدين) «دفعت 250 وقت الشراء، وفاضل 750 على 3 أقساط جاية». */
fun countPreviewLines(p: app.masroufy.core.InstallmentCountPreview, currency: Currency): List<String> {
    val total = t(UiKey.INST_COUNT_TOTAL, amountLabel(p.totalMinor, currency), p.count.toString(), amountLabel(p.installmentMinor, currency, showCurrency = false))
    val paid = p.paidAtPurchaseMinor ?: return listOf(total)
    val left = p.leftMinor ?: return listOf(total)
    return listOf(total, t(UiKey.INST_PAYLATER_SPLIT, amountLabel(paid, currency), amountLabel(left, currency), (p.upcomingCount ?: 0).toString()))
}

/** النوع والعملة بيتقفلوا بعد ما عمليات اتربطت بالخطة (قسط مدفوع أو المبلغ المستلم) — نفس قاعدة `save`. */
fun InstallmentView.locked(): Boolean = progress.paidMinor > 0 || plan.receivedTransactionId != null
