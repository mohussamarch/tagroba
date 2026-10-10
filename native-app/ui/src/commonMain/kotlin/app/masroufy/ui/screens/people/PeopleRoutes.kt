package app.masroufy.ui.screens.people

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.nav.Tab

/**
 * منطقة «الأشخاص» (`SCREENS.md` §٢.٥): People · PersonProfile · AddPersonSheet · OwedToYou · YouOwe · ProfileDebtSheet · OccasionSheet ·
 * Events · EventDetail · EventAddSheet · EventEditSheet · EventPrep · EventSpendLinkSheet · NuqootSheet · Projects · ProjectDetail.
 * الملف ده بتاع المنطقة بس. اسم المسار = اسم اللوحة في النموذج (KOTLIN-MAP §١).
 * - **لوحات بتتفتح من مناطق تانية** (`SheetRoute`): «إضافة شخص» (زرار «+») · «الديون القديمة» (سؤال الملف) · «مناسبة» (ملفك) · «حدث جديد» (التقويم).
 * - **جوه شاشة واحدة** (`Sheet` جوه الشاشة): EventEditSheet · EventSpendLinkSheet · NuqootSheet (تفاصيل الحدث).
 */

object EventsRoute : Route {
    override val name = "Events"
}

/** «المشاريع» — مدخلها من «المزيد» (§76: «المشاريع» في «بياناتك»). */
object ProjectsRoute : Route {
    override val name = "Projects"
}

object OwedToYouRoute : Route {
    override val name = "OwedToYou"
}

object YouOweRoute : Route {
    override val name = "YouOwe"
}

data class PersonProfileRoute(val personId: Id) : Route {
    override val name = "PersonProfile"
}

data class EventDetailRoute(val eventId: Id) : Route {
    override val name = "EventDetail"
}

data class EventPrepRoute(val eventId: Id) : Route {
    override val name = "EventPrep"
}

data class ProjectDetailRoute(val projectId: Id) : Route {
    override val name = "ProjectDetail"
}

/** «إضافة شخص» — من خانة «إضافة» في الأشخاص ومن زرار «+». */
object AddPersonSheetRoute : SheetRoute {
    override val name = "AddPersonSheet"
}

/** اتجاه الديون القديمة في «كمّل ملفك»: لك عندهم · عليك لهم · الاتنين (يختار لكل شخص). */
enum class DebtMode { RECEIVE, PAY, BOTH }

/** «أضف الديون القديمة» — من سؤال الديون في `ProfileQuestion` (منطقة «أول تشغيل»). */
data class ProfileDebtSheetRoute(val mode: DebtMode) : SheetRoute {
    override val name = "ProfileDebtSheet"
}

/** مناسبة لشخص ([personId]) أو ليك (null) — جديدة ([occasionId] null) أو تعديل. */
data class OccasionSheetRoute(val personId: Id?, val occasionId: Id? = null) : SheetRoute {
    override val name = "OccasionSheet"
}

/** «حدث جديد» — من الأحداث ومن التقويم ([defaultDate] = اليوم المختار في الشبكة). */
data class EventAddSheetRoute(val defaultDate: IsoDate? = null) : SheetRoute {
    override val name = "EventAddSheet"
}

fun RouteRegistry.registerPeople() {
    tabRoot(Tab.PEOPLE) { PeopleScreen() }
    screen<PersonProfileRoute> { r -> PersonProfileScreen(r.personId) }
    screen<OwedToYouRoute> { OwedScreen(OwedSide.OWED_TO_YOU) }
    screen<YouOweRoute> { OwedScreen(OwedSide.YOU_OWE) }
    screen<EventsRoute> { EventsScreen() }
    screen<EventDetailRoute> { r -> EventDetailScreen(r.eventId) }
    screen<EventPrepRoute> { r -> EventPrepScreen(r.eventId) }
    screen<ProjectsRoute> { ProjectsScreen() }
    screen<ProjectDetailRoute> { r -> ProjectDetailScreen(r.projectId) }
    sheet<AddPersonSheetRoute> { _, close -> AddPersonRouteSheet(close) }
    sheet<ProfileDebtSheetRoute> { r, close -> ProfileDebtRouteSheet(r.mode, close) }
    sheet<OccasionSheetRoute> { r, close -> OccasionRouteSheet(r.personId, r.occasionId, close) }
    sheet<EventAddSheetRoute> { r, close -> EventAddRouteSheet(r.defaultDate, close) }
}
