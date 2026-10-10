package app.masroufy.ui.screens.home

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.onboarding.ProfileQuestionRoute
import app.masroufy.ui.screens.operations.ReviewQueueRoute

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
 * «اختر الشهر» — الاختيار بيتحفظ في [PeriodChoice] للبلد الشغالة. مدخلها زرار الشهر («أكتوبر») في شاشة العمليات (§76)، والعمليات
 * والمراجعة بيقروا الاختيار ([PeriodChoice.periodOf]).
 */
object PeriodPickerRoute : Route {
    override val name = "PeriodPicker"
}

// «المراجعة» (الغامض بس — §18 · §74): المسار نفسه `operations.ReviewQueueRoute` (الدمج — مسار واحد)، والشاشة متسجّلة هنا.

/**
 * «اختر شكلك» — مدخلها الوحيد «ملفك» (`Account` في منطقة «المزيد»)، ومش موجود على الفرع ده ⇒ اللوحة دي **ما حدش بيفتحها هنا**.
 * ⚠️ وفرع `screens-more` بيفتح نسخته هو (`more/LookSheet.kt` جوه `AccountScreen.kt`) مش المسار ده ⇒ حتى بعد الدمج تفضل مقفولة لحد ما
 * تتحسم «نسخة واحدة» (سؤال مفتوح في HANDOVER — جلسة 43): يا «ملفك» يفتح `open(LookSheetRoute)` وتتشال نسخة المزيد، يا العكس.
 */
object LookSheetRoute : SheetRoute {
    override val name = "LookSheet"
}

// «حدث جديد» من التقويم: الدمج وصّله بـ`people.EventAddSheetRoute(defaultDate)` (متسجّل في منطقة «الأشخاص») والمسار المؤقت اتشال.

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
