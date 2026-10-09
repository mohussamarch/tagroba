package app.masroufy.ui.screens.investment

import androidx.compose.runtime.Composable
import app.masroufy.core.TextKey
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.text.t
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.usecase.SyncAssetPrices

/**
 * منطقة «الاستثمار» (`SCREENS.md` §٢.٧ — Investment · AssetDetail · AssetTradeSheet · AssetProjection · Zakat · ZakatPay · الحاسبات ·
 * Advisor · SavingsGoals · GoalDetail). الملف ده بتاع المنطقة بس.
 */
interface InvestmentDeps {
    /**
     * ملفات الأسعار والمتوسطات من النت (`prices.json` · `averages.json`) بنسخة على الجهاز و«آخر تحديث» — `FeedState.Ready` أو
     * `FeedState.Unavailable(السبب)` ⇒ «غير متاح» (مش صفر). «تحديث الأسعار» = `feeds.prices(force = true)`.
     */
    val feeds: LoadOnlineFeeds

    /** يحط أسعار الملف على الأصول المربوطة: `syncPrices.sync(feed)` بعد `feeds.prices()`. */
    val syncPrices: SyncAssetPrices

    /** المعدل الافتراضي لكل نوع بمصدره (حاسبة الادخار و«هتوصل لكام»): `defaultRates.load(averages, بلد، النهارده)`. */
    val defaultRates: LoadDefaultRates
}

fun RouteRegistry.registerInvestment() {
    tabRoot(Tab.INVESTMENT) { InvestmentTab() }
}

@Composable
private fun InvestmentTab() {
    TabScaffold(t(TextKey.TAB_INVESTMENT)) {
        item { EmptyState(t(TextKey.SHELL_PENDING_SCREEN)) }
    }
}
