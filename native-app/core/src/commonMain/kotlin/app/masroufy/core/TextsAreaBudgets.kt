package app.masroufy.core

/**
 * نصوص منطقة «الميزانيات والخطط والتصنيفات والقواعد» (`ui/screens/budgets/` — منطقة تاسعة، ARCHITECTURE §31.32) — **ملفات المنطقة دي بس**.
 * المفاتيح في `TextKeys.kt` تحت سطر المنطقة. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي (كتابة Claude — مستني
 * مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`)، واللي مالوش نص في النموذج
 * (حالة الخطأ · «احجز مبلغ» لميعاد من غير مبلغ · «يُحسب بعد الحفظ») كتابة Claude بنفس النبرة.
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». الملف ده فيه نصوص «الميزانيات» (الخانة جوه مبدّل العمليات) + تجميع ملفات المنطقة.
 */
private val MSA_BUDGETS_PANEL: Map<UiKey, String> = mapOf(
    UiKey.BUDGETS_TOTAL_TITLE to "سقف {0}",
    UiKey.BUDGETS_CHIP_OVER to "تجاوزت السقف",
    UiKey.BUDGETS_CHIP_NEAR to "اقتربت من السقف",
    UiKey.BUDGETS_CHIP_OK to "ضمن الحد",
    UiKey.BUDGETS_NA_REASON to "كل عمليات الشهر بلا تصنيف، فلا يمكن معرفة المصروف.",
    UiKey.BUDGETS_LEFT to "بقي {0}",
    UiKey.BUDGETS_OVER_BY to "تجاوز {0}",
    UiKey.BUDGETS_DAILY to "نحو {0} يوميًا حتى الراتب",
    UiKey.BUDGETS_NO_TOTAL_TITLE to "لم تحدّد سقفًا لـ{0}",
    UiKey.BUDGETS_NO_TOTAL_BODY to "المصروف يُحسب على أي حال. السقف يوضّح لك المتاح يوميًا.",
    UiKey.BUDGETS_SET_CAP to "حدّد سقفًا",
    UiKey.BUDGETS_COPY to "انسخ سقوف {0}",
    UiKey.BUDGETS_COPIED to "نُسخت سقوف {0}",
    UiKey.BUDGETS_AVG_LABEL to "متوسط آخر ٦ أشهر",
    UiKey.BUDGETS_AVG_NOTE to "معلومة، وليست سقفًا",
    UiKey.BUDGETS_ANOMALY_LABEL to "صرف غير معتاد",
    UiKey.BUDGETS_CATS_TITLE to "التصنيفات",
    UiKey.BUDGETS_CATS_EMPTY to "لا مصروف في التصنيفات هذا الشهر بعد.",
    UiKey.BUDGETS_CAT_NO_LIMIT to "بلا سقف · اضغط لتحديد سقف",
    UiKey.BUDGETS_CAT_OVER to "تجاوزت السقف بـ {0}",
    UiKey.BUDGETS_CAT_LEFT to "بقي {0}، {1}٪",
    UiKey.BUDGETS_UP_TITLE to "مواعيد قادمة",
    UiKey.BUDGETS_UP_EMPTY to "لا مواعيد دفع قبل نهاية الشهر.",
    UiKey.BUDGETS_UP_WHEN to "{0}، {1}",
    UiKey.BUDGETS_RESERVE to "احسبه من فلوسي",
    UiKey.BUDGETS_RESERVED to "محجوز ✓ · إلغاء",
    UiKey.BUDGETS_RESERVE_TITLE to "كم تحجز لـ«{0}»؟",
    UiKey.BUDGETS_RESERVE_AMOUNT to "المبلغ",
    UiKey.BUDGETS_RESERVE_SAVE to "احجزه",
    UiKey.BUDGETS_RESERVED_TOAST to "حُجز {0} لـ«{1}»",
    UiKey.BUDGETS_UNRESERVED_TOAST to "أُلغي الحجز",
    UiKey.BUDGETS_LEFTOVER to "{0} بعد المحجوز: {1}",
    UiKey.BUDGETS_TOTAL_SHEET_TITLE to "سقف الشهر كله",
    UiKey.BUDGETS_ERROR_TITLE to "تعذّر تحميل الميزانيات",
    UiKey.BUDGETS_ERROR_BODY to "تحقق من الاتصال ثم أعد المحاولة.",
    UiKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

private val EGYPTIAN_BUDGETS_PANEL: Map<UiKey, String> = mapOf(
    UiKey.BUDGETS_TOTAL_TITLE to "سقف {0}",
    UiKey.BUDGETS_CHIP_OVER to "عدّيت السقف",
    UiKey.BUDGETS_CHIP_NEAR to "قرّبت من السقف",
    UiKey.BUDGETS_CHIP_OK to "ماشي كويس",
    UiKey.BUDGETS_NA_REASON to "كل عمليات الشهر من غير تصنيف، فمش هنعرف المصروف.",
    UiKey.BUDGETS_LEFT to "فاضل {0}",
    UiKey.BUDGETS_OVER_BY to "زيادة {0}",
    UiKey.BUDGETS_DAILY to "حوالي {0} في اليوم لحد المرتب",
    UiKey.BUDGETS_NO_TOTAL_TITLE to "ما حدّدتش سقف لـ{0}",
    UiKey.BUDGETS_NO_TOTAL_BODY to "المصروف بيتحسب برضه. السقف بيخليك تعرف فاضلك كام في اليوم.",
    UiKey.BUDGETS_SET_CAP to "حدّد سقف",
    UiKey.BUDGETS_COPY to "انسخ سقوف {0}",
    UiKey.BUDGETS_COPIED to "اتنسخت سقوف {0}",
    UiKey.BUDGETS_AVG_LABEL to "متوسط آخر ٦ شهور",
    UiKey.BUDGETS_AVG_NOTE to "معلومة، مش سقف",
    UiKey.BUDGETS_ANOMALY_LABEL to "صرف مش معتاد",
    UiKey.BUDGETS_CATS_TITLE to "التصنيفات",
    UiKey.BUDGETS_CATS_EMPTY to "لسه مفيش صرف في التصنيفات الشهر ده.",
    UiKey.BUDGETS_CAT_NO_LIMIT to "من غير سقف · دوس تحدّد سقف",
    UiKey.BUDGETS_CAT_OVER to "عدّيت السقف بـ {0}",
    UiKey.BUDGETS_CAT_LEFT to "فاضل {0}، {1}٪",
    UiKey.BUDGETS_UP_TITLE to "مواعيد جاية",
    UiKey.BUDGETS_UP_EMPTY to "مفيش مواعيد دفع قبل آخر الشهر.",
    UiKey.BUDGETS_UP_WHEN to "{0}، {1}",
    UiKey.BUDGETS_RESERVE to "احسبه من فلوسي",
    UiKey.BUDGETS_RESERVED to "محجوز ✓ · شيله",
    UiKey.BUDGETS_RESERVE_TITLE to "هتحجز كام لـ«{0}»؟",
    UiKey.BUDGETS_RESERVE_AMOUNT to "المبلغ",
    UiKey.BUDGETS_RESERVE_SAVE to "احجزه",
    UiKey.BUDGETS_RESERVED_TOAST to "اتحجز {0} لـ«{1}»",
    UiKey.BUDGETS_UNRESERVED_TOAST to "اتشال الحجز",
    UiKey.BUDGETS_LEFTOVER to "{0} بعد المحجوز: {1}",
    UiKey.BUDGETS_TOTAL_SHEET_TITLE to "سقف الشهر كله",
    UiKey.BUDGETS_ERROR_TITLE to "الميزانيات ما اتحمّلتش",
    UiKey.BUDGETS_ERROR_BODY to "اتأكد من النت وجرّب تاني.",
    UiKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

private val ENGLISH_BUDGETS_PANEL: Map<UiKey, String> = mapOf(
    UiKey.BUDGETS_TOTAL_TITLE to "{0} cap",
    UiKey.BUDGETS_CHIP_OVER to "Over the cap",
    UiKey.BUDGETS_CHIP_NEAR to "Close to the cap",
    UiKey.BUDGETS_CHIP_OK to "Within the cap",
    UiKey.BUDGETS_NA_REASON to "None of this month's operations have a type yet, so spending can't be known.",
    UiKey.BUDGETS_LEFT to "{0} left",
    UiKey.BUDGETS_OVER_BY to "{0} over",
    UiKey.BUDGETS_DAILY to "About {0} a day until payday",
    UiKey.BUDGETS_NO_TOTAL_TITLE to "No cap set for {0}",
    UiKey.BUDGETS_NO_TOTAL_BODY to "Spending is counted either way. A cap shows what you can spend each day.",
    UiKey.BUDGETS_SET_CAP to "Set a cap",
    UiKey.BUDGETS_COPY to "Copy the {0} caps",
    UiKey.BUDGETS_COPIED to "Copied the {0} caps",
    UiKey.BUDGETS_AVG_LABEL to "Average of the last 6 months",
    UiKey.BUDGETS_AVG_NOTE to "Information, not a cap",
    UiKey.BUDGETS_ANOMALY_LABEL to "Unusual spending",
    UiKey.BUDGETS_CATS_TITLE to "Categories",
    UiKey.BUDGETS_CATS_EMPTY to "No category spending this month yet.",
    UiKey.BUDGETS_CAT_NO_LIMIT to "No cap · tap to set one",
    UiKey.BUDGETS_CAT_OVER to "Over the cap by {0}",
    UiKey.BUDGETS_CAT_LEFT to "{0} left, {1}%",
    UiKey.BUDGETS_UP_TITLE to "Upcoming dates",
    UiKey.BUDGETS_UP_EMPTY to "No payments due before the month ends.",
    UiKey.BUDGETS_UP_WHEN to "{0}, {1}",
    UiKey.BUDGETS_RESERVE to "Count it from my money",
    UiKey.BUDGETS_RESERVED to "Counted ✓ · undo",
    UiKey.BUDGETS_RESERVE_TITLE to "How much to set aside for “{0}”?",
    UiKey.BUDGETS_RESERVE_AMOUNT to "Amount",
    UiKey.BUDGETS_RESERVE_SAVE to "Set it aside",
    UiKey.BUDGETS_RESERVED_TOAST to "Set aside {0} for “{1}”",
    UiKey.BUDGETS_UNRESERVED_TOAST to "No longer set aside",
    UiKey.BUDGETS_LEFTOVER to "{0} after what's set aside: {1}",
    UiKey.BUDGETS_TOTAL_SHEET_TITLE to "Cap for the whole month",
    UiKey.BUDGETS_ERROR_TITLE to "Couldn't load the budgets",
    UiKey.BUDGETS_ERROR_BODY to "Check your connection, then try again.",
    UiKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

// التجميع في الآخر: المتغيرات اللي فوق لازم تتعمل الأول (ترتيب التهيئة في نفس الملف)
internal val MSA_AREA_BUDGETS_TEXTS: Map<UiKey, String> =
    MSA_BUDGETS_PANEL + MSA_BUDGETS_CATEGORY + MSA_BUDGETS_GOALS + MSA_BUDGETS_CATEGORIES + MSA_BUDGETS_RULES

internal val EGYPTIAN_AREA_BUDGETS_TEXTS: Map<UiKey, String> =
    EGYPTIAN_BUDGETS_PANEL + EGYPTIAN_BUDGETS_CATEGORY + EGYPTIAN_BUDGETS_GOALS + EGYPTIAN_BUDGETS_CATEGORIES + EGYPTIAN_BUDGETS_RULES

internal val ENGLISH_AREA_BUDGETS_TEXTS: Map<UiKey, String> =
    ENGLISH_BUDGETS_PANEL + ENGLISH_BUDGETS_CATEGORY + ENGLISH_BUDGETS_GOALS + ENGLISH_BUDGETS_CATEGORIES + ENGLISH_BUDGETS_RULES
