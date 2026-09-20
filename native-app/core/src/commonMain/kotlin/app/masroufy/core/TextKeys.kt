package app.masroufy.core

/**
 * مفاتيح النصوص المعروضة — OVERRIDES §40.
 * كل مفتاح لازم يكون ليه قيمة في `ARABIC_TEXTS` (اختبار بيتأكد)، والإنجليزي بيتكمّل مفتاح مفتاح.
 */
enum class TextKey {
    // الأنواع الاقتصادية (spec/02) — الاسم المعروض بس، والاسم المتخزن `wire` ما يتغيرش
    KIND_SALARY,
    KIND_BONUS,
    KIND_COMMISSION,
    KIND_OVERTIME,
    KIND_FREELANCE,
    KIND_PERSONAL_SALE,
    KIND_LOAN_RECEIVED,
    KIND_DEBT_COLLECTED,
    KIND_CUSTODY_RECEIVED,
    KIND_PURCHASE,
    KIND_SUPPORT_GIFT,
    KIND_FEE,
    KIND_LOAN_GRANTED,
    KIND_DEBT_REPAID,
    KIND_CUSTODY_RETURNED,
    KIND_INTERNAL_TRANSFER,
    KIND_ASSET_BUY,
    KIND_ASSET_SELL,
    KIND_UNCLASSIFIED,

    /** {0} = الاسم المتخزن اللي ماتعرفش عليه */
    KIND_UNKNOWN_WIRE,

    // رموز العملات
    CURRENCY_SAR,
    CURRENCY_EGP,
    CURRENCY_USD,
    CURRENCY_EUR,
    CURRENCY_GBP,
    CURRENCY_AED,

    /** القيمة المجهولة نص صريح مش صفر (CLAUDE.md #10) */
    NOT_AVAILABLE,

    // أسماء المبالغ جوه رسايل خطأ المبلغ
    AMOUNT_CONTEXT_DISPLAY,
    AMOUNT_CONTEXT_INCOME,
    AMOUNT_CONTEXT_REMAINING,

    // أسباب اقتراح النوع الاقتصادي
    SUGGEST_CASH_WITHDRAWAL,

    /** {0} = اسم التصنيف */
    SUGGEST_INVESTMENT_BUY,
    SUGGEST_BANK_FEES,
    SUGGEST_INSTALLMENT,
    SUGGEST_DEBT_PAYMENT,
    SUGGEST_DIGITAL_WALLET,
    SUGGEST_TRANSFER_OUT,
    SUGGEST_BARQ,

    /** {0} = اسم التصنيف */
    SUGGEST_PURCHASE,
    SUGGEST_OUT_NO_CATEGORY,
    SUGGEST_INVESTMENT_SELL,
    SUGGEST_REFUND,
    SUGGEST_IN_UNKNOWN,

    /** {0} = اسم التصنيف */
    SUGGEST_IN_WITH_CATEGORY,
}
