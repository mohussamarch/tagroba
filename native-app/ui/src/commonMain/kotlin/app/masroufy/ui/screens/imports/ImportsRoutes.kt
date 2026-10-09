package app.masroufy.ui.screens.imports

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry

/**
 * منطقة «الاستيراد» (`SCREENS.md` §٢.٤ — BankSms · BankSmsSettings · SmsPermission · SmsPaste · SmsWaiting · StatementImport ·
 * StatementColumns · ImportReview · ImportDuplicateSheet · ImportBatches · RevertBatchSheet). الملف ده بتاع المنطقة بس.
 * إذن رسايل البنك: `LocalPermissions.current.requestSms()` (شرح `SmsPermission` الأول — نافذة النظام بعده).
 */
interface ImportsDeps

object BankSmsRoute : Route {
    override val name = "BankSms"
}

fun RouteRegistry.registerImports() {
    // screen<BankSmsRoute> { BankSmsScreen() } — لما تتبني
}
