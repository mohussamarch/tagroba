package app.masroufy.core

/**
 * النصوص بالعربي — **الأصل**. أي قيمة هنا هي نفس النص اللي كان مكتوب جوه الكود بالحرف،
 * فملفات المرجع (golden) بتفضل مطابقة. تغيير أي نص هنا = تغيير في شاشة المالك.
 */
internal val ARABIC_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.KIND_SALARY to "مرتب",
    TextKey.KIND_BONUS to "بونص",
    TextKey.KIND_COMMISSION to "عمولة",
    TextKey.KIND_OVERTIME to "أوفر تايم",
    TextKey.KIND_FREELANCE to "فري لانس",
    TextKey.KIND_PERSONAL_SALE to "بيع شخصي",
    TextKey.KIND_LOAN_RECEIVED to "قرض مستلم",
    TextKey.KIND_DEBT_COLLECTED to "تحصيل دين لك",
    TextKey.KIND_CUSTODY_RECEIVED to "أمانة مستلمة",
    TextKey.KIND_PURCHASE to "شراء / فاتورة",
    TextKey.KIND_SUPPORT_GIFT to "دعم / هدية",
    TextKey.KIND_FEE to "رسوم",
    TextKey.KIND_LOAN_GRANTED to "قرض ممنوح",
    TextKey.KIND_DEBT_REPAID to "سداد دين عليك",
    TextKey.KIND_CUSTODY_RETURNED to "رد أمانة",
    TextKey.KIND_INTERNAL_TRANSFER to "تحويل داخلي",
    TextKey.KIND_ASSET_BUY to "شراء أصل",
    TextKey.KIND_ASSET_SELL to "بيع أصل",
    TextKey.KIND_UNCLASSIFIED to "غير محدد",
    TextKey.KIND_UNKNOWN_WIRE to "نوع اقتصادي غير معروف: {0}",

    TextKey.CURRENCY_SAR to "ر.س",
    TextKey.CURRENCY_EGP to "ج.م",
    TextKey.CURRENCY_USD to "$",
    TextKey.CURRENCY_EUR to "€",
    TextKey.CURRENCY_GBP to "£",
    TextKey.CURRENCY_AED to "د.إ",

    TextKey.NOT_AVAILABLE to "غير متاح",

    TextKey.AMOUNT_CONTEXT_DISPLAY to "مبلغ للعرض",
    TextKey.AMOUNT_CONTEXT_INCOME to "الدخل",
    TextKey.AMOUNT_CONTEXT_REMAINING to "المتبقي",

    TextKey.SUGGEST_CASH_WITHDRAWAL to "سحب نقدي: الفلوس اتنقلت من البنك لمحفظة الكاش، مش اتصرفت",
    TextKey.SUGGEST_INVESTMENT_BUY to "«{0}»: شراء أصل استثماري، مش استهلاك",
    TextKey.SUGGEST_BANK_FEES to "رسوم بنكية: مصروف مستقل",
    TextKey.SUGGEST_INSTALLMENT to
        "تقسيط: سداد جزء من التزام سابق. لو الشراء الأصلي مسجَّل كمصروف، احتسابه تاني هيبقى عدّ مزدوج.",
    TextKey.SUGGEST_DEBT_PAYMENT to
        "سداد: ممكن يكون سداد دين عليك (مش مصروف) وممكن يكون دفع فاتورة (مصروف). الكشف مش بيفرّق، فمحتاج قرارك.",
    TextKey.SUGGEST_DIGITAL_WALLET to
        "محفظة رقمية (زي برق): الفلوس خرجت من الراجحي، بس الكشف مش بيقول راحت فين. ممكن تكون محفظتك انت، وممكن تكون دعم أو قرض لحد.",
    TextKey.SUGGEST_TRANSFER_OUT to
        "تحويل صادر: الكشف بيقول الفلوس خرجت بس مش بيقول ليه. ممكن تحويل لحسابك، دعم، قرض، أو سداد.",
    TextKey.SUGGEST_BARQ to "برق وسيلة تحويل مش نوع مصروف. لازم تحدد الغرض: حسابك، دعم، قرض، ولا سداد.",
    TextKey.SUGGEST_PURCHASE to "«{0}»: شراء أو فاتورة",
    TextKey.SUGGEST_OUT_NO_CATEGORY to "صادر بلا تصنيف واضح. محتاج تحدد ده شراء ولا نقل فلوس.",
    TextKey.SUGGEST_INVESTMENT_SELL to "وارد من الاستثمار: حصيلة بيع أصل، والربح المحقق فقط هو المكسب",
    TextKey.SUGGEST_REFUND to
        "استرداد: ده رجوع فلوس دفعتها، مش دخل جديد. الأنسب تربطه بالعملية الأصلية بدل ما تعدّه دخلًا.",
    TextKey.SUGGEST_IN_UNKNOWN to
        "وارد: الكشف بيقول الفلوس دخلت بس مش بيقول منين. راتب؟ قرض استلمته؟ تحصيل دين لك؟ أمانة؟ الفرق بينهم كبير في الحساب.",
    TextKey.SUGGEST_IN_WITH_CATEGORY to "وارد بتصنيف «{0}»: محتاج تحدد نوعه الاقتصادي.",
)
