package app.masroufy.core

/**
 * §82-1 (جولة 0.3): كارت «من إمتى لإمتى؟» في المساعد — «صرفت كام في رمضان؟» (أو أي مناسبة بالاسم) بيسأل عن أول وآخر يوم. الفصحى · المصري ·
 * الإنجليزي (كتابة Claude — مستني مراجعة المالك §40). بتتضاف لجداول [AskKey] في `TextsAsk.kt`. الأرقام 0-9 بس (§79.1).
 */
internal val MSA_ASK_PERIOD_TEXTS: Map<AskKey, String> = mapOf(
    AskKey.CHAT_PERIOD_ASK to "متى كان {0}؟ اختر أول يوم وآخر يوم، وأحسبها لك.",
    AskKey.CHAT_PERIOD_FIRST to "أول يوم",
    AskKey.CHAT_PERIOD_LAST to "آخر يوم",
    AskKey.CHAT_PERIOD_GO to "احسب",
    AskKey.CHAT_PERIOD_BAD to "اختر اليومين أولًا.",
    AskKey.CHAT_PERIOD_ORDER to "آخر يوم يجب أن يكون بعد أول يوم أو هو نفسه.",
    AskKey.CHAT_PERIOD_FUTURE to "اختر أيامًا حتى اليوم — لا أرقام لما لم يحدث بعد.",
    AskKey.CHAT_PERIOD_BETWEEN to "من {0} إلى {1}",
    AskKey.CHAT_PERIOD_NAMED to "في {0} (من {1} إلى {2})",
    AskKey.CHAT_PERIOD_PICKED to "من {0} إلى {1}",
)

internal val EGYPTIAN_ASK_PERIOD_TEXTS: Map<AskKey, String> = mapOf(
    AskKey.CHAT_PERIOD_ASK to "{0} كان من إمتى لإمتى؟ اختار أول يوم وآخر يوم وأنا أحسبهالك.",
    AskKey.CHAT_PERIOD_FIRST to "أول يوم",
    AskKey.CHAT_PERIOD_LAST to "آخر يوم",
    AskKey.CHAT_PERIOD_GO to "احسب",
    AskKey.CHAT_PERIOD_BAD to "اختار اليومين الأول.",
    AskKey.CHAT_PERIOD_ORDER to "آخر يوم لازم يبقى بعد أول يوم أو هو نفسه.",
    AskKey.CHAT_PERIOD_FUTURE to "اختار أيام لحد النهارده — مفيش أرقام لحاجة لسه ما حصلتش.",
    AskKey.CHAT_PERIOD_BETWEEN to "من {0} لـ{1}",
    AskKey.CHAT_PERIOD_NAMED to "في {0} (من {1} لـ{2})",
    AskKey.CHAT_PERIOD_PICKED to "من {0} لـ{1}",
)

internal val ENGLISH_ASK_PERIOD_TEXTS: Map<AskKey, String> = mapOf(
    AskKey.CHAT_PERIOD_ASK to "When was {0}? Pick the first and last day and I'll work it out.",
    AskKey.CHAT_PERIOD_FIRST to "First day",
    AskKey.CHAT_PERIOD_LAST to "Last day",
    AskKey.CHAT_PERIOD_GO to "Calculate",
    AskKey.CHAT_PERIOD_BAD to "Pick both days first.",
    AskKey.CHAT_PERIOD_ORDER to "The last day must be on or after the first day.",
    AskKey.CHAT_PERIOD_FUTURE to "Pick days up to today — there are no numbers for what hasn't happened.",
    AskKey.CHAT_PERIOD_BETWEEN to "from {0} to {1}",
    AskKey.CHAT_PERIOD_NAMED to "in {0} ({1} to {2})",
    AskKey.CHAT_PERIOD_PICKED to "{0} to {1}",
)
