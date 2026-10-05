package app.masroufy.core

/**
 * نصوص مصادر الدخل (OVERRIDES §48 · §64) باللغتين — بيتدمجوا في `EGYPTIAN_TEXTS` و`ENGLISH_TEXTS`.
 * تنبيه المرتب المتأخر هنا كمان (مش في `TextsAlerts.kt`) عشان الشريحة تفضل في ملفها؛ **`ALERT_LOCK_INCOME_LATE` نص شاشة القفل:**
 * من غير اسم ولا رقم ولا عملة (`systemNoticeFor` بيتأكد، واختبار القفل بيمر على كل الأنواع باللغتين).
 * **مفيش أي كلام عند قفل مصدر** — مفيش مفتاح لده عن قصد (ممكن يكون اتفصل). الإنجليزي كتابة Claude ومستني مراجعة المالك (§40).
 */
internal val EGYPTIAN_INCOME_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.INCOME_SOURCE_NOT_FOUND to "مصدر الدخل مش موجود",
    TextKey.INCOME_SOURCE_ALREADY_CLOSED to "المصدر ده متقفل قبل كده",
    TextKey.INCOME_CHANGE_EMPTY to "اختار المصدر اللي خلص أو اكتب الشغل الجديد",
    TextKey.INCOME_CONGRATS to "مبروك الشغل الجديد في «{0}»!",
    TextKey.INCOME_Q_PAYDAY to "المرتب الجديد بينزل يوم كام في الشهر؟",
    TextKey.INCOME_Q_MONTH_START to "تغيّر بداية شهرك المالي ليوم {0}؟",
    TextKey.INCOME_Q_EXPECTED_SALARY to "المرتب المتوقع كام؟ (اختياري — تقدر تعدّيه)",
    TextKey.INCOME_Q_CAR_TO_WORK to "هتروح بيها الشغل؟",
    TextKey.INCOME_Q_PAYER to "ده مرتب من «{0}»؟",
    TextKey.INCOME_PAYER_NOT_ASKED to "السؤال ده اتجاوب قبل كده أو مابقاش له لازمة",
    TextKey.BACKUP_GROUP_INCOME_SOURCES to "مصادر الدخل",
    TextKey.ALERT_GROUP_INCOME to "الدخل",
    TextKey.ALERT_LOCK_INCOME_LATE to "فيه دخل متوقع لسه ما وصلش — التفاصيل جوه التطبيق",
    TextKey.ALERT_WHY_INCOME_LATE to "ما وصلش إيداع من المصدر ده لحد {0} أيام بعد ميعاده المعتاد",
    TextKey.ALERT_INCOME_LATE_TITLE to "لسه ما وصلش من «{0}»",
    TextKey.ALERT_INCOME_LATE_BODY to "ميعاده المعتاد {0} — ساعات بيتأخر، ومفيش حاجة مطلوبة منك",
    TextKey.INCOME_SOURCE_BAD_WEEKDAY to "يوم القبض في الأسبوع لازم يكون من 1 (الاتنين) لـ 7 (الحد)",
    TextKey.INCOME_SOURCE_FREQUENCY_MISMATCH to "القبض الشهري بيوم في الشهر والأسبوعي بيوم في الأسبوع",
    TextKey.INCOME_Q_PAY_FREQUENCY to "بتقبض من الشغل ده إمتى؟ كل شهر ولا كل أسبوع؟",
    TextKey.KIND_END_OF_SERVICE to "مكافأة نهاية خدمة",
    TextKey.INCOME_EOS_NOT_FROM_COMPANY to "الإيداع ده مش من شركة إنت أكدت إنها بتحوّلك المرتب",
    TextKey.INCOME_EOS_NEEDS_IN to "مكافأة نهاية الخدمة بتتعلّم على فلوس داخلة بس",
    TextKey.INCOME_EOS_NOT_MARKED to "الإيداع ده مش متعلّم مكافأة نهاية خدمة",
    TextKey.INCOME_EOS_TXN_NOT_FOUND to "العملية مش موجودة",
)

