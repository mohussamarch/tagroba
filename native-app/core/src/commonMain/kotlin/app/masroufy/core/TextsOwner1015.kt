package app.masroufy.core

/**
 * نصوص إجابات المالك 2026-10-15 مساءً (OVERRIDES §91): «الحساب ده بتاعك؟» بزرارين · علامة «مش في الكشف» · سطر «أمانة» في ملخص الفلوس ·
 * («امسح» للعملية الواحدة بيستخدم نصوص `TextsErase.kt` الموجودة). فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_OWNER_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_ACCOUNT_ASK to "هل الحساب المنتهي بـ{0} حسابك؟",
    UiKey.SMS_ACCOUNT_YES to "نعم، هذا حسابي",
    UiKey.SMS_ACCOUNT_NO to "لا",
    UiKey.SMS_ACCOUNT_SHEET_TITLE to "في أي محفظة الحساب المنتهي بـ{0}؟",
    UiKey.SMS_ACCOUNT_SHEET_BODY to "تُحفظ آخر 4 أرقام على المحفظة التي تختارها، وتُسجَّل رسائل هذا الحساب فيها تلقائيًا.",
    UiKey.SMS_ACCOUNT_MINE_SAVED to "حُفظ الحساب {0} على «{1}» — سُجّل {2}",
    UiKey.SMS_ACCOUNT_NOT_MINE_SAVED to "الحساب {0} ليس لك — سُجّل {1} كمال لشخص آخر أو منه",
    UiKey.TXN_NOT_IN_STATEMENT to "ليست في الكشف",
    UiKey.TXN_NOT_IN_STATEMENT_NOTE to "رسالة البنك في يوم يغطيه كشف الحساب المستورد، ومبلغها ليس في الكشف — سُجّلت عملية وعليها هذه العلامة.",
    UiKey.SUMMARY_HELD to "أمانة",
)

internal val EGYPTIAN_OWNER_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_ACCOUNT_ASK to "الحساب اللي آخره {0} بتاعك؟",
    UiKey.SMS_ACCOUNT_YES to "أيوه، ده حسابي",
    UiKey.SMS_ACCOUNT_NO to "لأ",
    UiKey.SMS_ACCOUNT_SHEET_TITLE to "الحساب اللي آخره {0} في أنهي محفظة؟",
    UiKey.SMS_ACCOUNT_SHEET_BODY to "آخر 4 أرقام هتتحفظ على المحفظة اللي تختارها، ورسايل الحساب ده هتتسجل فيها لوحدها.",
    UiKey.SMS_ACCOUNT_MINE_SAVED to "اتحفظ الحساب {0} على «{1}» — اتسجل {2}",
    UiKey.SMS_ACCOUNT_NOT_MINE_SAVED to "الحساب {0} مش بتاعك — اتسجل {1} فلوس لحد تاني أو منه",
    UiKey.TXN_NOT_IN_STATEMENT to "مش في الكشف",
    UiKey.TXN_NOT_IN_STATEMENT_NOTE to "رسالة البنك في يوم الكشف اللي استوردته مغطّيه، ومبلغها مش في الكشف — اتسجلت عملية وعليها العلامة دي.",
    UiKey.SUMMARY_HELD to "أمانة",
)

internal val ENGLISH_OWNER_1015_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_ACCOUNT_ASK to "Is the account ending in {0} yours?",
    UiKey.SMS_ACCOUNT_YES to "Yes, it's mine",
    UiKey.SMS_ACCOUNT_NO to "No",
    UiKey.SMS_ACCOUNT_SHEET_TITLE to "Which wallet is the account ending in {0}?",
    UiKey.SMS_ACCOUNT_SHEET_BODY to "The last 4 digits are saved on the wallet you pick, and this account's messages are recorded there on their own.",
    UiKey.SMS_ACCOUNT_MINE_SAVED to "Account {0} saved on «{1}» — recorded {2}",
    UiKey.SMS_ACCOUNT_NOT_MINE_SAVED to "Account {0} isn't yours — recorded {1} as money to or from someone else",
    UiKey.TXN_NOT_IN_STATEMENT to "Not in statement",
    UiKey.TXN_NOT_IN_STATEMENT_NOTE to "The bank message is on a day your imported statement covers, but its amount isn't in the statement — it was recorded with this mark.",
    UiKey.SUMMARY_HELD to "Held for you",
)
