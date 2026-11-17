package app.masroufy.core

/**
 * r053-a (OVERRIDES §121) — مفاتيح C121_ في [R06Key]:
 * - §121-1 اختيار الشهر والسنة: لوحة من تحت (سنة بسهمين + ١٢ شهر + «تمام»).
 * - §121-2 الضغطة الطويلة في «حاجاتي»: «بيع» و«امسح» و«متأكد؟».
 * - §121-14 «ابعتها تاني» جنب «رسايل ما وصلتش».
 * - §121-17 مفتاح «بيانات الاستخدام» لوحده (منفصل عن تقارير الأعطال).
 */
internal val MSA_R053A_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C121_MY_TITLE to "اختر الشهر",
    R06Key.C121_MY_PREV_YEAR to "السنة السابقة",
    R06Key.C121_MY_NEXT_YEAR to "السنة التالية",
    R06Key.C121_MY_OK to "تم",
    R06Key.C121_MY_OPEN to "اختر الشهر والسنة",
    R06Key.C121_HOLD_SELL to "بيع",
    R06Key.C121_HOLD_DELETE to "حذف",
    R06Key.C121_SURE to "متأكد؟",
    R06Key.C121_RESEND to "أعد إرسالها",
    R06Key.C121_RESENT to "أُرسلت: {0}",
    R06Key.C121_RESEND_FAILED to "لم تُرسل بعد — سنحاول مجددًا",
    R06Key.C121_USAGE_TITLE to "أرسل بيانات الاستخدام",
    R06Key.C121_USAGE_ON to "أي الشاشات تُفتح وكم مرة، بلا مبالغ ولا أسماء ولا رسائل.",
    R06Key.C121_USAGE_OFF to "متوقفة: لا يُرسل شيء عن الاستخدام.",
    R06Key.C121_USAGE_NA to "غير متاحة في هذه النسخة.",
)

internal val EGYPTIAN_R053A_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C121_MY_TITLE to "اختار الشهر",
    R06Key.C121_MY_PREV_YEAR to "السنة اللي فاتت",
    R06Key.C121_MY_NEXT_YEAR to "السنة الجاية",
    R06Key.C121_MY_OK to "تمام",
    R06Key.C121_MY_OPEN to "اختار الشهر والسنة",
    R06Key.C121_HOLD_SELL to "بيع",
    R06Key.C121_HOLD_DELETE to "امسح",
    R06Key.C121_SURE to "متأكد؟",
    R06Key.C121_RESEND to "ابعتها تاني",
    R06Key.C121_RESENT to "اتبعت: {0}",
    R06Key.C121_RESEND_FAILED to "لسه ما اتبعتتش — هنحاول تاني",
    R06Key.C121_USAGE_TITLE to "ابعت بيانات الاستخدام",
    R06Key.C121_USAGE_ON to "أنهي شاشات بتتفتح وكام مرة، من غير مبالغ ولا أسامي ولا رسايل.",
    R06Key.C121_USAGE_OFF to "مقفولة: مفيش حاجة عن الاستخدام بتتبعت.",
    R06Key.C121_USAGE_NA to "مش متاحة في النسخة دي.",
)

internal val ENGLISH_R053A_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C121_MY_TITLE to "Pick a month",
    R06Key.C121_MY_PREV_YEAR to "Previous year",
    R06Key.C121_MY_NEXT_YEAR to "Next year",
    R06Key.C121_MY_OK to "Done",
    R06Key.C121_MY_OPEN to "Pick month and year",
    R06Key.C121_HOLD_SELL to "Sell",
    R06Key.C121_HOLD_DELETE to "Delete",
    R06Key.C121_SURE to "Are you sure?",
    R06Key.C121_RESEND to "Send again",
    R06Key.C121_RESENT to "Sent: {0}",
    R06Key.C121_RESEND_FAILED to "Not sent yet — we'll try again",
    R06Key.C121_USAGE_TITLE to "Send usage data",
    R06Key.C121_USAGE_ON to "Which screens open and how often. No amounts, names or messages.",
    R06Key.C121_USAGE_OFF to "Off: nothing about usage is sent.",
    R06Key.C121_USAGE_NA to "Not available in this build.",
)
