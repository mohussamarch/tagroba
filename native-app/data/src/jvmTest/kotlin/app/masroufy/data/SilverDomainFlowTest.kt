package app.masroufy.data

import app.masroufy.core.ASSET_KIND_LABELS
import app.masroufy.core.AssetLot
import app.masroufy.core.Currency
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatItemStatus
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatOutcome
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.displayKind
import app.masroufy.core.emptyProfile
import app.masroufy.core.growthClassOf
import app.masroufy.core.parsePriceFeed
import app.masroufy.core.uiText
import app.masroufy.core.zakatPricesFromFeed
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
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
import app.masroufy.usecase.CalculateInheritance
import app.masroufy.usecase.CalculateInheritanceDeps
import app.masroufy.usecase.EstateHoldingsDeps
import app.masroufy.usecase.EstateItemDraft
import app.masroufy.usecase.EstateItemSource
import app.masroufy.usecase.ManageAssetGrowth
import app.masroufy.usecase.ManageAssetGrowthDeps
import app.masroufy.usecase.ManageZakat
import app.masroufy.usecase.ManageZakatDeps
import app.masroufy.usecase.ProjectionRateFrom
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * الفضة المتخزنة "other" + العلامة (OVERRIDES §69.9) — **أو** بالنوع القديم "silver" — بتفضل فضة في الزكاة والورث والتوقّع.
 * الأصل بيتقري من مستند فعلي على المحوّل (`StoredAssets`). كل الأسامي والأرقام مخترعة.
 * الفضة 1,000 جم نقاوة 999 · سعر الجرام الصافي 4.00 ر.س / 50.00 ج.م · الدهب 300.00 ر.س / 5,000.00 ج.م.
 */
class SilverDomainFlowTest {
    private val feed = parsePriceFeed(
        mapOf(
            "generatedAt" to "2026-02-18T00:00:00.000Z", "baseCurrency" to "SAR",
            "prices" to mapOf(
                "GOLD_24K_GRAM" to row(30_000), "SILVER_GRAM" to row(400),
                "GOLD_24K_GRAM_EGP" to row(500_000, "EGP"), "SILVER_GRAM_EGP" to row(5_000, "EGP"),
            ),
        ),
    )

    private fun row(price: Long, currency: String? = null) =
        mapOf("pricePerUnitMinor" to price, "asOf" to "2026-02-17", "source" to "test") + (currency?.let { mapOf("currency" to it) } ?: emptyMap())

    private fun silverDoc(currency: Currency, legacy: Boolean): Doc {
        val doc = linkedMapOf<String, Any?>("id" to "a-silver", "name" to "سبيكة فضة وهمية", "kind" to "other", "unitLabel" to "جرام", "currency" to currency.name, "archived" to false, "silver" to true)
        return if (legacy) LinkedHashMap(doc).apply { put("kind", "silver"); remove("silver") } else doc
    }

    private inner class Account(country: String, val currency: Currency, legacy: Boolean, opening: Long) {
        val assets = StoredAssets(silverDoc(currency, legacy))
        val wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", currency, "bank", opening, "2025-01-01")))
        val txns = MemoryTransactionRepository()
        val lots = MemoryAssetLotRepository(listOf(AssetLot("l-1", "a-silver", "2025-03-05", 1_000 * QUANTITY_SCALE, 300_000, 0)))
        val sales = MemoryAssetSaleRepository()
        val prices = MemoryAssetPriceRepository()
        val clock = FixedClock("2026-02-18T09:00:00.000Z")
        val years = MemoryZakatYearRepository()
        val zakat = ManageZakat(
            ManageZakatDeps(
                country, currency, MemoryProfileRepository(emptyProfile()), wallets, txns, assets, lots, sales, prices,
                MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryRoscaRepository(), MemoryRoscaEntryRepository(),
                MemoryZakatFactRepository(), years, MemoryUnitOfWork(listOf(years)), clock,
            ),
        )
    }

