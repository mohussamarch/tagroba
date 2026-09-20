package app.masroufy.core

import app.masroufy.core.EconomicKind.*

/**
 * الأنواع الاقتصادية — نقل `src/domain/entities/economicKind.ts` (جدول spec/02 حرفيًا).
 * **وارد الحساب لا يحدد وحده النوع الاقتصادي**: اتجاه السيولة حقيقة بنكية، والنوع قرار محاسبي.
 * `wire` = الاسم المتخزن في فايربيز — ما يتغيرش.
 */
enum class EconomicKind(val wire: String) {
    // وارد — بيزود الدخل
    SALARY("salary"), BONUS("bonus"), COMMISSION("commission"), OVERTIME("overtime"), FREELANCE("freelance"),
    PERSONAL_SALE("personal_sale"),
    // وارد — ما بيزودش الدخل
    LOAN_RECEIVED("loan_received"), DEBT_COLLECTED("debt_collected"), CUSTODY_RECEIVED("custody_received"),
    // صادر — مصروف
    PURCHASE("purchase"), SUPPORT_GIFT("support_gift"), FEE("fee"),
    // صادر — مش مصروف
    LOAN_GRANTED("loan_granted"), DEBT_REPAID("debt_repaid"), CUSTODY_RETURNED("custody_returned"),
    // داخلي
    INTERNAL_TRANSFER("internal_transfer"),
    // استثمار
    ASSET_BUY("asset_buy"), ASSET_SELL("asset_sell"),
    // وارد جديد — قرارات المالك 2026-09-20 (OVERRIDES §42)
    GIFT_RECEIVED("gift_received"), SUPPORT_RECEIVED("support_received"), BENEFIT_RECEIVED("benefit_received"),
    INVESTMENT_INCOME("investment_income"),
    // وارد بس مش دخل
    ROSCA_PAYOUT("rosca_payout"), REFUND_RECEIVED("refund_received"), ADVANCE_RECEIVED("advance_received"),
    // لسه ما اتحددش
    UNCLASSIFIED("unclassified");

    companion object {
        fun fromWire(wire: String): EconomicKind =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException(uiText(TextKey.KIND_UNKNOWN_WIRE, wire))
    }
}

enum class Direction(val wire: String) {
    IN("in"), OUT("out");

    companion object {
        fun fromWire(wire: String): Direction = entries.first { it.wire == wire }
    }
}

enum class Liquidity(val wire: String) { IN("in"), OUT("out"), INTERNAL("internal") }

enum class PersonEffect(val wire: String) {
    NONE("none"),
    RECEIVABLE_UP("receivable_up"), RECEIVABLE_DOWN("receivable_down"),
    PAYABLE_LOAN_UP("payable_loan_up"), PAYABLE_LOAN_DOWN("payable_loan_down"),
    PAYABLE_CUSTODY_UP("payable_custody_up"), PAYABLE_CUSTODY_DOWN("payable_custody_down"),
    BENEFICIARY_INFO("beneficiary_info"),
}

data class EconomicKindRule(
    val kind: EconomicKind,
    val labelKey: TextKey,
    val liquidity: Liquidity,
    /** بيزود الدخل الاقتصادي للفترة؟ */
    val countsAsIncome: Boolean,
    /** بيزود المصروف الشخصي؟ (نصيب المستخدم بس بعد فصل اللي على غيره) */
    val countsAsPersonalExpense: Boolean,
    val personEffect: PersonEffect,
    /**
     * بيـ**نقّص** المصروف بدل ما يزوده — الاسترداد بس (OVERRIDES §42).
     * فلوس رجعتلك عن حاجة دفعتها مش دخل جديد؛ لو اتحسبت دخل يبقى الشهر ده دخله كذب.
     */
    val reducesExpense: Boolean = false,
) {
    /** الاسم المعروض باللغة الحالية (OVERRIDES §40) — بيتقرا وقت العرض مش وقت التحميل. */
    val label: String get() = uiText(labelKey)
}

private fun rule(
    kind: EconomicKind,
    labelKey: TextKey,
    liquidity: Liquidity,
    income: Boolean,
    expense: Boolean,
    effect: PersonEffect,
    reducesExpense: Boolean = false,
) = kind to EconomicKindRule(kind, labelKey, liquidity, income, expense, effect, reducesExpense)

