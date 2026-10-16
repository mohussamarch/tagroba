package app.masroufy.core

/**
 * نصوص جولة r041b (فحص المحاكي r041) — جدول واحد لكل لغة يجمع حارتين:
 * - مشاكل أول تشغيل: كارت الرئيسية «عندك N رسالة من البنك مستنية تأكيدك» (P3) — بيفتح «رسائل البنك» على قايمة «أكّد الأشكال» على طول ·
 *   سطر «الناقص» تحت «احفظ» المعطّل في لوحة «+» (P7).
 * - الفلوس ورسايل البنك: جملة مكان «0.00» لما مفيش أساس للرقم (CLAUDE.md #10 — التوقع من غير عمليات · الالتزامات اللي ما اتسجلتش) ·
 *   اسم الطرف بدل «متجر جديد» للتحويل لشخص · «محفظة جديدة لهذا الحساب» ونقل الأرقام في سؤال «في أي محفظة؟».
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي.
 */
internal val MSA_R041B_TEXTS: Map<TextRef, String> = mapOf(
    // مشاكل أول تشغيل (P3 · P7)
    UiKey.HOME_SMS_WAIT_ONE to "لديك رسالة من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_TWO to "لديك رسالتان من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_FEW to "لديك {0} رسائل من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_MANY to "لديك {0} رسالة من البنك بانتظار تأكيدك",
    UiKey.HOME_SMS_WAIT_BODY to "أكّد رسالة واحدة من كل شكل مرة واحدة، وبعدها تُسجَّل رسائل هذا الشكل تلقائيًا.",
    UiKey.ADD_NEED_AMOUNT to "اكتب المبلغ أولًا.",
    UiKey.ADD_NEED_CATEGORY to "اختر التصنيف.",
    UiKey.ADD_NEED_INCOME_KIND to "اختر نوع الدخل.",
    UiKey.ADD_NEED_TO to "اختر المحفظة التي تحوّل إليها.",
    UiKey.ADD_NEED_FROM to "اختر المحفظة: من أين؟",
    // الفلوس ورسايل البنك
    TextKey.FORECAST_NO_DATA to "لا عمليات في هذه الفترة بعد — لا أساس للتوقع.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "معك {0}، ولا التزامات مسجّلة قبل الراتب، مقسومًا على الأيام الباقية.",
    UiKey.HOME_DAILY_BASIS_ON_HAND to "معك {0}، مقسومًا على الأيام الباقية.",
    UiKey.HOME_DAILY_UNPRICED_NOTE to "بعض الالتزامات قبل الراتب بلا مبلغ معروف أو بعملة أخرى، ولم تُخصم.",
    UiKey.BANK_SMS_PARTY_TO to "تحويل لشخص",
    UiKey.BANK_SMS_PARTY_FROM to "تحويل من شخص",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "محفظة جديدة لهذا الحساب",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "البنك نفسه، ورصيدها «غير متاح» حتى تحدّده.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "عليها الحساب {0} حاليًا",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "ستحلّ {0} محلّ الأرقام {1} على «{2}»، وتعود رسائل الحساب {1} للسؤال.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "أُنشئت «{0}» للحساب {1} — سُجّل {2}",
)

internal val EGYPTIAN_R041B_TEXTS: Map<TextRef, String> = mapOf(
    // مشاكل أول تشغيل (P3 · P7)
    UiKey.HOME_SMS_WAIT_ONE to "عندك رسالة من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_TWO to "عندك رسالتين من البنك مستنيين تأكيدك",
    UiKey.HOME_SMS_WAIT_FEW to "عندك {0} رسايل من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_MANY to "عندك {0} رسالة من البنك مستنية تأكيدك",
    UiKey.HOME_SMS_WAIT_BODY to "أكّد رسالة واحدة من كل شكل مرة واحدة، وبعدها رسايل الشكل ده هتتسجل لوحدها.",
    UiKey.ADD_NEED_AMOUNT to "اكتب المبلغ الأول.",
    UiKey.ADD_NEED_CATEGORY to "اختار التصنيف.",
    UiKey.ADD_NEED_INCOME_KIND to "اختار نوع الدخل.",
    UiKey.ADD_NEED_TO to "اختار المحفظة اللي هتحوّل لها.",
    UiKey.ADD_NEED_FROM to "اختار المحفظة: منين؟",
    // الفلوس ورسايل البنك
    TextKey.FORECAST_NO_DATA to "لسه مفيش عمليات في الفترة دي — مفيش أساس للتوقع.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "معاك {0}، ومفيش التزامات متسجلة قبل المرتب، على الأيام الفاضلة.",
    UiKey.HOME_DAILY_BASIS_ON_HAND to "معاك {0}، على الأيام الفاضلة.",
    UiKey.HOME_DAILY_UNPRICED_NOTE to "فيه التزامات قبل المرتب من غير مبلغ معروف أو بعملة تانية، ومش متطروحة.",
    UiKey.BANK_SMS_PARTY_TO to "تحويل لشخص",
    UiKey.BANK_SMS_PARTY_FROM to "تحويل من شخص",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "محفظة جديدة للحساب ده",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "نفس البنك، ورصيدها «غير متاح» لحد ما تحدّده.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "عليها الحساب {0} دلوقتي",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "{0} هتتحط مكان الأرقام {1} على «{2}»، ورسايل الحساب {1} هترجع تسأل تاني.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "اتعملت «{0}» للحساب {1} — اتسجل {2}",
)

internal val ENGLISH_R041B_TEXTS: Map<TextRef, String> = mapOf(
    // مشاكل أول تشغيل (P3 · P7)
    UiKey.HOME_SMS_WAIT_ONE to "You have 1 bank message awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_TWO to "You have 2 bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_FEW to "You have {0} bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_MANY to "You have {0} bank messages awaiting your confirmation",
    UiKey.HOME_SMS_WAIT_BODY to "Confirm one message of each shape once — after that, messages of that shape are recorded by themselves.",
    UiKey.ADD_NEED_AMOUNT to "Enter the amount first.",
    UiKey.ADD_NEED_CATEGORY to "Pick a category.",
    UiKey.ADD_NEED_INCOME_KIND to "Pick the income type.",
    UiKey.ADD_NEED_TO to "Pick the wallet to transfer to.",
    UiKey.ADD_NEED_FROM to "Pick the wallet it comes from.",
    // الفلوس ورسايل البنك
    TextKey.FORECAST_NO_DATA to "No transactions in this period yet — nothing to base a forecast on.",
    UiKey.HOME_DAILY_BASIS_NO_DUES to "You have {0}, with no commitments recorded before payday, over the days left.",
    UiKey.HOME_DAILY_BASIS_ON_HAND to "You have {0}, over the days left.",
    UiKey.HOME_DAILY_UNPRICED_NOTE to "Some commitments before payday have no known amount or are in another currency, and are not deducted.",
    UiKey.BANK_SMS_PARTY_TO to "Transfer to a person",
    UiKey.BANK_SMS_PARTY_FROM to "Transfer from a person",
    UiKey.SMS_ACCOUNT_NEW_WALLET to "A new wallet for this account",
    UiKey.SMS_ACCOUNT_NEW_WALLET_SUB to "Same bank; its balance stays «Not available» until you set it.",
    UiKey.SMS_ACCOUNT_HAS_DIGITS to "Currently account {0}",
    UiKey.SMS_ACCOUNT_REPLACE_NOTE to "{0} will replace {1} on «{2}», and messages for account {1} will ask again.",
    UiKey.SMS_ACCOUNT_NEW_SAVED to "Created «{0}» for account {1} — recorded {2}",
)
