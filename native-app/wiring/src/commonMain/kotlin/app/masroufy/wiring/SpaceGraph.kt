package app.masroufy.wiring

import app.masroufy.core.Space
import app.masroufy.ui.app.ShellDeps
import app.masroufy.ui.app.SpaceDeps
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadDuesDeps
import app.masroufy.usecase.LoadOnlineFeeds

/**
 * التجميع لبلد واحدة: [SpaceGraph] = `SpaceDeps` بتاع `:ui`، ومنطقة لكل `<Area>Graph` **في ملفها** (`HomeGraph.kt` · `OperationsGraph.kt` …)
 * — كل منطقة بتعدّل ملفها بس (ضيف حالة الاستخدام هناك وفي واجهة المنطقة `ui/screens/<area>/…Deps`). حالة الاستخدام بتتبني من
 * [SpaceRepositories] + [DeviceEnv] بنفس اعتماداتها في اختبارات `:app`. الكلاس ده ثابت (منطقة جديدة = قرار مكتوب).
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
    override val shell: ShellDeps = ShellGraph(space, repos, env, session)
    override val home = HomeGraph(space, repos)
    override val operations = OperationsGraph(space, repos, env)
    override val imports = ImportsGraph(space, repos, env)
    override val people = PeopleGraph(space, repos, env)
    override val dues = DuesGraph(space, repos, env)
    override val investment = InvestmentGraph(repos, feeds)
    override val more = MoreGraph(space, repos, env)
    override val onboarding = OnboardingGraph(space, repos, env)
}

/** المستحقات بكل مصادرها (الجمعيات · الأقساط · الديون بمواعيدها · الاشتراكات) — مشتركة بين الرئيسية والمستحقات والتقويم والخلفية. */
fun loadDues(r: SpaceRepositories) = LoadDues(
    LoadDuesDeps(r.roscas, r.roscaEntries, r.installmentPlans, r.installmentPayments, r.debtTerms, r.people, r.obligations, r.settlements, r.recurring, r.transactions),
)
