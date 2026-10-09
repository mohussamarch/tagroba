package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.operations.OperationsDeps

/**
 * «العمليات» — المنطقة بتضيف حالاتها هنا (`LoadTransactionsScreen` · `EditTransaction` · `SetEconomicKind` · `LoadBudgetScreen` · `SetBudget` · `ManageTransfers` …).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class OperationsGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : OperationsDeps
