package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.app.SpaceDeps
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadDuesDeps
import app.masroufy.usecase.LoadOnlineFeeds

/**
 * التجميع لبلد واحدة: [SpaceGraph] = `SpaceDeps` بتاع `:ui`، ومنطقة لكل `<Area>Graph(c: AreaContext)` **في ملفها** (`HomeGraph.kt` ·
 * `OperationsGraph.kt` …) — كل منطقة بتعدّل ملفها بس (ضيف حالة الاستخدام هناك وفي واجهة المنطقة `ui/screens/<area>/<Area>Routes.kt`).
 * حالة الاستخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`. **الملف ده ثابت** (منطقة جديدة = قرار مكتوب).
 * بيتبني من جديد مع كل تبديل بلد أو حساب ⇒ الشاشات بتحمّل تاني لوحدها (`LaunchedEffect(deps)`).
 */
class SpaceGraph(
    override val space: Space,
    val repos: SpaceRepositories,
    val env: DeviceEnv,
    session: SessionLinks,
    /** ملفات الأسعار مشتركة بين البلاد (نفس النسخة على الجهاز). */
    feeds: LoadOnlineFeeds,
) : SpaceDeps {
    override val shell = ShellGraph(space, repos, env, session)
    private val c = AreaContext(space, repos, env, session, feeds, shell)
    override val home = HomeGraph(c)
    override val operations = OperationsGraph(c)
    override val imports = ImportsGraph(c)
    override val people = PeopleGraph(c)
    override val dues = DuesGraph(c)
    override val investment = InvestmentGraph(c)
    override val more = MoreGraph(c)
    override val onboarding = OnboardingGraph(c)
}

/** المستحقات بكل مصادرها (الجمعيات · الأقساط · الديون بمواعيدها · الاشتراكات) — مشتركة بين الرئيسية والمستحقات والتقويم والخلفية. */
fun loadDues(r: SpaceRepositories) = LoadDues(
    LoadDuesDeps(r.roscas, r.roscaEntries, r.installmentPlans, r.installmentPayments, r.debtTerms, r.people, r.obligations, r.settlements, r.recurring, r.transactions),
)
