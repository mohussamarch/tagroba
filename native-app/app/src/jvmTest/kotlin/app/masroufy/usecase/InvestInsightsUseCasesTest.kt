package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestRatesState
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.UserSetting
import app.masroufy.core.VERIFIED_TIPS
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatFactRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** حالات استخدام INSIGHTS على مستودعات الذاكرة (CLAUDE.md #6) — بيانات مخترعة بس. */
class InvestInsightsUseCasesTest {
    private val clock = FixedClock("2026-10-16T10:00:00.000Z")
    private val today = "2026-10-16"
    private var seq = 0

    private fun txn(date: String, amount: Long, kind: EconomicKind, dir: Direction, note: String? = null) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", note = note,
    )

    /** 3 شهور مالية كاملة (يوم الراتب 28): دخل 10,000 وصرف 3,000 كل شهر ⇒ بيحوّش ومش بيستثمر. */
    private fun months() = listOf("2026-07-01", "2026-08-01", "2026-09-01").flatMap { d ->
        listOf(txn(d, 1_000_000, EconomicKind.SALARY, Direction.IN), txn(d, 300_000, EconomicKind.PURCHASE, Direction.OUT))
    }

    private fun tips(settings: MemoryUserSettingsStore, txns: MemoryTransactionRepository) = LoadInvestTip(
        LoadInvestTipDeps(
            settings, txns, MemoryAllocationRepository(), MemoryCategoryRepository(), LoadWithYouNow(MemoryWalletRepository(), txns, Currency.SAR),
            null, Currency.SAR, "SA", clock,
        ),
    )

    @Test
    fun situationTipFirstAndClosedTipNeverComesBack() = runBlocking {
        val settings = MemoryUserSettingsStore()
        val txns = MemoryTransactionRepository(months())
        val first = assertNotNull(tips(settings, txns).current(today, null))
        assertEquals("small-regular-amounts-over-time", first.id) // بيحوّش 7,000 في الشهر ومفيش شراء أصل
        assertEquals("تدّخر كل شهر ولا تستثمر", first.why)
        assertTrue(first.source.startsWith("المصدر: "))
        tips(settings, txns).close("small-regular-amounts-over-time")
        assertEquals("match-money-to-horizon", tips(settings, txns).current(today, null)!!.id) // جوال تاني: نفس المقفول
        assertEquals(listOf("small-regular-amounts-over-time"), settings.listAll().filterIsInstance<UserSetting.InvestTips>().single().closed)
        // اشترى أصل الشهر ده ⇒ مش «مش بيستثمر» (ولو ما اتقفلتش)
        val bought = MemoryTransactionRepository(months() + txn("2026-10-02", 50_000, EconomicKind.ASSET_BUY, Direction.OUT))
        assertEquals("track-spending-first", tips(MemoryUserSettingsStore(), bought).current(today, null)!!.id) // أول العامة
        // صفحة النصايح: كلها (المقفولة كمان) بمصدرها
        val all = tips(settings, txns).all(Space("sa", "x", "SA", Currency.SAR, "x"))
        assertEquals(VERIFIED_TIPS.count { it.country != "eg" }, all.sumOf { it.tips.size }) // نصايح مصر بس مش هنا
        assertEquals(5, all.size)
        VERIFIED_TIPS.forEach { tips(settings, txns).close(it.id) }
        assertNull(tips(settings, txns).current(today, null)) // مفيش نصيحة باقية ⇒ الكارت ما بيترسمش
    }

    @Test
    fun ropeAndReturnsReadTheSpaceCurrencyOnly() = runBlocking {
        val sources = MemoryIncomeSourceRepository(
            listOf(
                IncomeSource("j", "شركة وهمية", "x", IncomeSourceKind.JOB, Currency.SAR, "2024-01-01"),
                IncomeSource("e", "جهة مصرية", "x", IncomeSourceKind.RENT, Currency.EGP, "2025-01-01"),
            ),
        )
        val txns = MemoryTransactionRepository(
            listOf(
                txn("2026-03-04", 40_000, EconomicKind.PERSONAL_SALE, Direction.IN, note = "دراجة"),
                txn("2026-02-01", 60_000, EconomicKind.INVESTMENT_INCOME, Direction.IN),
                txn("2026-08-01", 40_000, EconomicKind.INVESTMENT_INCOME, Direction.IN),
            ),
        )
        val rope = assertNotNull(LoadIncomeRope(LoadIncomeRopeDeps(sources, txns, MemoryMerchantRepository())).load(today, Currency.SAR))
        assertEquals(1, rope.strands.size)
        assertEquals(listOf("دراجة"), rope.knots.map { it.label })
        assertNull(LoadIncomeRope(LoadIncomeRopeDeps(MemoryIncomeSourceRepository(), txns, MemoryMerchantRepository())).load(today, Currency.SAR))
        // راتب متسجّل كعملية من غير مصدر دخل ⇒ خيط الوظيفة (سموك r05: كان «لسه مفيش دخل متسجّل»)
        val salary = MemoryTransactionRepository(listOf(txn("2026-09-27", 900_000, EconomicKind.SALARY, Direction.IN, note = "شركة مخترعة")))
        val fromTxns = assertNotNull(LoadIncomeRope(LoadIncomeRopeDeps(MemoryIncomeSourceRepository(), salary, MemoryMerchantRepository())).load(today, Currency.SAR))
        assertEquals(listOf(IncomeSourceKind.JOB), fromTxns.strands.map { it.kind })
        assertEquals("شركة مخترعة", fromTxns.strands.single().name)
        assertTrue(fromTxns.strands.single().active)
        // فيه مصدر وظيفة متسجّل ⇒ الراتب ما بيعملش خيط تاني
        assertEquals(1, LoadIncomeRope(LoadIncomeRopeDeps(sources, salary, MemoryMerchantRepository())).load(today, Currency.SAR)!!.strands.size)
        val returns = LoadInvestReturns(LoadInvestReturnsDeps(txns, MemoryCategoryRepository()))
        val unavailable = assertNotNull(returns.load(today, Currency.SAR, null, InvestRatesState.Unavailable(listOf("CPI_SA"))))
        assertEquals(100_000, unavailable.totalMinor)
        assertNull(unavailable.math) // المتوسطات ناقصة ⇒ «غير متاح» مش صفر
        assertEquals(2, unavailable.rows.single().sub.let { if (it.contains(" و")) 2 else 0 }) // «مرتين: فبراير وأغسطس»
        assertNull(returns.load(today, Currency.EGP, null, null))
    }

    @Test
    fun homeTodayComparesTheSameItemsWithThePreviousDay() = runBlocking {
        val assets = MemoryAssetRepository()
        val prices = MemoryAssetPriceRepository()
        val manage = ManageAssets(ManageAssetsDeps(assets, MemoryAssetLotRepository(), MemoryAssetSaleRepository(), prices, SequentialIdGenerator(), clock))
        val gold = manage.addAsset(NewAsset("سبيكة وهمية", "gold"))
        manage.recordPurchase(PurchaseInput(gold.id, "2026-01-01", 10 * QUANTITY_SCALE, 2_000_000))
        manage.setPrice(gold.id, 250_000, "2026-10-15")
        val settings = MemoryUserSettingsStore()
        fun load(@Suppress("UNUSED_PARAMETER") day: String) = LoadInvestToday(
            LoadInvestTodayDeps(settings, LoadHoldings(LoadHoldingsDeps(manage, MemoryZakatFactRepository(), Currency.SAR)), Currency.SAR, clock),
        )
        assertNull(runBlocking { load("2026-10-15").load("sa", "2026-10-15") }) // أول يوم ⇒ «غير متاح»
        manage.setPrice(gold.id, 260_000, "2026-10-16")
        val other = manage.addAsset(NewAsset("سهم وهمي", "stock")) // اتضاف النهارده ⇒ مش مكسب
        manage.recordPurchase(PurchaseInput(other.id, "2026-10-16", QUANTITY_SCALE, 900_000))
        manage.setPrice(other.id, 900_000, "2026-10-16")
        val v = assertNotNull(load(today).load("sa", today))
        assertEquals(100_000, v.gainMinor) // 10 جرام × 100 ر.س
        assertEquals(400, v.pctBp) // 100,000 ÷ 2,500,000
        assertNull(v.sinceDate) // من امبارح
        assertTrue(settings.listAll().filterIsInstance<UserSetting.InvestDaily>().single().prevIds.contains(gold.id))
    }

    private companion object {
        val INVEST_IDS = listOf("emergency", "monthly", "compound", "spread", "daytrade", "influencer", "fraud", "halal")
    }
}
