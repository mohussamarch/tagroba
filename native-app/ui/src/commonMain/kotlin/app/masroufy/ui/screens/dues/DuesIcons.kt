package app.masroufy.ui.screens.dues

import app.masroufy.ui.icons.Lucide

/**
 * أيقونات منطقة «المستحقات» اللي مش في المشتركة — المسارات من `<svg>` لوحة `Dues` في النموذج (lucide-static v0.400.0):
 * الاشتراك = `refresh-ccw` · القسط = `calendar-days` (المستطيل اتحوّل لمسار بنفس الشكل). الدين = `Lucide.HAND_COINS` · الجمعية = `Lucide.COINS`.
 */
internal object DuesIcons {
    val REFRESH_CCW = Lucide(
        "REFRESH_CCW",
        "M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16",
        "M16 16h5v5",
    )
    /** عدّاد «كم دورًا؟» (lucide `minus`). */
    val MINUS = Lucide("MINUS", "M5 12h14")
    val CALENDAR_DAYS = Lucide(
        "CALENDAR_DAYS",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z",
        "M16 2v4M8 2v4M3 10h18",
        "M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01",
    )
}
