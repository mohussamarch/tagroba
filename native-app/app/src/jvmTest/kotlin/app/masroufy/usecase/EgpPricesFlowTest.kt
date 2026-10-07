package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.Currency
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatOutcome
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.emptyProfile
import app.masroufy.core.parsePriceFeed
import app.masroufy.core.uiText
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
import kotlin.test.assertNull

/**
 * أسعار الجنيه من ملف الأسعار لحد حساب زكاة مصر وتحديث أسعار الأصول (OVERRIDES §62 — جلسة 18). كل الأسعار والأسماء مخترعة.
 * الملف بنفس شكل `public/prices.json`: سطور الريال من غير حقل العملة + سطور `*_EGP` عليها `"currency": "EGP"` بالقرش.
 */
class EgpPricesFlowTest {
    private fun row(price: Long, currency: String? = null): Map<String, Any?> =
        linkedMapOf<String, Any?>("pricePerUnitMinor" to price, "asOf" to "2026-10-05", "source" to "مصدر وهمي").also { if (currency != null) it["currency"] = currency }

    private fun feed(withEgp: Boolean) = parsePriceFeed(
        mapOf(
            "generatedAt" to "2026-10-05T03:00:00Z", "baseCurrency" to "SAR",
            "prices" to linkedMapOf<String, Any?>("GOLD_24K_GRAM" to row(30_000), "GOLD_21K_GRAM" to row(26_250), "SILVER_GRAM" to row(350)).also {
                if (withEgp) {
                    it["GOLD_24K_GRAM_EGP"] = row(400_000, "EGP"); it["GOLD_21K_GRAM_EGP"] = row(350_000, "EGP"); it["SILVER_GRAM_EGP"] = row(4_700, "EGP")
                }
            },
        ),
    )

    @Test
    fun `تحديث الأسعار ما بيحطش سعر الجنيه على أصل بالريال ولا العكس`() = runBlocking<Unit> {
        val assets = MemoryAssetRepository(
            listOf(
                Asset("a-eg", "دهب مصر وهمي", "gold", "جرام", Currency.EGP, false, feedSymbol = "GOLD_21K_GRAM_EGP"),
                Asset("a-sa-wrong", "دهب سعودي مربوط بالجنيه", "gold", "جرام", Currency.SAR, false, feedSymbol = "GOLD_21K_GRAM_EGP"),
                Asset("a-eg-wrong", "دهب مصري مربوط بالريال", "gold", "جرام", Currency.EGP, false, feedSymbol = "GOLD_21K_GRAM"),
                Asset("a-sa", "دهب سعودي وهمي", "gold", "جرام", Currency.SAR, false, feedSymbol = "GOLD_21K_GRAM"),
            ),
        )
        val prices = MemoryAssetPriceRepository(listOf(AssetPrice("a-sa-wrong", 25_000, "2026-09-01", "manual")))
        val out = SyncAssetPrices(SyncAssetPricesDeps(assets, prices)).sync(feed(withEgp = true))
        assertEquals(listOf("دهب مصر وهمي", "دهب سعودي وهمي"), out.updated.map { it.assetName })
        assertEquals(
            listOf(
                "دهب سعودي مربوط بالجنيه" to uiText(TextKey.PRICE_CURRENCY_MISMATCH, "GOLD_21K_GRAM_EGP", "EGP", "SAR"),
                "دهب مصري مربوط بالريال" to uiText(TextKey.PRICE_CURRENCY_MISMATCH, "GOLD_21K_GRAM", "SAR", "EGP"),
            ),
            out.skipped.map { it.assetName to it.reason },
        )
        val stored = prices.listAll().associate { it.assetId to it.pricePerUnitMinor }
        assertEquals(mapOf("a-sa-wrong" to 25_000L, "a-eg" to 350_000L, "a-sa" to 26_250L), stored, "السعر القديم بيفضل زي ما هو، ومفيش سعر للمربوط غلط")
    }

    private fun egypt(): ManageZakat {
        val years = MemoryZakatYearRepository()
        return ManageZakat(
            ManageZakatDeps(
                countryCode = "EG", currency = Currency.EGP,
                profile = MemoryProfileRepository(emptyProfile()),
                wallets = MemoryWalletRepository(listOf(Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 30_000_000, "2025-01-01"))),
                txns = MemoryTransactionRepository(),
                assets = MemoryAssetRepository(listOf(Asset("a-bar", "سبيكة وهمية", "gold", "جرام", Currency.EGP, false))),
                lots = MemoryAssetLotRepository(listOf(AssetLot("l-1", "a-bar", "2025-01-01", 10 * QUANTITY_SCALE, 3_000_000, 0))),
                sales = MemoryAssetSaleRepository(), prices = MemoryAssetPriceRepository(),
                people = MemoryPersonRepository(), obligations = MemoryObligationRepository(), settlements = MemorySettlementRepository(),
                roscas = MemoryRoscaRepository(), roscaEntries = MemoryRoscaEntryRepository(),
                facts = MemoryZakatFactRepository(), years = years, uow = MemoryUnitOfWork(listOf(years)), clock = FixedClock("2026-10-05T09:00:00.000Z"),
            ),
        )
    }

    @Test
    fun `زكاة مصر بتتحسب بسطور الجنيه وتفضل غير متاح من غيرها`() = runBlocking<Unit> {
        val manage = egypt()
        manage.setAssetFacts("a-bar", purpose = ZakatPurpose.SAVING, karat = 21)
        val year = manage.confirmDate("2025-01-01")

        // ملف فيه الريال بس ⇒ النصاب مجهول ⇒ «غير متاح»، ومفيش اقتراح ميعاد
        val sarOnly = manage.pricesFrom(feed(withEgp = false))
        val missing = manage.assess(year.id, "2026-10-05", sarOnly)
        assertEquals(ZakatOutcome.UNAVAILABLE, missing.outcome)
        assertNull(missing.nisabMinor)
        assertNull(missing.dueMinor)
        assertNull(manage.suggestDate("2026-10-05", sarOnly))

        // بسطور الجنيه: النصاب 85 × 21/24 × 4,000.00 = 297,500.00 · الكاش 300,000 + السبيكة 10 × 21/24 × 4,000 = 35,000
        val egp = manage.pricesFrom(feed(withEgp = true))
        val a = manage.assess(year.id, "2026-10-05", egp)
        assertEquals(29_750_000L, a.nisabMinor)
        assertEquals(ZakatOutcome.DUE, a.outcome)
        assertEquals(33_500_000L, a.totalZakatableMinor)
        assertEquals(837_500L, a.dueMinor, "2.5% على كل سطر: 750,000 + 87,500")
        assertEquals(Currency.EGP, a.currency)
    }
}
