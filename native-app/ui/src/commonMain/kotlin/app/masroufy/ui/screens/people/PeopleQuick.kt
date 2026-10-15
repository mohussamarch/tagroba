package app.masroufy.ui.screens.people

import app.masroufy.core.Id
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.screens.common.QuickAct
import app.masroufy.ui.screens.common.eventQuickActs
import app.masroufy.ui.screens.common.personQuickActs
import app.masroufy.ui.screens.common.quickItems

/**
 * القايمة السريعة في «الناس» (قايمة المالك §6.5-13): الحدث: تفاصيله · تجهيزاته (`EventPrepRoute`) — الشخص: ملفه · مناسبة جديدة له
 * (`OccasionSheetRoute`). صف الناس اللي فوق مش هنا (ليه وضع تعديل لوحده §84).
 *
 * **المسح** (فرع r04-delete): [delete] = null لحد ما يتوصل — لما يبقى موجود «امسح» بيظهر لوحده.
 */
internal fun eventQuickItems(eventId: Id, archived: Boolean, nav: Navigator, delete: ((Id) -> Unit)? = null): List<MenuItem> =
    quickItems(eventQuickActs(archived, canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(EventDetailRoute(eventId))
            QuickAct.PREP -> nav.push(EventPrepRoute(eventId))
            QuickAct.DELETE -> delete?.invoke(eventId)
            else -> Unit
        }
    }

internal fun personQuickItems(personId: Id, nav: Navigator, delete: ((Id) -> Unit)? = null): List<MenuItem> =
    quickItems(personQuickActs(canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> nav.push(PersonProfileRoute(personId))
            QuickAct.ADD_OCCASION -> nav.open(OccasionSheetRoute(personId))
            QuickAct.DELETE -> delete?.invoke(personId)
            else -> Unit
        }
    }
