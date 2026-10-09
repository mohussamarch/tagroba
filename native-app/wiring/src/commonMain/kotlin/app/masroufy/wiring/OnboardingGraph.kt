package app.masroufy.wiring

import app.masroufy.ui.screens.onboarding.OnboardingDeps

/**
 * «أول تشغيل» — `c.shell.profile.needsOnboarding`/`completeOnboarding` · `OnboardAccount` · `ManageSpaces.create` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] (`c.repos` · `c.env` · `c.shell`) بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class OnboardingGraph(c: AreaContext) : OnboardingDeps
