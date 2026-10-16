package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.AverageEntry
import app.masroufy.core.Currency
import app.masroufy.core.DebtLine
import app.masroufy.core.FeedPrice
import app.masroufy.core.PriceFeed
import app.masroufy.core.FutureChoice
import app.masroufy.core.FutureGoal
import app.masroufy.core.GrowthBucket
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingValue
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestRates
import app.masroufy.core.InvestRatesState
import app.masroufy.core.UserSetting
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.usecase.FX_USD_EGP
import app.masroufy.usecase.FX_USD_SAR
import app.masroufy.usecase.FutureSpaceSource
import app.masroufy.usecase.LoadFutureInputs
import app.masroufy.usecase.LoadFutureInputsDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageBirthDate
import app.masroufy.usecase.ManageInvestView
import app.masroufy.usecase.ProjectInvestFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * «هتبقى معاك كام» مرسومة PNG (بس لو `FUTURE_RENDER_DIR` متظبط) للمقارنة بالنموذج `InvestFuture` · `InvestFutureCards` · `YearWheel`.
 * البيانات مخترعة (نفس أرقام النموذج المخترعة). من غير المجلد: بيتأكد إن الحالة بتتحمل وبتحسب بس.
 */
class FuturePngTest {
    private val clock = FixedClock("2026-10-16T09:00:00.000Z")

    private fun entry(key: String, bp: Int) = AverageEntry(key, bp, "2016", "2025", key != "STOCKS_SA", false, "مصدر تجريبي", null, "test")

    private val rates = InvestRatesState.Ready(InvestRates(174, 1_488, 795, entry("CPI_SA", 174), entry("GOLD_USD", 1_488), entry("STOCKS_SA", 795)))

    private val snapshot = HoldingsSnapshot(
        Currency.SAR,
        listOf(
            HoldingValue("flat", "شقة", HoldingGroup.PROPERTY, HoldingPurpose.INVEST, GrowthBucket.PROPERTY, 57_600_000),
            HoldingValue("gold", "سبائك", HoldingGroup.METAL, HoldingPurpose.INVEST, GrowthBucket.GOLD, 5_256_000),
            HoldingValue("stk", "أسهم", HoldingGroup.SHARES, HoldingPurpose.INVEST, GrowthBucket.SHARES, 8_900_000),
        ),
        goldPriceMissing = false,
    )

    private fun deps(save: Long?, withEgypt: Boolean = false): InvestFutureDeps {
        val settings = MemoryUserSettingsStore(listOf(UserSetting.BirthDate("1990-04-12", "x")))
        val view = ManageInvestView(settings, clock)
        val birth = ManageBirthDate(settings, clock)
        val src = FutureSpaceSource(
            "sa", "SA", Currency.SAR,
            LoadWithYouNow(MemoryWalletRepository(listOf(Wallet("bank", "بنك", Currency.SAR, "bank", 735_000, "2026-01-01"))), MemoryTransactionRepository(), Currency.SAR),
            saving = { save }, income = { 1_596_667 },
            debts = { listOf(DebtLine(3_480_000, 145_000), DebtLine(100_000, 25_000), DebtLine(245_000, 0)) },
            incomeSources = MemoryIncomeSourceRepository(), goals = { listOf(FutureGoal("السفر", "2026-12-28"), FutureGoal("الطوارئ", "2027-06-30")) },
        )
        val others = if (!withEgypt) emptyList() else listOf(
            FutureSpaceSource(
                "eg", "EG", Currency.EGP,
                LoadWithYouNow(MemoryWalletRepository(listOf(Wallet("egb", "بنك", Currency.EGP, "bank", 30_000_000, "2026-01-01"))), MemoryTransactionRepository(), Currency.EGP),
                saving = { 600_000 }, income = { null }, debts = { emptyList() }, incomeSources = MemoryIncomeSourceRepository(), goals = { emptyList() },
                holdings = { HoldingsSnapshot(Currency.EGP, listOf(HoldingValue("egf", "شقة", HoldingGroup.PROPERTY, HoldingPurpose.INVEST, GrowthBucket.PROPERTY, 250_000_000)), false) },
                rates = { InvestRatesState.Ready(InvestRates(1_500, 2_000, 1_800, entry("CPI_EG", 1_500), entry("GOLD_EGP", 2_000), entry("STOCKS_EG", 1_800))) },
            ),
        )
        val feed = PriceFeed(
            "2026-10-15T00:00:00Z", "SAR",
            listOf(FeedPrice(FX_USD_SAR, "دولار", "1", 375, "2026-10-15", "مصدر تجريبي"), FeedPrice(FX_USD_EGP, "دولار", "1", 5_000, "2026-10-15", "مصدر تجريبي", "EGP")),
            emptyList(), emptyList(),
        )
        val inputs = LoadFutureInputs(LoadFutureInputsDeps(src, others, birth, prices = { feed }))
        return object : InvestFutureDeps {
            override val inputs = inputs
            override val project = ProjectInvestFuture()
            override val view = view
            override val birth = birth
            override fun today() = "2026-10-16"
        }
    }

