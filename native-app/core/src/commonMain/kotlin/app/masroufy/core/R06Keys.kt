package app.masroufy.core

/**
 * r06 (PLAN-0.6) — مفاتيح الجولة (القيم في `TextsR06.kt`). نوع لوحده تحت [TextRef] لأن [UiKey] وصل حد الـJVM («Method too large» في بداية الـenum) —
 * زي [BnplKey] و[AskKey] (ARCHITECTURE §31.34). الأسامي لازم تفضل فريدة بين الأنواع (`textKeyNamed`).
 */
enum class R06Key : TextRef {
    /** «سجل المشاكل»: العامل في الخلفية اتخلّى عنه لأن الجلسة ما جهزتش (مستخدم داخل). */
    PLOG_C_BACKGROUND_GAVE_UP,

    /** PLAN §7 — «الفلوس دي إيه؟» على تحويل من حساب جهة الشغل مش راتب: الردود · القاعدة · «استرداد مصاريف» و«مستني استرداد». */
    PAYER_ASK_TITLE, PAYER_ASK_REFUND, PAYER_ASK_BONUS, PAYER_ASK_ADVANCE, PAYER_ASK_OTHER, PAYER_ASK_SALARY, PAYER_RULE_SAVED,
    PAYER_RULE_LINE, PAYER_RULE_FORGET, REFUND_PICK_TITLE, REFUND_PICK_EMPTY, REFUND_LINKED, AWAIT_REFUND_MARK, AWAIT_REFUND_ON, AWAIT_REFUND_OFF,

    /** PLAN §8 — «مجموع أكتر من قسط» في لوحة «ادفع بعدين» وصفحة التوزيع. */
    BNPL_MULTI, BNPL_MULTI_TITLE, BNPL_MULTI_BODY, BNPL_MULTI_DIFF, BNPL_MULTI_SAVE, BNPL_MULTI_NEW, BNPL_MULTI_DONE,
    BNPL_MULTI_NEW_WHAT, BNPL_MULTI_NEW_COUNT, BNPL_MULTI_AMOUNT, BNPL_MULTI_EMPTY, BNPL_MULTI_ERR_SUM, BNPL_MULTI_ERR_PARTS,

    /** «رسائل البنك» — سطر «آخر مرة قرا لوحده». */
    BANK_SMS_LAST_READ, BANK_SMS_NEVER_READ, BANK_SMS_AGO_NOW, BANK_SMS_AGO_MIN, BANK_SMS_AGO_HOUR,

    /** اختيار ملف النسخة: كبير أوي · مش ملف نسخة. */
    BAK_PICK_TOO_BIG, BAK_PICK_NOT_BACKUP,

    /** علامة التحميل «عملات بتترص» — وصف للقارئ الصوتي · «بنحدّث…» جنب علامة السحب · «بيتحفظ…» جوه الزرار الأساسي. */
    LOADER_A11Y, LOADER_UPDATING, LOADER_SAVING,

    /** شاشة التحميل «بنجهّز فلوسك…» · السحب للتحديث: قبل الحد · عند الحد · بعد ما خلص. */
    LOADER_PREPARING, LOADER_PULL, LOADER_RELEASE, LOADER_DONE,
}
