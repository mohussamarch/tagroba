package app.masroufy.core

/** r06 — نصوص [R06Key]: الفصحى (السعودية والافتراضي) · المصري · الإنجليزي. الأرقام 0-9 بس (`WesternDigitsTest`). */
internal val MSA_R06_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.PLOG_C_BACKGROUND_GAVE_UP to "تعذّر بدء العمل في الخلفية لأن الجلسة لم تجهز في الوقت المحدد",
    R06Key.BANK_SMS_LAST_READ to "آخر قراءة تلقائية: {0}",
    R06Key.BANK_SMS_NEVER_READ to "لم تُقرأ الرسائل تلقائيًا بعد",
    R06Key.BANK_SMS_AGO_NOW to "الآن",
    R06Key.BANK_SMS_AGO_MIN to "منذ {0} دقيقة",
    R06Key.BANK_SMS_AGO_HOUR to "منذ {0} ساعة",
    R06Key.BAK_PICK_TOO_BIG to "الملف أكبر من 50 ميجابايت، وهذا أكبر من أي نسخة احتياطية من مصروفي. اختر ملف النسخة الصحيح.",
    R06Key.BAK_PICK_NOT_BACKUP to "هذا ليس ملف نسخة احتياطية. اختر ملفًا ينتهي بـ json.",
    R06Key.LOADER_A11Y to "جارٍ التحميل",
    R06Key.LOADER_UPDATING to "جارٍ التحديث…",
    R06Key.LOADER_SAVING to "جارٍ الحفظ…",
)

internal val EGYPTIAN_R06_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.PLOG_C_BACKGROUND_GAVE_UP to "الشغل في الخلفية ما بدأش لأن الجلسة ما جهزتش في الوقت",
    R06Key.BANK_SMS_LAST_READ to "آخر مرة قرا لوحده: {0}",
    R06Key.BANK_SMS_NEVER_READ to "لسه ما قراش لوحده",
    R06Key.BANK_SMS_AGO_NOW to "دلوقتي",
    R06Key.BANK_SMS_AGO_MIN to "من {0} دقيقة",
    R06Key.BANK_SMS_AGO_HOUR to "من {0} ساعة",
    R06Key.BAK_PICK_TOO_BIG to "الملف أكبر من 50 ميجا، وده أكبر من أي نسخة احتياطية من مصروفي. اختار ملف النسخة الصح.",
    R06Key.BAK_PICK_NOT_BACKUP to "ده مش ملف نسخة احتياطية. اختار ملف آخره json.",
    R06Key.LOADER_A11Y to "بيحمّل",
    R06Key.LOADER_UPDATING to "بنحدّث…",
    R06Key.LOADER_SAVING to "بيتحفظ…",
)

internal val ENGLISH_R06_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.PLOG_C_BACKGROUND_GAVE_UP to "Background work did not start: the session was not ready in time",
    R06Key.BANK_SMS_LAST_READ to "Last automatic read: {0}",
    R06Key.BANK_SMS_NEVER_READ to "Messages have not been read automatically yet",
    R06Key.BANK_SMS_AGO_NOW to "just now",
    R06Key.BANK_SMS_AGO_MIN to "{0} min ago",
    R06Key.BANK_SMS_AGO_HOUR to "{0} h ago",
    R06Key.BAK_PICK_TOO_BIG to "That file is over 50 MB, far bigger than any Masroufy backup. Pick the right backup file.",
    R06Key.BAK_PICK_NOT_BACKUP to "That is not a backup file. Pick a file ending in json.",
    R06Key.LOADER_A11Y to "Loading",
    R06Key.LOADER_UPDATING to "Updating…",
    R06Key.LOADER_SAVING to "Saving…",
)
