package app.masroufy.ui.screens.imports

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry

/**
 * منطقة «الاستيراد» (`SCREENS.md` §٢.٤ — BankSms · BankSmsSettings · SmsPermission · SmsPaste · SmsWaiting · StatementImport ·
 * StatementColumns · ImportReview · ImportDuplicateSheet · ImportBatches · RevertBatchSheet). الملف ده بتاع المنطقة بس.
 * - `SmsWaiting` قسم جوه `BankSms` · `CategoryPicker` لوحة جوه `BankSms` · `SmsPermission` لوحة جوه `BankSmsSettings` (KOTLIN-MAP §١).
 * - `ImportDuplicateSheet` جوه `ImportReview` · `RevertBatchSheet` جوه `ImportBatches` (لوحات شاشة واحدة ⇒ `Sheet` جوه الشاشة).
 * - الدخول: «المزيد» (رسائل البنك · كشف الحساب · دفعات الاستيراد) ولوحة «+» — منطقتهم بتعمل `push` للمسارات دي.
 */

/** «رسائل البنك» — من «المزيد» ولوحة «+» وإشعار «رسائل بانتظار تأكيدك». */
object BankSmsRoute : Route {
    override val name = "BankSms"
}

/** «إعداد القراءة» (ترس «رسائل البنك»). */
object BankSmsSettingsRoute : Route {
    override val name = "BankSmsSettings"
}

/** «إضافة من الرسائل» — لصق (طريق الآيفون) أو قراءة فترة بالطلب (أندرويد). */
object SmsPasteRoute : Route {
    override val name = "SmsPaste"
}

/** «كشف الحساب» — اختيار الملف وقراءته. */
object StatementImportRoute : Route {
    override val name = "StatementImport"
}

/** «تحديد الأعمدة» لملف CSV أعمدته مش معروفة — [draftId] = الملف في [ImportDrafts]. */
data class StatementColumnsRoute(val draftId: Long) : Route {
    override val name = "StatementColumns"
}

/** «مراجعة الكشف» — [draftId] = طلب الاستيراد في [ImportDrafts]. */
data class ImportReviewRoute(val draftId: Long) : Route {
    override val name = "ImportReview"
}

/** «دفعات الاستيراد» — من «المزيد» ومن «كشف الحساب» وبعد التسجيل. */
object ImportBatchesRoute : Route {
    override val name = "ImportBatches"
}

fun RouteRegistry.registerImports() {
    screen<BankSmsRoute> { BankSmsScreen() }
    screen<BankSmsSettingsRoute> { BankSmsSettingsScreen() }
    screen<SmsPasteRoute> { SmsPasteScreen() }
    screen<StatementImportRoute> { StatementImportScreen() }
    screen<StatementColumnsRoute> { r -> StatementColumnsScreen(r.draftId) }
    screen<ImportReviewRoute> { r -> ImportReviewScreen(r.draftId) }
    screen<ImportBatchesRoute> { ImportBatchesScreen() }
}
