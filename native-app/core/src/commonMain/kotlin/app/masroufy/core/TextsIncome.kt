package app.masroufy.core

/**
 * نصوص مصادر الدخل (OVERRIDES §48 · §64) باللغتين — بيتدمجوا في `ARABIC_TEXTS` و`ENGLISH_TEXTS`.
 * تنبيه المرتب المتأخر هنا كمان (مش في `TextsAlerts.kt`) عشان الشريحة تفضل في ملفها؛ **`ALERT_LOCK_INCOME_LATE` نص شاشة القفل:**
 * من غير اسم ولا رقم ولا عملة (`systemNoticeFor` بيتأكد، واختبار القفل بيمر على كل الأنواع باللغتين).
 * **مفيش أي كلام عند قفل مصدر** — مفيش مفتاح لده عن قصد (ممكن يكون اتفصل). الإنجليزي كتابة Claude ومستني مراجعة المالك (§40).
 */
internal val ARABIC_INCOME_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.INCOME_SOURCE_NOT_FOUND to "مصدر الدخل مش موجود",
    TextKey.INCOME_SOURCE_ALREADY_CLOSED to "المصدر ده متقفل قبل كده",
    TextKey.INCOME_CHANGE_EMPTY to "اختار المصدر اللي خلص أو اكتب الشغل الجديد",
    TextKey.INCOME_CONGRATS to "مبروك الشغل الجديد في «{0}»!",
    TextKey.INCOME_Q_END_OF_SERVICE to "فيه مكافأة نهاية خدمة جاية؟",
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
)

internal val ENGLISH_INCOME_TEXTS: Map<TextKey, String> = mapOf(
    TextKey.INCOME_SOURCE_NOT_FOUND to "This income source was not found",
    TextKey.INCOME_SOURCE_ALREADY_CLOSED to "This source is already closed",
    TextKey.INCOME_CHANGE_EMPTY to "Pick the source that ended or enter the new job",
    TextKey.INCOME_CONGRATS to "Congratulations on the new job at “{0}”!",
    TextKey.INCOME_Q_END_OF_SERVICE to "Is an end-of-service benefit coming?",
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
)
