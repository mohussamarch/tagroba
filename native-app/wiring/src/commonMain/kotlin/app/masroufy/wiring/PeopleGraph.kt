package app.masroufy.wiring

import app.masroufy.ui.screens.people.PeopleDeps

/**
 * «الأشخاص» — `LoadPeopleOverview` · `ManagePeople` · `ManagePersonCircles` · `ManageOccasions` · `ManageEvents` · `EventGifts` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class PeopleGraph(c: AreaContext) : PeopleDeps
