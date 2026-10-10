package app.masroufy.ui.screens.onboarding

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.screens.more.LookSetting
import app.masroufy.ui.screens.more.SpacesAdmin
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.OnboardAccount

/**
 * منطقة «أول تشغيل» (`SCREENS.md` §٢.٩ — Onboarding · SignInEmail · ProfileQuestion · Lock). الملف ده بتاع المنطقة بس.
 * **الدخول نفسه** (قبل ما الحساب يجهز) مش هنا: `screens/auth/` (`SignInFlow` — الترحيب · جوجل · الإيميل · نسيت كلمة المرور) —
 * وكروت أول تشغيل بعد الدخول (الشكل · البلد · يوم الراتب · مصدر العمليات · جاهز) هنا: [OnboardingRoute].
 *
 * ⚠️ **للدمج:** مين يفتح [OnboardingRoute]؟ الرئيسية (أو الهيكل) لما `needsOnboarding()` = true — ده مش في المنطقة دي (ملف الرئيسية
 * والهيكل بتوع مناطق تانية)، فمكتوب في HANDOVER «missingLogic». `ProfileQuestionRoute` بتسجّله منطقة الرئيسية (فرع `screens-home`) —
 * **ما يتسجلش هنا** (التسجيل مرتين بيوقّع التطبيق وقت التشغيل).
 */
interface OnboardingDeps {
    /** ملفك — القيم الموجودة (الحساب القديم خاناته بتبدأ بالموجود) و`needsOnboarding`. نفس `ManageProfile` بتاع الهيكل. */
    val profile: ManageProfile

    /** أسئلة البداية: `start` (الموجود) · `finish` (التأكد قبل أي كتابة، وتسجيل الانتهاء آخر حاجة). */
    val onboard: OnboardAccount

    /** «اختر شكلك» — نفس نقطة ربط «المزيد» (null لحد ما `UserProfile` يبقى فيه حقل الشكل) ⇒ «غير متاح بعد» والتخطي شغال. */
    val look: LookSetting? get() = null

    /** إنشاء بلد غير السعودية — نفس نقطة ربط «البلدان» (null لحد ما `ManageSpaces` يتجمّع على الجوال) ⇒ مصر مقفولة بسببها. */
    val spacesAdmin: SpacesAdmin? get() = null
}

/** الحساب ده لسه ما خلّصش أسئلة البداية؟ (للرئيسية/الهيكل وقت الدمج — `ManageProfile.needsOnboarding`). */
suspend fun OnboardingDeps.needsOnboarding(): Boolean = profile.needsOnboarding(profile.load())

/** كروت أول تشغيل بعد الدخول (`Onboarding` الخطوات ١ و٣–٦). */
object OnboardingRoute : Route {
    override val name = "Onboarding"
}

object ProfileQuestionRoute : Route {
    override val name = "ProfileQuestion"
}

fun RouteRegistry.registerOnboarding() {
    screen<OnboardingRoute> { OnboardingScreen() }
}
