package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DebtLine
import app.masroufy.core.FeedPrice
import app.masroufy.core.FutureChoice
import app.masroufy.core.FutureGoal
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.GrowthBucket
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestRatesState
import app.masroufy.core.PayFrequency
import app.masroufy.core.PriceFeed
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مدخلات «هتبقى معاك كام» من مخازن الذاكرة ببيانات مخترعة (مواصفة الاستثمار §2.1 · §4.1). */
class LoadFutureInputsTest {
    private val today = "2026-10-16"
    private val clock = FixedClock("2026-10-16T09:00:00.000Z")
    private val settings = MemoryUserSettingsStore()

    private fun source(id: String, kind: IncomeSourceKind, minor: Long?, currency: Currency = Currency.SAR, ended: String? = null, weekly: Boolean = false) =
        IncomeSource(id, id, id, kind, currency, "2025-01-01", ended, expectedMinor = minor, payFrequency = if (weekly) PayFrequency.WEEKLY else PayFrequency.MONTHLY)

    private fun space(save: Long?, income: Long? = null, holdings: HoldingsSnapshot? = null) = FutureSpaceSource(
        "sa", "SA", Currency.SAR,
        LoadWithYouNow(
            MemoryWalletRepository(listOf(Wallet("bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01"), Wallet("late", "محفظة جديدة", Currency.SAR, "bank", 0, "2026-12-01"))),
            MemoryTransactionRepository(), Currency.SAR,
        ),
        saving = { save }, income = { income }, debts = { listOf(DebtLine(30_000, 1_000)) },
        incomeSources = MemoryIncomeSourceRepository(
            listOf(
                source("rent", IncomeSourceKind.RENT, 5_000), source("old-rent", IncomeSourceKind.RENT, 9_000, ended = "2026-01-01"),
                source("weekly", IncomeSourceKind.INVESTMENT, 700, weekly = true), source("eg", IncomeSourceKind.RENT, 8_000, Currency.EGP),
                source("job", IncomeSourceKind.JOB, 999_999), source("no-amount", IncomeSourceKind.INVESTMENT, null),
            ),
        ),
        goals = { listOf(FutureGoal("سفر", "2027-04-16")) },
        holdings = if (holdings == null) null else { _ -> holdings },
    )

    private val snapshot = HoldingsSnapshot(
        Currency.SAR,
        listOf(
            HoldingValue("g", "سبيكة", HoldingGroup.METAL, HoldingPurpose.INVEST, GrowthBucket.GOLD, 40_000),
            HoldingValue("car", "سيارة", HoldingGroup.PERSONAL, HoldingPurpose.PERSONAL, GrowthBucket.NONE, 900_000),
            HoldingValue("s", "أسهم", HoldingGroup.SHARES, HoldingPurpose.INVEST, GrowthBucket.SHARES, null),
            HoldingValue("land", "أرض", HoldingGroup.PROPERTY, HoldingPurpose.RESERVE, GrowthBucket.PROPERTY, 60_000),
        ),
        goldPriceMissing = false,
    )

    private fun loader(mine: FutureSpaceSource, others: List<FutureSpaceSource> = emptyList(), prices: PriceFeed? = null) =
        LoadFutureInputs(LoadFutureInputsDeps(mine, others, ManageBirthDate(settings, clock), if (prices == null) null else suspend { prices }))

    @Test fun unknownWalletAndUnknownHoldingAreCountedNotZero() = runBlocking<Unit> {
        val i = loader(space(save = 2_000, income = 50_000)).load(today, snapshot, InvestRatesState.Unavailable(listOf("CPI_SA")))
        assertEquals(100_000L, i.cashMinor, "المحفظة اللي ما اتفتحتش برّه")
        assertEquals(2, i.unknownCount, "محفظة + أسهم من غير قيمة")
        assertEquals(60_000L, i.split.propertyMinor)
        assertEquals(40_000L, i.split.goldMinor)
        assertEquals(100_000L + 100_000 - 30_000, i.netNowMinor, "الشخصي برّه (السيارة)")
        assertEquals(5_000L, i.passiveKnownMinor, "إيجار شغال شهري بعملة البلد بس")
        assertEquals(listOf(DebtLine(30_000, 1_000)), i.debts)
        assertEquals(50_000L, i.incomeMinor)
        assertNull(i.ageNow, "تاريخ الميلاد مش مكتوب")
    }

