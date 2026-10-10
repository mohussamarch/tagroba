package app.masroufy.ui.screens.investment

import app.masroufy.core.GoalProgress
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatFact
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.investment.calc.CalculatorsDeps
import app.masroufy.ui.screens.investment.calc.registerCalculators
import app.masroufy.usecase.LoadDefaultRates
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.usecase.ManageAssetGrowth
import app.masroufy.usecase.ManageAssets
import app.masroufy.usecase.ManageZakat
import app.masroufy.usecase.PayZakat
import app.masroufy.usecase.RunAlertEngine
import app.masroufy.usecase.SyncAssetPrices
import app.masroufy.usecase.TransactionsScreenData

/**
 * منطقة «الاستثمار» (`SCREENS.md` §2.7 — Investment · AssetDetail · AssetTradeSheet · AssetProjection · Zakat · ZakatPay · الحاسبات ·
 * Advisor · SavingsGoals · GoalDetail). الملف ده بتاع المنطقة بس. **حالات استخدام بس** (CLAUDE.md #4) — التنفيذ في `:wiring` (`InvestmentGraph`).
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

    /** الأصول: `listPortfolio` · `addAsset` · `linkToFeed` · `archiveAsset` · `recordPurchase` · `recordSale` · `setPrice`. */
    val assets: ManageAssets

    /** «الصورة كاملة»: `setProfile` · `updatePricePerSqm` · `project`. */
    val growth: ManageAssetGrowth

    /** الزكاة: `visible` · `scopeNote` · `rules` · `pricesFrom` · `setAssetFacts` · `suggestDate` · `confirmDate` · `openYear` · `assess` · `close`. */
    val zakat: ManageZakat

    /** دفع الزكاة: `status` · `payments` · `pay` · `payCash` · `unlink`. */
    val payZakat: PayZakat

    /** محرك التنبيهات (مشترك مع الهيكل): `inbox` (مجموعة المساعد) · `opened` · `setGroupEnabled`. */
    val alerts: RunAlertEngine

    /** النهارده بتوقيت الجوال. */
    fun today(): IsoDate

    /** عمليات الشهر المالي الحالي (للربط بشراء أو بيع أصل أو دفعة زكاة) — `LoadTransactionsScreen.load(النهارده، يوم الراتب)`. */
    suspend fun recentOperations(): TransactionsScreenData

    /** خطط الادخار النهارده (عدّها في قايمة «الاستثمار») — `LoadGoalsOverview.load`. */
    suspend fun goals(): List<GoalProgress>

    /** مجموعة «المساعد المالي» شغالة؟ (مفتاح «التحليلات الذكية»). ⚠️ مفيش حالة استخدام بتقرا المجموعات المقفولة — `InvestmentGraph` بيقراها. */
    suspend fun advisorEnabled(): Boolean

    /** «هل يُرجى سداده؟» على دين ليك بمعرّفه بس — `ManageZakat.setReceivableFact` محتاج صاحب الدين، و`InvestmentGraph` بيلاقيه. */
    suspend fun setReceivableFact(obligationId: Id, collectability: ZakatCollectability): ZakatFact

    /** الحاسبات (الادخار · التقاعد · الورث) — ملفاتها في `investment/calc/` (`CalcRoutes.kt`). */
    val calculators: CalculatorsDeps
}

/** تفاصيل أصل (من «الاستثمار»). */
data class AssetDetailRoute(val assetId: Id) : Route {
    override val name = "AssetDetail"
}

/** «الصورة كاملة» للأصل — والعقار وتوقّعه. */
data class AssetProjectionRoute(val assetId: Id) : Route {
    override val name = "AssetProjection"
}

object ZakatRoute : Route {
    override val name = "Zakat"
}

/**
 * «دفع زكاة السنة» لسنة متثبّتة بمعرّفها (= يوم ميعادها). في النموذج `ZakatPay` مرسومة **جوه** `Zakat` بعد «ثبّت» (وده اللي بيتعرض
 * هناك)؛ المسار ده عشان أي مكان تاني (تنبيه الزكاة مثلًا) يفتح دفع سنة بعينها.
 */
data class ZakatPayRoute(val yearId: Id) : Route {
    override val name = "ZakatPay"
}

object AdvisorRoute : Route {
    override val name = "Advisor"
}

// الدمج (app-integration): روابط «خطط الادخار» والحاسبات بقت المسارات الحقيقية — `SavingsGoalsRoute` من `screens/budgets` والحاسبات
// من `CalculatorRoutes.kt` (متسجّلة في `registerCalculators`).

fun RouteRegistry.registerInvestment() {
    tabRoot(Tab.INVESTMENT) { InvestmentScreen() }
    screen<AssetDetailRoute> { r -> AssetDetailScreen(r.assetId) }
    screen<AssetProjectionRoute> { r -> AssetProjectionScreen(r.assetId) }
    screen<ZakatRoute> { ZakatScreen() }
    screen<ZakatPayRoute> { r -> ZakatPayScreen(r.yearId) }
    screen<AdvisorRoute> { AdvisorScreen() }
    registerCalculators()
}
