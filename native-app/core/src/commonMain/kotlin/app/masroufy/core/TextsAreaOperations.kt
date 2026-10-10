package app.masroufy.core

/**
 * نصوص شاشات منطقة «العمليات» (`ui/screens/operations/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة العمليات»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». القيم نفسها في ٩ ملفات (`TextsAreaOperations{Msa,Egyptian,English}{,Links,Transfers}.kt` — حد الـ300 سطر)
 * متضمومة هنا بـ`+`: القايمة والتفاصيل والتصفية والوسوم · الربط بشخص أو مشروع أو حدث وصفحة التاجر · التحويلات و«من هذا؟» والتحويل بين البلدين.
 */
internal val MSA_AREA_OPERATIONS_TEXTS: Map<UiKey, String> =
    MSA_OPERATIONS_LIST_TEXTS + MSA_OPERATIONS_LINK_TEXTS + MSA_OPERATIONS_TRANSFER_TEXTS

internal val EGYPTIAN_AREA_OPERATIONS_TEXTS: Map<UiKey, String> =
    EGYPTIAN_OPERATIONS_LIST_TEXTS + EGYPTIAN_OPERATIONS_LINK_TEXTS + EGYPTIAN_OPERATIONS_TRANSFER_TEXTS

internal val ENGLISH_AREA_OPERATIONS_TEXTS: Map<UiKey, String> =
    ENGLISH_OPERATIONS_LIST_TEXTS + ENGLISH_OPERATIONS_LINK_TEXTS + ENGLISH_OPERATIONS_TRANSFER_TEXTS
