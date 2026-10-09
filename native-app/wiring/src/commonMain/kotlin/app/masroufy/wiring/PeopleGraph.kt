package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.people.PeopleDeps

/**
 * «الأشخاص» — `LoadPeopleOverview` · `ManagePeople` · `ManagePersonCircles` · `ManageOccasions` · `ManageEvents` · `EventGifts` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class PeopleGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : PeopleDeps
