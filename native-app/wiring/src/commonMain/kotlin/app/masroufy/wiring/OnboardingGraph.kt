package app.masroufy.wiring

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.countryPack
import app.masroufy.seed.BundledSeeds
import app.masroufy.ui.screens.onboarding.OnboardingDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.NewAccountSeeding
import app.masroufy.usecase.OnboardAccount
import app.masroufy.usecase.OnboardAccountDeps

/**
 * «أول تشغيل» — ملفك (`c.shell.profile` نفس كائن الهيكل) · `OnboardAccount` (`start` القيم الموجودة · `finish` التأكد ثم الكتابة).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`. `ManagePeople` هنا عشان
 * `OnboardAccount` محتاجه للديون الافتتاحية — رحلة §63 ما بتسألش عن ديون، فمش بيتنادى فعلًا. نقاط الربط (الشكل · إنشاء بلد) **null**
 * لحد ما منطقهم يتبني (نفس نقط «المزيد») ⇒ الشاشة بتقول «غير متاح بعد».
 * **حساب جديد خالص** (البلد الأساسية بس — الحساب الجديد بيبدأ فيها): التجهيز بالمراجع المضمّنة في التطبيق ([BundledSeeds]) وعلامة التجهيز.
 */
class OnboardingGraph(c: AreaContext) : OnboardingDeps {
    private val r = c.repos

    override val profile = c.shell.profile

    override val onboard: OnboardAccount by lazy {
        val people = ManagePeople(
            ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, c.env.ids, c.env.clock),
        )
        val seeding = r.referenceSeed?.takeIf { c.space.id == DEFAULT_SPACE_ID }?.let {
            NewAccountSeeding(r.categories, r.rules, it, BundledSeeds, countryPack(c.space.countryCode))
        }
        OnboardAccount(OnboardAccountDeps(c.shell.profile, people, r.wallets, c.env.clock, seeding))
    }
}
