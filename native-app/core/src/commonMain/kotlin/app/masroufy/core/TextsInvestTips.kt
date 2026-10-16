package app.masroufy.core

import app.masroufy.core.InvestInsightKey as K

/**
 * بنك النصايح (مواصفة النقل §1.7 — النص بالحرف من `InvestTipCard.dc.html`؛ الإنجليزي كتابة Claude لحد مراجعة المالك §40).
 * قاعدة ثابتة (§87-12 · §78): عمرها ما تقول «اشتري/بيع حاجة معيّنة» ولا اسم منصة. المصدر **اسم بس** من غير رابط لحد ما كل رابط رسمي
 * يتأكد (Q3). مصادر العربي واحدة للفصحى والمصري.
 */
private val ARABIC_TIP_SOURCES: Map<TextRef, String> = mapOf(
    K.SRC_EMERGENCY to "موقع توعية المستثمرين — هيئة الأوراق المالية الأمريكية",
    K.SRC_MONTHLY to "موقع توعية المستثمرين — هيئة الأوراق المالية الأمريكية (متوسط تكلفة الشراء)",
    K.SRC_COMPOUND to "موقع توعية المستثمرين — هيئة الأوراق المالية الأمريكية (حاسبة الربح المركّب)",
    K.SRC_SPREAD to "موقع توعية المستثمرين — دليل المبتدئين في توزيع الأصول والتنويع",
    K.SRC_DAYTRADE to "هيئة الأوراق المالية الأمريكية — نشرة «المضاربة اليومية»",
    K.SRC_INFLUENCER to "المنظمة الدولية لهيئات الأوراق المالية — تقرير المؤثرين الماليين",
    K.SRC_FRAUD_SA to "هيئة السوق المالية السعودية — التحذيرات",
    K.SRC_FRAUD_EG to "الهيئة العامة للرقابة المالية — التحذيرات",
    K.SRC_HALAL to "المعيار الشرعي 21 — هيئة المحاسبة والمراجعة للمؤسسات المالية الإسلامية",
)

internal val MSA_INVEST_TIP_TEXTS: Map<TextRef, String> = ARABIC_TIP_SOURCES + mapOf(
    K.TIP_WHY_GENERAL to "نصيحة",
    K.TIP_WHY_EMERGENCY to "لا يوجد احتياطي طوارئ كافٍ",
    K.TIP_WHY_MONTHLY to "تدّخر كل شهر ولا تستثمر",
    K.TIP_WHY_COMPOUND to "يصلك إيجار أو توزيعات",
    K.TIP_WHY_SPREAD to "أكثر من 70% من أصولك في نوع واحد",
    K.TIP_EMERGENCY to "قبل أن تستثمر، اجعل لديك احتياطي طوارئ يكفيك عدة أشهر.",
    K.TIP_MONTHLY to "الشراء بمبلغ ثابت كل شهر يوزّع سعر الشراء على الوقت، بدل انتظار «الوقت المناسب».",
    K.TIP_COMPOUND to "الأرباح التي تعود إليك إن أعدت استثمارها تكبر أسرع، وهذا معنى الربح المركّب.",
    K.TIP_SPREAD to "لا تضع كل مالك في نوع واحد؛ التوزيع يقلّل أثر أي خسارة.",
    K.TIP_DAYTRADE to "أغلب من يضاربون يوميًا يخسرون؛ وإن فعلت فبمال تتحمّل خسارته.",
    K.TIP_INFLUENCER to "أي ناصح على وسائل التواصل: تأكد أنه مرخّص، واسأل: ماذا يكسب لو أخذت بكلامه؟",
    K.TIP_FRAUD to "الوعد بربح كبير ومضمون في وقت قصير علامة احتيال.",
    K.TIP_HALAL to "للسهم المتوافق مع الشريعة شروط معروفة: ديون بفائدة أقل من 30% من قيمته السوقية، وودائع بفائدة أقل من 30%، ودخل غير مشروع أقل من 5%.",
)

