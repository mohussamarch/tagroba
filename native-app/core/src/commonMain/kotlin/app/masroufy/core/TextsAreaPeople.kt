package app.masroufy.core

/**
 * نصوص شاشات منطقة «الأشخاص» (`ui/screens/people/`) — **الملف ده بتاع المنطقة بس** (المناطق بتتبني بالتوازي، ARCHITECTURE §31.31).
 * المفاتيح في `TextKeys.kt` **تحت سطر «منطقة الأشخاص»** بس. الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66) · الإنجليزي
 * (كتابة Claude — مستني مراجعة المالك §40). النص من النموذج التفاعلي بالحرف (الفصحى من `space = السعودية` والمصري من `مصر`).
 * **ممنوع «·» جنب رقم عربي** ⇒ «،». القيم مقسومة على ملفات تحت ٣٠٠ سطر:
 * `TextsAreaPeopleMsa1/2` (الفصحى) · `TextsAreaPeopleEgy1/2` (المصري) · `TextsAreaPeopleEng1/2` (الإنجليزي) —
 * ١ = العام والأشخاص و«لك/عليك» وملف الشخص والإضافة والديون القديمة · ٢ = المناسبات والأحداث والتجهيزات والنقوط والمشاريع.
 */
internal val MSA_AREA_PEOPLE_TEXTS: Map<TextKey, String> = MSA_PEOPLE_AREA_1 + MSA_PEOPLE_AREA_2

internal val EGYPTIAN_AREA_PEOPLE_TEXTS: Map<TextKey, String> = EGYPTIAN_PEOPLE_AREA_1 + EGYPTIAN_PEOPLE_AREA_2

internal val ENGLISH_AREA_PEOPLE_TEXTS: Map<TextKey, String> = ENGLISH_PEOPLE_AREA_1 + ENGLISH_PEOPLE_AREA_2
