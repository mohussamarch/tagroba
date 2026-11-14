package app.masroufy.core

/**
 * r052-c (OVERRIDES §120) — مفاتيح C120_ في [R06Key]:
 * - §120-6 «ما تسألنيش تاني» على نوع الوارد من جهة · كارت «بتتسجل لوحدها» في إعداد رسائل البنك و«اسألني تاني».
 * - §120-7 «ده مرتب دايمًا من الحساب ده» في صفحة مصدر الدخل.
 * - §120-14 «امتلكتها إمتى؟» (كلمة المالك — أعم من «اشتريته»: ورث أو جايزة أو هدية) و«من زمان».
 * - التصميم: «مؤرشف ومتباع» تحت «حاجاتي» · صفحة مصدر الدخل (شهر بشهر + «وصل كام من المتوقع»).
 */
internal val MSA_R052C_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C120_DONT_ASK to "لا تسألني مرة أخرى",
    R06Key.C120_DONT_ASK_BODY to "التحويلات القادمة من هذا الحساب تأخذ النوع نفسه",
    R06Key.C120_AUTO_TITLE to "حسابات تُسجَّل تلقائيًا",
    R06Key.C120_AUTO_SUB to "جهات اخترت لها «لا تسألني مرة أخرى»",
    R06Key.C120_AUTO_ROW to "دائمًا: {0}",
    R06Key.C120_AUTO_STOP to "اسألني مجددًا",
    R06Key.C120_AUTO_STOPPED to "سنسألك عن نوعها مجددًا",
    R06Key.C120_ALWAYS_SALARY to "هذا راتب دائمًا من هذا الحساب",
    R06Key.C120_ALWAYS_SALARY_ON to "أي مبلغ من {0} يُسجَّل راتبًا",
    R06Key.C120_ALWAYS_SALARY_OFF to "راتب عندما يقترب المبلغ والموعد",
    R06Key.C120_ALWAYS_SALARY_SAVED to "تم الحفظ",
    R06Key.C120_OWNED_SINCE to "متى امتلكتها؟",
    R06Key.C120_OWNED_SINCE_HINT to "اختياري — شراء أو إرث أو هدية أو جائزة",
    R06Key.C120_OWNED_LONG_AGO to "منذ زمن",
    R06Key.C120_AWAY_TITLE to "المؤرشف والمبيع ({0})",
    R06Key.C120_AWAY_ARCHIVED to "مؤرشف",
    R06Key.C120_AWAY_SOLD to "مبيع",
    R06Key.C120_AWAY_OPEN to "افتحها",
    R06Key.C120_INC_MONTHS to "ما وصل شهرًا بشهر",
    R06Key.C120_INC_THIS_MONTH to "وصل {0} من {1} المتوقع هذا الشهر",
    R06Key.C120_INC_THIS_MONTH_NA to "وصل {0} هذا الشهر — المتوقع غير مسجل",
    R06Key.C120_INC_NONE to "لم يصل شيء",
    R06Key.C120_INC_WAITING to "{0} بانتظار تحديد نوعها",
)

internal val EGYPTIAN_R052C_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C120_DONT_ASK to "ما تسألنيش تاني",
    R06Key.C120_DONT_ASK_BODY to "التحويلات الجاية من الحساب ده تاخد نفس النوع",
    R06Key.C120_AUTO_TITLE to "حسابات بتتسجل لوحدها",
    R06Key.C120_AUTO_SUB to "جهات قلت عليها «ما تسألنيش تاني»",
    R06Key.C120_AUTO_ROW to "دايمًا: {0}",
    R06Key.C120_AUTO_STOP to "اسألني تاني",
    R06Key.C120_AUTO_STOPPED to "هنسألك عن نوعها تاني",
    R06Key.C120_ALWAYS_SALARY to "ده مرتب دايمًا من الحساب ده",
    R06Key.C120_ALWAYS_SALARY_ON to "أي فلوس من {0} تتسجل مرتب",
    R06Key.C120_ALWAYS_SALARY_OFF to "مرتب لما المبلغ والميعاد يقربوا",
    R06Key.C120_ALWAYS_SALARY_SAVED to "اتحفظ",
    R06Key.C120_OWNED_SINCE to "امتلكتها إمتى؟",
    R06Key.C120_OWNED_SINCE_HINT to "اختياري — شرا أو ورث أو هدية أو جايزة",
    R06Key.C120_OWNED_LONG_AGO to "من زمان",
    R06Key.C120_AWAY_TITLE to "مؤرشف ومتباع ({0})",
    R06Key.C120_AWAY_ARCHIVED to "مؤرشف",
    R06Key.C120_AWAY_SOLD to "اتباع",
    R06Key.C120_AWAY_OPEN to "افتحها",
    R06Key.C120_INC_MONTHS to "اللي وصل شهر بشهر",
    R06Key.C120_INC_THIS_MONTH to "وصل {0} من {1} المتوقع الشهر ده",
    R06Key.C120_INC_THIS_MONTH_NA to "وصل {0} الشهر ده — المتوقع مش مكتوب",
    R06Key.C120_INC_NONE to "ما وصلش",
    R06Key.C120_INC_WAITING to "{0} مستنية تحدد نوعها",
)

internal val ENGLISH_R052C_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.C120_DONT_ASK to "Don't ask me again",
    R06Key.C120_DONT_ASK_BODY to "Next transfers from this account get the same type",
    R06Key.C120_AUTO_TITLE to "Accounts recorded automatically",
    R06Key.C120_AUTO_SUB to "Payers you marked «Don't ask me again»",
    R06Key.C120_AUTO_ROW to "Always: {0}",
    R06Key.C120_AUTO_STOP to "Ask me again",
    R06Key.C120_AUTO_STOPPED to "We'll ask about its type again",
    R06Key.C120_ALWAYS_SALARY to "Always salary from this account",
    R06Key.C120_ALWAYS_SALARY_ON to "Any money from {0} is salary",
    R06Key.C120_ALWAYS_SALARY_OFF to "Salary when amount and date are close",
    R06Key.C120_ALWAYS_SALARY_SAVED to "Saved",
    R06Key.C120_OWNED_SINCE to "Since when do you own it?",
    R06Key.C120_OWNED_SINCE_HINT to "Optional — bought, inherited, gift or prize",
    R06Key.C120_OWNED_LONG_AGO to "Long ago",
    R06Key.C120_AWAY_TITLE to "Archived and sold ({0})",
    R06Key.C120_AWAY_ARCHIVED to "Archived",
    R06Key.C120_AWAY_SOLD to "Sold",
    R06Key.C120_AWAY_OPEN to "Open",
    R06Key.C120_INC_MONTHS to "Received month by month",
    R06Key.C120_INC_THIS_MONTH to "{0} of {1} expected arrived this month",
    R06Key.C120_INC_THIS_MONTH_NA to "{0} arrived this month — no expected amount",
    R06Key.C120_INC_NONE to "Nothing arrived",
    R06Key.C120_INC_WAITING to "{0} waiting for their type",
)
