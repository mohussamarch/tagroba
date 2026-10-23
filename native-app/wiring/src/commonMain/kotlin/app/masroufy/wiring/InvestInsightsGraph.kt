package app.masroufy.wiring

import app.masroufy.ui.screens.investment.insights.InvestInsightsDeps
import app.masroufy.usecase.LoadHoldings
import app.masroufy.usecase.LoadHoldingsDeps
import app.masroufy.usecase.LoadIncomeRope
import app.masroufy.usecase.LoadIncomeRopeDeps
import app.masroufy.usecase.LoadInvestReturns
import app.masroufy.usecase.LoadInvestReturnsDeps
import app.masroufy.usecase.LoadInvestTip
import app.masroufy.usecase.LoadInvestTipDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageAssetsDeps

/**
 * حزمة INSIGHTS في «الاستثمار» الجديد (مواصفة النقل §4.3) — **الملف ده بتاع الحزمة بس**. نفس اعتمادات اختبارات `:app`: النصايح المقفولة
 * في `userSettings` على الحساب · العمليات والتصنيفات والتوزيع على الأشخاص للبلد · مصادر الدخل والتجار.
 */
class InvestInsightsGraph(c: AreaContext) : InvestInsightsDeps {
    private val r = c.repos
    private val env = c.env

    override val tips = LoadInvestTip(
        LoadInvestTipDeps(
            r.assistant.settings, r.transactions, r.allocations, r.categories, LoadWithYouNow(r.wallets, r.transactions, c.space.currency),
            r.profile, c.space.currency, c.space.countryCode, env.clock,
        ),
    )

    override val rope = LoadIncomeRope(LoadIncomeRopeDeps(r.incomeSources, r.transactions, r.merchants))

    override val returns = LoadInvestReturns(LoadInvestReturnsDeps(r.transactions, r.categories))

    override fun today() = env.today()
}

/** «حاجاتي» للبلد (نسخة INSIGHTS لحد ما «الخطوة 0» تتدمج — §4.0 بند 4): نفس `ManageAssets` بتاع «الاستثمار». */
internal fun holdingsOf(c: AreaContext): LoadHoldings {
    val r = c.repos
    val assets = ManageAssets(ManageAssetsDeps(r.assets, r.assetLots, r.assetSales, r.assetPrices, c.env.ids, c.env.clock, c.space.countryCode))
    return LoadHoldings(LoadHoldingsDeps(assets, r.zakatFacts, c.space.currency))
}