internal val EGYPTIAN_INVEST_TIP_TEXTS: Map<TextRef, String> = ARABIC_TIP_SOURCES + mapOf(
    K.TIP_WHY_GENERAL to "نصيحة",
    K.TIP_WHY_EMERGENCY to "مفيش احتياطي طوارئ كفاية",
    K.TIP_WHY_MONTHLY to "بتحوّش كل شهر ومش بتستثمر",
    K.TIP_WHY_COMPOUND to "بيدخلك إيجار أو توزيعات",
    K.TIP_WHY_SPREAD to "أكتر من 70% من أصولك في نوع واحد",
    K.TIP_EMERGENCY to "قبل ما تستثمر، خلّي معاك احتياطي طوارئ يكفيك كام شهر.",
    K.TIP_MONTHLY to "الشرا بمبلغ ثابت كل شهر بيوزّع سعر الشرا على الوقت، بدل ما تستنى «الوقت المناسب».",
    K.TIP_COMPOUND to "الأرباح اللي بترجعلك لو رجّعتها تستثمرها بتكبر أسرع، وده معنى الربح المركّب.",
    K.TIP_SPREAD to "متحطش كل فلوسك في نوع واحد، التوزيع بيقلّل أثر أي خسارة.",
    K.TIP_DAYTRADE to "أغلب اللي بيضاربوا يوميًا بيخسروا، ولو هتعمل كده فبفلوس تقدر تخسرها.",
    K.TIP_INFLUENCER to "أي حد بينصحك على السوشيال ميديا: اتأكد إنه مرخّص، واسأل نفسك: هو بيكسب إيه لو سمعت كلامه؟",
    K.TIP_FRAUD to "أي حد بيوعدك بمكسب كبير ومضمون في وقت قصير، ده علامة احتيال.",
    K.TIP_HALAL to "السهم الحلال ليه شروط معروفة: ديون بفايدة أقل من 30% من قيمته السوقية، وودائع بفايدة أقل من 30%، ودخل غير مشروع أقل من 5%.",
)

internal val ENGLISH_INVEST_TIP_TEXTS: Map<TextRef, String> = mapOf(
    K.SRC_EMERGENCY to "Investor.gov — US SEC",
    K.SRC_MONTHLY to "Investor.gov — US SEC (dollar-cost averaging)",
    K.SRC_COMPOUND to "Investor.gov — US SEC (compound interest calculator)",
    K.SRC_SPREAD to "Investor.gov — beginner's guide to asset allocation and diversification",
    K.SRC_DAYTRADE to "US SEC — \"Day Trading\" bulletin",
    K.SRC_INFLUENCER to "IOSCO — report on finfluencers",
    K.SRC_FRAUD_SA to "Saudi CMA — warnings",
    K.SRC_FRAUD_EG to "FRA Egypt — warnings",
    K.SRC_HALAL to "AAOIFI Sharia Standard 21",
    K.TIP_WHY_GENERAL to "Tip",
    K.TIP_WHY_EMERGENCY to "No sufficient emergency reserve",
    K.TIP_WHY_MONTHLY to "You save every month but don't invest",
    K.TIP_WHY_COMPOUND to "You receive rent or dividends",
    K.TIP_WHY_SPREAD to "Over 70% of your assets are in one type",
    K.TIP_EMERGENCY to "Before investing, keep an emergency reserve that covers several months.",
    K.TIP_MONTHLY to "Buying a fixed amount every month spreads your buying price over time, instead of waiting for “the right moment”.",
    K.TIP_COMPOUND to "Returns you reinvest grow faster — that is what compounding means.",
    K.TIP_SPREAD to "Don't put all your money in one type; spreading it reduces the effect of any loss.",
    K.TIP_DAYTRADE to "Most day traders lose money; if you do it, use only money you can afford to lose.",
    K.TIP_INFLUENCER to "Anyone advising you on social media: check they're licensed, and ask what they gain if you follow them.",
    K.TIP_FRAUD to "A promise of big, guaranteed profit in a short time is a sign of fraud.",
    K.TIP_HALAL to "A Sharia-compliant share has known conditions: interest-bearing debt under 30% of its market value, interest-bearing deposits under 30%, and impermissible income under 5%.",
)
