package app.masroufy.ui.screens.investment

import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatPrices
import app.masroufy.core.isRealEstate
import app.masroufy.core.uiText
import app.masroufy.usecase.AssetProjectionView
import app.masroufy.usecase.DefaultRatesView
import app.masroufy.usecase.FeedState

/**
 * التحميل لكل شاشة في المنطقة: **حالات الاستخدام ⇒ شكل الشاشة** (`…Ui`) من غير Compose — الشاشة بتنادي الدالة وبس، والاختبار
 * (`InvestmentScreensTest` في `:wiring`) بينادي نفس الدالة على التجميع الحقيقي بمستودعات الذاكرة. مفيش حساب فلوس هنا (CLAUDE.md #4).
 */

/** «الاستثمار»: [ui] null = الأصول ما اتحمّلتش · [feedProblem] شريط «تعذّر تحديث الأسعار» · [zakatHint] null = الزكاة مخفية · [skipped] أصول مربوطة ما اتحدّثتش. */
data class InvestmentLoad(val ui: InvestmentUi?, val feedProblem: String?, val zakatHint: String?, val goals: Int?, val skipped: Int) {
    val failed: Boolean get() = ui == null
}

/** المعدلات الافتراضية لكل نوع بمصدرها (من ملف المتوسطات — مش متاح ⇒ «غير متاح» بسببه جوه حالة الاستخدام). */
suspend fun defaultRatesFor(deps: InvestmentDeps, space: Space): DefaultRatesView {
    val averages = (runCatching { deps.feeds.averages() }.getOrNull() as? FeedState.Ready)?.feed
    return deps.defaultRates.load(averages, space.countryCode, deps.today())
}

/** بيحمّل «الاستثمار»: الأسعار (ويحطها على الأصول المربوطة) ⇒ الأصول ⇒ توقّع كل عقار ⇒ الزكاة والخطط. [force] = «تحديث الأسعار». */
suspend fun loadInvestment(deps: InvestmentDeps, space: Space, force: Boolean = false): InvestmentLoad {
    val today = deps.today()
    val prices = runCatching { deps.feeds.prices(force) }.getOrNull()
    val skipped = if (prices is FeedState.Ready) runCatching { deps.syncPrices.sync(prices.feed).skipped.size }.getOrDefault(0) else 0
    val problem = feedProblem(prices)
    val zakatHint = runCatching {
        if (deps.zakat.visible()) deps.zakat.rules().firstOrNull()?.source?.authority?.label?.let { uiText(TextKey.INVEST_TOOL_ZAKAT_HINT, it) } else null
    }.getOrNull()
    val goals = runCatching { deps.goals().size }.getOrNull()
    val view = runCatching { deps.assets.listPortfolio(today) }.getOrNull() ?: return InvestmentLoad(null, problem, zakatHint, goals, skipped)
    val estates = view.rows.filter { it.asset.isRealEstate && !it.asset.archived }
    val defaults = if (estates.isEmpty()) null else defaultRatesFor(deps, space)
    val projections = estates.mapNotNull { row ->
        runCatching { row.asset.id to deps.growth.project(row.asset.id, defaultSellYear(today), space.countryCode, defaults!!) }.getOrNull()
    }.toMap()
    return InvestmentLoad(investmentUi(view, projections, space.currency, today), problem, zakatHint, goals, skipped)
}

/** «تحديث الأسعار» من شريط «تعذّر التحديث»: الملف من النت دلوقتي ⇒ على الأصول المربوطة. الرسالة للمستخدم (اتحدّثت · أو السبب). */
suspend fun refreshPrices(deps: InvestmentDeps): String = when (val feed = deps.feeds.prices(force = true)) {
    is FeedState.Ready -> {
        deps.syncPrices.sync(feed.feed)
        feed.refreshFailed?.let { uiText(TextKey.INVEST_ERROR_BODY) } ?: uiText(TextKey.INVEST_REFRESHED)
    }
    is FeedState.Unavailable -> feed.reason
}

