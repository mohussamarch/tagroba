package app.masroufy.core

/**
 * r06 (PLAN-0.6) — مفاتيح الجولة (القيم في `TextsR06.kt`). نوع لوحده تحت [TextRef] لأن [UiKey] وصل حد الـJVM («Method too large» في بداية الـenum) —
 * زي [BnplKey] و[AskKey] (ARCHITECTURE §31.34). الأسامي لازم تفضل فريدة بين الأنواع (`textKeyNamed`).
 */
enum class R06Key : TextRef {
    /** «سجل المشاكل»: العامل في الخلفية اتخلّى عنه لأن الجلسة ما جهزتش (مستخدم داخل). */
    PLOG_C_BACKGROUND_GAVE_UP,

    /** «رسائل البنك» — سطر «آخر مرة قرا لوحده». */
    BANK_SMS_LAST_READ, BANK_SMS_NEVER_READ, BANK_SMS_AGO_NOW, BANK_SMS_AGO_MIN, BANK_SMS_AGO_HOUR,

    /** اختيار ملف النسخة: كبير أوي · مش ملف نسخة. */
    BAK_PICK_TOO_BIG, BAK_PICK_NOT_BACKUP,

    /** علامة التحميل «عملات بتترص» — وصف للقارئ الصوتي · «بنحدّث…» جنب علامة السحب · «بيتحفظ…» جوه الزرار الأساسي. */
    LOADER_A11Y, LOADER_UPDATING, LOADER_SAVING,

    /** شاشة التحميل «بنجهّز فلوسك…» · السحب للتحديث: قبل الحد · عند الحد · بعد ما خلص. */
    LOADER_PREPARING, LOADER_PULL, LOADER_RELEASE, LOADER_DONE,
}
