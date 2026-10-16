package app.masroufy.core

import app.masroufy.core.InvestInsightKey as K

/**
 * سطر «ليه دي» فوق كارت النصيحة (حالتك من بياناتك — مواصفة النقل §1.7، من `InvestTipCard.dc.html` بالحرف؛ الإنجليزي كتابة Claude §40)
 * + عنوان مجموعة «الديون والتمويل» الجديدة في صفحة النصايح (Claude choice ❓ — البنك الموثّق فيه 5 نصايح عن التمويل).
 * النصايح نفسها **بيانات** من البنك الموثّق (`InvestTipsBank.kt`)، مش هنا.
 */
internal val MSA_INVEST_TIP_TEXTS: Map<TextRef, String> = mapOf(
    K.TIP_WHY_GENERAL to "نصيحة",
    K.TIP_WHY_EMERGENCY to "لا يوجد احتياطي طوارئ كافٍ",
    K.TIP_WHY_MONTHLY to "تدّخر كل شهر ولا تستثمر",
    K.TIP_WHY_COMPOUND to "يصلك إيجار أو توزيعات",
    K.TIP_WHY_SPREAD to "أكثر من 70% من أصولك في نوع واحد",
    K.TIPS_GROUP_DEBT to "الديون والتمويل",
)

internal val EGYPTIAN_INVEST_TIP_TEXTS: Map<TextRef, String> = mapOf(
    K.TIP_WHY_GENERAL to "نصيحة",
    K.TIP_WHY_EMERGENCY to "مفيش احتياطي طوارئ كفاية",
    K.TIP_WHY_MONTHLY to "بتحوّش كل شهر ومش بتستثمر",
    K.TIP_WHY_COMPOUND to "بيدخلك إيجار أو توزيعات",
    K.TIP_WHY_SPREAD to "أكتر من 70% من أصولك في نوع واحد",
    K.TIPS_GROUP_DEBT to "الديون والتقسيط",
)

internal val ENGLISH_INVEST_TIP_TEXTS: Map<TextRef, String> = mapOf(
    K.TIP_WHY_GENERAL to "Tip",
    K.TIP_WHY_EMERGENCY to "No sufficient emergency reserve",
    K.TIP_WHY_MONTHLY to "You save every month but don't invest",
    K.TIP_WHY_COMPOUND to "You receive rent or dividends",
    K.TIP_WHY_SPREAD to "Over 70% of your assets are in one type",
    K.TIPS_GROUP_DEBT to "Debt and financing",
)
