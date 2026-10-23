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

    /** r06-1b3 — كارت «الفلوس دي إيه؟» (MW_ — التصميم `MoneyWhat`) و«مستني استرداد» على المشترى (RW_ — `RefundWait`). */
    MW_CHIP, MW_LINE, MW_WHY, MW_WHY_AMOUNT, MW_WHY_DATE, MW_HINT_REFUND, MW_HINT_BONUS, MW_HINT_LOAN, MW_HINT_OTHER, MW_SAVED_FOR, MW_REFUND_ASK, MW_WAITING, MW_SUM_EXACT, MW_SUM_LEFT, MW_SUM_OVER, MW_REFUND_EMPTY, MW_OTHER_ASK, MW_OTHER_PH, MW_EMOJI_ASK, MW_EFF_REFUND, MW_EFF_BONUS, MW_EFF_LOAN, MW_EFF_OTHER, MW_EFF_OTHER_EMPTY, MW_EFF_CUSTOM, MW_RULE_TITLE, MW_RULE_OFF, MW_RULE_ON, MW_RULE_ON_REFUND, MW_BACK, MW_SAVE, MW_LINK_SAVE, MW_DONE, MW_DONE_CUSTOM, MW_DONE_RULE, MW_DONE_ASK, RW_OFF_TITLE, RW_OFF_HINT, RW_REMOVE, RW_BODY, RW_BACK_TITLE, RW_BACK_BODY, RW_BACK_PART,

    /** PLAN §8 — «مجموع أكتر من قسط» في لوحة «ادفع بعدين» وصفحة التوزيع. */
    BNPL_MULTI, BNPL_MULTI_TITLE, BNPL_MULTI_BODY, BNPL_MULTI_DIFF, BNPL_MULTI_SAVE, BNPL_MULTI_NEW, BNPL_MULTI_DONE,
    BNPL_MULTI_NEW_WHAT, BNPL_MULTI_NEW_COUNT, BNPL_MULTI_AMOUNT, BNPL_MULTI_EMPTY, BNPL_MULTI_ERR_SUM, BNPL_MULTI_ERR_PARTS,

    /** «رسائل البنك» — سطر «آخر مرة قرا لوحده». */
    BANK_SMS_LAST_READ, BANK_SMS_NEVER_READ, BANK_SMS_AGO_NOW, BANK_SMS_AGO_MIN, BANK_SMS_AGO_HOUR,
    /** 0.5.2: الجمع بالعربي (1 · 2 · 3-10 · 11+) — [BANK_SMS_AGO_MIN]/[BANK_SMS_AGO_HOUR] = 11 وأكتر. */
    BANK_SMS_AGO_MIN_ONE, BANK_SMS_AGO_MIN_TWO, BANK_SMS_AGO_MIN_FEW, BANK_SMS_AGO_HOUR_ONE, BANK_SMS_AGO_HOUR_TWO, BANK_SMS_AGO_HOUR_FEW,

    /** اختيار ملف النسخة: كبير أوي · مش ملف نسخة. */
    BAK_PICK_TOO_BIG, BAK_PICK_NOT_BACKUP,

    /** علامة التحميل «عملات بتترص» — وصف للقارئ الصوتي · «بنحدّث…» جنب علامة السحب · «بيتحفظ…» جوه الزرار الأساسي. */
    LOADER_A11Y, LOADER_UPDATING, LOADER_SAVING,

    /** شاشة التحميل «بنجهّز فلوسك…» · السحب للتحديث: قبل الحد · عند الحد · بعد ما خلص. */
    LOADER_PREPARING, LOADER_PULL, LOADER_RELEASE, LOADER_DONE,

    /** PLAN §12 (دراهم) — الوجبة · «برّه الميزانية» و«برّه التقارير» · سؤال «مكرر؟». */
    MEAL_TITLE, MEAL_BREAKFAST, MEAL_LUNCH, MEAL_DINNER, MEAL_SUGGESTED, MEAL_CLEAR,
    EXCLUDE_TITLE, EXCLUDE_BUDGET, EXCLUDE_REPORTS,
    DUP_ASK_TITLE, DUP_ASK_BODY, DUP_ASK_REMOVE, DUP_ASK_KEEP, DUP_ASK_REMOVED, DUP_ASK_KEPT,
    /** r06-1b4 — خصم تابي/تمارا المجمّع (`BnplSplit`) و«الدفعة دي رقم كام؟» (BS_). */
    BS_CHOICE, BS_CHOICE_SUB, BS_HINT, BS_TITLE, BS_LEAD, BS_SUGG, BS_PLAN_SUB, BS_INST, BS_NEW_TITLE, BS_NEW_PH, BS_PAY, BS_DIVIDE, BS_AT,
    BS_REMOVE, BS_SUM_EXACT, BS_SUM_LEFT, BS_SUM_OVER, BS_SAVE, BS_BACK, BS_DONE, BS_DONE_NEW, BS_FINISH, BS_AT_Q, BS_TAG_BEFORE, BS_BEFORE_NOTE,

    /** r06-1a3 — «القراية في الخلفية»: كارت «فيه رسايل فاتته» (AWAY_) — نصوصه في `TextsR06Away.kt`. */
    AWAY_TITLE, AWAY_BODY, AWAY_READ, AWAY_READING, AWAY_KEEP, AWAY_DONE, AWAY_DONE_WAIT, AWAY_NEVER_BODY, AWAY_MORE,
    /** المظهر الليلي (فاتح · غامق · زي الجوال) وزرار إخفاء الأرقام — NP_ (التصميم NightPrivacy). */
    NP_THEME_TITLE, NP_THEME_LIGHT, NP_THEME_DARK, NP_THEME_AUTO, NP_EYE_HIDE, NP_EYE_SHOW, NP_START_TITLE, NP_START_SUB,
    /** PLAN-0.6 §13 (ج) — إشعارات حالة القراية في الخلفية (شاشة القفل: من غير أرقام ولا أسامي) — نصوصها في `TextsR06Notify.kt`. */
    NOTIFY_SMS_NEW_SHAPE, NOTIFY_SMS_PERMISSION_OFF, NOTIFY_SMS_MISSED,
}
