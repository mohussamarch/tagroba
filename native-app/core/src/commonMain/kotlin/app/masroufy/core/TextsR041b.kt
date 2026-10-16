package app.masroufy.core

/**
 * نصوص جولة r041b (الفلوس ورسايل البنك من فحص المحاكي): جملة مكان «0.00» لما مفيش أساس للرقم (CLAUDE.md #10 — التوقع من غير عمليات ·
 * الالتزامات اللي ما اتسجلتش) · اسم الطرف بدل «متجر جديد» للتحويل لشخص · «محفظة جديدة لهذا الحساب» ونقل الأرقام في سؤال «في أي محفظة؟».
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_R041B_TEXTS: Map<TextRef, String> = mapOf(
    TextKey.FORECAST_NO_DATA to "لا عمليات في هذه الفترة بعد — لا أساس للتوقع.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "معك {0}، ولا التزامات مسجّلة قبل الراتب، مقسومًا على الأيام الباقية.",
    UiKey.BANK_SMS_PARTY_TO to "تحويل لشخص",
    UiKey.BANK_SMS_PARTY_FROM to "تحويل من شخص",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "محفظة جديدة لهذا الحساب",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "البنك نفسه، ورصيدها «غير متاح» حتى تحدّده.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "عليها الحساب {0} حاليًا",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "ستحلّ {0} محلّ الأرقام {1} على «{2}»، وتعود رسائل الحساب {1} للسؤال.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "أُنشئت «{0}» للحساب {1} — سُجّل {2}",
)

internal val EGYPTIAN_R041B_TEXTS: Map<TextRef, String> = mapOf(
    TextKey.FORECAST_NO_DATA to "لسه مفيش عمليات في الفترة دي — مفيش أساس للتوقع.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "معاك {0}، ومفيش التزامات متسجلة قبل المرتب، على الأيام الفاضلة.",
    UiKey.BANK_SMS_PARTY_TO to "تحويل لشخص",
    UiKey.BANK_SMS_PARTY_FROM to "تحويل من شخص",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "محفظة جديدة للحساب ده",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "نفس البنك، ورصيدها «غير متاح» لحد ما تحدّده.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "عليها الحساب {0} دلوقتي",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "{0} هتتحط مكان الأرقام {1} على «{2}»، ورسايل الحساب {1} هترجع تسأل تاني.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "اتعملت «{0}» للحساب {1} — اتسجل {2}",
)

internal val ENGLISH_R041B_TEXTS: Map<TextRef, String> = mapOf(
    TextKey.FORECAST_NO_DATA to "No transactions in this period yet — nothing to base a forecast on.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "You have {0}, with no commitments recorded before payday, over the days left.",
    UiKey.BANK_SMS_PARTY_TO to "Transfer to a person",
    UiKey.BANK_SMS_PARTY_FROM to "Transfer from a person",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "A new wallet for this account",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "Same bank; its balance stays «Not available» until you set it.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "Currently account {0}",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "{0} will replace {1} on «{2}», and messages for account {1} will ask again.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "Created «{0}» for account {1} — recorded {2}",
)
