package app.masroufy.wiring

import app.masroufy.ui.screens.more.MoreDeps

/**
 * «المزيد» — `c.shell.profile` · `ManageSpaces` · `ManageCategories` · `ManageRules` · `ManageIncomeSources` · `FullBackup` · `ExportCsv` …
 * (`AppLock` و`SignIn` على مستوى التطبيق: `LocalApp`).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class MoreGraph(c: AreaContext) : MoreDeps
