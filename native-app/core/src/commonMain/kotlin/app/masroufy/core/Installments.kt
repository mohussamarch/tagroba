package app.masroufy.core

/**
 * الأقساط اللي عليك لبنك أو شركة — قرار المالك 2026-09-30 إنها تدخل «المستحقات» (OVERRIDES §50).
 * الطرف التاني **جهة** مش شخص، عشان كده ليها كيان لوحدها مش `Obligation` (اللي مربوط بشخص).
 *
 * نوعين، والفرق في الحساب مش في الشكل:
 * - **تقسيط مشتريات** (جهاز، تابي، تمارا): كل قسط **مصروف** وقت ما يتدفع — الحاجة نفسها ما ظهرتش في الكشف.
 * - **تمويل** (فلوس استلمتها من البنك): الأصل **مش مصروف** (استلمته واتصرف في عمليات تانية)،
 *   والزيادة عليه (الأرباح أو الفوايد) **مصروف** بيتوزع على الأقساط بنسبة اللي اتدفع.
 */
enum class InstallmentKind(val wire: String) {
    PURCHASE_PLAN("purchase_plan"), FINANCING("financing");

    companion object {
        fun fromWire(wire: String): InstallmentKind = entries.first { it.wire == wire }
    }
}

data class InstallmentPlan(
    val id: Id,
    /** «تمويل شخصي» أو «جوال». */
    val name: String,
    /** اسم الجهة. */
    val provider: String,
    val kind: InstallmentKind,
    val currency: Currency,
    /** تمويل: اللي استلمته. تقسيط: تمن الحاجة. */
    val principalMinor: Halalas,
    /** إجمالي اللي هتدفعه. */
    val totalMinor: Halalas,
    val installmentMinor: Halalas,
    val cycleMonths: Int,
    val firstDueAt: IsoDate,
    /** «فيها فوايد؟» (OVERRIDES §46) — null = المستخدم ما قالش. ميزة «حلال تشك» بعدين بتقرا منه. */
    val hasInterest: Boolean? = null,
    val createdAt: String = "",
    /**
     * عملية «مبلغ تمويل مستلم» من الكشف (تمويل بس) — قرار المالك 2026-10-03 (OVERRIDES §59): لوحة الديون بتحسب من اللي
     * استلمته فعلًا، مش من الرقم المكتوب في الخطة بس. null = لسه ما اتربطش.
     */
    val receivedTransactionId: Id? = null,
    /** §75-8 (الشريحة S4): عمليات المالك قال عنها «مش قسط الخطة دي» ⇒ ما تتقترحش عليها تاني (`SuggestDueLinks`). */
    val dismissedTxnIds: List<Id> = emptyList(),
)

/** ربط عملية من الكشف بخطة الأقساط. */
data class InstallmentPayment(val id: Id, val planId: Id, val transactionId: Id, val amountMinor: Halalas)

const val INSTALLMENT_NAME_MAX = 80

class InstallmentError(message: String) : IllegalArgumentException(message)

fun installmentSchedule(p: InstallmentPlan): DueSchedule = DueSchedule(p.firstDueAt, p.cycleMonths, p.installmentMinor, p.totalMinor, p.currency)

fun checkInstallmentPlan(p: InstallmentPlan): CheckedName {
    val clean = JsText.collapseWhitespace(JsText.trim(p.name))
    if (clean.isEmpty() || clean.length > INSTALLMENT_NAME_MAX) {
        throw InstallmentError(uiText(TextKey.INSTALLMENT_NAME_LENGTH, INSTALLMENT_NAME_MAX.toString()))
    }
    if (JsText.trim(p.provider).length > INSTALLMENT_NAME_MAX) throw InstallmentError(uiText(TextKey.INSTALLMENT_NAME_LENGTH, INSTALLMENT_NAME_MAX.toString()))
    assertHalalas(p.principalMinor)
    if (p.principalMinor <= 0) throw InstallmentError(uiText(TextKey.DUE_AMOUNT_POSITIVE))
    checkDueSchedule(installmentSchedule(p))
    if (p.totalMinor < p.principalMinor) throw InstallmentError(uiText(TextKey.INSTALLMENT_TOTAL_BELOW_PRINCIPAL))
    return CheckedName(clean, normalizeText(clean))
}

