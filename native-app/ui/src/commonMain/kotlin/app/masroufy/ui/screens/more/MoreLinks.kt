package app.masroufy.ui.screens.more

import app.masroufy.ui.nav.Route

/**
 * روابط «المزيد» لشاشات **لسه ما اتبنتش في المنطقة دي** أو بتاعة منطقة تانية — الاسم = اسم اللوحة في النموذج، فـ`RouteRegistry` بيعرض
 * «قيد البناء» لحد ما تتسجّل. ⚠️ **للدمج:** لو منطقة «الاستيراد» أو «العمليات» عرّفت نفس الشاشة بمسار من عندها، الرابط هنا يتشال
 * ويتستورد مسارها (التسجيل بالنوع مش بالاسم — `RouteRegistry.screens`).
 */
object CategoriesRoute : Route {
    override val name = "Categories"
}

object RulesRoute : Route {
    override val name = "Rules"
}

object ProjectsRoute : Route {
    override val name = "Projects"
}

/** منطقة «الاستيراد» (`StatementImport`). */
object StatementImportLink : Route {
    override val name = "StatementImport"
}

/** منطقة «الاستيراد» (`ImportBatches`). */
object ImportBatchesLink : Route {
    override val name = "ImportBatches"
}

/** منطقة «العمليات» (`SpaceTransfer` — التحويل لنفسك بين البلدين). */
object SpaceTransferLink : Route {
    override val name = "SpaceTransfer"
}
