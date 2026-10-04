package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.HawlState
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatError
import app.masroufy.core.ZakatItemStatus
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatOutcome
import app.masroufy.core.ZakatPrices
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatShareHolding
import app.masroufy.core.ZakatYearLine
import app.masroufy.core.emptyProfile
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الزكاة من بيانات الحساب نفسها (OVERRIDES §62) — مكتوب بالإيد، كل الأسماء والأرقام مخترعة.
 * الحساب: بنك افتتاحي 30,000 (2025-01-01) ⇒ سحب 28,500 (نزل تحت النصاب) ⇒ إيداع 41,500 (2025-03-01 = 1 رمضان 1446)
 * ⇒ سلفة لشخص 3,000 ⇒ الكاش 40,000 · دهب ادخار 100 جم عيار 21 · دهب لبس 50 جم · سهم طويل الأجل 20,000 · جمعية +2,000 ·
 * دين عليك 10,000 · عملة رقمية 9,000.
 */
class ManageZakatTest {
    private val sa = ZakatPrices(goldPureGramMinor = 30_000, silverPureGramMinor = 350)

    private fun txn(id: String, date: String, dir: Direction, amount: Long, currency: Currency) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )

    private inner class Account(country: String = "SA", val currency: Currency = Currency.SAR, profile: Boolean? = null) {
        val years = MemoryZakatYearRepository()
        val facts = MemoryZakatFactRepository()
        private val g = QUANTITY_SCALE
        val manage = ManageZakat(
            ManageZakatDeps(
                countryCode = country, currency = currency,
                profile = MemoryProfileRepository(emptyProfile().copy(islamicContentVisible = profile)),
                wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", currency, "bank", 3_000_000, "2025-01-01"))),
                txns = MemoryTransactionRepository(
                    listOf(
                        txn("t-1", "2025-02-10", Direction.OUT, 2_850_000, currency), txn("t-2", "2025-03-01", Direction.IN, 4_150_000, currency),
                        txn("t-4", "2025-04-15", Direction.OUT, 300_000, currency),
                    ),
                ),
                assets = MemoryAssetRepository(
                    listOf(
                        Asset("a-bar", "سبيكة وهمية", "gold", "جرام", currency, false), Asset("a-ring", "شبكة وهمية", "gold", "جرام", currency, false),
                        Asset("a-shares", "سهم وهمي", "stock", "سهم", currency, false), Asset("a-coin", "عملة وهمية", "digital", "وحدة", currency, false),
                    ),
                ),
                lots = MemoryAssetLotRepository(
                    listOf(
                        AssetLot("l-1", "a-bar", "2025-03-05", 100 * g, 2_500_000, 0), AssetLot("l-2", "a-ring", "2024-05-01", 50 * g, 1_200_000, 0),
                        AssetLot("l-3", "a-shares", "2025-03-10", g, 1_800_000, 0), AssetLot("l-4", "a-coin", "2025-05-01", g, 800_000, 0),
                    ),
                ),
                sales = MemoryAssetSaleRepository(),
                prices = MemoryAssetPriceRepository(listOf(AssetPrice("a-shares", 2_000_000, "2026-02-17", "manual"), AssetPrice("a-coin", 900_000, "2026-02-17", "manual"))),
                people = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي"))),
                obligations = MemoryObligationRepository(
                    listOf(Obligation("o-1", "p-1", "t-4", ObligationKind.RECEIVABLE, 300_000, currency), Obligation("o-2", "p-1", null, ObligationKind.LOAN_PAYABLE, 1_000_000, currency)),
                ),
                settlements = MemorySettlementRepository(),
                roscas = MemoryRoscaRepository(listOf(Rosca("r-1", "جمعية وهمية", currency, 100_000, 1, "2025-04-01", 10, listOf(10), 1_000_000, createdAt = "x"))),
                roscaEntries = MemoryRoscaEntryRepository(
                    listOf(RoscaEntry("e-1", "r-1", "t-r1", RoscaEntryKind.CONTRIBUTION, 100_000), RoscaEntry("e-2", "r-1", "t-r2", RoscaEntryKind.CONTRIBUTION, 100_000)),
                ),
                facts = facts, years = years, uow = MemoryUnitOfWork(listOf(years)), clock = FixedClock("2026-02-18T09:00:00.000Z"),
            ),
        )

        suspend fun answerFacts() {
            manage.setAssetFacts("a-bar", purpose = ZakatPurpose.SAVING, karat = 21)
            manage.setAssetFacts("a-ring", purpose = ZakatPurpose.WEAR, karat = 21)
            manage.setAssetFacts("a-shares", holding = ZakatShareHolding.LONG_TERM)
            if (currency == Currency.SAR) manage.setReceivableFact("p-1", "o-1", ZakatCollectability.STRONG)
        }
    }

    @Test
    fun `السعودية — من بيانات الحساب لحد تثبيت السنة`() = runBlocking<Unit> {
        val acc = Account()
        acc.answerFacts()
        val suggestion = acc.manage.suggestDate("2026-02-18", sa)!!
        assertEquals(ZakatDateSuggestion("2025-03-01", "2026-02-18"), suggestion, "آخر رجوع فوق النصاب = 1 رمضان 1446 ⇒ 1 رمضان 1447")
        val year = acc.manage.confirmDate(suggestion.hawlStart)
        val a = acc.manage.assess(year.id, "2026-02-18", sa)
        assertEquals(ZakatOutcome.DUE, a.outcome)
        assertEquals(HawlState.Complete(true), a.hawl)
        assertEquals(7_125_000L to 178_125L, a.totalZakatableMinor to a.dueMinor)
        assertEquals(listOf(ZakatLineKind.CASH, ZakatLineKind.GOLD, ZakatLineKind.RECEIVABLES, ZakatLineKind.ROSCA), a.lines.map { it.kind })
        assertEquals(ZakatItemStatus.NO_RULING, a.items.first { it.holding.id == "a-coin" }.status, "العملة الرقمية لوحدها — ما اتحسبتش")
        assertEquals(ZakatItemStatus.NOT_DEDUCTED, a.items.first { it.holding.id == "o-2" }.status)

        val closed = acc.manage.close(year.id, "2026-02-18", sa)
        assertEquals(208_250L, closed.nisabMinor)
        assertEquals(ZakatYearLine(ZakatLineKind.CASH, 4_000_000, 100_000), closed.lines.first())
        assertEquals(178_125L, closed.dueMinor)
        val next = acc.manage.openYear()!!
        assertEquals(Triple("2027-02-08", "2026-02-18", false), Triple(next.dueAt, next.hawlStart, next.closed), "السنة الجاية اتفتحت لوحدها")
        assertFailsWith<ZakatError> { acc.manage.close(year.id, "2026-02-18", sa) }
    }

    @Test
    fun `السعودية — ميعاد قبل النزول ⇒ الحول بدأ من جديد وما يتثبّتش`() = runBlocking<Unit> {
        val acc = Account()
        acc.answerFacts()
        val wrong = acc.manage.confirmDate("2025-01-01")
        assertEquals("2025-12-21", wrong.dueAt)
        val a = acc.manage.assess(wrong.id, "2026-02-18", sa)
        assertEquals(ZakatOutcome.HAWL_RESTARTED, a.outcome)
        assertEquals(HawlState.Restarted("2025-03-01"), a.hawl)
        assertEquals(0L, a.dueMinor)
        assertEquals(uiText(TextKey.ZAKAT_CANNOT_CLOSE), assertFailsWith<ZakatError> { acc.manage.close(wrong.id, "2026-02-18", sa) }.message)
        // تأكيد الميعاد الصح بيبدّل السنة المفتوحة
        acc.manage.confirmDate("2025-03-01")
        assertEquals(listOf("2026-02-18"), acc.years.listAll().map { it.id })
    }

    @Test
    fun `مصر — دار الإفتاء والسعر بالجنيه ناقص ⇒ غير متاح`() = runBlocking<Unit> {
        val acc = Account("EG", Currency.EGP)
        acc.answerFacts()
        assertEquals("2025-01-01", acc.manage.suggestDate("2026-02-18", sa)!!.hawlStart, "مصر: أول يوم وصل النصاب")
        val year = acc.manage.confirmDate("2025-03-01")
        val missing = acc.manage.assess(year.id, "2026-02-18", ZakatPrices(null, null))
        assertEquals(ZakatOutcome.UNAVAILABLE, missing.outcome)
        assertNull(missing.dueMinor)
        assertNull(acc.manage.suggestDate("2026-02-18", ZakatPrices(null, null)))
        assertFailsWith<ZakatError> { acc.manage.close(year.id, "2026-02-18", ZakatPrices(null, null)) }
        // بسعر (مخترع) بالجنيه: الكاش والدهب بس — الدين ليك والجمعية «المرجع ما حددش»
        val a = acc.manage.assess(year.id, "2026-02-18", sa)
        assertEquals(165_625L, a.dueMinor)
        assertEquals(setOf("o-1", "r-1", "a-coin"), a.notComputed.map { it.holding.id }.toSet())
        assertEquals(Currency.EGP, a.currency)
    }

    @Test
    fun `واقعة ما اتسألتش ⇒ جزئي وما يتثبّتش`() = runBlocking<Unit> {
        val acc = Account()
        val year = acc.manage.confirmDate("2025-03-01")
        val a = acc.manage.assess(year.id, "2026-02-18", sa)
        assertEquals(ZakatOutcome.PARTIAL, a.outcome)
        assertEquals(setOf("purpose", "holding", "collectability"), a.blockers.mapNotNull { it.missingFact }.toSet())
        assertEquals(100_000L, a.lines.first { it.kind == ZakatLineKind.CASH }.dueMinor)
        assertFailsWith<ZakatError> { acc.manage.close(year.id, "2026-02-18", sa) }
    }

    @Test
    fun `الوقائع بتتحفظ صح والغلط بيترفض`() = runBlocking<Unit> {
        val acc = Account()
        acc.manage.setAssetFacts("a-bar", karat = 21)
        val f = acc.manage.setAssetFacts("a-bar", purpose = ZakatPurpose.SAVING)
        assertEquals(21 to ZakatPurpose.SAVING, f.karat to f.purpose, "الواقعة الجديدة ما بتمسحش القديمة")
        assertEquals(uiText(TextKey.ZAKAT_KARAT_RANGE), assertFailsWith<ZakatError> { acc.manage.setAssetFacts("a-bar", karat = 25) }.message)
        assertFailsWith<ZakatError> { acc.manage.setAssetFacts("a-shares", purpose = ZakatPurpose.WEAR) }
        assertFailsWith<ZakatError> { acc.manage.setAssetFacts("a-bar", holding = ZakatShareHolding.TRADING) }
        assertFailsWith<ZakatError> { acc.manage.setAssetFacts("a-bar", fineness = 925) }
        assertFailsWith<ZakatError> { acc.manage.setAssetFacts("مش-موجود", karat = 21) }
        assertFailsWith<ZakatError> { acc.manage.setReceivableFact("p-1", "o-2", ZakatCollectability.STRONG) }
        assertFailsWith<ZakatError> { acc.manage.confirmDate("2025-13-01") }
    }

    @Test
    fun `بتظهر مع المحتوى الإسلامي وفي بلد ليها قواعد بس`() = runBlocking<Unit> {
        assertTrue(Account().manage.visible())
        assertTrue(Account(profile = true).manage.visible())
        assertFalse(Account(profile = false).manage.visible())
        val other = Account("AE")
        assertFalse(other.manage.visible())
        assertEquals(uiText(TextKey.ZAKAT_COUNTRY_UNSUPPORTED), assertFailsWith<ZakatError> { other.manage.confirmDate("2025-03-01") }.message)
        assertEquals(15, Account().manage.rules().size)
    }
}
