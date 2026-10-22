package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatPrices
import app.masroufy.core.emptyProfile
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
 * مراجعة r06 people-2: الديون في حساب الزكاة **بموقفها يوم الميعاد** — زي الجمعية: دين ليك اتحصّل **بعد** الميعاد لسه دين يوم الميعاد،
 * ودين اتعمل بعد الميعاد ما يتعدش، والدين القديم (من غير عملية) بيفضل. كل الأسامي والمبالغ مخترعة.
 */
class ZakatDebtsAsOfTest {
    private val prices = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    private fun txn(id: String, date: String, dir: Direction, amount: Long) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )

    @Test fun aReceivableCollectedAfterTheDueDateStillCountsOnTheDueDate() = runBlocking<Unit> {
        val txns = listOf(
            txn("t-lend", "2024-08-01", Direction.OUT, 50_000), txn("t-back", "2025-08-01", Direction.IN, 50_000),
            txn("t-late", "2025-07-01", Direction.OUT, 20_000),
        )
        val obligations = listOf(
            Obligation("o-1", "p-1", "t-lend", ObligationKind.RECEIVABLE, 50_000, Currency.SAR),
            Obligation("o-2", "p-1", "t-late", ObligationKind.RECEIVABLE, 20_000, Currency.SAR),
            Obligation("o-3", "p-1", null, ObligationKind.RECEIVABLE, 10_000, Currency.SAR),
        )
        val years = MemoryZakatYearRepository()
        val m = ManageZakat(
            ManageZakatDeps(
                countryCode = "SA", currency = Currency.SAR, profile = MemoryProfileRepository(emptyProfile()),
                wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 3_000_000, "2024-01-01"))),
                txns = MemoryTransactionRepository(txns), assets = MemoryAssetRepository(), lots = MemoryAssetLotRepository(),
                sales = MemoryAssetSaleRepository(), prices = MemoryAssetPriceRepository(), people = MemoryPersonRepository(listOf(Person("p-1", "صديق تجربة"))),
                obligations = MemoryObligationRepository(obligations), settlements = MemorySettlementRepository(listOf(Settlement("s-1", "t-back", "o-1", 50_000))),
                roscas = MemoryRoscaRepository(), roscaEntries = MemoryRoscaEntryRepository(), facts = MemoryZakatFactRepository(), years = years,
                uow = MemoryUnitOfWork(listOf(years)), clock = FixedClock("2026-02-18T09:00:00.000Z"),
            ),
        )
        val year = m.confirmDate("2024-06-01")
        assertTrue(year.dueAt > "2025-05-01" && year.dueAt < "2025-06-01", year.dueAt)
        val items = m.assess(year.id, "2026-02-18", prices).items.associateBy { it.holding.id }

        assertEquals(50_000L, items["o-1"]?.valueMinor, "اتحصّل بعد الميعاد ⇒ لسه دين يوم الميعاد")
        assertTrue("o-2" !in items, "اتعمل بعد الميعاد ⇒ مش موجود يوم الميعاد")
        assertEquals(10_000L, items["o-3"]?.valueMinor, "الدين القديم من غير عملية بيفضل")
    }
}
