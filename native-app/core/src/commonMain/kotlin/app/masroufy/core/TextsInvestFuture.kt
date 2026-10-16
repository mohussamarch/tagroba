package app.masroufy.core

import app.masroufy.core.InvestFutureKey.*

/**
 * نصوص «هتبقى معاك كام» (حزمة FUTURE — مواصفة النقل §1): المصري من النموذج بالحرف (`InvestFuture` · `InvestChart` · `YearWheel` ·
 * `InvestFutureCards`)، والفصحى المختصرة للسعودية (§66) من نفس النموذج — إلا «نصف مليون» مكان «نص مليون» (§88-7، سؤال Q11).
 * `{0}` و`{1}` متغيرات. الأرقام 0-9 دايمًا (§79.1). الإنجليزي في `TextsInvestFutureEnglish.kt`.
 */
private const val RIYAL_ONE = "⁦⃁ 1⁩"

internal val MSA_INVEST_FUTURE_TEXTS: Map<TextRef, String> = mapOf(
    FUT_YOU_IN to "أنت بعد", FUT_YEARS_ONE to "سنة", FUT_YEARS_TWO to "سنتان", FUT_YEARS_FEW to "{0} سنوات", FUT_YEARS_MANY to "{0} سنة",
    FUT_WHEEL_A11Y to "غيّر المدة، الآن {0}", FUT_YEAR_LINE to "سنة {0}", FUT_AGE_SUFFIX to " · عمرك وقتها {0} سنة",
    FUT_THEN_SUFFIX to " — بالرقم كما سيكون وقتها", FUT_TODAY_ROW to "اليوم ≈ {0}",
    FUT_NA_BODY to "غير متاح: ملف المتوسطات لم يصل بعد، فلا نستطيع حساب الأرباح.", FUT_AGE_LINK to "اكتب عمرك في حاسبة التقاعد ←",
    FUT_UNKNOWN_COUNT to "{0} من أموالك بلا قيمة معروفة وليست داخل الحساب",
    FUT_PATHS_TITLE to "اختر طريقك", FUT_PATHS_SUB to "الرقم في الأعلى والخط العريض يتغيران معه",
    FUT_KEEP_TITLE to "كما أنت", FUT_KEEP_DESC to "تدّخر {0} شهريًا وتتركها في البنك", FUT_KEEP_DESC_LOW to "تصرف أكثر من دخلك بـ{0} شهريًا",
    FUT_KEEP_DESC_UNKNOWN to "ادخارك غير معروف بعد (يلزم 3 أشهر كاملة)", FUT_KEEP_SENTENCE to "لو استمررت كما أنت",
    FUT_INVEST_TITLE to "استثمر ادخارك", FUT_INVEST_DESC to "نفس الـ{0}، لكن تستثمرها", FUT_INVEST_SENTENCE to "لو استثمرت ادخارك ({0} شهريًا)",
    FUT_MORE_TITLE to "استثمر أكثر", FUT_MORE_TITLE_LOW to "ابدأ تدّخر", FUT_MORE_DESC to "تستثمر {0} شهريًا",
    FUT_MORE_SENTENCE to "لو استثمرت {0} شهريًا", FUT_MORE_SENTENCE_LOW to "لو بدأت تدّخر وتستثمر {0} شهريًا",
    FUT_STEP_TITLE to "كم تستثمر شهريًا؟", FUT_STEP_PLUS to "زد {0}", FUT_STEP_MINUS to "قلّل {0}",
    FUT_HINT_LOW_INCOME to "بدأنا بنحو 10% من دخلك (ادفع لنفسك أولًا)", FUT_HINT_NO_INCOME to "دخلك غير معروف، فبدأنا بـ{0}",
    FUT_HINT_NORMAL to "ادخارك الحالي {0} + نحو 10% من دخلك", FUT_HINT_NORMAL_NO_INCOME to "ادخارك الحالي {0} + {1}",
    FUT_MIX_TITLE to "تستثمرها في", FUT_MIX_MINE to "مثل أملاكك", FUT_MIX_SHARES to "أسهم", FUT_MIX_GOLD to "ذهب", FUT_MIX_PROPERTY to "عقار",
    FUT_MIX_SRC_MINE to "بمتوسط {0} سنويًا (متوسط أملاكك بحسب قيمة كل نوع)", FUT_MIX_SRC to "بمتوسط {0} سنويًا — {1}",
    FUT_CHART_TITLE to "كم سيصبح معك", FUT_MODE_A11Y to "شكل الأرقام", FUT_MODE_TODAY to "بقيمة اليوم", FUT_MODE_THEN to "وقتها",
    FUT_CHART_NOTE to "الخط العريض طريقك المختار · مرّر إصبعك على التشارت لترى أي سنة", FUT_PINS_TITLE to "علاماتك",
    FUT_AXIS_MILLION to "{0} مليون", FUT_AXIS_THOUSAND to "{0} ألف", FUT_TIP_AGE to " · عمرك {0}", FUT_CHART_A11Y_ITEM to "{0} {1} سنة {2}",
    FUT_WHEEL_TITLE to "بعد كم سنة؟", FUT_WHEEL_HINT to "اسحب لأعلى أو لأسفل", FUT_WHEEL_CLOSE to "إغلاق", FUT_WHEEL_TYPE to "أو اكتب السنة نفسها",
    FUT_WHEEL_MARK to "سجّلها علامة", FUT_WHEEL_ERR to "اكتب سنة من {0} إلى {1}", FUT_WHEEL_REMOVE to "احذف علامة {0}", FUT_WHEEL_DONE to "تم",
    FUT_PASSIVE_TITLE to "استثماراتك ستأتيك بكم شهريًا دون عمل", FUT_PASSIVE_TODAY to "اليوم", FUT_PASSIVE_ON_PATH to "بطريق «{0}»",
    FUT_PASSIVE_NOTE to "الربح فوق الغلاء فقط دون أن تمس الأصل: الذهب {0} · الأسهم {1} سنويًا", FUT_PASSIVE_RENT to " + الإيجار وعائد الشهادة {0}",
    FUT_MILES_TITLE to "محطاتك", FUT_MILE_100K to "100 ألف", FUT_MILE_HALF_M to "نصف مليون", FUT_MILE_FIRST_M to "أول مليون",
    FUT_MILE_5M to "5 ملايين", FUT_MILE_10M to "10 ملايين", FUT_MILE_PASSED to "تجاوزته", FUT_MILE_REACHED to "سنة {0} (بعد {1})",
    FUT_MILE_NEVER to "ليس خلال 40 سنة", FUT_GOAL_LABEL to "هدف «{0}»", FUT_GOAL_NO_DATE to "بلا موعد",
    FUT_MILES_NOTE to "المحطات بطريق «{0}» · والمعيّنات أهداف خطط الادخار",
    FUT_COUNTRIES_TITLE to "في البلدين سنة {0}", FUT_COUNTRIES_TOTAL to "المجموع التقريبي",
    FUT_FX_LINE to "لو استمر كل شيء كما هو · بسعر صرف اليوم: $RIYAL_ONE = {0} ج.م — من سعر الدولار بالريال وبالجنيه ({1}، {2})",
    FUT_FX_WARN to "الرقم المجمّع بسعر صرف اليوم، والجنيه يتحرك كثيرًا — تقريبي جدًا.",
    FUT_FX_MISSING to "سعر صرف الجنيه غير موجود في ملف الأسعار بعد", FUT_COUNTRY_SA to "السعودية", FUT_COUNTRY_EG to "مصر",
)

