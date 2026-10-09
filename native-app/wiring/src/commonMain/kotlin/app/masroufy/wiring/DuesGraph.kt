package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.dues.DuesDeps

/**
 * «المستحقات» — `loadDues(r)` · `ManageRoscas` · `ManageInstallments` · `ManageRecurring` · `RoscaSetup` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class DuesGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : DuesDeps
