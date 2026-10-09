package app.masroufy.wiring

import app.masroufy.ui.screens.investment.InvestmentDeps
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.SyncAssetPrices
import app.masroufy.usecase.SyncAssetPricesDeps

/**
 * «الاستثمار» — **الملف ده بتاع المنطقة بس.** الأسعار والمتوسطات من النت ([AreaContext.feeds] — مشتركة بين البلاد) جاهزة؛ ضيف `ManageAssets` ·
 * `ManageAssetGrowth` · `ManageZakat` · `PayZakat` · `AdvisorSignals` · `ManageSavingsGoals` … الحاسبات في [CalculatorsGraph].
 */
class InvestmentGraph(c: AreaContext) : InvestmentDeps {
    override val feeds = c.feeds
    override val syncPrices = SyncAssetPrices(SyncAssetPricesDeps(c.repos.assets, c.repos.assetPrices))
    override val defaultRates = LoadDefaultRates()
    override val calculators = CalculatorsGraph(c, defaultRates)
}
