package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetError
import app.masroufy.core.AssetLot
import app.masroufy.core.Currency
import app.masroufy.core.HoldingOrigin
import app.masroufy.core.HoldingPurpose
import app.masroufy.core.HoldingType
import app.masroufy.core.NewHolding
import app.masroufy.core.OWNED_LONG_AGO
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.Wallet
import app.masroufy.core.ZAKAT_FACT_HAWL
import app.masroufy.core.ZakatItemStatus
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatPurpose
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
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §121-6 + §121-7 على مستودعات الذاكرة — حاجة قديمة اتضافت قبل «امتلكتها إمتى؟» (أول دفعة بيوم الإضافة). بيانات مخترعة. */
class OwnedLongAgoFlowTest {
    private val g = QUANTITY_SCALE
    private val clock = FixedClock("2026-02-18T09:00:00.000Z")
    private val legacy = Asset("a-old", "دهب قديم وهمي", "gold", "جرام", Currency.SAR, false, holdingType = HoldingType.GOLD.wire)
    private val assetRepo = MemoryAssetRepository(listOf(legacy))
    private val rawLots = MemoryAssetLotRepository(listOf(AssetLot("l-old", "a-old", "2026-01-10", 100 * g, 0, 0)))
    private val lots = LegacyHoldingLots(rawLots, assetRepo)
    private val manage = ManageAssets(ManageAssetsDeps(assetRepo, lots, MemoryAssetSaleRepository(), MemoryAssetPriceRepository(), SequentialIdGenerator(), clock))

    @Test fun legacyHoldingIsLongAgoSoAnOlderSaleIsAccepted() = runBlocking<Unit> {
        assertEquals(OWNED_LONG_AGO, lots.listByAsset("a-old").single().purchasedAt)
        assertEquals(OWNED_LONG_AGO, lots.listAll().single().purchasedAt)
        assertEquals("2026-01-10", rawLots.listAll().single().purchasedAt, "قراية بس — المستند ما اتكتبش")
        manage.recordSale(SaleInput("a-old", "2025-06-01", 40 * g, 1_000_000))
    }

    @Test fun newHoldingIsMarkedSoItsOwnDateStillGuardsTheSale() = runBlocking<Unit> {
        val add = AddHolding(
            AddHoldingDeps(
                manage, ManageAssetGrowth(ManageAssetGrowthDeps(assetRepo, lots, MemoryAssetSaleRepository(), MemoryAssetPriceRepository(), clock)),
                assetRepo, lots, MemoryAssetPriceRepository(),
                feedPrice = FeedPriceLookup { _, _ -> null }, metalFacts = MetalFactsWriter { _, _, _, _ -> },
                currency = Currency.SAR, uow = MemoryUnitOfWork(emptyList()), ids = SequentialIdGenerator(),
            ),
        )
        val id = add.add(
            NewHolding(HoldingType.CAR, "سيارة وهمية", HoldingPurpose.PERSONAL, HoldingOrigin.GIFT, g, valueMinor = 1_000_000, ownedSince = "2025-05-01"),
            "2026-02-18",
        )
        assertEquals(true, assetRepo.listAll().single { it.id == id }.ownedSinceAsked)
        assertEquals("2025-05-01", lots.listByAsset(id).single().purchasedAt)
        assertFailsWith<AssetError> { manage.recordSale(SaleInput(id, "2025-01-01", g, 900_000)) }
    }

    private fun zakat(facts: MemoryZakatFactRepository) = ManageZakat(
        ManageZakatDeps(
            countryCode = "SA", currency = Currency.SAR, profile = MemoryProfileRepository(emptyProfile()),
            wallets = MemoryWalletRepository(listOf(Wallet("w", "بنك وهمي", Currency.SAR, "bank", 3_000_000, "2025-01-01"))),
            txns = MemoryTransactionRepository(emptyList()), assets = assetRepo, lots = lots, sales = MemoryAssetSaleRepository(),
            prices = MemoryAssetPriceRepository(), people = MemoryPersonRepository(emptyList()), obligations = MemoryObligationRepository(emptyList()),
            settlements = MemorySettlementRepository(emptyList()), roscas = MemoryRoscaRepository(emptyList()), roscaEntries = MemoryRoscaEntryRepository(emptyList()),
            facts = facts, years = MemoryZakatYearRepository(), uow = MemoryUnitOfWork(emptyList()), clock = clock,
        ),
    )
    private val sa = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    @Test fun longAgoGoldAsksTheHawlAndTheAnswerIsKeptOnTheAsset() = runBlocking<Unit> {
        val facts = MemoryZakatFactRepository()
        val z = zakat(facts)
        z.setAssetFacts("a-old", purpose = ZakatPurpose.SAVING, karat = 21)
        val year = z.confirmDate("2025-01-01")
        val before = z.assess(year.id, "2026-02-18", sa).items.single { it.holding.id == "a-old" }
        assertEquals(ZakatItemStatus.NEEDS_FACT to ZAKAT_FACT_HAWL, before.status to before.missingFact)
        assertNull(before.zakatableMinor)

        z.setAssetFacts("a-old", hawlPassed = true)
        assertEquals(true, facts.listAll().single().hawlPassed)
        assertEquals(ZakatPurpose.SAVING, facts.listAll().single().purpose, "الإجابة ما مسحتش الوقائع التانية")
        val after = z.assess(year.id, "2026-02-18", sa).items.single { it.holding.id == "a-old" }
        assertEquals(ZakatItemStatus.COUNTED, after.status)
        assertTrue((after.zakatableMinor ?: 0) > 0)
    }
}
