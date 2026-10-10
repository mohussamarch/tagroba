package app.masroufy.ui.screens.people

import app.masroufy.ui.icons.Lucide

/** أيقونات منطقة «الأشخاص» (لوسيد بسُمك 1.5 — المسارات من لوحات النموذج نفسها: `PersonProfile` · `Projects` · `OccasionSheet`). */
internal object PeopleIcons {
    val MINUS = Lucide("MINUS", "M5 12h14")
    val USER_PLUS = Lucide("USER_PLUS", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M19 8v6M22 11h-6")
    val ARCHIVE = Lucide("ARCHIVE", "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4")
    val PEN = Lucide("PEN", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "M15 5l4 4")
    val SPLIT_BILL = Lucide(
        "SPLIT_BILL",
        "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z",
        "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8",
        "M12 17.5v-11",
    )
}