    @Test
    fun `الزكاة في السعودية — نصاب الفضة 595 جم بسطر الريال`() = runBlocking<Unit> {
        for (legacy in listOf(false, true)) {
            val acc = Account("SA", Currency.SAR, legacy, opening = 1_000_000)
            acc.zakat.setAssetFacts("a-silver", purpose = ZakatPurpose.SAVING, fineness = 999)
            val prices = zakatPricesFromFeed(feed, Currency.SAR)
            val a = acc.zakat.assess(acc.zakat.confirmDate("2025-03-01").id, "2026-02-18", prices)
            assertEquals(595L * 400, a.nisabMinor, "الأقل من 85 جم دهب و595 جم فضة ⇒ الفضة (legacy=$legacy)")
            val item = a.items.single { it.holding.id == "a-silver" }
            assertEquals(Triple(ZakatLineKind.SILVER, ZakatItemStatus.COUNTED, 399_600L), Triple(item.line, item.status, item.valueMinor), "1,000 × 0.999 × 4.00")
            assertEquals(listOf(ZakatLineKind.CASH, ZakatLineKind.SILVER), a.lines.map { it.kind })
            assertEquals(Triple(ZakatOutcome.DUE, 1_399_600L, 34_990L), Triple(a.outcome, a.totalZakatableMinor, a.dueMinor))
        }
    }

    @Test
    fun `الزكاة في مصر — الفضة بسطر الجنيه`() = runBlocking<Unit> {
        for (legacy in listOf(false, true)) {
            val acc = Account("EG", Currency.EGP, legacy, opening = 50_000_000)
            acc.zakat.setAssetFacts("a-silver", purpose = ZakatPurpose.SAVING, fineness = 999)
            val prices = zakatPricesFromFeed(feed, Currency.EGP)
            val a = acc.zakat.assess(acc.zakat.confirmDate("2025-03-01").id, "2026-02-18", prices)
            assertEquals(37_187_500L, a.nisabMinor, "مصر: 85 جم دهب عيار 21 بالجنيه")
            val item = a.items.single { it.holding.id == "a-silver" }
            assertEquals(Triple(ZakatLineKind.SILVER, ZakatItemStatus.COUNTED, 4_995_000L), Triple(item.line, item.status, item.valueMinor), "1,000 × 0.999 × 50.00 (legacy=$legacy)")
            assertEquals(Triple(ZakatOutcome.DUE, 54_995_000L, 1_374_875L), Triple(a.outcome, a.totalZakatableMinor, a.dueMinor))
        }
    }

    @Test
    fun `الورث والتوقّع بيشوفوها فضة`() = runBlocking<Unit> {
        for (legacy in listOf(false, true)) {
            val acc = Account("SA", Currency.SAR, legacy, opening = 0)
            acc.prices.save(app.masroufy.core.AssetPrice("a-silver", 400, "2026-02-17", "manual"))
            val asset = acc.assets.listAll().single()
            assertEquals("silver" to uiText(TextKey.ASSET_KIND_SILVER), asset.displayKind to ASSET_KIND_LABELS[asset.displayKind])

            val inheritance = CalculateInheritance(
                CalculateInheritanceDeps("SA", Currency.SAR, acc.clock, EstateHoldingsDeps(acc.wallets, acc.txns, acc.assets, acc.lots, acc.sales, acc.prices)),
            )
            assertEquals(listOf(EstateItemDraft("سبيكة فضة وهمية", 400_000, EstateItemSource.ASSET, "a-silver", "silver")), inheritance.prefillMyEstate())

            // الفضة مالهاش معدل افتراضي (زي ما كانت — مش دهب ولا عقار) ⇒ المعدل اللي المستخدم يكتبه
            assertNull(growthClassOf(asset, Currency.SAR))
            val view = ManageAssetGrowth(ManageAssetGrowthDeps(acc.assets, acc.lots, acc.sales, acc.prices, acc.clock)).project("a-silver", 2030, "SA", null, typedRateBp = 300)
            assertEquals(Triple("silver", ProjectionRateFrom.TYPED_NOW, 400_000L), Triple(view.asset.kind, view.rateFrom, view.projection.currentValueMinor))
        }
    }
}
