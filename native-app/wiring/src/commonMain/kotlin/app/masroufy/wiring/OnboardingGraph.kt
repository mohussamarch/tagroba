package app.masroufy.wiring

import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.TextKey
import app.masroufy.core.countryPack
import app.masroufy.core.uiText
import app.masroufy.seed.BundledSeeds
import app.masroufy.ui.screens.onboarding.CountryMoney
import app.masroufy.ui.screens.onboarding.OnboardingDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ManageWallets
import app.masroufy.usecase.NewAccountSeeding
import app.masroufy.usecase.NewCountryMoney
import app.masroufy.usecase.OnboardAccount
import app.masroufy.usecase.OnboardAccountDeps
import app.masroufy.usecase.OnboardingResult

/**
 * «أول تشغيل» — ملفك (`c.shell.profile` نفس كائن الهيكل) · `OnboardAccount` (`start` القيم الموجودة · `finish` التأكد ثم الكتابة).
 * **الملف ده بتاع المنطقة بس.** حالة الاستخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`. `ManagePeople` هنا عشان
 * `OnboardAccount` محتاجه للديون الافتتاحية — رحلة §63 ما بتسألش عن ديون، فمش بيتنادى فعلًا. إنشاء بلد **متوصّل** (نفس كائن «البلدان» —
 * `c.shell.spacesAdmin`) ⇒ مصر مفتوحة في كارت البلد. نقطة ربط الشكل **null** لحد ما منطقها يتبني ⇒ الشاشة بتقول «غير متاح بعد».
 * **حساب جديد خالص** (البلد الأساسية بس — الحساب الجديد بيبدأ فيها): التجهيز بالمراجع المضمّنة في التطبيق ([BundledSeeds]) وعلامة التجهيز.
 */
class OnboardingGraph(c: AreaContext) : OnboardingDeps {
    private val r = c.repos

    override val profile = c.shell.profile

    /** مصر في كارت البلد (رد المالك §88-8): نفس طريق «البلدان» — بتتعمل وتتفتح في نفس الجلسة وبعدين التبديل لها. */
    override val spacesAdmin = c.shell.spacesAdmin

    /** r041b P2: مسودة الإجابات على الجهاز (رجوع الجهاز أو قفل التطبيق في النص ⇒ الفتح الجاي بيكمّل من نفس الكارت). */
    override val draft = c.env.onboardingDraft

    override val onboard: OnboardAccount by lazy {
        val people = ManagePeople(
            ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, c.env.ids, c.env.clock),
        )
        val seeding = r.referenceSeed?.takeIf { c.space.id == DEFAULT_SPACE_ID }?.let {
            NewAccountSeeding(r.categories, r.rules, it, BundledSeeds, countryPack(c.space.countryCode))
        }
        val wallets = ManageWallets(r.wallets, c.env.ids, c.env.clock, c.space.currency, countryPack(c.space.countryCode).timeZone)
        OnboardAccount(OnboardAccountDeps(c.shell.profile, people, r.wallets, c.env.clock, seeding, wallets))
    }

    /**
     * كارتين الفلوس لمصر لما الحساب الجديد يختارها (§81 = رحلة الحساب الجديد لأي بلد — جولة 0.3): بعد ما البلد تتعمل وتتفتح في الجلسة،
     * الكاش والبنك بيتكتبوا في **محافظها هي** (`NewCountryMoney` على مستودعات البلد من `SessionLinks.spaces`). من غير إدارة بلاد ⇒ null.
     */
    override val countryMoney: CountryMoney? = c.shell.spacesAdmin?.let {
        CountryMoney { spaceId, cashMinor, bank ->
            val (space, repos) = c.session.spaces().firstOrNull { it.first.id == spaceId }
                ?: return@CountryMoney OnboardingResult.Failed("cash", uiText(TextKey.NOT_AVAILABLE))
            val pack = countryPack(space.countryCode)
            NewCountryMoney(repos.wallets, ManageWallets(repos.wallets, c.env.ids, c.env.clock, space.currency, pack.timeZone), pack).apply(cashMinor, bank)
        }
    }
}
