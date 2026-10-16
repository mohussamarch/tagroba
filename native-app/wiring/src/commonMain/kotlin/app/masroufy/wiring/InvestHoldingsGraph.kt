package app.masroufy.wiring

import app.masroufy.core.feedPriceIn
import app.masroufy.ui.screens.investment.holdings.InvestHoldingsDeps
import app.masroufy.usecase.AddHolding
import app.masroufy.usecase.AddHoldingDeps
import app.masroufy.usecase.FeedPriceLookup
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.ManageAssetGrowth
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageZakat
import app.masroufy.usecase.MetalFactsWriter
import app.masroufy.usecase.PreviewHoldingValue
import app.masroufy.usecase.ProjectHoldingGroup
import app.masroufy.usecase.SetHoldingPurpose

/**
 * «حاجاتي» (مواصفة r05 §4.2) — **الملف ده بتاع حزمة HOLDINGS بس.** نفس حالات الاستخدام اللي `InvestmentGraph` بانيها (الأصول ·
 * «الصورة كاملة» · الزكاة) عشان الكتابة تعدّي من نفس الطريق: سعر المعدن من ملف الأسعار (`feedPriceIn` بعملة البلد) · وقائع المعادن من
 * `ManageZakat.setAssetFacts` (مصدر واحد مع الزكاة).
 */
class InvestHoldingsGraph(c: AreaContext, assets: ManageAssets, growth: ManageAssetGrowth, zakat: ManageZakat) : InvestHoldingsDeps {
    private val env = c.env
    private val lookup = FeedPriceLookup { symbol, currency ->
        (c.feeds.prices() as? FeedState.Ready)?.feed?.let { feedPriceIn(it, symbol, currency) }
    }
    override val add = AddHolding(
        AddHoldingDeps(
            assets = assets, growth = growth, assetRepo = c.repos.assets, lots = c.repos.assetLots, prices = c.repos.assetPrices,
            feedPrice = lookup,
            metalFacts = MetalFactsWriter { id, purpose, karat, fineness -> zakat.setAssetFacts(id, purpose = purpose, karat = karat, fineness = fineness) },
            currency = c.space.currency, uow = c.repos.uow, ids = env.ids,
        ),
    )
    override val preview = PreviewHoldingValue(lookup)
    override val group = ProjectHoldingGroup()
    override val purpose = SetHoldingPurpose(c.repos.assets)
    override fun today() = env.today()
}
