package app.masroufy.core

/**
 * نصوص جلسة 31 (OVERRIDES §72): رسايل البنك بتتسجل لوحدها، وإشعار الجوال **للمحتاج تأكيد بس**.
 * `ALERT_LOCK_SMS_CONFIRM` بيظهر على شاشة القفل ⇒ **من غير أي رقم ولا اسم** (`isLockSafe` بيتفحص في الكود وفي الاختبار).
 * فصحى مختصرة للسعودية · مصري لمصر · إنجليزي (كتابة Claude ومستني مراجعة المالك زي باقي الجدول §40).
 * الجولة الرابعة: `SMS_WAIT_UNKNOWN_SHAPE` = سبب انتظار الرسالة اللي اتفهمت من كلمات عامة بس (مش شكل معروف) — بيظهر جوه التطبيق
 * بس (مش على شاشة القفل)، و`ALERT_WHY_SMS_CONFIRM` بقى بيذكرها.
 */
internal val MSA_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "رسائل من البنك تنتظر تأكيدك",
    TextKey.ALERT_WHY_SMS_CONFIRM to "رسالة من البنك لم تُفهم، أو شكلها جديد لم تؤكده بعد أو غير معروف، أو تشبه عملية مسجلة، فلم تُسجَّل تلقائيًا",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "رسائل بنكية تنتظر تأكيدك: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "افتح رسائل البنك لتأكيدها أو حذفها",
    TextKey.ALERT_CHANNEL_NAME to "تنبيهات مصروفي",
    TextKey.BACKGROUND_WORK_NOTICE to "جارٍ تحديث مصروفي",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "المحفظة غير موجودة في هذا البلد",
    TextKey.ALERT_GROUP_BANK_SMS to "رسائل البنك",
    TextKey.SMS_NOT_TRANSACTION to "ليست عملية مكتملة: حجز مبلغ أو طلب لم يُنفَّذ أو رسالة معلومات",
    TextKey.SMS_WAIT_UNKNOWN_SHAPE to "شكل الرسالة غير معروف، وفُهمت من كلماتها فقط؛ لذلك لم تُسجَّل تلقائيًا. راجعها وأكّدها",
    TextKey.SMS_WAIT_OTHER_ACCOUNT to "الرسالة عن حساب آخر من حساباتك، لا عن حساب هذه المحفظة؛ لذلك لم تُسجَّل تلقائيًا. راجعها وأكّدها",
    TextKey.SMS_WAIT_REFUND to "استرداد مقترح: يُسجَّل «استرداد» بعد تأكيدك فقط، ولا يُسجَّل تلقائيًا أبدًا. راجعه وأكّده",
    TextKey.SMS_WAIT_CASH_WITHDRAWAL to "سحب نقدي: يُسجَّل نقلًا من الحساب إلى محفظة النقد إذا كانت في هذا البلد محفظة نقد واحدة فقط، غير هذه المحفظة وبعملتها نفسها. راجعه وأكّده",
    TextKey.SMS_HIDDEN_TEXT to "في الرسالة علامات تعكس اتجاه النص، فما يظهر لك قد يختلف عما يُقرأ؛ لم تُقرأ",
    TextKey.SMS_OTHER_COUNTRY to "الرسالة على شكل رسالة بنك من البلد الآخر، فتُقرأ هناك",
    TextKey.SMS_WAIT_CARD_CREDIT to "مبلغ دخل على بطاقة ائتمانية، لا على حساب هذه المحفظة؛ فلم يُسجَّل تلقائيًا. راجعه وأكّده",
    TextKey.SMS_WAIT_CASH_DEPOSIT to "إيداع نقدي: نقله من محفظة النقد إلى البنك لم يُبنَ بعد، فلم يُسجَّل تلقائيًا. راجعه وأكّده",
    TextKey.SMS_WAIT_PURCHASE_CASH to "شراء مع سحب نقدي: جزء من المبلغ نقد لمحفظة النقد، ونقله لم يُبنَ بعد؛ فلم يُسجَّل تلقائيًا. راجعه وأكّده",
)

