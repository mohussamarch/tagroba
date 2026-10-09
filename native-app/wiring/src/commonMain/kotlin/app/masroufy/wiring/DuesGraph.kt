package app.masroufy.wiring

import app.masroufy.ui.screens.dues.DuesDeps

/**
 * «المستحقات» — `loadDues(c.repos)` · `ManageRoscas` · `ManageInstallments` · `ManageRecurring` · `RoscaSetup` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class DuesGraph(c: AreaContext) : DuesDeps