    private suspend fun state(
        scope: CoroutineScope, save: Long?, r: InvestRatesState = rates, marks: List<Int> = emptyList(), withEgypt: Boolean = false,
    ): InvestFutureState {
        val s = InvestFutureState(deps(save, withEgypt), "sa", scope)
        s.load(snapshot, r)
        if (marks.isNotEmpty()) for (y in marks) s.addMark(y.toString())
        var waited = 0
        while (r is InvestRatesState.Ready && (s.projection == null || s.projection!!.choice.marks != marks) && waited < 300) { delay(10); waited++ }
        return s
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun png(name: String, w: Int, h: Int, content: @Composable () -> Unit) {
        val out = System.getenv("FUTURE_RENDER_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        val scene = ImageComposeScene(w * 2, h * 2, Density(2f)) {
            CompositionLocalProvider(LocalReduceMotion provides true, LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(Color(0xFFEFF3F0))) { content() }
            }
        }
        try {
            scene.render()
            File(out, "$name.png").writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test fun rendersTheFutureSections() = runBlocking<Unit> {
        val normal = state(this, save = 180_000, marks = listOf(2030))
        val p = assertNotNull(normal.projection)
        assertEquals(FutureChoice(path = null).years, p.years)
        png("future-normal", 350, 1900) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FutureHero(normal); FuturePathsCard(normal); FutureChartCard(normal); FutureCards(normal)
            }
        }
        val low = state(this, save = -45_000)
        png("future-low", 350, 700) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { FutureHero(low); FuturePathsCard(low) }
        }
        val na = state(this, save = 180_000, r = InvestRatesState.Unavailable(listOf("CPI_SA")))
        png("future-na", 350, 260) { Column(Modifier.padding(16.dp)) { FutureHero(na) } }
        normal.openWheel()
        png("future-wheel", 390, 844) { YearWheel(normal) }
    }

    @Test fun rendersTheTwoCountriesCard() = runBlocking<Unit> {
        val both = state(this, save = 180_000, withEgypt = true)
        var waited = 0
        while (both.countries == null && waited < 300) { delay(10); waited++ }
        val c = assertNotNull(both.countries, "بلدين ومعاهم سعر الصرف ⇒ الكارت ظاهر")
        assertEquals(2, c.rows.size)
        assertNotNull(c.totalMinor, "سعر الصرف موجود ⇒ المجموع محسوب")
        png("future-countries", 350, 1000) { Column(Modifier.padding(16.dp)) { FutureCards(both) } }
        both.setTodayMoney(false)
        waited = 0
        while (both.projection?.choice?.todayMoney != false && waited < 300) { delay(10); waited++ }
        png("future-countries-then", 350, 1000) { Column(Modifier.padding(16.dp)) { FutureCards(both) } }
    }
}
