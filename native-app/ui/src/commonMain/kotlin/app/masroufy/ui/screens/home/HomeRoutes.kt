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

/**
 * «اختر الشهر» — الاختيار بيتحفظ في [PeriodChoice] للبلد الشغالة. ⚠️ **مدخلها الوحيد** زرار الشهر («أكتوبر») في شاشة العمليات (§76) — مش
 * موجود على الفرع ده (العمليات هنا «قيد البناء») ⇒ الشاشة دي ما حدش بيفتحها لحد الدمج. فرع `screens-operations` فيه المدخل فعلًا
 * (`OperationsScreen.kt`: `MonthButton(…) { nav.push(PeriodPickerRoute) }`) بس **لسه ما بيقراش** [PeriodChoice] — لازم يتوصل وقت الدمج.
 */
object PeriodPickerRoute : Route {
    override val name = "PeriodPicker"
}

/** «المراجعة» (الغامض بس — §18 · §74): من شريط «N عمليات نوعها غير مؤكد» في العمليات ومن صفحة الإشعارات. */
object ReviewQueueRoute : Route {
    override val name = "ReviewQueue"
}

/**
 * «اختر شكلك» — مدخلها الوحيد «ملفك» (`Account` في منطقة «المزيد»)، ومش موجود على الفرع ده ⇒ اللوحة دي **ما حدش بيفتحها هنا**.
 * ⚠️ وفرع `screens-more` بيفتح نسخته هو (`more/LookSheet.kt` جوه `AccountScreen.kt`) مش المسار ده ⇒ حتى بعد الدمج تفضل مقفولة لحد ما
 * تتحسم «نسخة واحدة» (سؤال مفتوح في HANDOVER — جلسة 43): يا «ملفك» يفتح `open(LookSheetRoute)` وتتشال نسخة المزيد، يا العكس.
 */
object LookSheetRoute : SheetRoute {
    override val name = "LookSheet"
}

/**
 * «حدث جديد» من التقويم بيومه المختار — اللوحة نفسها (`EventAddSheet`) مشتركة مع صفحة الأحداث (منطقة «الأشخاص»)، فمش متسجّلة هنا:
 * لحد الدمج زرار التقويم **مقفول** (`EVENT_ADD_READY` في `CalendarScreen.kt`) — مش بيفتح «قيد البناء». ⚠️ وقت الدمج: فرع `screens-people`
 * مسجّل `people.EventAddSheetRoute(defaultDate)` ⇒ التقويم يستعمله و`EVENT_ADD_READY = true`، والمسار المؤقت ده يتشال.
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
