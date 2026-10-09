package app.masroufy.wiring

import app.masroufy.ui.screens.operations.OperationsDeps

/**
 * «العمليات» — المنطقة بتضيف حالاتها هنا (`LoadTransactionsScreen` · `EditTransaction` · `SetEconomicKind` · `LoadBudgetScreen` · `SetBudget` ·
 * `ManageTransfers` …). الإضافة اليدوية نفسها في الهيكل: `c.shell.addTransaction`.
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class OperationsGraph(c: AreaContext) : OperationsDeps
