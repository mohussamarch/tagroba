package app.masroufy.ui.screens.onboarding

import app.masroufy.core.Halalas
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.screens.more.SpacesAdmin
import app.masroufy.usecase.ManageProfile
import app.masroufy.usecase.AddWalletInput
import app.masroufy.usecase.OnboardAccount
import app.masroufy.usecase.OnboardingResult

/**
 * منطقة «أول تشغيل» (`SCREENS.md` §2.9 — Onboarding · SignInEmail · ProfileQuestion · Lock). الملف ده بتاع المنطقة بس.
 * **الدخول نفسه** (قبل ما الحساب يجهز) مش هنا: `screens/auth/` (`SignInFlow` — الترحيب · جوجل · الإيميل · نسيت كلمة المرور) —
 * وكروت أول تشغيل بعد الدخول (البلد · الكاش والبنك · يوم الراتب · مصدر العمليات · جاهز — الشكل اتنقل لـ«كمّل بروفايلك») هنا: [OnboardingRoute].
 *
 * مين يفتح [OnboardingRoute]؟ **الهيكل** (`AppShell`) لما `OnboardAccount.shouldStart()` = true — حساب جديد خالص بس (الأسئلة ما خلصتش
 * ومفيش ولا محفظة في البلد الأساسية)؛ حساب فيه بيانات ما بيتفتحلوش. `ProfileQuestionRoute` بتسجّله منطقة الرئيسية (فرع `screens-home`) —
 * **ما يتسجلش هنا** (التسجيل مرتين بيوقّع التطبيق وقت التشغيل).
 */
interface OnboardingDeps {
    /** ملفك — القيم الموجودة (الحساب القديم خاناته بتبدأ بالموجود) و`needsOnboarding`. نفس `ManageProfile` بتاع الهيكل. */
    val profile: ManageProfile

    /** أسئلة البداية: `start` (الموجود) · `finish` (التأكد قبل أي كتابة، وتسجيل الانتهاء آخر حاجة). */
    val onboard: OnboardAccount

    /**
     * إنشاء بلد غير السعودية — **نفس كائن «البلدان»** (`ManageSpaces` متجمّع من الجلسة — `OnboardingGraph`، رد المالك §88-8): مصر بتتعمل
     * وتتفتح في نفس الجلسة وبعدين التبديل لها. null (المعاينة · جلسة مالهاش إدارة بلاد) ⇒ مصر مقفولة بسببها.
     */
    val spacesAdmin: SpacesAdmin? get() = null

    /** كارتين الفلوس (§81) لبلد اتعملت في نفس الرحلة (مصر) — بيكتب في محافظ البلد دي (`NewCountryMoney`). null ⇒ الكارتين لبلد الحساب بس. */
    val countryMoney: CountryMoney? get() = null

    /** r041b P2: مسودة الإجابات على الجهاز (الفتح الجاي بيكمّل من نفس الكارت). null (المعاينة · الاختبار) ⇒ الحالة في الذاكرة بس. */
    val draft: OnbDraftStore? get() = null
}

/** الكاش والبنك من كارتين الفلوس ⇒ محافظ البلد [spaceId] (لازم تكون مفتوحة في الجلسة). */
fun interface CountryMoney {
    suspend fun apply(spaceId: String, cashMinor: Halalas?, bank: AddWalletInput?): OnboardingResult
}

/** الحساب ده لسه ما خلّصش أسئلة البداية؟ (للرئيسية/الهيكل وقت الدمج — `ManageProfile.needsOnboarding`). */
suspend fun OnboardingDeps.needsOnboarding(): Boolean = profile.needsOnboarding(profile.load())

/** كروت أول تشغيل بعد الدخول (`Onboarding` الخطوات 1 و3–6). */
object OnboardingRoute : Route {
    override val name = "Onboarding"
}

object ProfileQuestionRoute : Route {
    override val name = "ProfileQuestion"
}

fun RouteRegistry.registerOnboarding() {
    screen<OnboardingRoute> { OnboardingScreen() }
}
