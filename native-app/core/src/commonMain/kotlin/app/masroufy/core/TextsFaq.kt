package app.masroufy.core

import app.masroufy.core.InvestInsightKey as K

/**
 * «الأسئلة الشائعة» (مواصفة النقل §1.13 — `Faq.dc.html`): أول سؤال بس دلوقتي، والصياغة «لسه هتتراجع مع المالك» (OVERRIDES فرع
 * التصميم سطر 1659 — Q11). `{0}` في `FAQ_A1_4` = مصدر التضخم الرسمي للبلد ([K.FAQ_CPI_SA] · [K.FAQ_CPI_EG]) — النموذج كتب جهة
 * السعودية بس في الفصحى.
 */
internal val MSA_FAQ_TEXTS: Map<TextRef, String> = mapOf(
    K.FAQ_TITLE to "الأسئلة الشائعة",
    K.FAQ_MORE_SUB to "على ماذا بُني حساب الاستثمار؟ وأكثر",
    K.FAQ_FOOTER to "سنضيف أسئلة أخرى لاحقًا.",
    K.FAQ_Q1 to "على ماذا بُني حساب «كم سيصبح معك» في الاستثمار؟",
    K.FAQ_A1_1 to "كل مالك = الحسابات والكاش + الأصول − الديون. السيارة والجوال وأشياء الاستخدام الشخصي والأصول المؤرشفة ليست داخل الحساب.",
    K.FAQ_A1_2 to "ادخارك = متوسط آخر 3 أشهر (دخلك − صرفك).",
    K.FAQ_A1_3 to "الذهب يزيد بمتوسط سعره في آخر 10 سنوات من البنك الدولي (رسمي). والأسهم بمتوسط مؤشر السوق من مزوّد مؤشرات (ليس رسميًا). والعقار يزيد مثل الغلاء الرسمي.",
    K.FAQ_A1_4 to "«بقيمة اليوم» = نخصم التضخم الرسمي ({0}).",
    K.FAQ_A1_5 to "«كما أنت»: ادخارك يبقى كاشًا دون أرباح، والأقساط تنتهي في مواعيدها.",
    K.FAQ_A1_6 to "هذا متوسط ما مضى، وليس وعدًا بما سيأتي.",
    K.FAQ_CPI_SA to "الهيئة العامة للإحصاء عبر البنك الدولي",
    K.FAQ_CPI_EG to "الجهاز المركزي للتعبئة العامة والإحصاء عبر البنك الدولي",
)

internal val EGYPTIAN_FAQ_TEXTS: Map<TextRef, String> = mapOf(
    K.FAQ_TITLE to "الأسئلة الشائعة",
    K.FAQ_MORE_SUB to "الحساب في الاستثمار مبني على إيه؟ وأكتر",
    K.FAQ_FOOTER to "هنضيف أسئلة تانية بعدين.",
    K.FAQ_Q1 to "حساب «هيبقى معاك كام» في الاستثمار مبني على إيه؟",
    K.FAQ_A1_1 to "كل فلوسك = الحسابات والكاش + الأصول − الديون. العربية والموبايل وحاجات الاستخدام الشخصي والأصول المتأرشفة مش داخلة في الحساب.",
    K.FAQ_A1_2 to "تحويشك = متوسط آخر 3 شهور (دخلك − صرفك).",
    K.FAQ_A1_3 to "الدهب بيزيد بمتوسط سعره في آخر 10 سنين من البنك الدولي (رسمي). والأسهم بمتوسط مؤشر السوق من مزوّد مؤشرات (مش رسمي). والعقار بيزيد زي الغلاء الرسمي.",
    K.FAQ_A1_4 to "«بفلوس النهارده» = بنخصم التضخم الرسمي ({0}).",
    K.FAQ_A1_5 to "«زي ما إنت»: تحويشك بيفضل كاش من غير أرباح، والأقساط بتخلص في مواعيدها.",
    K.FAQ_A1_6 to "ده متوسط اللي فات، مش وعد باللي جاي.",
    K.FAQ_CPI_SA to "الهيئة العامة للإحصاء عبر البنك الدولي",
    K.FAQ_CPI_EG to "الجهاز المركزي للتعبئة العامة والإحصاء عبر البنك الدولي",
)

internal val ENGLISH_FAQ_TEXTS: Map<TextRef, String> = mapOf(
    K.FAQ_TITLE to "Frequently asked questions",
    K.FAQ_MORE_SUB to "What the investment numbers are based on, and more",
    K.FAQ_FOOTER to "More questions coming later.",
    K.FAQ_Q1 to "What is “How much you'll have” based on?",
    K.FAQ_A1_1 to "All your money = accounts and cash + assets − debts. Your car, phone, other personal-use things and archived assets are not counted.",
    K.FAQ_A1_2 to "Your saving = the average of the last 3 months (income − spending).",
    K.FAQ_A1_3 to "Gold grows at its average price change over the last 10 years from the World Bank (official). Shares at the market index average from an index provider (not official). Property rises with official inflation.",
    K.FAQ_A1_4 to "“In today's money” = we subtract official inflation ({0}).",
    K.FAQ_A1_5 to "“As you are”: your saving stays as cash with no growth, and instalments end on schedule.",
    K.FAQ_A1_6 to "This is a past average, not a promise of what's to come.",
    K.FAQ_CPI_SA to "GASTAT via the World Bank",
    K.FAQ_CPI_EG to "CAPMAS via the World Bank",
)
