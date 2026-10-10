package app.masroufy.wiring

import app.masroufy.core.AssistPlatform
import app.masroufy.core.AssistTab
import app.masroufy.ui.app.AskDeps
import app.masroufy.usecase.AssistContext
import app.masroufy.usecase.AssistLexiconSource
import app.masroufy.usecase.AssistantDeps
import app.masroufy.usecase.AssistantSources
import app.masroufy.usecase.AssistantSuite
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.FindLastAtMerchant
import app.masroufy.usecase.LoadCategorySpend
import app.masroufy.usecase.LoadCategorySpendDeps
import app.masroufy.usecase.LoadHomeHistory
import app.masroufy.usecase.LoadHomeHistoryDeps
import app.masroufy.usecase.LoadMoneySummary
import app.masroufy.usecase.LoadMoneySummaryDeps
import app.masroufy.usecase.PersonAcrossSpaces
import app.masroufy.usecase.PersonSpaceBook

/**
 * المساعد «مصروفي» للبلد الشغالة (ARCHITECTURE §31.36): `AssistantDeps` من **نفس حالات الاستخدام اللي الشاشات بتستعملها** (من [areas] —
 * الرئيسية · الميزانيات · المستحقات · الأشخاص · الاستثمار · المزيد) ومخازن المساعد على الحساب (`repos.assistant` — فايربيز على الجوال).
 * أي رقم في رد = نفس دالة الشاشة (§78 (د)). بيتبني أول مرة المساعد يتفتح (أسعار الزكاة من ملف الأسعار المحفوظ — من غير نت ⇒ «غير متاح»).
 */
class AssistantGraph(private val c: AreaContext, private val areas: SpaceGraph) : AskDeps {
    private val r = c.repos
    private var built: AssistantSuite? = null

    override suspend fun suite(): AssistantSuite = built ?: AssistantSuite(deps()).also { built = it }

    override suspend fun context(tab: AssistTab): AssistContext = AssistContext(
        nowIso = c.env.clock.nowIso(), today = c.env.today(), hour = c.env.hourNow(), space = c.space, tab = tab, platform = AssistPlatform.ANDROID,
        spaceCount = runCatching { c.session.spaces().size }.getOrDefault(1).coerceAtLeast(1),
    )

    internal suspend fun deps(): AssistantDeps = AssistantDeps(
        stores = r.assistant,
        sources = sources(),
        lexicon = AssistLexiconSource(
            r.categories, r.merchants, r.people, r.wallets, r.savingsGoals, r.lifeEvents, r.projects, r.recurring, r.assets, r.roscas, r.installmentPlans,
        ),
        ids = c.env.ids,
        clock = c.env.clock,
        add = c.shell.addTransaction,
        people = areas.people.people,
        rules = areas.budgets.rules,
        allocations = r.allocations,
    )

    private suspend fun sources(): AssistantSources {
        val books = runCatching { c.session.spaces() }.getOrDefault(emptyList()).ifEmpty { listOf(c.space to r) }
            .map { (s, repos) -> PersonSpaceBook(s, repos.obligations, repos.settlements) }
        val prices = runCatching { (c.feeds.prices() as? FeedState.Ready)?.feed }.getOrNull()
        return AssistantSources(
            wallets = r.wallets,
            txns = r.transactions,
            profile = r.profile,
            home = areas.home.homeScreen,
            money = LoadMoneySummary(LoadMoneySummaryDeps(r.transactions, r.categories, r.allocations)),
            history = LoadHomeHistory(LoadHomeHistoryDeps(r.transactions, r.allocations, r.categories)),
            budget = areas.budgets.budgetScreen,
            leftover = areas.budgets.leftover,
            cash = areas.home.cash,
            categorySpend = LoadCategorySpend(LoadCategorySpendDeps(r.transactions, r.categories, r.allocations)),
            lastAt = FindLastAtMerchant(r.transactions, r.wallets),
            people = areas.people.overview,
            personAcross = PersonAcrossSpaces(books),
            dues = areas.dues.loadDues,
            debtTerms = r.debtTerms,
            incomeSources = areas.more.incomeSources,
            recurring = areas.dues.recurring,
            sms = c.env.smsInbox?.let { autoRecordSms(c, it) },
            goals = areas.budgets.goalsOverview,
            zakat = areas.investment.zakat,
            payZakat = areas.investment.payZakat,
            zakatPrices = prices?.let { areas.investment.zakat.pricesFrom(it) },
            assets = areas.investment.assets,
            events = areas.people.events,
            projects = areas.people.projects,
            roscas = areas.dues.roscas,
            installments = areas.dues.installments,
            occasions = areas.people.occasions,
            calendar = areas.budgets.calendar,
        )
    }
}
