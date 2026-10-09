package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.more.MoreDeps

/**
 * «المزيد» — `ManageProfile` · `ManageSpaces` · `ManageCategories` · `ManageRules` · `ManageIncomeSources` · `FullBackup` · `ExportCsv` … (`AppLock` و`SignIn` على مستوى التطبيق: `LocalApp`).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class MoreGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : MoreDeps
