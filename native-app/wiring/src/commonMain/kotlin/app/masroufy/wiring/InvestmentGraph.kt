package app.masroufy.wiring

import app.masroufy.core.AlertGroup
import app.masroufy.core.GoalProgress
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatError
import app.masroufy.core.ZakatFact
import app.masroufy.core.uiText
import app.masroufy.ui.screens.investment.InvestmentDeps
import app.masroufy.usecase.GoalLedger
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadGoalsOverviewDeps
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.ManageAssetGrowth
import app.masroufy.usecase.ManageAssetGrowthDeps
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageAssetsDeps
import app.masroufy.usecase.ManageZakat
import app.masroufy.usecase.ManageZakatDeps
import app.masroufy.usecase.PayZakat
import app.masroufy.usecase.PayZakatDeps
import app.masroufy.usecase.SyncAssetPrices
import app.masroufy.usecase.SyncAssetPricesDeps
import app.masroufy.usecase.TransactionsScreenData

/**
 * «الاستثمار» — **الملف ده بتاع المنطقة بس.** كل حالة استخدام بتتبني من [AreaContext] بنفس اعتماداتها في اختبارات `:app`:
 * الأسعار من النت ([AreaContext.feeds] — مشتركة بين البلاد) · الأصول · «الصورة كاملة» · الزكاة ودفعها · محرك التنبيهات (نفس اللي في
 * الهيكل — [AreaContext.shell]) · عمليات الشهر للربط · ملخص خطط الادخار.
 */
class InvestmentGraph(private val c: AreaContext) : InvestmentDeps {
    private val r = c.repos
    override val feeds = c.feeds
    override val syncPrices = SyncAssetPrices(SyncAssetPricesDeps(r.assets, r.assetPrices))
    override val defaultRates = LoadDefaultRates()
    override val assets = ManageAssets(ManageAssetsDeps(r.assets, r.assetLots, r.assetSales, r.assetPrices, c.env.ids, c.env.clock))
    override val growth = ManageAssetGrowth(ManageAssetGrowthDeps(r.assets, r.assetLots, r.assetSales, r.assetPrices, c.env.clock))
    override val zakat = ManageZakat(
        ManageZakatDeps(
            c.space.countryCode, c.space.currency, r.profile, r.wallets, r.transactions, r.assets, r.assetLots, r.assetSales, r.assetPrices,
            r.people, r.obligations, r.settlements, r.roscas, r.roscaEntries, r.zakatFacts, r.zakatYears, r.uow, c.env.clock, spaces = r.spaces,
        ),
    )

    // ⚠️ `spaceLegs` (رجول التحويل لنفسك بين البلدين) محتاج `TransferBetweenSpaces` على مستوى الحساب — مش متوصل هنا لسه (null = من غير
    // فحص الرجل؛ نفس اللي `DueLinks` بيعمله من غيره). HANDOVER «ناقص».
    override val payZakat = PayZakat(
        PayZakatDeps(
            r.zakatYears, r.zakatPayments, r.transactions, r.roscaEntries, r.installmentPayments, r.installmentPlans, r.categories, r.uow,
            c.env.ids, c.env.clock, eventLinks = r.eventLinks,
        ),
    )
    override val alerts = c.shell.engine

    private val operations = LoadTransactionsScreen(
        LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants, r.tags, r.transactionTags),
    )
    private val goalsOverview = LoadGoalsOverview(
        LoadGoalsOverviewDeps(r.savingsGoals, r.goalContributions, listOf(GoalLedger(c.space.id, r.wallets, r.transactions))),
    )

    override fun today() = c.env.today()

    override suspend fun recentOperations(): TransactionsScreenData =
        operations.load(LoadTransactionsScreenRequest(today = c.env.today(), payday = c.shell.profile.load().payday))

    override suspend fun goals(): List<GoalProgress> = goalsOverview.load(c.env.today())

    // ⚠️ مفيش حالة استخدام بتقرا المجموعات المقفولة (`RunAlertEngine` بيكتب بس — `setGroupEnabled`) ⇒ القراية هنا من نفس المخزن اللي
    // المحرك بيقرا منه. تتنقل لحالة استخدام لما «إعدادات الإشعارات» تتبني (HANDOVER «ناقص»).
    override suspend fun advisorEnabled(): Boolean = AlertGroup.ADVISOR !in r.alertSettings.disabledGroups()

    // ⚠️ `ManageZakat.setReceivableFact` محتاج صاحب الدين، والحساب (`ZakatHolding.Receivable`) بيدّي الدين بس ⇒ بنلاقي صاحبه هنا.
    override suspend fun setReceivableFact(obligationId: Id, collectability: ZakatCollectability): ZakatFact {
        val owner = r.people.listAll().firstOrNull { p -> r.obligations.listByPerson(p.id).any { it.id == obligationId } }
            ?: throw ZakatError(uiText(TextKey.ZAKAT_RECEIVABLE_NOT_FOUND))
        return zakat.setReceivableFact(owner.id, obligationId, collectability)
    }
}
