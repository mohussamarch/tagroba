package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatAssessment
import app.masroufy.core.ZakatItemStatus
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatTopic
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.emptyProfile
import app.masroufy.core.nextZakatDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatFactRepository
import app.masroufy.memory.MemoryZakatYearRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * الجمعية في مصر = زي الدين ليك (رد المالك §62، فتوى 4399): اللي دفعته وما قبضتوش برا الحساب السنوي، ولما تقبض دورك جوه السنة
 * سطر «اتحصّل» مرة واحدة على اللي كان **من فلوسك**. السعودية ما اتغيرتش. كل الأسامي والمبالغ مخترعة.
 *
 * البنك 30,000 (2025-01-01) · دفعت 1,000 تلات مرات (أبريل · مايو · يونيو) · قبضت الدور 10,000 (2025-09-15) · دفعت 1,000 (أكتوبر).
 * يوم الميعاد: الكاش 36,000 · فلوسك في القبض 3,000.
 */
class ZakatRoscaPayoutTest {
    private val prices = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    private fun txn(id: String, date: String, dir: Direction, amount: Long, currency: Currency) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )

    private fun zakat(country: String, currency: Currency, payout: Boolean = true, opening: String = "2025-01-01"): ManageZakat {
        val txns = listOf(
            txn("t-c1", "2025-04-01", Direction.OUT, 100_000, currency), txn("t-c2", "2025-05-01", Direction.OUT, 100_000, currency),
            txn("t-c3", "2025-06-01", Direction.OUT, 100_000, currency), txn("t-p", "2025-09-15", Direction.IN, 1_000_000, currency),
            txn("t-c4", "2025-10-01", Direction.OUT, 100_000, currency),
        ).filter { payout || it.id != "t-p" }
        val entries = listOf(
            RoscaEntry("e-1", "r-1", "t-c1", RoscaEntryKind.CONTRIBUTION, 100_000), RoscaEntry("e-2", "r-1", "t-c2", RoscaEntryKind.CONTRIBUTION, 100_000),
            RoscaEntry("e-3", "r-1", "t-c3", RoscaEntryKind.CONTRIBUTION, 100_000), RoscaEntry("e-p", "r-1", "t-p", RoscaEntryKind.PAYOUT, 1_000_000),
            RoscaEntry("e-4", "r-1", "t-c4", RoscaEntryKind.CONTRIBUTION, 100_000),
        ).filter { payout || it.id != "e-p" }
        val years = MemoryZakatYearRepository()
        return ManageZakat(
            ManageZakatDeps(
                countryCode = country, currency = currency, profile = MemoryProfileRepository(emptyProfile()),
                wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", currency, "bank", 3_000_000, opening))),
                txns = MemoryTransactionRepository(txns), assets = MemoryAssetRepository(), lots = MemoryAssetLotRepository(),
                sales = MemoryAssetSaleRepository(), prices = MemoryAssetPriceRepository(), people = MemoryPersonRepository(),
                obligations = MemoryObligationRepository(), settlements = MemorySettlementRepository(),
                roscas = MemoryRoscaRepository(listOf(Rosca("r-1", "جمعية وهمية", currency, 100_000, 1, "2025-04-01", 10, listOf(6), 1_000_000, createdAt = "x"))),
                roscaEntries = MemoryRoscaEntryRepository(entries), facts = MemoryZakatFactRepository(), years = years,
                uow = MemoryUnitOfWork(listOf(years)), clock = FixedClock("2026-02-18T09:00:00.000Z"),
            ),
        )
    }

    private suspend fun assessFrom(m: ManageZakat, start: String, today: String): ZakatAssessment {
        val year = m.confirmDate(start)
        return m.assess(year.id, today, prices)
    }

    @Test
    fun `مصر — قبض الدور جوه السنة سطر مرة واحدة على فلوسك بس`() = runBlocking<Unit> {
        val a = assessFrom(zakat("EG", Currency.EGP), "2025-03-01", "2026-02-18")
        val line = a.lines.single { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES }
        assertEquals(300_000L to 7_500L, line.zakatableMinor to line.dueMinor, "2.5% على الـ3,000 اللي دفعتهم قبل القبض — مش على الـ10,000")
        val item = a.items.single { it.holding.id == "e-p" }
        assertEquals(ZakatItemStatus.COUNTED to ZakatTopic.RECEIVABLE_COLLECTED, item.status to item.topic)
        assertEquals(3_600_000L, a.lines.single { it.kind == ZakatLineKind.CASH }.zakatableMinor)
        assertEquals(97_500L, a.dueMinor, "90,000 كاش + 7,500 مرة واحدة")
        assertTrue(a.lines.none { it.kind == ZakatLineKind.ROSCA }, "الجمعية نفسها مش في الحساب السنوي")
    }

    @Test
    fun `مصر — القبض قبل بداية السنة ما بيتعدش فيها`() = runBlocking<Unit> {
        val a = assessFrom(zakat("EG", Currency.EGP), "2025-10-01", "2026-09-20")
        assertTrue(a.items.none { it.holding.id == "e-p" })
        assertTrue(a.lines.none { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES })
    }

    @Test
    fun `مصر — اللي دفعته وما قبضتوش لسه معفي من الحساب السنوي`() = runBlocking<Unit> {
        // لسه ما قبضتش: الموقف +4,000 في الجمعية ⇒ سطر معفي بقاعدة «زي الدين ليك» (4399)، ومفيش سطر «اتحصّل»
        val a = assessFrom(zakat("EG", Currency.EGP, payout = false), "2025-03-01", "2026-02-18")
        val rosca = a.items.single { it.holding.id == "r-1" }
        assertEquals(ZakatItemStatus.EXEMPT to 0L, rosca.status to rosca.zakatableMinor)
        assertEquals(400_000L, rosca.valueMinor)
        assertTrue(a.notComputed.none { it.holding.id == "r-1" }, "مش «المرجع ما حددش» تاني")
        assertTrue(a.lines.none { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES || it.kind == ZakatLineKind.ROSCA })
    }

    /**
     * سنة قديمة ميعادها قبل يونيو 2025 (الحول من 2024-06-01): يوم الميعاد كنت دفعت قسطين بس (أبريل · مايو)، والقبض والأقساط اللي بعده
     * **ما بيغيّروش موقفها** (عيب `roscaStatus` اللي كان بياخد كل الحركات — جلسة 11، اتصلح في 13).
     */
    private suspend fun pastYear(country: String, currency: Currency): ZakatAssessment {
        val m = zakat(country, currency, opening = "2024-01-01")
        val year = m.confirmDate("2024-06-01")
        assertTrue(year.dueAt > "2025-05-01" && year.dueAt < "2025-06-01", year.dueAt)
        return m.assess(year.id, "2026-02-18", prices)
    }

    @Test
    fun `السعودية — موقف الجمعية في سنة قديمة بحركاتها لحد يوم الميعاد بس`() = runBlocking<Unit> {
        val a = pastYear("SA", Currency.SAR)
        val rosca = a.items.single { it.holding.id == "r-1" }
        assertEquals(200_000L to 200_000L, rosca.valueMinor to rosca.zakatableMinor, "قسطين قبل الميعاد — مش 400,000 − 1,000,000 بعده")
        assertTrue(a.items.none { it.holding.id == "e-p" })
    }

    @Test
    fun `القسط اللي يوم الميعاد نفسه محسوب في موقف الجمعية`() = runBlocking<Unit> {
        // سنة ميعادها 2025-05-01 بالظبط = يوم القسط التاني ⇒ الموقف قسطين (اليوم نفسه جوه الحساب)
        val first = toDayNumber(parseIsoDate("2024-04-20"))
        val start = (0..30).map { dayNumberToIso(first + it) }.first { nextZakatDate(it) == "2025-05-01" }
        val m = zakat("SA", Currency.SAR, opening = "2024-01-01")
        val a = m.assess(m.confirmDate(start).id, "2026-02-18", prices)
        assertEquals(200_000L, a.items.single { it.holding.id == "r-1" }.valueMinor)
    }

    @Test
    fun `مصر — موقف الجمعية في سنة قديمة بحركاتها لحد يوم الميعاد بس`() = runBlocking<Unit> {
        val a = pastYear("EG", Currency.EGP)
        val rosca = a.items.single { it.holding.id == "r-1" }
        assertEquals(ZakatItemStatus.EXEMPT to 200_000L, rosca.status to rosca.valueMinor, "اللي دفعته لحد الميعاد — والقبض بعده مش دين عليك")
        assertTrue(a.items.none { it.holding.id == "e-p" }, "القبض بعد الميعاد ما بيطلّعش سطر «اتحصّل» في السنة دي")
    }

    @Test
    fun `السعودية ما اتغيرتش — الجمعية في الحساب السنوي والقبض ما بيطلّعش سطر`() = runBlocking<Unit> {
        val a = assessFrom(zakat("SA", Currency.SAR), "2025-03-01", "2026-02-18")
        assertTrue(a.items.none { it.holding.id == "e-p" })
        assertTrue(a.lines.none { it.kind == ZakatLineKind.COLLECTED_RECEIVABLES })
        assertEquals(90_000L, a.dueMinor)
        val before = assessFrom(zakat("SA", Currency.SAR, payout = false), "2025-03-01", "2026-02-18")
        assertEquals(400_000L, before.lines.single { it.kind == ZakatLineKind.ROSCA }.zakatableMinor, "السعودية: اللي دفعته وما قبضتوش كل سنة")
    }
}
