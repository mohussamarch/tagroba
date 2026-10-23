package app.masroufy.usecase

import app.masroufy.core.AssetError
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.saleOutcome
import app.masroufy.core.walletBalancesOn
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** §112-4 «بيع أصل»: الفلوس راحت فين (إجباري) · الربط بالتحويل اللي وصل · المكسب أو الخسارة. بيانات مخترعة بالكامل. */
class SellAssetTest {
    private val g = QUANTITY_SCALE
    private val day = "2026-03-01"

    private inner class World {
        val assetRepo = MemoryAssetRepository()
        val lots = MemoryAssetLotRepository()
        val sales = MemoryAssetSaleRepository()
        val bank = Wallet("w-bank", "بنك تجربة", Currency.SAR, "bank", 100_000, "2026-01-01")
        val cash = Wallet("w-cash", "كاش تجربة", Currency.SAR, "cash", 0, "2026-01-01")
        val egp = Wallet("w-egp", "محفظة جنيه", Currency.EGP, "digital_wallet", 0, "2026-01-01")
        val wallets = MemoryWalletRepository(listOf(bank, cash, egp))
        val txns = MemoryTransactionRepository(listOf(credit("t-in", "w-bank", 149_000, "2026-03-02")))
        val clock = FixedClock("2026-03-05T09:00:00.000Z")
        val ids = SequentialIdGenerator()
        val manage = ManageAssets(ManageAssetsDeps(assetRepo, lots, sales, MemoryAssetPriceRepository(), ids, clock))
        val sell = SellAsset(SellAssetDeps(manage, assetRepo, lots, sales, wallets, txns, AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)), clock))

        /** 10 جم بـ3,000.00 + 5 جم بـ2,000.01 ⇒ التكلفة 5,000.01 على 15 جم. */
        val assetId = runBlocking {
            val a = manage.addAsset(NewAsset("ذهب تجربة", "gold", currency = Currency.SAR))
            manage.recordPurchase(PurchaseInput(a.id, "2026-01-05", 10 * g, 300_000))
            manage.recordPurchase(PurchaseInput(a.id, "2026-02-01", 5 * g, 200_001))
            a.id
        }

        suspend fun balance(id: String) = walletBalancesOn(wallets.listAll(), txns.listByDateRange("2000-01-01", "2100-01-01"), "2026-03-31")[id]
    }

    private fun credit(id: String, wallet: String, amount: Long, at: String) = Transaction(
        id = id, occurredAt = at, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "${at}T10:00:00Z", updatedAt = "${at}T10:00:00Z", walletId = wallet,
    )

    @Test fun gainAndLossUseTheWeightedAverageCostOfTheUnitsSold() {
        val lots = listOf(AssetLot("l1", "a", "2026-01-05", 10 * g, 300_000, 0), AssetLot("l2", "a", "2026-02-01", 5 * g, 200_000, 1))
        // جزئي برسوم: حصة التكلفة = 5,000.01 × 4 ÷ 15 = 1,333.336 ⇒ 1,333.34 (نص لفوق مرة واحدة) · المكسب = 1,500 − 10 − 1,333.34
        val gain = saleOutcome(lots, emptyList(), AssetSale("s", "a", day, 4 * g, 150_000, 1_000))
        assertEquals(133_334L, gain.costShareMinor)
        assertEquals(15_666L, gain.gainMinor)
        // خسارة: نفس الكمية بـ1,000 من غير رسوم
        assertEquals(-33_334L, saleOutcome(lots, emptyList(), AssetSale("s", "a", day, 4 * g, 100_000, 0)).gainMinor)
        // بعد بيعة سابقة: الباقي 11 جم بتكلفة 3,666.67 ⇒ بيع الكل ياخد التكلفة كلها (مفيش هللة تضيع)
        val prior = AssetSale("s0", "a", "2026-02-15", 4 * g, 150_000, 1_000)
        val all = saleOutcome(lots, listOf(prior), AssetSale("s", "a", day, 11 * g, 400_000, 0))
        assertEquals(500_001L - 133_334L, all.costShareMinor)
        assertEquals(400_000L - (500_001 - 133_334), all.gainMinor)
        // شراء بعد يوم البيع ما بيدخلش في المتوسط · بيع أكتر من المملوك وقتها مرفوض
        val later = lots + AssetLot("l3", "a", "2026-04-01", 100 * g, 1, 0)
        assertEquals(133_334L, saleOutcome(later, emptyList(), AssetSale("s", "a", day, 4 * g, 150_000, 1_000)).costShareMinor)
        assertFailsWith<AssetError> { saleOutcome(later, emptyList(), AssetSale("s", "a", day, 16 * g, 1, 0)) }
    }

    @Test fun saleToCashCreatesTheIncomingOperationAndSavesWhereAndGain() = runBlocking {
        val w = World()
        val before = w.balance("w-cash")!!
        val sold = w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 4 * g, 150_000, 1_000), toWalletId = "w-cash"))
        assertTrue(sold.created)
        val op = w.txns.findByIds(listOf(sold.transactionId!!)).single()
        assertEquals(EconomicKind.ASSET_SELL, op.economicKind)
        assertEquals(Direction.IN, op.observedDirection)
        assertEquals("w-cash", op.walletId)
        assertEquals(149_000L, op.amountMinor, "الصافي = الحصيلة − الرسوم")
        assertEquals(before + 149_000, w.balance("w-cash"))
        val saved = w.sales.listAll().single()
        assertEquals("w-cash", saved.toWalletId)
        assertEquals(15_666L, saved.gainMinor)
        assertEquals(op.id, saved.transactionId)
        // مجاميع الاستثمار: المكسب المحقق والمتبقي 11 جم
        val view = w.manage.listPortfolio("2026-03-05")
        assertEquals(15_666L, view.totals.realizedGainMinor)
        assertEquals(11 * g, view.rows.single().position.heldQuantity)
    }

    @Test fun linkingToTheCreditThatArrivedAddsNoDuplicate() = runBlocking {
        val w = World()
        val credits = w.sell.matchingCredits("w-bank", day, 150_000, 1_000)
        assertEquals(listOf("t-in"), credits.map { it.id })
        assertTrue(w.sell.matchingCredits("w-cash", day, 150_000, 1_000).isEmpty(), "الربط للبنك بس")
        assertTrue(w.sell.matchingCredits("w-bank", "2026-04-01", 150_000, 1_000).isEmpty(), "بعيد عن يوم البيع")
        assertTrue(w.sell.matchingCredits("w-bank", day, 300_000, 0).isEmpty(), "بعيد عن المبلغ")
        val countBefore = w.txns.listByDateRange("2000-01-01", "2100-01-01").size
        val balanceBefore = w.balance("w-bank")
        val sold = w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 4 * g, 150_000, 1_000), toWalletId = "w-bank", linkTransactionId = "t-in"))
        assertFalse(sold.created)
        assertEquals("t-in", sold.transactionId)
        assertEquals(countBefore, w.txns.listByDateRange("2000-01-01", "2100-01-01").size, "مفيش عملية جديدة")
        assertEquals(balanceBefore, w.balance("w-bank"), "الرصيد ما اتغيرش — الفلوس كانت متسجلة")
        assertEquals(EconomicKind.ASSET_SELL, w.txns.findByIds(listOf("t-in")).single().economicKind, "التحويل بقى «بيع أصل» مش دخل")
        assertEquals("w-bank", w.sales.listAll().single().toWalletId)
        // نفس التحويل ما يتربطش ببيعة تانية
        assertTrue(w.sell.matchingCredits("w-bank", day, 150_000, 1_000).isEmpty())
        assertFailsWith<AssetError> {
            w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 1 * g, 150_000), toWalletId = "w-bank", linkTransactionId = "t-in"))
        }
        assertEquals(1, w.sales.listAll().size)
    }

    @Test fun theWalletIsRequiredAndNothingIsWrittenWithoutIt() = runBlocking {
        val w = World()
        val countBefore = w.txns.listByDateRange("2000-01-01", "2100-01-01").size
        val e = assertFailsWith<AssetError> { w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 4 * g, 150_000), toWalletId = null)) }
        assertEquals(app.masroufy.core.uiText(app.masroufy.core.R06Key.SELL_ERR_WALLET), e.message)
        assertFailsWith<AssetError> { w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 4 * g, 150_000), toWalletId = "w-egp")) }
        // بيع أكتر من المملوك ⇒ مرفوض قبل أي عملية
        assertFailsWith<AssetError> { w.sell.sell(SellAssetInput(SaleInput(w.assetId, day, 16 * g, 150_000), toWalletId = "w-cash")) }
        assertTrue(w.sales.listAll().isEmpty())
        assertEquals(countBefore, w.txns.listByDateRange("2000-01-01", "2100-01-01").size)
        // المربعات: محافظ بعملة الأصل بس — البنك الأول
        assertEquals(listOf("w-bank", "w-cash"), w.sell.destinations(w.assetId).map { it.wallet.id })
    }
}
