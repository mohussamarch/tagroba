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
    // لسه ما اتحددش
    UNCLASSIFIED("unclassified");

    companion object {
        fun fromWire(wire: String): EconomicKind =
            entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("نوع اقتصادي غير معروف: $wire")
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
    val label: String,
    val liquidity: Liquidity,
    /** بيزود الدخل الاقتصادي للفترة؟ */
    val countsAsIncome: Boolean,
    /** بيزود المصروف الشخصي؟ (نصيب المستخدم بس بعد فصل اللي على غيره) */
    val countsAsPersonalExpense: Boolean,
    val personEffect: PersonEffect,
)

private fun rule(kind: EconomicKind, label: String, liquidity: Liquidity, income: Boolean, expense: Boolean, effect: PersonEffect) =
    kind to EconomicKindRule(kind, label, liquidity, income, expense, effect)

private val RULES: Map<EconomicKind, EconomicKindRule> =
    mapOf(
        rule(SALARY, "مرتب", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(BONUS, "بونص", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(COMMISSION, "عمولة", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(OVERTIME, "أوفر تايم", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(FREELANCE, "فري لانس", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(PERSONAL_SALE, "بيع شخصي", Liquidity.IN, true, false, PersonEffect.NONE),
        rule(LOAN_RECEIVED, "قرض مستلم", Liquidity.IN, false, false, PersonEffect.PAYABLE_LOAN_UP),
        rule(DEBT_COLLECTED, "تحصيل دين لك", Liquidity.IN, false, false, PersonEffect.RECEIVABLE_DOWN),
        rule(CUSTODY_RECEIVED, "أمانة مستلمة", Liquidity.IN, false, false, PersonEffect.PAYABLE_CUSTODY_UP),
        rule(PURCHASE, "شراء / فاتورة", Liquidity.OUT, false, true, PersonEffect.RECEIVABLE_UP),
        rule(SUPPORT_GIFT, "دعم / هدية", Liquidity.OUT, false, true, PersonEffect.BENEFICIARY_INFO),
        rule(FEE, "رسوم", Liquidity.OUT, false, true, PersonEffect.NONE),
        rule(LOAN_GRANTED, "قرض ممنوح", Liquidity.OUT, false, false, PersonEffect.RECEIVABLE_UP),
        rule(DEBT_REPAID, "سداد دين عليك", Liquidity.OUT, false, false, PersonEffect.PAYABLE_LOAN_DOWN),
        rule(CUSTODY_RETURNED, "رد أمانة", Liquidity.OUT, false, false, PersonEffect.PAYABLE_CUSTODY_DOWN),
        rule(INTERNAL_TRANSFER, "تحويل داخلي", Liquidity.INTERNAL, false, false, PersonEffect.NONE),
        rule(ASSET_BUY, "شراء أصل", Liquidity.OUT, false, false, PersonEffect.NONE),
        // بيع الأصل: الربح المحقق بس هو المكسب، ويتحسب في الاستثمار — مش دخل معيشة
        rule(ASSET_SELL, "بيع أصل", Liquidity.IN, false, false, PersonEffect.NONE),
        rule(UNCLASSIFIED, "غير محدد", Liquidity.OUT, false, false, PersonEffect.NONE),
    )

fun ruleFor(kind: EconomicKind): EconomicKindRule = RULES.getValue(kind)

val ALL_ECONOMIC_KINDS: List<EconomicKind> = EconomicKind.entries.toList()

fun countsAsIncome(kind: EconomicKind): Boolean = ruleFor(kind).countsAsIncome
fun countsAsPersonalExpense(kind: EconomicKind): Boolean = ruleFor(kind).countsAsPersonalExpense

/** للتحذير في المراجعة، مش للرفض — الكشف بيعرف الاتجاه بس مش النية. */
fun isConsistentWithObservedDirection(kind: EconomicKind, observed: Direction): Boolean = when (ruleFor(kind).liquidity) {
    Liquidity.INTERNAL -> true
    Liquidity.IN -> observed == Direction.IN
    Liquidity.OUT -> observed == Direction.OUT
}
