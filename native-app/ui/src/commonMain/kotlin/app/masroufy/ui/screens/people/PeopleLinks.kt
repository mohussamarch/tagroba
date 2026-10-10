package app.masroufy.ui.screens.people

import app.masroufy.core.Id
import app.masroufy.ui.nav.Route

/**
 * روابط «الأشخاص» لشاشات **منطقة تانية** — الاسم = اسم اللوحة في النموذج، فـ`RouteRegistry` بيعرض «قيد البناء» لحد ما تتسجّل.
 * ⚠️ **للدمج:** التسجيل بالنوع مش بالاسم (`RouteRegistry.screens`)، فلما منطقة «الديون» تتدمج الرابط هنا يتشال ويتستورد مسارها
 * (`app.masroufy.ui.screens.dues.DebtDetailRoute(obligationId)` — نفس المعامل بالظبط).
 */

/** تفاصيل دين (`DebtDetail` — صف في «السجل بينكم» في ملف الشخص) — منطقة «الديون». */
data class DebtDetailLink(val obligationId: Id) : Route {
    override val name = "DebtDetail"
}
