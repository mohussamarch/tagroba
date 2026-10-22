package app.masroufy.core

/**
 * r06-1a3 — كارت «فيه رسايل فاتته» (مفاتيح AWAY_ في [R06Key]). ملف لوحده عشان `TextsR06.kt` عدّى حد الـ300 سطر.
 * قاعدة الكتابة (المالك 2026-10-22): الحالة كلمة أو اتنين · الشرح سطر واحد لو لازم. {0} = «3 رسائل» (جاهزة بالجمع).
 */
internal val MSA_R06_AWAY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.AWAY_TITLE to "رسائل فاتت القراءة",
    R06Key.AWAY_BODY to "وصلت {0} من البنك ولم تُقرأ تلقائيًا. على الأرجح أوقف الجوال مصروفي لتوفير البطارية.",
    R06Key.AWAY_READ to "اقرأها الآن",
    R06Key.AWAY_READING to "جارٍ القراءة…",
    R06Key.AWAY_KEEP to "اسمح بالعمل في الخلفية",
    R06Key.AWAY_DONE to "قُرئت {0} وسُجّلت",
    R06Key.AWAY_DONE_WAIT to "قُرئت — بانتظار تأكيدك",
    R06Key.AWAY_NEVER_BODY to "أول رسالة من البنك ستُقرأ وتُسجَّل تلقائيًا.",
)

internal val EGYPTIAN_R06_AWAY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.AWAY_TITLE to "فيه رسايل فاتته",
    R06Key.AWAY_BODY to "{0} من البنك وصلت وما اتقرتش لوحدها. الجوال غالبًا موقّف مصروفي عشان يوفّر البطارية.",
    R06Key.AWAY_READ to "اقراهم دلوقتي",
    R06Key.AWAY_READING to "بيقرا…",
    R06Key.AWAY_KEEP to "خلّيه يشتغل في الخلفية",
    R06Key.AWAY_DONE to "اتقرت {0} واتسجلت",
    R06Key.AWAY_DONE_WAIT to "اتقرت — مستنية تأكيدك",
    R06Key.AWAY_NEVER_BODY to "أول رسالة من البنك توصل هتتقرا وتتسجل لوحدها.",
)

internal val ENGLISH_R06_AWAY_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.AWAY_TITLE to "Missed messages",
    R06Key.AWAY_BODY to "{0} from the bank were not read automatically. The phone probably stopped Masroufy to save battery.",
    R06Key.AWAY_READ to "Read them now",
    R06Key.AWAY_READING to "Reading…",
    R06Key.AWAY_KEEP to "Let it run in background",
    R06Key.AWAY_DONE to "Read and recorded {0}",
    R06Key.AWAY_DONE_WAIT to "Read — waiting for you",
    R06Key.AWAY_NEVER_BODY to "The next bank message will be read and recorded automatically.",
)