internal val ENGLISH_INCOME_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.INCOME_SOURCE_NOT_FOUND to "This income source was not found",
    TextKey.INCOME_SOURCE_ALREADY_CLOSED to "This source is already closed",
    TextKey.INCOME_CHANGE_EMPTY to "Pick the source that ended or enter the new job",
    TextKey.INCOME_CONGRATS to "Congratulations on the new job at “{0}”!",
    TextKey.INCOME_Q_PAYDAY to "On which day of the month does the new salary arrive?",
    TextKey.INCOME_Q_MONTH_START to "Move the start of your financial month to day {0}?",
    TextKey.INCOME_Q_EXPECTED_SALARY to "What salary do you expect? (optional — you can skip it)",
    TextKey.INCOME_Q_CAR_TO_WORK to "Will you drive it to work?",
    TextKey.INCOME_Q_PAYER to "Is this your salary from “{0}”?",
    TextKey.INCOME_PAYER_NOT_ASKED to "This question was already answered or is no longer needed",
    TextKey.BACKUP_GROUP_INCOME_SOURCES to "Income sources",
    TextKey.ALERT_GROUP_INCOME to "Income",
    TextKey.ALERT_LOCK_INCOME_LATE to "Some expected income has not arrived yet — details inside the app",
    TextKey.ALERT_WHY_INCOME_LATE to "No deposit from this source within {0} days after its usual date",
    TextKey.ALERT_INCOME_LATE_TITLE to "Nothing yet from “{0}”",
    TextKey.ALERT_INCOME_LATE_BODY to "Its usual date was {0} — it is sometimes late, and nothing is needed from you",
    TextKey.INCOME_SOURCE_BAD_WEEKDAY to "The weekly pay day has to be from 1 (Monday) to 7 (Sunday)",
    TextKey.INCOME_SOURCE_FREQUENCY_MISMATCH to "Monthly pay takes a day of the month and weekly pay a day of the week",
    TextKey.INCOME_Q_PAY_FREQUENCY to "When are you paid for this work? Every month or every week?",
    TextKey.KIND_END_OF_SERVICE to "End-of-service benefit",
    TextKey.INCOME_EOS_NOT_FROM_COMPANY to "This deposit is not from a company you confirmed pays your salary",
    TextKey.INCOME_EOS_NEEDS_IN to "An end-of-service benefit can only be marked on incoming money",
    TextKey.INCOME_EOS_NOT_MARKED to "This deposit is not marked as an end-of-service benefit",
    TextKey.INCOME_EOS_TXN_NOT_FOUND to "The transaction was not found",
)

/** مصادر الدخل بالفصحى المختصرة (OVERRIDES §66). `ALERT_LOCK_INCOME_LATE` بلا اسم ولا رقم ولا عملة. */
internal val MSA_INCOME_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.INCOME_SOURCE_NOT_FOUND to "مصدر الدخل غير موجود",
    TextKey.INCOME_SOURCE_ALREADY_CLOSED to "هذا المصدر مغلق سابقًا",
    TextKey.INCOME_CHANGE_EMPTY to "اختر المصدر المنتهي أو أدخل العمل الجديد",
    TextKey.INCOME_CONGRATS to "مبارك العمل الجديد في «{0}»!",
    TextKey.INCOME_Q_PAYDAY to "في أي يوم من الشهر ينزل الراتب الجديد؟",
    TextKey.INCOME_Q_MONTH_START to "أتغيّر بداية شهرك المالي إلى يوم {0}؟",
    TextKey.INCOME_Q_EXPECTED_SALARY to "كم الراتب المتوقع؟ (اختياري — يمكنك تخطيه)",
    TextKey.INCOME_Q_CAR_TO_WORK to "أتذهب بها إلى العمل؟",
    TextKey.INCOME_Q_PAYER to "أهذا راتب من «{0}»؟",
    TextKey.INCOME_PAYER_NOT_ASKED to "أُجيب هذا السؤال سابقًا أو لم تعد له حاجة",
    TextKey.BACKUP_GROUP_INCOME_SOURCES to "مصادر الدخل",
    TextKey.ALERT_GROUP_INCOME to "الدخل",
    TextKey.ALERT_LOCK_INCOME_LATE to "دخل متوقع لم يصل بعد — التفاصيل داخل التطبيق",
    TextKey.ALERT_WHY_INCOME_LATE to "لم يصل إيداع من هذا المصدر بعد {0} أيام من موعده المعتاد",
    TextKey.ALERT_INCOME_LATE_TITLE to "لم يصل بعد من «{0}»",
    TextKey.ALERT_INCOME_LATE_BODY to "موعده المعتاد {0} — قد يتأخر أحيانًا، ولا مطلوب منك شيء",
    TextKey.INCOME_SOURCE_BAD_WEEKDAY to "يوم القبض في الأسبوع من 1 (الاثنين) إلى 7 (الأحد)",
    TextKey.INCOME_SOURCE_FREQUENCY_MISMATCH to "القبض الشهري بيوم من الشهر، والأسبوعي بيوم من الأسبوع",
    TextKey.INCOME_Q_PAY_FREQUENCY to "متى تقبض من هذا العمل؟ شهريًا أم أسبوعيًا؟",
    TextKey.KIND_END_OF_SERVICE to "مكافأة نهاية الخدمة",
    TextKey.INCOME_EOS_NOT_FROM_COMPANY to "هذا الإيداع ليس من شركة أكّدت أنها تحوّل لك الراتب",
    TextKey.INCOME_EOS_NEEDS_IN to "مكافأة نهاية الخدمة تُعلَّم على مبلغ وارد فقط",
    TextKey.INCOME_EOS_NOT_MARKED to "هذا الإيداع غير معلَّم مكافأة نهاية خدمة",
    TextKey.INCOME_EOS_TXN_NOT_FOUND to "العملية غير موجودة",
)
