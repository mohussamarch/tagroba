package app.masroufy.core

/**
 * نصوص شاشات منطقة «المستحقات» (`ui/screens/dues/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة المستحقات»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». القيم في جزأين عشان حد الـ300 سطر:
 * - `TextsAreaDuesDebts*.kt`: خانة المستحقات · الديون · تفاصيل الدين · السداد · الدين القديم
 * - `TextsAreaDuesPlans*.kt`: الأقساط · الجمعيات ومعالجها · الاشتراكات
 */
internal val MSA_AREA_DUES_TEXTS: Map<UiKey, String> = MSA_DUES_DEBTS_TEXTS + MSA_DUES_PLANS_TEXTS

internal val EGYPTIAN_AREA_DUES_TEXTS: Map<UiKey, String> = EGYPTIAN_DUES_DEBTS_TEXTS + EGYPTIAN_DUES_PLANS_TEXTS

internal val ENGLISH_AREA_DUES_TEXTS: Map<UiKey, String> = ENGLISH_DUES_DEBTS_TEXTS + ENGLISH_DUES_PLANS_TEXTS
