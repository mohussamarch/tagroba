package app.masroufy.wiring

import app.masroufy.ui.screens.investment.InvestmentDeps
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.usecase.SyncAssetPrices
import app.masroufy.usecase.SyncAssetPricesDeps

/**
 * «الاستثمار» — **الملف ده بتاع المنطقة بس.** الأسعار والمتوسطات من النت ([feeds] — مشتركة بين البلاد) جاهزة؛ ضيف `ManageAssets` ·
 * `ManageAssetGrowth` · `ManageZakat` · `PayZakat` · الحاسبات · `AdvisorSignals` · `ManageSavingsGoals` …
 */
class InvestmentGraph(r: SpaceRepositories, override val feeds: LoadOnlineFeeds) : InvestmentDeps {
    override val syncPrices = SyncAssetPrices(SyncAssetPricesDeps(r.assets, r.assetPrices))
    override val defaultRates = LoadDefaultRates()
}