internal val EGYPTIAN_INVEST_FUTURE_TEXTS: Map<TextRef, String> = mapOf(
    FUT_YOU_IN to "إنت كمان", FUT_YEARS_ONE to "سنة", FUT_YEARS_TWO to "سنتين", FUT_YEARS_FEW to "{0} سنين", FUT_YEARS_MANY to "{0} سنة",
    FUT_WHEEL_A11Y to "غيّر المدة، دلوقتي {0}", FUT_YEAR_LINE to "سنة {0}", FUT_AGE_SUFFIX to " · هتبقى {0} سنة",
    FUT_THEN_SUFFIX to " — بالرقم زي ما هيكون وقتها", FUT_TODAY_ROW to "النهارده ≈ {0}",
    FUT_NA_BODY to "غير متاح: ملف المتوسطات لسه ما وصلش، فمش هنقدر نحسب الأرباح.", FUT_AGE_LINK to "اكتب سنك في حاسبة التقاعد ←",
    FUT_UNKNOWN_COUNT to "{0} من حاجاتك من غير قيمة معروفة ومش داخلة في الحساب",
    FUT_PATHS_TITLE to "اختار طريقك", FUT_PATHS_SUB to "الرقم اللي فوق والخط العريض بيتغيّروا معاه",
    FUT_KEEP_TITLE to "زي ما إنت", FUT_KEEP_DESC to "بتحوّش {0} في الشهر وبتسيبهم في البنك", FUT_KEEP_DESC_LOW to "بتصرف أكتر من دخلك بـ{0} في الشهر",
    FUT_KEEP_DESC_UNKNOWN to "تحويشك مش معروف لسه (محتاج 3 شهور كاملة)", FUT_KEEP_SENTENCE to "لو كمّلت زي ما إنت",
    FUT_INVEST_TITLE to "استثمر تحويشك", FUT_INVEST_DESC to "نفس الـ{0}، بس تستثمرهم", FUT_INVEST_SENTENCE to "لو استثمرت تحويشك ({0} في الشهر)",
    FUT_MORE_TITLE to "استثمر أكتر", FUT_MORE_TITLE_LOW to "ابدأ تحوّش", FUT_MORE_DESC to "تستثمر {0} في الشهر",
    FUT_MORE_SENTENCE to "لو استثمرت {0} في الشهر", FUT_MORE_SENTENCE_LOW to "لو بدأت تحوّش وتستثمر {0} في الشهر",
    FUT_STEP_TITLE to "هتستثمر كام في الشهر؟", FUT_STEP_PLUS to "زوّد {0}", FUT_STEP_MINUS to "قلّل {0}",
    FUT_HINT_LOW_INCOME to "بدأنا بحوالي 10% من دخلك (ادفع لنفسك الأول)", FUT_HINT_NO_INCOME to "دخلك مش معروف، فبدأنا بـ{0}",
    FUT_HINT_NORMAL to "تحويشك دلوقتي {0} + حوالي 10% من دخلك", FUT_HINT_NORMAL_NO_INCOME to "تحويشك دلوقتي {0} + {1}",
    FUT_MIX_TITLE to "بتستثمرها في", FUT_MIX_MINE to "زي أملاكك", FUT_MIX_SHARES to "أسهم", FUT_MIX_GOLD to "دهب", FUT_MIX_PROPERTY to "عقار",
    FUT_MIX_SRC_MINE to "بمتوسط {0} في السنة (متوسط أملاكك حسب قيمة كل نوع)", FUT_MIX_SRC to "بمتوسط {0} في السنة — {1}",
    FUT_CHART_TITLE to "هيبقى معاك كام", FUT_MODE_A11Y to "شكل الأرقام", FUT_MODE_TODAY to "بفلوس النهارده", FUT_MODE_THEN to "زي ما هيكون",
    FUT_CHART_NOTE to "الخط العريض هو طريقك · حرّك صباعك على التشارت تشوف أي سنة", FUT_PINS_TITLE to "علاماتك",
    FUT_AXIS_MILLION to "{0} مليون", FUT_AXIS_THOUSAND to "{0} ألف", FUT_TIP_AGE to " · عمرك {0}", FUT_CHART_A11Y_ITEM to "{0} {1} سنة {2}",
    FUT_WHEEL_TITLE to "كمان كام سنة؟", FUT_WHEEL_HINT to "اسحب لفوق أو لتحت", FUT_WHEEL_CLOSE to "اقفل", FUT_WHEEL_TYPE to "أو اكتب السنة نفسها",
    FUT_WHEEL_MARK to "سجّلها علامة", FUT_WHEEL_ERR to "اكتب سنة من {0} لـ{1}", FUT_WHEEL_REMOVE to "شيل علامة {0}", FUT_WHEEL_DONE to "تمام",
    FUT_PASSIVE_TITLE to "استثماراتك هتجيبلك كام في الشهر من غير شغل", FUT_PASSIVE_TODAY to "النهارده", FUT_PASSIVE_ON_PATH to "بطريق «{0}»",
    FUT_PASSIVE_NOTE to "الربح فوق الغلاء بس من غير ما تلمس الأصل: الدهب {0} · الأسهم {1} في السنة", FUT_PASSIVE_RENT to " + الإيجار وعائد الشهادة {0}",
    FUT_MILES_TITLE to "محطاتك", FUT_MILE_100K to "100 ألف", FUT_MILE_HALF_M to "نص مليون", FUT_MILE_FIRST_M to "أول مليون",
    FUT_MILE_5M to "5 مليون", FUT_MILE_10M to "10 مليون", FUT_MILE_PASSED to "عدّيته", FUT_MILE_REACHED to "سنة {0} (بعد {1})",
    FUT_MILE_NEVER to "مش خلال 40 سنة", FUT_GOAL_LABEL to "هدف «{0}»", FUT_GOAL_NO_DATE to "من غير ميعاد",
    FUT_MILES_NOTE to "المحطات بطريق «{0}» · والمعيّنات أهداف خطط الادخار",
    FUT_COUNTRIES_TITLE to "في البلدين سنة {0}", FUT_COUNTRIES_TOTAL to "المجموع التقريبي",
    FUT_FX_LINE to "لو كل حاجة فضلت زي ما هي · بسعر صرف النهارده: $RIYAL_ONE = {0} ج.م — من سعر الدولار بالريال وبالجنيه ({1}، {2})",
    FUT_FX_WARN to "الرقم المجمّع بسعر صرف النهارده، والجنيه بيتحرك كتير — تقريبي جدًا.",
    FUT_FX_MISSING to "سعر صرف الجنيه مش موجود في ملف الأسعار لسه", FUT_COUNTRY_SA to "السعودية", FUT_COUNTRY_EG to "مصر",
)
