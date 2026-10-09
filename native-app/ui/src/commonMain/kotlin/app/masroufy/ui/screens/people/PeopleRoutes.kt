package app.masroufy.ui.screens.people

import androidx.compose.runtime.Composable
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.text.t

/**
 * منطقة «الأشخاص» (`SCREENS.md` §٢.٥ — People · PersonProfile · AddPersonSheet · DebtDetail · SettleSheet · OpeningDebtSheet · OccasionSheet ·
 * Events · EventDetail · EventEditSheet · NuqootSheet · EventPrep + OwedToYou · YouOwe). الملف ده بتاع المنطقة بس.
 */
interface PeopleDeps

fun RouteRegistry.registerPeople() {
    tabRoot(Tab.PEOPLE) { PeopleTab() }
}

@Composable
private fun PeopleTab() {
    TabScaffold(t(TextKey.TAB_PEOPLE)) {
        item { EmptyState(t(TextKey.SHELL_PENDING_SCREEN)) }
    }
}