internal val EGYPTIAN_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "فيه رسايل من البنك مستنية تأكيدك",
    TextKey.ALERT_WHY_SMS_CONFIRM to "رسالة من البنك ما اتفهمتش، أو شكلها جديد لسه ما أكدتهوش أو مش معروف، أو شبه عملية متسجلة، فما اتسجلتش لوحدها",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "رسايل بنك مستنية تأكيدك: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "افتح رسايل البنك وأكّدها أو شيلها",
    TextKey.ALERT_CHANNEL_NAME to "تنبيهات مصروفي",
    TextKey.BACKGROUND_WORK_NOTICE to "مصروفي بيحدّث",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "المحفظة دي مش موجودة في البلد دي",
    TextKey.ALERT_GROUP_BANK_SMS to "رسايل البنك",
    TextKey.SMS_NOT_TRANSACTION to "مش عملية خلصت: حجز مبلغ أو طلب لسه ما اتنفذش أو رسالة معلومات",
    TextKey.SMS_WAIT_UNKNOWN_SHAPE to "شكل الرسالة مش معروف، واتفهمت من كلامها بس؛ عشان كده ما اتسجلتش لوحدها. راجعها وأكّدها",
    TextKey.SMS_WAIT_OTHER_ACCOUNT to "الرسالة عن حساب تاني من حساباتك، مش حساب المحفظة دي؛ عشان كده ما اتسجلتش لوحدها. راجعها وأكّدها",
    TextKey.SMS_WAIT_REFUND to "استرداد مقترح: هيتسجل «استرداد» بعد ما تأكده بس، وعمره ما بيتسجل لوحده. راجعه وأكّده",
    TextKey.SMS_WAIT_CASH_WITHDRAWAL to "سحب كاش: بيتسجل نقل من الحساب لمحفظة الكاش لو في البلد دي محفظة كاش واحدة بس، غير المحفظة دي وبنفس عملتها. راجعه وأكّده",
    TextKey.SMS_HIDDEN_TEXT to "الرسالة فيها علامات بتعكس اتجاه الكلام، فاللي شايفه ممكن يختلف عن اللي بيتقري؛ ما اتقرتش",
    TextKey.SMS_OTHER_COUNTRY to "الرسالة على شكل رسالة بنك من البلد التانية، فبتتقري هناك",
    TextKey.SMS_WAIT_CARD_CREDIT to "مبلغ دخل على كارت ائتمان، مش على حساب المحفظة دي؛ عشان كده ما اتسجلش لوحده. راجعه وأكّده",
    TextKey.SMS_WAIT_CASH_DEPOSIT to "إيداع كاش: نقله من محفظة الكاش للبنك لسه ما اتعملش، عشان كده ما اتسجلش لوحده. راجعه وأكّده",
    TextKey.SMS_WAIT_PURCHASE_CASH to "شراء ومعاه سحب كاش: جزء من المبلغ كاش لمحفظة الكاش ونقله لسه ما اتعملش؛ عشان كده ما اتسجلش لوحده. راجعه وأكّده",
)

internal val ENGLISH_SMS_AUTO_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.ALERT_LOCK_SMS_CONFIRM to "Bank messages are waiting for your confirmation",
    TextKey.ALERT_WHY_SMS_CONFIRM to "A bank message wasn't understood, has a new layout you haven't confirmed yet or one the app doesn't know, or looks like a recorded transaction, so it wasn't recorded automatically",
    TextKey.ALERT_SMS_CONFIRM_TITLE to "Bank messages waiting for you: {0}",
    TextKey.ALERT_SMS_CONFIRM_BODY to "Open bank messages to confirm or remove them",
    TextKey.ALERT_CHANNEL_NAME to "Masroufy alerts",
    TextKey.BACKGROUND_WORK_NOTICE to "Masroufy is updating",
    TextKey.SMS_AUTO_WALLET_UNKNOWN to "That wallet isn't in this country",
    TextKey.ALERT_GROUP_BANK_SMS to "Bank messages",
    TextKey.SMS_NOT_TRANSACTION to "Not a completed transaction: a hold, a pending request or an information message",
    TextKey.SMS_WAIT_UNKNOWN_SHAPE to "The app doesn't know this message's layout and read it from its wording only, so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_WAIT_OTHER_ACCOUNT to "This message is about another of your accounts, not this wallet's account, so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_WAIT_REFUND to "A suggested refund: it is recorded as a refund only after you confirm it, never automatically. Check it and confirm",
    TextKey.SMS_WAIT_CASH_WITHDRAWAL to "A cash withdrawal: it is recorded as a move from the account to your cash wallet when this country has exactly one cash wallet, it isn't this wallet, and it has the same currency. Check it and confirm",
    TextKey.SMS_HIDDEN_TEXT to "The message has marks that reverse the text direction, so what you see may differ from what is read; it wasn't read",
    TextKey.SMS_OTHER_COUNTRY to "The message is in a bank layout from the other country, so it is read there",
    TextKey.SMS_WAIT_CARD_CREDIT to "Money credited to a credit card, not to this wallet's account, so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_WAIT_CASH_DEPOSIT to "A cash deposit: moving it from your cash wallet to the bank isn't built yet, so it wasn't recorded automatically. Check it and confirm",
    TextKey.SMS_WAIT_PURCHASE_CASH to "A purchase with cash back: part of it is cash for your cash wallet, and moving it isn't built yet, so it wasn't recorded automatically. Check it and confirm",
)
