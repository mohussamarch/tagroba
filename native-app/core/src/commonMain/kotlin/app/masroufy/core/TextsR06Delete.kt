package app.masroufy.core

/**
 * r06-delete (OVERRIDES §118-1 — قاعدة المالك 2026-10-24): لوحة المسح الواحدة لكل حاجة («امسح <الاسم>؟» · «<الاسم> عليه N عملية» والتلات
 * اختيارات) وشريط «تراجع». ملف لوحده زي `TextsR06Split.kt` (مفاتيح DEL_ في [R06Key]). كلام المالك بالحرف في المصري.
 */
internal val MSA_R06_DELETE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.DEL_ACTION to "حذف",
    R06Key.DEL_TITLE to "حذف «{0}»؟",
    R06Key.DEL_BODY to "تقدر تتراجع خلال ثوانٍ.",
    R06Key.DEL_CONFIRM to "حذف",
    R06Key.DEL_LINKED_TITLE to "على «{0}» {1} عملية",
    R06Key.DEL_LINKED_BODY to "اختر ما يحدث للعمليات. تقدر تتراجع بعدها.",
    R06Key.DEL_ARCHIVE to "أرشفة",
    R06Key.DEL_KEEP_OPS to "احذفه واترك العمليات",
    R06Key.DEL_WITH_OPS to "احذفه مع عملياته",
    R06Key.DEL_MOVE_OPS to "انقل العمليات لتصنيف آخر",
    R06Key.DEL_MOVE_PICK to "اختر التصنيف",
    R06Key.DEL_DONE to "حُذف «{0}»",
    R06Key.DEL_ARCHIVED to "أُرشف «{0}»",
    R06Key.DEL_MOVED to "نُقلت العمليات وحُذف «{0}»",
    R06Key.DEL_KEEP_BLOCKED_DEBTS to "عليه ديون مفتوحة — سدّدها أو احذفها أولًا",
    R06Key.DEL_REFUSED_CHILDREN to "تحته تصنيفات فرعية — احذفها أولًا",
    R06Key.DEL_SOURCE_REMOVED to "مصدرها محذوف",
)

internal val EGYPTIAN_R06_DELETE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.DEL_ACTION to "امسح",
    R06Key.DEL_TITLE to "امسح «{0}»؟",
    R06Key.DEL_BODY to "تقدر تتراجع في ثواني.",
    R06Key.DEL_CONFIRM to "امسح",
    R06Key.DEL_LINKED_TITLE to "«{0}» عليه {1} عملية",
    R06Key.DEL_LINKED_BODY to "اختار العمليات يحصل لها إيه. تقدر تتراجع بعدها.",
    R06Key.DEL_ARCHIVE to "أرشفه",
    R06Key.DEL_KEEP_OPS to "امسحه وسيب العمليات",
    R06Key.DEL_WITH_OPS to "امسحه هو وعملياته",
    R06Key.DEL_MOVE_OPS to "انقل العمليات لتصنيف تاني",
    R06Key.DEL_MOVE_PICK to "اختار التصنيف",
    R06Key.DEL_DONE to "«{0}» اتمسح",
    R06Key.DEL_ARCHIVED to "«{0}» اتأرشف",
    R06Key.DEL_MOVED to "العمليات اتنقلت و«{0}» اتمسح",
    R06Key.DEL_KEEP_BLOCKED_DEBTS to "عليه ديون مفتوحة — سدّدها أو امسحها الأول",
    R06Key.DEL_REFUSED_CHILDREN to "تحته تصنيفات فرعية — امسحها الأول",
    R06Key.DEL_SOURCE_REMOVED to "مصدرها اتمسح",
)

internal val ENGLISH_R06_DELETE_TEXTS: Map<R06Key, String> = mapOf(
    R06Key.DEL_ACTION to "Delete",
    R06Key.DEL_TITLE to "Delete “{0}”?",
    R06Key.DEL_BODY to "You can undo within seconds.",
    R06Key.DEL_CONFIRM to "Delete",
    R06Key.DEL_LINKED_TITLE to "“{0}” has {1} operations",
    R06Key.DEL_LINKED_BODY to "Choose what happens to the operations. You can undo afterwards.",
    R06Key.DEL_ARCHIVE to "Archive",
    R06Key.DEL_KEEP_OPS to "Delete, keep operations",
    R06Key.DEL_WITH_OPS to "Delete with its operations",
    R06Key.DEL_MOVE_OPS to "Move operations to another category",
    R06Key.DEL_MOVE_PICK to "Pick a category",
    R06Key.DEL_DONE to "“{0}” deleted",
    R06Key.DEL_ARCHIVED to "“{0}” archived",
    R06Key.DEL_MOVED to "Operations moved and “{0}” deleted",
    R06Key.DEL_KEEP_BLOCKED_DEBTS to "Has open debts — settle or delete them first",
    R06Key.DEL_REFUSED_CHILDREN to "Has subcategories — delete them first",
    R06Key.DEL_SOURCE_REMOVED to "Source deleted",
)
