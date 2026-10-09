package app.masroufy.ui.screens.operations

import androidx.compose.runtime.Composable
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.text.t

/**
 * منطقة «العمليات» (`SCREENS.md` §٢.٣ — Operations · OperationFilters · OperationDetail · OperationMenu · CategoryPicker · TagsSheet ·
 * LinkPersonSheet · LinkProjectEventSheet · MerchantProfile · ReviewQueue · Budgets · CategoryBudget · BudgetLimitSheet · Transfers ·
 * TransferPartySheet · SpaceTransfer). الملف ده بتاع المنطقة بس. `AddSheet`/`AddOperation` في الهيكل (`shell/AddOperationSheet.kt`).
 */
interface OperationsDeps

fun RouteRegistry.registerOperations() {
    tabRoot(Tab.OPERATIONS) { OperationsTab() }
}

/** جذر التبويب لحد ما الشاشة تتبني: الرأس الموحّد + «قيد البناء». */
@Composable
private fun OperationsTab() {
    TabScaffold(t(TextKey.TAB_OPERATIONS)) {
        item { EmptyState(t(TextKey.SHELL_PENDING_SCREEN)) }
    }
}