    @Test fun personalThingsCountOnlyWhenTheUserChoseSo() = runBlocking<Unit> {
        val with = loader(space(save = 2_000)).load(today, snapshot.copy(includePersonal = true), InvestRatesState.Unavailable(emptyList()))
        assertEquals(900_000L, with.split.noGrowthMinor, "رد المالك 2a: السيارة داخلة ثابتة من غير نمو")
        assertEquals(100_000L + 100_000 + 900_000 - 30_000, with.netNowMinor)
        val without = loader(space(save = 2_000)).load(today, snapshot, InvestRatesState.Unavailable(emptyList()))
        assertEquals(0L, without.split.noGrowthMinor, "الافتراضي من غيرها")
    }

    @Test fun savingNullMeansUnknownAndAgeComesFromBirthDate() = runBlocking<Unit> {
        ManageBirthDate(settings, clock).save("1990-10-17")
        val i = loader(space(save = null)).load(today, snapshot, InvestRatesState.Unavailable(emptyList()))
        assertTrue(i.saveUnknown)
        assertEquals(listOf(FuturePath.KEEP, FuturePath.MORE), i.paths)
        assertEquals(35, i.ageNow, "عيد ميلاده بكرة")
        assertNull(i.incomeMinor)
    }

    @Test fun otherSpaceWithoutHoldingsIsNotAvailable() = runBlocking<Unit> {
        val other = space(save = 1_000, holdings = null)
        val list = loader(space(save = 1_000), listOf(other)).otherSpaces(today)
        assertEquals(1, list.size)
        assertTrue(list[0].rates is InvestRatesState.Unavailable)
        val view = ProjectInvestFuture().countries(list[0], list, FutureChoice(), null)!!
        assertNull(view.totalMinor)
        assertTrue(view.rows.all { it.valueMinor == null })
    }

    @Test fun fxNeedsBothDollarRows() = runBlocking<Unit> {
        fun row(sym: String, minor: Long) = FeedPrice(sym, sym, "USD", minor, "2026-10-10", "مصدر تجريبي")
        val both = PriceFeed("2026-10-10", "SAR", listOf(row(FX_USD_SAR, 375), row(FX_USD_EGP, 5_225).copy(currency = "EGP")), emptyList(), emptyList())
        assertEquals(139_333L, loader(space(1_000), prices = both).fx()!!.egpPerSar4)
        val onlySar = both.copy(prices = both.prices.take(1))
        assertNull(loader(space(1_000), prices = onlySar).fx(), "سطر الجنيه مش موجود ⇒ غير متاح")
        assertNull(loader(space(1_000)).fx())
    }

    @Test fun investViewKeepsMarksPathAmountAndMix() = runBlocking<Unit> {
        val view = ManageInvestView(settings, clock)
        assertEquals(FutureChoice(), view.load("sa"), "أول مرة ⇒ الافتراضي")
        assertEquals(listOf(2030, 2040), view.addMark("sa", 2040, 2026).let { view.addMark("sa", 2030, 2026) })
        assertEquals(listOf(2030, 2040), view.addMark("sa", 2030, 2026), "من غير تكرار")
        assertFailsWith<IllegalArgumentException> { view.addMark("sa", 2026, 2026) }
        assertFailsWith<IllegalArgumentException> { view.addMark("sa", 2067, 2026) }
        view.save("sa", view.load("sa").copy(path = FuturePath.MORE, amountMinor = 50_000, mix = FutureMix.GOLD, years = 25, todayMoney = false))
        val back = view.load("sa")
        assertEquals(FutureChoice(path = FuturePath.MORE, amountMinor = 50_000, mix = FutureMix.GOLD, marks = listOf(2030, 2040)), back, "المدة وشكل الأرقام ما بيتحفظوش")
        assertEquals(listOf(2040), view.removeMark("sa", 2030))
        assertEquals(FutureChoice(), view.load("eg"), "كل بلد لوحده")
    }
}
