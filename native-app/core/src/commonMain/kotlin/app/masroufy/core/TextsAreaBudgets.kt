package app.masroufy.core

/**
 * نصوص منطقة «الميزانيات والخطط والتصنيفات والقواعد» (`ui/screens/budgets/` — منطقة تاسعة، ARCHITECTURE §31.32) — **ملفات المنطقة دي بس**.
 * المفاتيح في `TextKeys.kt` تحت سطر المنطقة. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي (كتابة Claude — مستني
 * مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`)، واللي مالوش نص في النموذج
 * (حالة الخطأ · «احجز مبلغ» لميعاد من غير مبلغ · «يُحسب بعد الحفظ») كتابة Claude بنفس النبرة.
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». الملف ده فيه نصوص «الميزانيات» (الخانة جوه مبدّل العمليات) + تجميع ملفات المنطقة.
 */
private val MSA_BUDGETS_PANEL: Map<TextKey, String> = mapOf(
    TextKey.BUDGETS_TOTAL_TITLE to "سقف {0}",
    TextKey.BUDGETS_CHIP_OVER to "تجاوزت السقف",
    TextKey.BUDGETS_CHIP_NEAR to "اقتربت من السقف",
    TextKey.BUDGETS_CHIP_OK to "ضمن الحد",
    TextKey.BUDGETS_NA_REASON to "كل عمليات الشهر بلا تصنيف، فلا يمكن معرفة المصروف.",
    TextKey.BUDGETS_LEFT to "بقي {0}",
    TextKey.BUDGETS_OVER_BY to "تجاوز {0}",
    TextKey.BUDGETS_DAILY to "نحو {0} يوميًا حتى الراتب",
    TextKey.BUDGETS_NO_TOTAL_TITLE to "لم تحدّد سقفًا لـ{0}",
    TextKey.BUDGETS_NO_TOTAL_BODY to "المصروف يُحسب على أي حال. السقف يوضّح لك المتاح يوميًا.",
    TextKey.BUDGETS_SET_CAP to "حدّد سقفًا",
    TextKey.BUDGETS_COPY to "انسخ سقوف {0}",
    TextKey.BUDGETS_COPIED to "نُسخت سقوف {0}",
    TextKey.BUDGETS_AVG_LABEL to "متوسط آخر ٦ أشهر",
    TextKey.BUDGETS_AVG_NOTE to "معلومة، وليست سقفًا",
    TextKey.BUDGETS_ANOMALY_LABEL to "صرف غير معتاد",
    TextKey.BUDGETS_CATS_TITLE to "التصنيفات",
    TextKey.BUDGETS_CATS_EMPTY to "لا مصروف في التصنيفات هذا الشهر بعد.",
    TextKey.BUDGETS_CAT_NO_LIMIT to "بلا سقف · اضغط لتحديد سقف",
    TextKey.BUDGETS_CAT_OVER to "تجاوزت السقف بـ {0}",
    TextKey.BUDGETS_CAT_LEFT to "بقي {0}، {1}٪",
    TextKey.BUDGETS_UP_TITLE to "مواعيد قادمة",
    TextKey.BUDGETS_UP_EMPTY to "لا مواعيد دفع قبل نهاية الشهر.",
    TextKey.BUDGETS_UP_WHEN to "{0}، {1}",
    TextKey.BUDGETS_RESERVE to "احسبه من فلوسي",
    TextKey.BUDGETS_RESERVED to "محجوز ✓ · إلغاء",
    TextKey.BUDGETS_RESERVE_TITLE to "كم تحجز لـ«{0}»؟",
    TextKey.BUDGETS_RESERVE_AMOUNT to "المبلغ",
    TextKey.BUDGETS_RESERVE_SAVE to "احجزه",
    TextKey.BUDGETS_RESERVED_TOAST to "حُجز {0} لـ«{1}»",
    TextKey.BUDGETS_UNRESERVED_TOAST to "أُلغي الحجز",
    TextKey.BUDGETS_LEFTOVER to "{0} بعد المحجوز: {1}",
    TextKey.BUDGETS_TOTAL_SHEET_TITLE to "سقف الشهر كله",
    TextKey.BUDGETS_ERROR_TITLE to "تعذّر تحميل الميزانيات",
    TextKey.BUDGETS_ERROR_BODY to "تحقق من الاتصال ثم أعد المحاولة.",
    TextKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

private val EGYPTIAN_BUDGETS_PANEL: Map<TextKey, String> = mapOf(
    TextKey.BUDGETS_TOTAL_TITLE to "سقف {0}",
    TextKey.BUDGETS_CHIP_OVER to "عدّيت السقف",
    TextKey.BUDGETS_CHIP_NEAR to "قرّبت من السقف",
    TextKey.BUDGETS_CHIP_OK to "ماشي كويس",
    TextKey.BUDGETS_NA_REASON to "كل عمليات الشهر من غير تصنيف، فمش هنعرف المصروف.",
    TextKey.BUDGETS_LEFT to "فاضل {0}",
    TextKey.BUDGETS_OVER_BY to "زيادة {0}",
    TextKey.BUDGETS_DAILY to "حوالي {0} في اليوم لحد المرتب",
    TextKey.BUDGETS_NO_TOTAL_TITLE to "ما حدّدتش سقف لـ{0}",
    TextKey.BUDGETS_NO_TOTAL_BODY to "المصروف بيتحسب برضه. السقف بيخليك تعرف فاضلك كام في اليوم.",
    TextKey.BUDGETS_SET_CAP to "حدّد سقف",
    TextKey.BUDGETS_COPY to "انسخ سقوف {0}",
    TextKey.BUDGETS_COPIED to "اتنسخت سقوف {0}",
    TextKey.BUDGETS_AVG_LABEL to "متوسط آخر ٦ شهور",
    TextKey.BUDGETS_AVG_NOTE to "معلومة، مش سقف",
    TextKey.BUDGETS_ANOMALY_LABEL to "صرف مش معتاد",
    TextKey.BUDGETS_CATS_TITLE to "التصنيفات",
    TextKey.BUDGETS_CATS_EMPTY to "لسه مفيش صرف في التصنيفات الشهر ده.",
    TextKey.BUDGETS_CAT_NO_LIMIT to "من غير سقف · دوس تحدّد سقف",
    TextKey.BUDGETS_CAT_OVER to "عدّيت السقف بـ {0}",
    TextKey.BUDGETS_CAT_LEFT to "فاضل {0}، {1}٪",
    TextKey.BUDGETS_UP_TITLE to "مواعيد جاية",
    TextKey.BUDGETS_UP_EMPTY to "مفيش مواعيد دفع قبل آخر الشهر.",
    TextKey.BUDGETS_UP_WHEN to "{0}، {1}",
    TextKey.BUDGETS_RESERVE to "احسبه من فلوسي",
    TextKey.BUDGETS_RESERVED to "محجوز ✓ · شيله",
    TextKey.BUDGETS_RESERVE_TITLE to "هتحجز كام لـ«{0}»؟",
    TextKey.BUDGETS_RESERVE_AMOUNT to "المبلغ",
    TextKey.BUDGETS_RESERVE_SAVE to "احجزه",
    TextKey.BUDGETS_RESERVED_TOAST to "اتحجز {0} لـ«{1}»",
    TextKey.BUDGETS_UNRESERVED_TOAST to "اتشال الحجز",
    TextKey.BUDGETS_LEFTOVER to "{0} بعد المحجوز: {1}",
    TextKey.BUDGETS_TOTAL_SHEET_TITLE to "سقف الشهر كله",
    TextKey.BUDGETS_ERROR_TITLE to "الميزانيات ما اتحمّلتش",
    TextKey.BUDGETS_ERROR_BODY to "اتأكد من النت وجرّب تاني.",
    TextKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

private val ENGLISH_BUDGETS_PANEL: Map<TextKey, String> = mapOf(
    TextKey.BUDGETS_TOTAL_TITLE to "{0} cap",
    TextKey.BUDGETS_CHIP_OVER to "Over the cap",
    TextKey.BUDGETS_CHIP_NEAR to "Close to the cap",
    TextKey.BUDGETS_CHIP_OK to "Within the cap",
    TextKey.BUDGETS_NA_REASON to "None of this month's operations have a type yet, so spending can't be known.",
    TextKey.BUDGETS_LEFT to "{0} left",
    TextKey.BUDGETS_OVER_BY to "{0} over",
    TextKey.BUDGETS_DAILY to "About {0} a day until payday",
    TextKey.BUDGETS_NO_TOTAL_TITLE to "No cap set for {0}",
    TextKey.BUDGETS_NO_TOTAL_BODY to "Spending is counted either way. A cap shows what you can spend each day.",
    TextKey.BUDGETS_SET_CAP to "Set a cap",
    TextKey.BUDGETS_COPY to "Copy the {0} caps",
    TextKey.BUDGETS_COPIED to "Copied the {0} caps",
    TextKey.BUDGETS_AVG_LABEL to "Average of the last 6 months",
    TextKey.BUDGETS_AVG_NOTE to "Information, not a cap",
    TextKey.BUDGETS_ANOMALY_LABEL to "Unusual spending",
    TextKey.BUDGETS_CATS_TITLE to "Categories",
    TextKey.BUDGETS_CATS_EMPTY to "No category spending this month yet.",
    TextKey.BUDGETS_CAT_NO_LIMIT to "No cap · tap to set one",
    TextKey.BUDGETS_CAT_OVER to "Over the cap by {0}",
    TextKey.BUDGETS_CAT_LEFT to "{0} left, {1}%",
    TextKey.BUDGETS_UP_TITLE to "Upcoming dates",
    TextKey.BUDGETS_UP_EMPTY to "No payments due before the month ends.",
    TextKey.BUDGETS_UP_WHEN to "{0}, {1}",
    TextKey.BUDGETS_RESERVE to "Count it from my money",
    TextKey.BUDGETS_RESERVED to "Counted ✓ · undo",
    TextKey.BUDGETS_RESERVE_TITLE to "How much to set aside for “{0}”?",
    TextKey.BUDGETS_RESERVE_AMOUNT to "Amount",
    TextKey.BUDGETS_RESERVE_SAVE to "Set it aside",
    TextKey.BUDGETS_RESERVED_TOAST to "Set aside {0} for “{1}”",
    TextKey.BUDGETS_UNRESERVED_TOAST to "No longer set aside",
    TextKey.BUDGETS_LEFTOVER to "{0} after what's set aside: {1}",
    TextKey.BUDGETS_TOTAL_SHEET_TITLE to "Cap for the whole month",
    TextKey.BUDGETS_ERROR_TITLE to "Couldn't load the budgets",
    TextKey.BUDGETS_ERROR_BODY to "Check your connection, then try again.",
    TextKey.BUDGETS_SPENT_OF to "{0} / {1}",
)

// التجميع في الآخر: المتغيرات اللي فوق لازم تتعمل الأول (ترتيب التهيئة في نفس الملف)
internal val MSA_AREA_BUDGETS_TEXTS: Map<TextKey, String> =
    MSA_BUDGETS_PANEL + MSA_BUDGETS_CATEGORY + MSA_BUDGETS_GOALS + MSA_BUDGETS_CATEGORIES + MSA_BUDGETS_RULES

internal val EGYPTIAN_AREA_BUDGETS_TEXTS: Map<TextKey, String> =
    EGYPTIAN_BUDGETS_PANEL + EGYPTIAN_BUDGETS_CATEGORY + EGYPTIAN_BUDGETS_GOALS + EGYPTIAN_BUDGETS_CATEGORIES + EGYPTIAN_BUDGETS_RULES

internal val ENGLISH_AREA_BUDGETS_TEXTS: Map<TextKey, String> =
    ENGLISH_BUDGETS_PANEL + ENGLISH_BUDGETS_CATEGORY + ENGLISH_BUDGETS_GOALS + ENGLISH_BUDGETS_CATEGORIES + ENGLISH_BUDGETS_RULES
