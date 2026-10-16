package app.masroufy.core

/**
 * «الاستيراد» — نصوص الجولة 0.3 لرسايل البنك (كتابة Claude — المالك يقدر يغيّرها):
 * - §82-14 الرسالة الملصوقة لازم تاريخ، وجنب خانة التاريخ اختيار بلمسة واحدة. المالك كتب الكلمة «النهاردة ؟» ⇒ المصري بيها بالحرف، والفصحى
 *   (السعودية — §66) «اليوم؟» بنفس المعنى.
 * - §82-15 «اقرأ فترة»: كل بنك على محفظته المربوطة.
 * - §83 «اقرأ اللي فات؟» ومن إمتى (أقدم رسالة · رصيد البداية) وتأكيد شكل واحد من كل نوع.
 * - §82-13 «دعم» اتسجل لوحده قبل «سلفة ولا دعم؟».
 * الأرقام 0-9 بس (§79.1) — والأرقام نفسها جاية من `{0}`.
 */
internal val MSA_SMS_ROUND3_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_PASTE_DATE to "تاريخ الرسالة",
    UiKey.SMS_PASTE_TODAY to "اليوم؟",
    UiKey.SMS_PASTE_DATE_NEEDED to "اكتب تاريخ الرسالة أولًا",
    UiKey.SMS_PASTE_GOES_TO to "رسائل «{0}» تُسجَّل في «{1}»",
    UiKey.SMS_PASTE_OTHERS_WALLET to "والبنوك غير المربوطة — في أي محفظة؟",
    UiKey.SMS_HISTORY_TITLE to "نقرأ الرسائل السابقة؟",
    UiKey.SMS_HISTORY_BODY to "على جوالك رسائل بنك أقدم من الربط، أقدمها يوم {0}. نقرؤها كلها ونكمل الناقص؟ ما سُجّل من كشف الحساب يُدمج ولا يتكرر.",
    UiKey.SMS_HISTORY_YES to "نعم، اقرأها",
    UiKey.SMS_HISTORY_NO to "لا، من الآن فقط",
    UiKey.SMS_HISTORY_FROM_TITLE to "من أي تاريخ؟",
    UiKey.SMS_HISTORY_FROM_BODY to "أقدم رسالة يوم {0}، ورصيد البداية في «{1}» يوم {2}. ما قبل رصيد البداية لا يغيّر الرصيد.",
    UiKey.SMS_HISTORY_FROM_OPENING to "من رصيد البداية ({0})",
    UiKey.SMS_HISTORY_FROM_OLDEST to "من أقدم رسالة ({0})",
    UiKey.SMS_HISTORY_FROM_OTHER to "تاريخ آخر",
    UiKey.SMS_HISTORY_START to "اقرأ من {0}",
    UiKey.SMS_HISTORY_READING to "نقرأ الرسائل…",
    UiKey.SMS_HISTORY_DONE_TITLE to "قرأنا الرسائل من {0}",
    UiKey.SMS_HISTORY_COUNTS to "سُجّل أو دُمج: {0}، مكرر: {1}، بانتظارك: {2}",
    UiKey.SMS_HISTORY_SHAPES_TITLE to "أكّد رسالة واحدة من كل شكل",
    UiKey.SMS_HISTORY_SHAPES_BODY to "بعد التأكيد تُسجَّل الرسائل التي بنفس الأشكال وحدها ({0}).",
    UiKey.SMS_HISTORY_SHAPE_MORE to "+{0} مثلها",
    UiKey.SMS_RECORDED_MORE to "اعرض {0} أخرى",
    UiKey.SMS_HISTORY_CONFIRM to "أكّد الأشكال",
    UiKey.SMS_HISTORY_LEFT to "باقي {0} بانتظار تأكيدك في الأسفل.",
    UiKey.SMS_HISTORY_UNREAD to "{0} لم تُقرأ بعد، وستظهر بعد تأكيد ما سبقها.",
    UiKey.ASK_LEGACY_SUPPORT_BODY to "تحويل {0} إلى {1} سُجّل «دعم» تلقائيًا قبل أن نسألك. سلفة أم دعم؟",
)

