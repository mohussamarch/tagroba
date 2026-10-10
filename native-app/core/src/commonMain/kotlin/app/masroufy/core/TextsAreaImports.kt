package app.masroufy.core

/**
 * نصوص شاشات منطقة «الاستيراد» (`ui/screens/imports/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة الاستيراد»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». الجداول في خمس ملفات (كل واحد تحت ٣٠٠ سطر):
 * ١ `TextsAreaImportsSms` (المشترك · `BankSms` · `SmsWaiting` · `CategoryPicker`) · ٢ `TextsAreaImportsSettings` (`BankSmsSettings` · `SmsPermission` ·
 * `SmsPaste`) · ٣ `TextsAreaImportsStatement` (`StatementImport` · `StatementColumns`) · ٤ `TextsAreaImportsReview` (`ImportReview` ·
 * `ImportDuplicateSheet`) · ٥ `TextsAreaImportsBatches` (`ImportBatches` · `RevertBatchSheet`).
 */
internal val MSA_AREA_IMPORTS_TEXTS: Map<UiKey, String> =
    MSA_IMPORTS_SMS_TEXTS + MSA_IMPORTS_SETTINGS_TEXTS + MSA_IMPORTS_STATEMENT_TEXTS + MSA_IMPORTS_REVIEW_TEXTS + MSA_IMPORTS_BATCHES_TEXTS

internal val EGYPTIAN_AREA_IMPORTS_TEXTS: Map<UiKey, String> =
    EGYPTIAN_IMPORTS_SMS_TEXTS + EGYPTIAN_IMPORTS_SETTINGS_TEXTS + EGYPTIAN_IMPORTS_STATEMENT_TEXTS + EGYPTIAN_IMPORTS_REVIEW_TEXTS +
        EGYPTIAN_IMPORTS_BATCHES_TEXTS

internal val ENGLISH_AREA_IMPORTS_TEXTS: Map<UiKey, String> =
    ENGLISH_IMPORTS_SMS_TEXTS + ENGLISH_IMPORTS_SETTINGS_TEXTS + ENGLISH_IMPORTS_STATEMENT_TEXTS + ENGLISH_IMPORTS_REVIEW_TEXTS +
        ENGLISH_IMPORTS_BATCHES_TEXTS
