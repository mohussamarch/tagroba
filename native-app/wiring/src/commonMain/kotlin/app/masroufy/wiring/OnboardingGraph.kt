package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.screens.onboarding.OnboardingDeps

/**
 * «أول تشغيل» — `ManageProfile.needsOnboarding`/`completeOnboarding` · `OnboardAccount` · `ManageSpaces.create` …
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`.
 */
@Suppress("UNUSED_PARAMETER")
class OnboardingGraph(space: Space, r: SpaceRepositories, env: DeviceEnv) : OnboardingDeps
