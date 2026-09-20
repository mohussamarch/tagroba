package app.masroufy.core

/**
 * النصوص بالإنجليزي — كتابة Claude ومراجعة المالك (OVERRIDES §40).
 * أي مفتاح ناقص هنا بيرجع بالعربي، فالشاشة ما بتفضاش أبدًا.
 */
internal val ENGLISH_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.KIND_SALARY to "Salary",
    TextKey.KIND_BONUS to "Bonus",
    TextKey.KIND_COMMISSION to "Commission",
    TextKey.KIND_OVERTIME to "Overtime",
    TextKey.KIND_FREELANCE to "Freelance",
    TextKey.KIND_PERSONAL_SALE to "Personal sale",
    TextKey.KIND_LOAN_RECEIVED to "Loan received",
    TextKey.KIND_DEBT_COLLECTED to "Debt collected",
    TextKey.KIND_CUSTODY_RECEIVED to "Custody received",
    TextKey.KIND_PURCHASE to "Purchase / bill",
    TextKey.KIND_SUPPORT_GIFT to "Support / gift",
    TextKey.KIND_FEE to "Fee",
    TextKey.KIND_LOAN_GRANTED to "Loan granted",
    TextKey.KIND_DEBT_REPAID to "Debt repaid",
    TextKey.KIND_CUSTODY_RETURNED to "Custody returned",
    TextKey.KIND_INTERNAL_TRANSFER to "Internal transfer",
    TextKey.KIND_ASSET_BUY to "Asset purchase",
    TextKey.KIND_ASSET_SELL to "Asset sale",
    TextKey.KIND_UNCLASSIFIED to "Undecided",
    TextKey.KIND_UNKNOWN_WIRE to "Unknown economic kind: {0}",

    TextKey.CURRENCY_SAR to "SAR",
    TextKey.CURRENCY_EGP to "EGP",
    TextKey.CURRENCY_USD to "$",
    TextKey.CURRENCY_EUR to "€",
    TextKey.CURRENCY_GBP to "£",
    TextKey.CURRENCY_AED to "AED",

    TextKey.NOT_AVAILABLE to "Not available",

    TextKey.AMOUNT_CONTEXT_DISPLAY to "amount to display",
    TextKey.AMOUNT_CONTEXT_INCOME to "income",
    TextKey.AMOUNT_CONTEXT_REMAINING to "remaining",

    TextKey.SUGGEST_CASH_WITHDRAWAL to
        "Cash withdrawal: the money moved from the bank to your cash wallet — it was not spent",
    TextKey.SUGGEST_INVESTMENT_BUY to "“{0}”: buying an investment asset, not consumption",
    TextKey.SUGGEST_BANK_FEES to "Bank fees: an expense in its own right",
    TextKey.SUGGEST_INSTALLMENT to
        "Installment: paying part of an earlier commitment. If the original purchase is already recorded as an expense, counting it again would be double counting.",
    TextKey.SUGGEST_DEBT_PAYMENT to
        "Payment: this can be repaying a debt you owe (not an expense) or paying a bill (an expense). The statement does not tell them apart, so it needs your decision.",
    TextKey.SUGGEST_DIGITAL_WALLET to
        "Digital wallet (such as Barq): the money left the bank, but the statement does not say where it went. It may be your own wallet, or support or a loan to someone.",
    TextKey.SUGGEST_TRANSFER_OUT to
        "Outgoing transfer: the statement says money left but not why. It may be a transfer to your own account, support, a loan, or a repayment.",
    TextKey.SUGGEST_BARQ to
        "Barq is a way to transfer, not a kind of expense. You need to set the purpose: your account, support, a loan, or a repayment.",
    TextKey.SUGGEST_PURCHASE to "“{0}”: a purchase or a bill",
    TextKey.SUGGEST_OUT_NO_CATEGORY to
        "Outgoing with no clear category. You need to say whether this is a purchase or moving money.",
    TextKey.SUGGEST_INVESTMENT_SELL to
        "Incoming from investment: proceeds of an asset sale, and only the realized profit is a gain",
    TextKey.SUGGEST_REFUND to
        "Refund: this is money you paid coming back, not new income. It is better linked to the original transaction than counted as income.",
    TextKey.SUGGEST_IN_UNKNOWN to
        "Incoming: the statement says money came in but not from where. Salary? A loan you received? A debt collected? Custody? The difference matters a great deal.",
    TextKey.SUGGEST_IN_WITH_CATEGORY to "Incoming under “{0}”: you need to set its economic kind.",
)