private val RULES: Map<EconomicKind, EconomicKindRule> =
    mapOf(
        rule(SALARY, TextKey.KIND_SALARY, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(BONUS, TextKey.KIND_BONUS, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(COMMISSION, TextKey.KIND_COMMISSION, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(OVERTIME, TextKey.KIND_OVERTIME, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(FREELANCE, TextKey.KIND_FREELANCE, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(PERSONAL_SALE, TextKey.KIND_PERSONAL_SALE, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(LOAN_RECEIVED, TextKey.KIND_LOAN_RECEIVED, Liquidity.IN, false, false, PersonEffect.PAYABLE_LOAN_UP),
        rule(DEBT_COLLECTED, TextKey.KIND_DEBT_COLLECTED, Liquidity.IN, false, false, PersonEffect.RECEIVABLE_DOWN),
        rule(CUSTODY_RECEIVED, TextKey.KIND_CUSTODY_RECEIVED, Liquidity.IN, false, false, PersonEffect.PAYABLE_CUSTODY_UP),
        rule(PURCHASE, TextKey.KIND_PURCHASE, Liquidity.OUT, false, true, PersonEffect.RECEIVABLE_UP),
        rule(SUPPORT_GIFT, TextKey.KIND_SUPPORT_GIFT, Liquidity.OUT, false, true, PersonEffect.BENEFICIARY_INFO),
        rule(FEE, TextKey.KIND_FEE, Liquidity.OUT, false, true, PersonEffect.NONE),
        rule(LOAN_GRANTED, TextKey.KIND_LOAN_GRANTED, Liquidity.OUT, false, false, PersonEffect.RECEIVABLE_UP),
        rule(DEBT_REPAID, TextKey.KIND_DEBT_REPAID, Liquidity.OUT, false, false, PersonEffect.PAYABLE_LOAN_DOWN),
        rule(CUSTODY_RETURNED, TextKey.KIND_CUSTODY_RETURNED, Liquidity.OUT, false, false, PersonEffect.PAYABLE_CUSTODY_DOWN),
        rule(INTERNAL_TRANSFER, TextKey.KIND_INTERNAL_TRANSFER, Liquidity.INTERNAL, false, false, PersonEffect.NONE),
        rule(ASSET_BUY, TextKey.KIND_ASSET_BUY, Liquidity.OUT, false, false, PersonEffect.NONE),
        // بيع الأصل: الربح المحقق بس هو المكسب، ويتحسب في الاستثمار — مش دخل معيشة
        rule(ASSET_SELL, TextKey.KIND_ASSET_SELL, Liquidity.IN, false, false, PersonEffect.NONE),
        // وارد جديد بيزود الدخل — مش من شغل، عشان كده منفصل عن المرتب (OVERRIDES §42)
        rule(GIFT_RECEIVED, TextKey.KIND_GIFT_RECEIVED, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(SUPPORT_RECEIVED, TextKey.KIND_SUPPORT_RECEIVED, Liquidity.IN, true, false, PersonEffect.NONE),
        rule(BENEFIT_RECEIVED, TextKey.KIND_BENEFIT_RECEIVED, Liquidity.IN, true, false, PersonEffect.NONE),
        // عايد الاستثمار المتكرر — غير بيع الأصل نفسه
        rule(INVESTMENT_INCOME, TextKey.KIND_INVESTMENT_INCOME, Liquidity.IN, true, false, PersonEffect.NONE),
        // دور الجمعية: جزء فلوسك راجعة وجزء دين عليك ⇒ مش دخل
        rule(ROSCA_PAYOUT, TextKey.KIND_ROSCA_PAYOUT, Liquidity.IN, false, false, PersonEffect.NONE),
        // الاسترداد بينقّص المصروف، ما بيزودش الدخل
        rule(REFUND_RECEIVED, TextKey.KIND_REFUND_RECEIVED, Liquidity.IN, false, false, PersonEffect.NONE, reducesExpense = true),
        // سلفة الشغل دين هيتخصم من المرتب
        rule(ADVANCE_RECEIVED, TextKey.KIND_ADVANCE_RECEIVED, Liquidity.IN, false, false, PersonEffect.PAYABLE_LOAN_UP),
        rule(UNCLASSIFIED, TextKey.KIND_UNCLASSIFIED, Liquidity.OUT, false, false, PersonEffect.NONE),
    )

fun ruleFor(kind: EconomicKind): EconomicKindRule = RULES.getValue(kind)

val ALL_ECONOMIC_KINDS: List<EconomicKind> = EconomicKind.entries.toList()

fun countsAsIncome(kind: EconomicKind): Boolean = ruleFor(kind).countsAsIncome
fun countsAsPersonalExpense(kind: EconomicKind): Boolean = ruleFor(kind).countsAsPersonalExpense

/** بينقّص المصروف بدل ما يزوده — الاسترداد (OVERRIDES §42). */
fun reducesExpense(kind: EconomicKind): Boolean = ruleFor(kind).reducesExpense

/** للتحذير في المراجعة، مش للرفض — الكشف بيعرف الاتجاه بس مش النية. */
fun isConsistentWithObservedDirection(kind: EconomicKind, observed: Direction): Boolean = when (ruleFor(kind).liquidity) {
    Liquidity.INTERNAL -> true
    Liquidity.IN -> observed == Direction.IN
    Liquidity.OUT -> observed == Direction.OUT
}