internal val EGYPTIAN_SMS_ROUND3_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_PASTE_DATE to "تاريخ الرسالة",
    UiKey.SMS_PASTE_TODAY to "النهاردة؟",
    UiKey.SMS_PASTE_DATE_NEEDED to "اكتب تاريخ الرسالة الأول",
    UiKey.SMS_PASTE_GOES_TO to "رسايل «{0}» هتتسجل في «{1}»",
    UiKey.SMS_PASTE_OTHERS_WALLET to "والبنوك اللي مش مربوطة — في أنهي محفظة؟",
    UiKey.SMS_HISTORY_TITLE to "اقرأ اللي فات؟",
    UiKey.SMS_HISTORY_BODY to "على جوالك رسايل بنك أقدم من الربط، أقدمها يوم {0}. نقراها كلها ونكمّل الناقص؟ اللي متسجل من كشف الحساب بيتدمج وما بيتكررش.",
    UiKey.SMS_HISTORY_YES to "أيوه، اقراها",
    UiKey.SMS_HISTORY_NO to "لأ، من دلوقتي بس",
    UiKey.SMS_HISTORY_FROM_TITLE to "من إمتى؟",
    UiKey.SMS_HISTORY_FROM_BODY to "أقدم رسالة يوم {0}، ورصيد البداية في «{1}» يوم {2}. اللي قبل رصيد البداية ما بيغيّرش الرصيد.",
    UiKey.SMS_HISTORY_FROM_OPENING to "من رصيد البداية ({0})",
    UiKey.SMS_HISTORY_FROM_OLDEST to "من أقدم رسالة ({0})",
    UiKey.SMS_HISTORY_FROM_OTHER to "تاريخ تاني",
    UiKey.SMS_HISTORY_START to "اقرا من {0}",
    UiKey.SMS_HISTORY_READING to "بنقرا الرسايل…",
    UiKey.SMS_HISTORY_DONE_TITLE to "قرينا الرسايل من {0}",
    UiKey.SMS_HISTORY_COUNTS to "اتسجل أو اتدمج: {0}، مكرر: {1}، مستني تأكيدك: {2}",
    UiKey.SMS_HISTORY_SHAPES_TITLE to "أكّد رسالة واحدة من كل شكل",
    UiKey.SMS_HISTORY_SHAPES_BODY to "بعد ما تأكد، الرسايل اللي بنفس الأشكال بتتسجل لوحدها ({0}).",
    UiKey.SMS_HISTORY_SHAPE_MORE to "+{0} زيها",
    UiKey.SMS_RECORDED_MORE to "اعرض {0} كمان",
    UiKey.SMS_HISTORY_CONFIRM to "أكّد الأشكال",
    UiKey.SMS_HISTORY_LEFT to "باقي {0} مستنية تأكيدك تحت.",
    UiKey.SMS_HISTORY_UNREAD to "{0} لسه ما اتقريتش، وهتظهر لما تأكّد اللي قبلها.",
    UiKey.ASK_LEGACY_SUPPORT_BODY to "تحويل {0} لـ{1} اتسجل «دعم» لوحده قبل ما نسألك. سلفة ولا دعم؟",
)

internal val ENGLISH_SMS_ROUND3_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.SMS_PASTE_DATE to "Message date",
    UiKey.SMS_PASTE_TODAY to "Today?",
    UiKey.SMS_PASTE_DATE_NEEDED to "Enter the message date first",
    UiKey.SMS_PASTE_GOES_TO to "«{0}» messages go to «{1}»",
    UiKey.SMS_PASTE_OTHERS_WALLET to "And the banks with no wallet — which wallet?",
    UiKey.SMS_HISTORY_TITLE to "Read the earlier messages?",
    UiKey.SMS_HISTORY_BODY to "Your phone has bank messages from before the link, the oldest on {0}. Read them all and fill what is missing? What the statement already recorded is merged, never doubled.",
    UiKey.SMS_HISTORY_YES to "Yes, read them",
    UiKey.SMS_HISTORY_NO to "No, from now on",
    UiKey.SMS_HISTORY_FROM_TITLE to "From when?",
    UiKey.SMS_HISTORY_FROM_BODY to "The oldest message is on {0}, and the opening balance of «{1}» is on {2}. Anything before the opening balance does not change the balance.",
    UiKey.SMS_HISTORY_FROM_OPENING to "From the opening balance ({0})",
    UiKey.SMS_HISTORY_FROM_OLDEST to "From the oldest message ({0})",
    UiKey.SMS_HISTORY_FROM_OTHER to "Another date",
    UiKey.SMS_HISTORY_START to "Read from {0}",
    UiKey.SMS_HISTORY_READING to "Reading the messages…",
    UiKey.SMS_HISTORY_DONE_TITLE to "Read the messages since {0}",
    UiKey.SMS_HISTORY_COUNTS to "Recorded or merged: {0}, duplicates: {1}, waiting for you: {2}",
    UiKey.SMS_HISTORY_SHAPES_TITLE to "Confirm one message of each shape",
    UiKey.SMS_HISTORY_SHAPES_BODY to "After you confirm, the messages with the same shapes record themselves ({0}).",
    UiKey.SMS_HISTORY_SHAPE_MORE to "+{0} like it",
    UiKey.SMS_RECORDED_MORE to "Show {0} more",
    UiKey.SMS_HISTORY_CONFIRM to "Confirm the shapes",
    UiKey.SMS_HISTORY_LEFT to "{0} still wait for you below.",
    UiKey.SMS_HISTORY_UNREAD to "{0} not read yet — they show up once you confirm the ones before them.",
    UiKey.ASK_LEGACY_SUPPORT_BODY to "{0} to {1} was recorded as «support» automatically before we asked. Loan or support?",
)