/** «تفاصيل الأصل» بالمعرّف: صف الأصل من المحفظة + «الصورة كاملة» للعقار. null = مش موجود (أو اتمسح). */
suspend fun loadAssetDetail(deps: InvestmentDeps, space: Space, assetId: Id): AssetDetailUi? {
    val today = deps.today()
    val row = deps.assets.listPortfolio(today).rows.firstOrNull { it.asset.id == assetId } ?: return null
    val projection = if (row.asset.isRealEstate) runCatching {
        deps.growth.project(assetId, defaultSellYear(today), space.countryCode, defaultRatesFor(deps, space))
    }.getOrNull() else null
    return assetDetailUi(row, projection, row.asset.currency, today)
}

/** «الصورة كاملة» لسنة بيع [year] — [typedRateBp] النسبة المكتوبة دلوقتي من غير حفظ (null = المحفوظة أو الافتراضية). */
suspend fun loadProjection(deps: InvestmentDeps, space: Space, assetId: Id, year: Int, typedRateBp: Int?, defaults: DefaultRatesView): AssetProjectionView =
    deps.growth.project(assetId, year, space.countryCode, defaults, typedRateBp)

/** اللي شاشة الزكاة جابته: ظاهرة؟ · الحساب · الأسئلة · دفع السنة اللي اتثبّتت (لو فيه). */
data class ZakatLoad(val visible: Boolean, val data: ZakatData?, val ui: ZakatUi?, val questions: List<FactQuestion>, val pay: ZakatPayUi?)

/** أسعار الذهب والفضة للزكاة من ملف الأسعار (بعملة البلد بس) — الملف مش متاح ⇒ السعرين `null` ⇒ النصاب «غير متاح». */
suspend fun zakatPrices(deps: InvestmentDeps): ZakatPrices =
    deps.zakat.pricesFrom((runCatching { deps.feeds.prices() }.getOrNull() as? FeedState.Ready)?.feed)

suspend fun loadZakat(deps: InvestmentDeps, space: Space): ZakatLoad {
    if (!deps.zakat.visible()) return ZakatLoad(false, null, null, emptyList(), null)
    val today = deps.today()
    val prices = zakatPrices(deps)
    val open = deps.zakat.openYear()
    val data = ZakatData(
        currency = space.currency,
        scopeNote = deps.zakat.scopeNote(),
        rules = deps.zakat.rules(),
        prices = prices,
        openYear = open,
        suggestion = runCatching { deps.zakat.suggestDate(today, prices) }.getOrNull(),
        assessment = open?.let { deps.zakat.assess(it.id, today, prices) },
    )
    // السنة اللي اتثبّتت: `close` بيفتح اللي بعدها وبدايتها = ميعاد المتثبّتة = معرّفها. سنة مفتوحة من «أكّده» ⇒ مفيش متثبّتة ⇒ من غير دفع.
    val pay = open?.hawlStart?.let { closedId -> runCatching { loadZakatPay(deps, space, closedId) }.getOrNull() }
    return ZakatLoad(true, data, zakatUi(data, today), factQuestions(data.assessment, space.currency), pay)
}

/** «دفع زكاة السنة» لسنة متثبّتة بمعرّفها (= يوم ميعادها). سنة مش متثبّتة ⇒ رسالة حالة الاستخدام (`PayZakat.status` بيرفض). */
suspend fun loadZakatPay(deps: InvestmentDeps, space: Space, yearId: Id): ZakatPayUi {
    val status = deps.payZakat.status(yearId)
    return zakatPayUi(yearId, yearId, status, deps.payZakat.payments(yearId), runCatching { deps.recentOperations() }.getOrNull(), space.currency)
}

/** «التحليلات الذكية»: كروت مجموعة المساعد من صفحة الإشعارات + المفتاح. القراية فشلت ⇒ زي «ساكت» (مفيش كروت مخترعة). */
suspend fun loadAdvisor(deps: InvestmentDeps): AdvisorUi {
    val enabled = runCatching { deps.advisorEnabled() }.getOrDefault(true)
    return advisorUi(runCatching { deps.alerts.inbox() }.getOrDefault(emptyList()), enabled)
}
