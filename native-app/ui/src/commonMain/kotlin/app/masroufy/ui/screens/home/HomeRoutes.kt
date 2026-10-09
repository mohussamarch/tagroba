package app.masroufy.ui.screens.home

import app.masroufy.core.IsoDate
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.onboarding.ProfileQuestionRoute

/**
 * شاشات منطقة «الرئيسية» — **الملف ده بتاع المنطقة بس** (المناطق التانية ما بتلمسوش). اسم كل شاشة = اسم لوحتها في النموذج.
 * الهيكل بيستعمل [NotificationsRoute] (من الجرس) و[CalendarRoute] (من «القادم») — سيبهم بنفس الاسم.
 */
object NotificationsRoute : Route {
    override val name = "Notifications"
}

object CalendarRoute : Route {
    override val name = "Calendar"
}

/** «اختر الشهر» — الاختيار بيتحفظ في [PeriodChoice] للبلد الشغالة (شاشة العمليات بتقراه من هناك). */
object PeriodPickerRoute : Route {
    override val name = "PeriodPicker"
}

/** «المراجعة» (الغامض بس — §18 · §74): من شريط «N عمليات نوعها غير مؤكد» في العمليات ومن صفحة الإشعارات. */
object ReviewQueueRoute : Route {
    override val name = "ReviewQueue"
}

/** «اختر شكلك» — بتتفتح من «ملفك» (`Account` في منطقة «المزيد»): `LocalNavigator.current.open(LookSheetRoute)`. */
object LookSheetRoute : SheetRoute {
    override val name = "LookSheet"
}

/**
 * «حدث جديد» من التقويم بيومه المختار — اللوحة نفسها (`EventAddSheet`) مشتركة مع صفحة الأحداث (منطقة «الأشخاص»)، فمش متسجّلة هنا:
 * لحد الدمج بتفتح «قيد البناء». ⚠️ وقت الدمج: لوحة واحدة متسجّلة بالمسار ده (أو مسار منطقة الأشخاص بنفس الاسم).
 */
data class EventAddSheetRoute(val defaultDate: IsoDate?) : SheetRoute {
    override val name = "EventAddSheet"
}

fun RouteRegistry.registerHome() {
    tabRoot(Tab.HOME) { HomeScreen() }
    screen<NotificationsRoute> { NotificationsScreen() }
    screen<CalendarRoute> { CalendarScreen() }
    screen<PeriodPickerRoute> { PeriodPickerScreen() }
    screen<ReviewQueueRoute> { ReviewQueueScreen() }
    // المسار متعرّف في ملف «أول تشغيل» من الأساس (اسمه ثابت)؛ الشاشة اتبنت هنا (توزيع جلسة البناء: «الرئيسية» + سؤال الملف)
    screen<ProfileQuestionRoute> { ProfileQuestionScreen() }
    sheet<LookSheetRoute> { _, close -> LookSheet(close) }
}
