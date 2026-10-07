package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EstateItem
import app.masroufy.core.HeirKind
import app.masroufy.core.InheritanceCase
import app.masroufy.core.InheritanceLaw
import app.masroufy.core.InheritanceResult
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * حاسبة الورث من حالة الاستخدام (§69) — «هات أملاكي من مصروفي» + القانون من بلد المساحة. كل الأسماء والأرقام مخترعة.
 * المساحة: بنك افتتاحي 20,000 ⇒ صرف 5,000 ⇒ 15,000 · كاش رصيده صفر · محفظة بالجنيه (مش عملة المساحة) · دهب 100 جم بسعر 300 ·
 * سهم من غير سعر · أصل مؤرشف · أصل اتباع كله.
 */
class CalculateInheritanceTest {
    private val g = QUANTITY_SCALE

    private fun txn(id: String, date: String, amount: Long) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )

    private fun useCase(country: String = "SA", withHoldings: Boolean = true) = CalculateInheritance(
        CalculateInheritanceDeps(
            countryCode = country,
            currency = Currency.SAR,
            clock = FixedClock("2026-10-05T09:00:00.000Z"),
            holdings = if (!withHoldings) null else EstateHoldingsDeps(
                wallets = MemoryWalletRepository(
                    listOf(
                        Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 2_000_000, "2026-01-01"),
                        Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 0, "2026-01-01"),
                        Wallet("w-egp", "محفظة بالجنيه", Currency.EGP, "bank", 5_000_000, "2026-01-01"),
                    ),
                ),
                txns = MemoryTransactionRepository(listOf(txn("t-1", "2026-03-01", 500_000))),
                assets = MemoryAssetRepository(
                    listOf(
                        Asset("a-gold", "سبيكة وهمية", "gold", "جرام", Currency.SAR, false),
                        Asset("a-stock", "سهم وهمي", "stock", "سهم", Currency.SAR, false),
                        Asset("a-old", "أصل مؤرشف", "other", "وحدة", Currency.SAR, true),
                        Asset("a-sold", "أصل اتباع", "fund", "وحدة", Currency.SAR, false),
                    ),
                ),
                lots = MemoryAssetLotRepository(
                    listOf(
                        AssetLot("l-1", "a-gold", "2026-02-01", 100 * g, 2_500_000, 0), AssetLot("l-2", "a-stock", "2026-02-01", 10 * g, 100_000, 0),
                        AssetLot("l-3", "a-old", "2026-02-01", g, 100_000, 0), AssetLot("l-4", "a-sold", "2026-02-01", g, 100_000, 0),
                    ),
                ),
                sales = MemoryAssetSaleRepository(listOf(AssetSale("s-1", "a-sold", "2026-04-01", g, 120_000, 0))),
                prices = MemoryAssetPriceRepository(listOf(AssetPrice("a-gold", 30_000, "2026-10-04", "feed"))),
            ),
        ),
    )

    @Test
    fun prefillBringsBalancesAndAssetsAtTodaysPrices() = runBlocking {
        val drafts = useCase().prefillMyEstate()
        assertEquals(
            listOf(
                EstateItemDraft("بنك وهمي", 1_500_000, EstateItemSource.WALLET, "w-bank"),
                EstateItemDraft("سبيكة وهمية", 3_000_000, EstateItemSource.ASSET, "a-gold", "gold"),
                // من غير سعر ⇒ «مش معروف» مش صفر — المستخدم يكتبه
                EstateItemDraft("سهم وهمي", null, EstateItemSource.ASSET, "a-stock", "stock"),
            ),
            drafts,
        )
        assertEquals(emptyList(), useCase(withHoldings = false).prefillMyEstate(), "تركة شخص تاني ⇒ مفيش أملاك تتجاب")
    }

    @Test
    fun editedDraftsAreCalculatedWithTheSpaceLaw() = runBlocking {
        val calc = useCase(country = "SA")
        val items = calc.prefillMyEstate().map { EstateItem(it.name, it.valueMinor ?: 900_000) }
        // المسألة مكتوب فيها مصر — القانون بييجي من المساحة (السعودية) مش من المسألة
        val case = InheritanceCase("EG", mapOf(HeirKind.GRANDFATHER to 1, HeirKind.FULL_BROTHER to 1), items)
        val r = assertIs<InheritanceResult.Computed>(calc.calculate(case))
        assertEquals(InheritanceLaw.SA, r.law)
        assertEquals(5_400_000L, r.grossMinor)
        assertEquals(listOf(5_400_000L), r.heir(HeirKind.GRANDFATHER)!!.amountsMinor)
        val eg = assertIs<InheritanceResult.Computed>(useCase(country = "EG").calculate(case.copy(countryCode = "SA")))
        assertEquals(InheritanceLaw.EG, eg.law)
        assertEquals(listOf(2_700_000L), eg.heir(HeirKind.FULL_BROTHER)!!.amountsMinor)
        assertTrue(eg.items.all { item -> item.parts.sumOf { it.amountMinor } == item.valueMinor })
    }
}
