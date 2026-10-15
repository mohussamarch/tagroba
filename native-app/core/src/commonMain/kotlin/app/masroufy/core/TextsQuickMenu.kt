package app.masroufy.core

/**
 * القايمة السريعة بالضغطة الطويلة على أي عنصر (قايمة المالك §6.5-13 — نفس قايمة «العمليات»): أسامي الأفعال بس.
 * الفصحى للسعودية والافتراضي · المصري لمصر (OVERRIDES §66 — مفيش كلمة مصري في الفصحى) · الإنجليزي (كتابة Claude — مستني مراجعة المالك §40).
 * مضمومة في `TextsAreaOperations.kt` (القايمة طالعة من «العمليات»).
 */
internal val MSA_QUICK_MENU_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.QM_OPEN to "عرض التفاصيل",
    UiKey.QM_OPEN_SECTION to "فتح القسم",
    UiKey.QM_EDIT to "تعديل",
    UiKey.QM_SETTLE to "تسجيل سداد أو تحصيل",
    UiKey.QM_PERSON to "فتح ملف الشخص",
    UiKey.QM_ADD_OCCASION to "إضافة مناسبة",
    UiKey.QM_PREP to "التجهيزات",
    UiKey.QM_ARCHIVE to "أرشفة",
    UiKey.QM_UNARCHIVE to "إلغاء الأرشفة",
    UiKey.QM_SHOW_DAY to "عرض مواعيد اليوم",
    UiKey.QM_ADD_EVENT_DAY to "حدث جديد في هذا اليوم",
    UiKey.QM_MARK_READ to "تعليم كمقروء",
    UiKey.QM_DELETE to "حذف",
)

internal val EGYPTIAN_QUICK_MENU_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.QM_OPEN to "شوف التفاصيل",
    UiKey.QM_OPEN_SECTION to "افتح القسم",
    UiKey.QM_EDIT to "عدّل",
    UiKey.QM_SETTLE to "سجّل سداد أو تحصيل",
    UiKey.QM_PERSON to "افتح ملف الشخص",
    UiKey.QM_ADD_OCCASION to "ضيف مناسبة",
    UiKey.QM_PREP to "التجهيزات",
    UiKey.QM_ARCHIVE to "أرشفه",
    UiKey.QM_UNARCHIVE to "رجّعه من الأرشيف",
    UiKey.QM_SHOW_DAY to "شوف مواعيد اليوم",
    UiKey.QM_ADD_EVENT_DAY to "حدث جديد في اليوم ده",
    UiKey.QM_MARK_READ to "علّمه مقروء",
    UiKey.QM_DELETE to "امسح",
)

internal val ENGLISH_QUICK_MENU_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.QM_OPEN to "View details",
    UiKey.QM_OPEN_SECTION to "Open its section",
    UiKey.QM_EDIT to "Edit",
    UiKey.QM_SETTLE to "Record a payment",
    UiKey.QM_PERSON to "Open their profile",
    UiKey.QM_ADD_OCCASION to "Add an occasion",
    UiKey.QM_PREP to "Preparations",
    UiKey.QM_ARCHIVE to "Archive",
    UiKey.QM_UNARCHIVE to "Unarchive",
    UiKey.QM_SHOW_DAY to "Show this day",
    UiKey.QM_ADD_EVENT_DAY to "New event on this day",
    UiKey.QM_MARK_READ to "Mark as read",
    UiKey.QM_DELETE to "Delete",
)
