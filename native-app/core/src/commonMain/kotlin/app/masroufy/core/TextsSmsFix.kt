package app.masroufy.core

/**
 * نصوص مسار sms-fix (HANDOVER §6.5-3 — رصيد البنك الغلط): سؤال «الحساب ده بتاعك؟» · رسالة غطّاها الكشف · شاشة «صلّح رسايل البنك القديمة».
 * فصحى مختصرة للسعودية (من غير كلام مصري — §66/§88-7) · مصري لمصر · إنجليزي.
 */
internal val MSA_SMS_FIX_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_UNKNOWN_ACCOUNT to "رقم الحساب في الرسالة لا يطابق أي محفظة لديك. هل هذا الحساب لك؟ أضفه محفظة أو اكتب آخر 4 أرقام منه في محفظته، وإن كان حساب شخص آخر فتجاهل الرسالة",
    TextKey.SMS_COVERED_BY_STATEMENT to "كشف الحساب المستورد يغطي هذا اليوم (حتى {0})؛ لذلك لم تُضف الرسالة عملية جديدة",
    TextKey.SMS_REPAIR_ENTRY to "تصحيح رسائل البنك القديمة",
    TextKey.SMS_REPAIR_TITLE to "تصحيح رسائل البنك القديمة",
    TextKey.SMS_REPAIR_INTRO to "أعدنا قراءة رسائل البنك المسجلة بالقواعد الجديدة: اتجاه التحويل ومحفظته من سطري «من» و«إلى». هذه العمليات سيتغير اتجاهها أو محفظتها. لن يتغير شيء قبل تأكيدك، وتُحفظ نسخة احتياطية أولًا",
    TextKey.SMS_REPAIR_EMPTY to "لا توجد رسائل قديمة تحتاج إلى تصحيح",
    TextKey.SMS_REPAIR_DIR_IN to "داخل",
    TextKey.SMS_REPAIR_DIR_OUT to "خارج",
    TextKey.SMS_REPAIR_APPLY to "صحّح المحدد ({0})",
    TextKey.SMS_REPAIR_DONE to "صُحّحت {0} عملية",
    TextKey.SMS_REPAIR_UNDO to "تراجع",
    TextKey.SMS_REPAIR_DUPES to "عمليات مكررة بين الرسائل والكشف: {0}. راجعها في «راجع المكرر»",
    TextKey.SMS_REPAIR_WALLET_DIGITS to "آخر 4 أرقام من رقم الحساب",
)

internal val EGYPTIAN_SMS_FIX_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_UNKNOWN_ACCOUNT to "رقم الحساب في الرسالة مش لأي محفظة عندك. الحساب ده بتاعك؟ ضيفه محفظة أو اكتب آخر 4 أرقام منه في محفظته، ولو حساب حد تاني تجاهل الرسالة",
    TextKey.SMS_COVERED_BY_STATEMENT to "كشف الحساب اللي استوردته مغطّي اليوم ده (لحد {0})؛ عشان كده الرسالة ما اتضافتش عملية جديدة",
    TextKey.SMS_REPAIR_ENTRY to "صلّح رسايل البنك القديمة",
    TextKey.SMS_REPAIR_TITLE to "صلّح رسايل البنك القديمة",
    TextKey.SMS_REPAIR_INTRO to "قرينا رسايل البنك المتسجلة تاني بالقواعد الجديدة: اتجاه التحويل ومحفظته من سطر «من» و«الى». العمليات دي اتجاهها أو محفظتها هيتغير. مفيش حاجة هتتغير قبل ما تأكد، وبنعمل نسخة احتياطية الأول",
    TextKey.SMS_REPAIR_EMPTY to "مفيش رسايل قديمة محتاجة تتصلّح",
    TextKey.SMS_REPAIR_DIR_IN to "داخل",
    TextKey.SMS_REPAIR_DIR_OUT to "خارج",
    TextKey.SMS_REPAIR_APPLY to "صلّح اللي اخترته ({0})",
    TextKey.SMS_REPAIR_DONE to "اتصلّحت {0} عملية",
    TextKey.SMS_REPAIR_UNDO to "تراجع",
    TextKey.SMS_REPAIR_DUPES to "عمليات متكررة بين الرسايل والكشف: {0}. راجعها في «راجع المكرر»",
    TextKey.SMS_REPAIR_WALLET_DIGITS to "آخر 4 أرقام من رقم الحساب",
)

internal val ENGLISH_SMS_FIX_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.SMS_WAIT_UNKNOWN_ACCOUNT to "The account number in this message doesn't match any of your wallets. Is this account yours? Add it as a wallet or type its last 4 digits in its wallet; if it's someone else's account, ignore the message",
    TextKey.SMS_COVERED_BY_STATEMENT to "Your imported statement already covers this day (up to {0}), so the message wasn't added as a new transaction",
    TextKey.SMS_REPAIR_ENTRY to "Fix old bank messages",
    TextKey.SMS_REPAIR_TITLE to "Fix old bank messages",
    TextKey.SMS_REPAIR_INTRO to "We re-read your recorded bank messages with the new rules: a transfer's direction and wallet come from its «From» and «To» lines. These transactions would change direction or wallet. Nothing changes until you confirm, and a backup is saved first",
    TextKey.SMS_REPAIR_EMPTY to "No old messages need fixing",
    TextKey.SMS_REPAIR_DIR_IN to "In",
    TextKey.SMS_REPAIR_DIR_OUT to "Out",
    TextKey.SMS_REPAIR_APPLY to "Fix selected ({0})",
    TextKey.SMS_REPAIR_DONE to "{0} transactions fixed",
    TextKey.SMS_REPAIR_UNDO to "Undo",
    TextKey.SMS_REPAIR_DUPES to "Message/statement duplicates: {0}. Review them in «Review duplicates»",
    TextKey.SMS_REPAIR_WALLET_DIGITS to "Last 4 digits of the account number",
)
