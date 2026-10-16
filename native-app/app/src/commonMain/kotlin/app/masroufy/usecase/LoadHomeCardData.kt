package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.DailySpend
import app.masroufy.core.GoalProgress
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.dailySpend
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.daysBetween
import app.masroufy.core.duesItemsUntil
import app.masroufy.core.goalPercent
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.sumMoney
import app.masroufy.core.toDayNumber
import app.masroufy.core.unpricedDuesUntil
import app.masroufy.core.walletBalancesOn
import app.masroufy.port.ProfileRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * حالات استخدام كروت الرئيسية اللي فيها أرقام (اختيار المالك 2026-10-15 — `core/HomeCards.kt`). الشاشة بتعرض بس (CLAUDE.md #4).
 * كل رقم محسوب أو null ⇒ «غير متاح» (CLAUDE.md #10).
 */

/**
 * «تقدر تصرف النهارده» + شريط الشهر (فات كام يوم من كام). [period] = الشهر المالي بيوم الراتب · [payday] = يوم المرتب الجاي (null = مفيش
 * ملف حساب ⇒ الشهر المالي بيوم 28 الافتراضي والسطر «حتى نهاية الشهر»).
 */
data class DailyAllowanceView(
    val spend: DailySpend,
    val currency: Currency,
    val period: Period,
    val passedDays: Int,
    val payday: IsoDate?,
    /**
     * r041b P4: مفيش ولا سطر التزام بتدفعه قبل المرتب **خالص** ⇒ الشرح بجملة «مفيش التزامات متسجلة» مش «ناقص 0.00» (CLAUDE.md #10).
     * R041B-1: سطر من غير مبلغ أو بعملة تانية = التزام موجود ⇒ ده مش «مفيش» — ده [unpricedDues].
     */
    val noDuesListed: Boolean = false,
    /** R041B-1: فيه التزامات قبل المرتب ما اتطرحتش (من غير مبلغ معروف أو بعملة تانية) ⇒ الكارت يقول كده صراحة. */
    val unpricedDues: Boolean = false,
)

/**
 * «تقدر تصرف النهارده» (رد المالك 2026-10-16): اللي معاك دلوقتي (أرصدة محافظ البلد النهارده — نفس `walletBalancesOn`) − الالتزامات اللي
 * ميعادها قبل يوم المرتب (كل سطور التقويم اللي بتدفعها — `duesUntil`، مش المحجوز بس) ÷ الأيام لحد اليوم اللي قبل المرتب (شامل النهارده).
 * مفيش يوم مرتب في الملف ⇒ لحد آخر الشهر المالي. أي محفظة رصيدها مش معروف (أو مفيش محافظ) ⇒ «غير متاح».
 */
class LoadDailyAllowance(
    private val calendar: LoadCalendar,
    private val wallets: WalletRepository,
    private val txns: TransactionRepository,
    private val profile: ProfileRepository,
    private val currency: Currency,
) {
    suspend fun load(today: IsoDate): DailyAllowanceView {
        val payday = profile.load()?.payday
        val period = periodForDate(today, payday ?: DEFAULT_PAYDAY)
        val items = calendar.items(today, period.end, today)
        val listed = duesItemsUntil(items, today, period.end, currency)
        val unpriced = unpricedDuesUntil(items, today, period.end, currency).isNotEmpty()
        val dues = sumMoney(listed.map { it.amountMinor!! })
        val spend = dailySpend(onHand(today), dues, daysBetween(today, period.end) + 1)
        val next = payday?.let { dayNumberToIso(toDayNumber(parseIsoDate(period.end)) + 1) }
        val passed = daysBetween(period.start, today).coerceIn(0, period.days)
        return DailyAllowanceView(spend, currency, period, passed, next, noDuesListed = listed.isEmpty() && !unpriced, unpricedDues = unpriced)
    }

    private suspend fun onHand(today: IsoDate): Halalas? {
        val list = wallets.listAll().filter { it.currency == currency }
        val from = list.minOfOrNull { it.openingAt } ?: return null
        val rows = if (from > today) emptyList() else txns.listByDateRange(from, today).filter { it.currency == currency }
        val balances = walletBalancesOn(list, rows, today).values
        if (balances.any { it == null }) return null
        return sumMoney(balances.map { it!! })
    }
}

/** سطر في «أكتر حاجة صرفت فيها»: [barPercent] من أكبر تصنيف (0–100). [categoryId] null = «بلا تصنيف». */
data class TopCategoryRow(val categoryId: Id?, val name: String?, val amountMinor: Halalas, val barPercent: Int)

data class TopCategoriesView(val rows: List<TopCategoryRow>, val period: Period)

/**
 * «أكتر حاجة صرفت فيها»: أول [TOP_CATEGORIES_COUNT] بالكتير من نفس الترتيب ([rankSpend]) اللي شاشة «أكثر ما صرفت عليه» بتعرضه كله
 * (طلب المالك 2026-10-16 — OVERRIDES §100). الكارت دايمًا تلاتة بالكتير.
 */
const val TOP_CATEGORIES_COUNT = 3

class LoadTopCategories(private val homeScreen: LoadHomeScreen) {
    suspend fun load(today: IsoDate, payday: Int): TopCategoriesView =
        of(homeScreen.load(LoadHomeScreenRequest(periodForDate(today, payday), today, payday, includeHistory = false)))

    /** من بيانات الرئيسية اللي اتقرت (الكروت بتقرا مرة واحدة للتلات كروت: أكتر حاجة · مراجعة · آخر العمليات). */
    fun of(data: HomeScreenData): TopCategoriesView =
        TopCategoriesView(rankSpend(data).rows.take(TOP_CATEGORIES_COUNT).map { TopCategoryRow(it.categoryId, it.name, it.amountMinor, it.barPercent) }, data.period)

    /**
     * الترتيب كامل لشاشة «أكثر ما صرفت عليه»: الشهر المالي اللي فيه [today] (من يوم الراتب [payday]) أو اللي قبله بـ[monthsBack] —
     * قراية واحدة للفترة بنفس `LoadHomeScreen` اللي الكارت بيقرا منه (من غير آخر ست فترات).
     */
    suspend fun loadAll(today: IsoDate, payday: Int, monthsBack: Int = 0): TopSpendView {
        val current = periodForDate(today, payday)
        val period = if (monthsBack == 0) current else shiftPeriod(current, -monthsBack, payday)
        return rankSpend(homeScreen.load(LoadHomeScreenRequest(period, today, payday, includeHistory = false)))
    }
}

/** سطر في «أهدافك»: [percent] null = المدّخر مش معروف ⇒ «غير متاح». */
data class GoalRow(val goalId: Id, val name: String, val savedMinor: Halalas?, val targetMinor: Halalas, val currency: Currency, val percent: Int?)

/** «أهدافك»: أقرب [GOALS_ON_HOME] خطط (المميزة بنجمة الأول، وبعدها الأقرب ميعادًا) من `LoadGoalsOverview`. */
const val GOALS_ON_HOME = 2

class LoadGoalsProgress(private val overview: LoadGoalsOverview) {
    suspend fun load(today: IsoDate): List<GoalRow> = of(overview.load(today))

    fun of(goals: List<GoalProgress>): List<GoalRow> =
        goals.sortedWith(compareBy<GoalProgress> { if (it.goal.starred) 0 else 1 }.thenBy { it.goal.targetDate })
            .take(GOALS_ON_HOME)
            .map { g -> GoalRow(g.goal.id, g.goal.name, g.savedMinor, g.goal.targetMinor, g.goal.currency, goalPercent(g.savedMinor, g.goal.targetMinor)) }
}
