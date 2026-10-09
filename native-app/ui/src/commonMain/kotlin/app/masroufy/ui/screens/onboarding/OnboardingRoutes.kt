package app.masroufy.ui.screens.onboarding

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry

/**
 * منطقة «أول تشغيل» (`SCREENS.md` §٢.٩ — Onboarding · SignInEmail · ProfileQuestion · Lock). الملف ده بتاع المنطقة بس.
 * **الدخول نفسه** (قبل ما الحساب يجهز) مش هنا: `screens/auth/` (`SignInFlow` — الترحيب · جوجل · الإيميل · نسيت كلمة المرور) —
 * وكروت أول تشغيل بعد الدخول (الشكل · البلد · يوم المرتب · مصدر العمليات) بتتبني هنا وبتتفتح من الرئيسية لو `ManageProfile.needsOnboarding`.
 */
interface OnboardingDeps

object ProfileQuestionRoute : Route {
    override val name = "ProfileQuestion"
}

fun RouteRegistry.registerOnboarding() {
}