/** تكلفة التمويل = الإجمالي − الأصل. في تقسيط المشتريات بتبقى جزء من المصروف أصلًا. */
fun financingCostOf(p: InstallmentPlan): Halalas = subtractMoney(p.totalMinor, p.principalMinor)

/**
 * جزء التكلفة اللي **اتحقق** لما المدفوع وصل [paidMinor] — بنسبة المدفوع للإجمالي.
 * التقريب على المجموع التراكمي مش على كل دفعة ⇒ مجموع أجزاء كل الدفعات = التكلفة كاملة بالهللة.
 */
fun financingCostPaidThrough(p: InstallmentPlan, paidMinor: Halalas): Halalas {
    if (paidMinor < 0 || paidMinor > p.totalMinor) {
        throw InstallmentError(uiText(TextKey.DUE_OVERPAID, formatMoney(paidMinor, p.currency), formatMoney(p.totalMinor, p.currency)))
    }
    return rateOfMoney(financingCostOf(p), paidMinor, p.totalMinor)
}

/**
 * مصروف التمويل في فترة: الدفعات بالترتيب، وكل دفعة جوه الفترة بتضيف الفرق في التكلفة المتحققة.
 * [paymentDates] = تاريخ كل دفعة (من العملية المربوطة). تقسيط المشتريات بيرجع صفر — أقساطه مصروف بنوعها.
 */
fun financingCostInPeriod(p: InstallmentPlan, payments: List<InstallmentPayment>, paymentDates: Map<Id, IsoDate>, period: Period): Halalas {
    if (p.kind != InstallmentKind.FINANCING) return 0
    val ordered = payments.filter { it.planId == p.id }.sortedWith(compareBy({ paymentDates[it.id] ?: "" }, { it.id }))
    var paid = 0L
    var cost = 0L
    for (pay in ordered) {
        val before = financingCostPaidThrough(p, paid)
        paid = addMoney(paid, pay.amountMinor)
        val date = paymentDates[pay.id] ?: continue
        if (isDateInPeriod(date, period)) cost = addMoney(cost, financingCostPaidThrough(p, paid) - before)
    }
    return cost
}

/** النوع الاقتصادي اللي العملية المربوطة بتاخده. */
fun installmentPaymentKind(p: InstallmentPlan): EconomicKind =
    if (p.kind == InstallmentKind.PURCHASE_PLAN) EconomicKind.PURCHASE else EconomicKind.INSTALLMENT_PAID

/**
 * مواعيد دين بين الناس (`Obligation`) — كيان جنبه مش حقول جواه، عشان الدين نفسه يفضل
 * مطابق للتطبيق الحالي ونسخته الاحتياطية بالحرف. الدين من غير مواعيد بيفضل زي ما هو: رصيد بس.
 */
data class DebtTerms(
    val obligationId: Id,
    /** عشان القراية بالشخص (المستودع ما بيقراش كل الديون مرة واحدة). */
    val personId: Id,
    val firstDueAt: IsoDate,
    val cycleMonths: Int = 1,
    /** null = دفعة واحدة بالمبلغ كله. */
    val installmentMinor: Halalas? = null,
    /** «فيها فوايد؟» (OVERRIDES §46). */
    val hasInterest: Boolean? = null,
)

fun debtSchedule(terms: DebtTerms, obligation: Obligation): DueSchedule =
    DueSchedule(terms.firstDueAt, terms.cycleMonths, terms.installmentMinor ?: obligation.originalMinor, obligation.originalMinor, obligation.currency)
