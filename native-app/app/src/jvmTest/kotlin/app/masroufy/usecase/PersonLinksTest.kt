package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.AutoMeaning
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonEntryType
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.SuggestWhy
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * جولة r05 — شكوى المالك 2026-10-16 («علّمت عمليات بيني وبين شخص إنها دين… وصفحة الشخص فاضية»): كل طريق ربط ⇒ صفحة الشخص بتعرض الدين والعملية،
 * والعمليات اللي اتعلّمت بتصنيف بس بتطلع في «محتاجة تربطها بشخص» وبتتربط صح. **كل الأسماء والأرقام مخترعة.**
 */
class PersonLinksTest {
    private val op = "عملية تحويل داخلية"
    private fun t(
        id: String, dir: Direction, date: String, amount: Long, desc: String? = null, kind: EconomicKind = EconomicKind.UNCLASSIFIED,
        confirmed: Boolean = false, category: String? = null, merchant: String? = null, transfer: Boolean = true,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = category != null, excludedFromBudget = false,
        reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = desc, sourceOperationType = if (transfer) op else null, categoryId = category, rawMerchantName = merchant, walletId = "w-bank",
    )

    // «سامي التجريبي» حسابه آخره 4321 — الصادر ليه والوارد منه (الوارد متسجل «استرداد مبلغ» مؤكد زي حالة المالك)
    private val out = t("t-out", Direction.OUT, "2026-02-01", 50_000, "ساميW-/TOACCT/99990000004321TO:ملاحظة", category = "c-people")
    private val refund = t("t-in", Direction.IN, "2026-02-10", 20_000, "ساميW-/FRACCT/99990000004321FR:ملاحظة", EconomicKind.REFUND_RECEIVED, true, "c-people")
    private val buy = t("t-buy", Direction.OUT, "2026-02-05", 12_000, merchant = "مطعم تجريبي", kind = EconomicKind.PURCHASE, confirmed = true, transfer = false)
    private val later = t("t-later", Direction.IN, "2026-03-05", 30_000, "ساميW-/FRACCT/99990000004321FR:ملاحظة")
    private val older = t("t-older", Direction.IN, "2026-02-20", 4_000, "ساميW-/FRACCT/99990000004321FR:ملاحظة")

    private val txns = MemoryTransactionRepository(listOf(out, refund, buy, later, older))
    private val links = MemoryPersonLinkRepository()
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val parties = MemoryTransferPartyRepository()
    private val people = MemoryPersonRepository(listOf(Person("p-1", "سامي التجريبي"), Person("p-2", "شخص تاني")))
    private val categories = MemoryCategoryRepository(
        listOf(
            Category("c-transfers", null, "تحويلات", "send", "#000000", "#ffffff", true, 1),
            Category("c-people", "c-transfers", "تحويل لأشخاص", "users", "#000000", "#ffffff", true, 2),
            Category("c-food", null, "مطاعم", "utensils", "#000000", "#ffffff", true, 3),
        ),
    )
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01"), Wallet("w-bank", "البنك", Currency.SAR, "bank", 0, "2026-01-01")))
    private val clock = FixedClock("2026-03-01T10:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val manage = ManagePersonLinks(
        PersonLinksDeps(
            links, people, obligations, settlements, allocations, txns, categories, parties, MemoryUnitOfWork(listOf(txns, links, parties)), ids, clock,
            wallets = wallets, add = AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)),
        ),
    )

    private suspend fun byId(id: String) = txns.findByIds(listOf(id)).single()

    @Test
    fun categoryOnlyLabelIsListedAsNeedingAPerson() = runBlocking {
        assertEquals(listOf("t-in", "t-out"), manage.needsPerson().map { it.transaction.id })
        assertEquals("تحويل لأشخاص", manage.needsPerson().first().categoryName)
        assertTrue(manage.statusOf("t-out").needsPerson)
        assertFalse(manage.statusOf("t-buy").needsPerson)
        // الصفحة قبل أي ربط فاضية فعلًا (ومفيش حساب مربوط)
        assertTrue(manage.page.load("p-1")!!.ledger.isEmpty)
    }

    @Test
    fun lentThenCollectedShowsOnThePersonPageAndFixesTheRefundLabel() = runBlocking {
        val lent = manage.link("t-out", "p-1", PersonMeaning.LENT)
        assertEquals(EconomicKind.LOAN_GRANTED, byId("t-out").economicKind)
        assertTrue(byId("t-out").economicKindConfirmed)
        val debt = obligations.listByPerson("p-1").single()
        assertEquals(ObligationKind.RECEIVABLE, debt.kind)
        assertEquals(50_000L, debt.originalMinor)
        assertEquals("t-out", debt.originTransactionId)
        assertEquals(debt.id, lent.link.obligationId)

        val collected = manage.link("t-in", "p-1", PersonMeaning.COLLECTED)
        assertEquals(0L, collected.unmatchedMinor)
        // «استرداد مبلغ» اتصلح لـ«تحصيل دين» لأنه طلع سداد من الشخص
        assertEquals(EconomicKind.DEBT_COLLECTED, byId("t-in").economicKind)
        assertEquals(20_000L, settlements.listByObligations(listOf(debt.id)).single().amountMinor)

        val page = manage.page.load("p-1")!!
        assertEquals(listOf("t-in", "t-out"), page.ledger.entries.map { it.transactionId })
        assertEquals(listOf(30_000L, 50_000L), page.ledger.entries.map { it.netAfterMinor })
        assertEquals(30_000L, page.ledger.totals.single().owedToYouMinor)
        assertEquals(30_000L, page.ledger.open.single().remainingMinor)
        assertTrue(manage.needsPerson().isEmpty())
        assertEquals("سامي التجريبي", manage.statusOf("t-in").personName)
    }

    @Test
    fun unlinkIsTheUndoAndRestoresEverything() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        manage.link("t-in", "p-1", PersonMeaning.COLLECTED)
        // الدين اتسدد منه ⇒ فكّه مرفوض لحد ما السداد يتشال
        assertFailsWith<PersonLinkError> { manage.unlink("t-out") }
        assertTrue(manage.unlink("t-in"))
        assertEquals(EconomicKind.REFUND_RECEIVED, byId("t-in").economicKind)
        assertTrue(byId("t-in").economicKindConfirmed)
        assertTrue(manage.unlink("t-out"))
        assertEquals(EconomicKind.UNCLASSIFIED, byId("t-out").economicKind)
        assertFalse(byId("t-out").economicKindConfirmed)
        assertTrue(obligations.listByPerson("p-1").isEmpty())
        assertEquals(listOf("t-in", "t-out"), manage.needsPerson().map { it.transaction.id })
        assertFalse(manage.unlink("t-out"))
    }

    @Test
    fun wrongDirectionAndDoubleLinkAreRefused() = runBlocking {
        assertFailsWith<PersonLinkError> { manage.link("t-out", "p-1", PersonMeaning.BORROWED) }
        manage.link("t-out", "p-1", PersonMeaning.WITH)
        assertFailsWith<PersonLinkError> { manage.link("t-out", "p-2", PersonMeaning.LENT) }
        // «مع الشخص ده بس»: بيبان في السجل من غير دين ولا تغيير نوع
        val page = manage.page.load("p-1")!!
        assertEquals(PersonEntryType.LINK, page.ledger.entries.single().type)
        assertEquals(0L, page.ledger.entries.single().netAfterMinor)
        assertEquals(EconomicKind.UNCLASSIFIED, byId("t-out").economicKind)
    }

    @Test
    fun purchaseGiftWritesAGiftShareAndCollectedWithoutDebtIsKept() = runBlocking {
        manage.link("t-buy", "p-2", PersonMeaning.GAVE)
        assertEquals(AllocationKind.GIFT, allocations.listByTransactionIds(listOf("t-buy")).single().allocationKind)
        assertEquals(EconomicKind.PURCHASE, byId("t-buy").economicKind)
        val out = manage.link("t-in", "p-2", PersonMeaning.COLLECTED)
        assertEquals(20_000L, out.unmatchedMinor)
        assertEquals(listOf("t-in", "t-buy"), manage.page.load("p-2")!!.ledger.entries.map { it.transactionId })
    }

    @Test
    fun manualAccountAutoRecordsOnlyNewTransfers() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        val account = manage.accounts.addManual("p-1", "٤٣٢١", "بنك تجريبي")
        assertEquals("#4321", account.key)
        assertEquals("4321", account.last4)
        assertFailsWith<PersonLinkError> { manage.accounts.addManual("p-2", "4321", null) }
        assertFailsWith<PersonLinkError> { manage.accounts.addManual("p-1", "12a", null) }
        // الحساب المربوط ⇒ تحويلاته بتبان في السجل حتى من غير معنى
        val before = manage.page.load("p-1")!!
        assertTrue(before.ledger.entries.any { it.transactionId == "t-later" && it.type == PersonEntryType.ACCOUNT_TRANSFER })
        manage.accounts.setAuto(account.key, AutoMeaning.DUES)
        assertEquals(1, manage.accounts.applyAuto())
        val auto = links.listByTransactionIds(listOf("t-later")).single()
        assertTrue(auto.auto)
        assertEquals(PersonMeaning.COLLECTED, auto.meaning)
        assertNull(links.listByTransactionIds(listOf("t-older")).firstOrNull())
        assertEquals(20_000L, manage.page.load("p-1")!!.ledger.totals.single().owedToYouMinor)
        assertEquals(0, manage.accounts.applyAuto())
        // شيل الحساب و«تراجع»
        val removed = manage.accounts.remove("p-1", account.key)!!
        assertTrue(manage.accounts.of("p-1").isEmpty())
        manage.accounts.restore(removed)
        assertEquals(listOf("#4321"), manage.accounts.of("p-1").map { it.key })
    }

    @Test
    fun suggestionsAndSearchSkipLinkedOperations() = runBlocking {
        val first = manage.page.suggestions("p-1")
        assertTrue(first.any { it.transaction.id == "t-later" && it.why == SuggestWhy.NAME })
        manage.link("t-later", "p-1", PersonMeaning.RECEIVED)
        assertTrue(manage.page.suggestions("p-1").none { it.transaction.id == "t-later" })
        assertEquals(listOf("t-out"), manage.page.search("500").map { it.id })
        assertEquals(listOf("t-buy"), manage.page.search("مطعم").map { it.id })
    }

    @Test
    fun addOperationWithPersonInCashAndUndo() = runBlocking {
        val added = manage.accounts.addOperation("p-1", PersonMeaning.LENT, 7_500, "2026-03-02", "w-cash", "سلفة كاش")
        val tx = byId(added.link.transactionId)
        assertEquals(Direction.OUT, tx.observedDirection)
        assertEquals(EconomicKind.LOAN_GRANTED, tx.economicKind)
        assertEquals("w-cash", tx.walletId)
        assertTrue(allocations.listByTransactionIds(listOf(tx.id)).isEmpty())
        assertEquals(7_500L, manage.page.load("p-1")!!.ledger.open.single().remainingMinor)
        assertFailsWith<PersonLinkError> { manage.accounts.addOperation("p-1", PersonMeaning.WITH, 100, "2026-03-02", "w-cash", null) }
        manage.accounts.undoAdd(tx.id)
        assertTrue(txns.findByIds(listOf(tx.id)).isEmpty())
        assertTrue(manage.page.load("p-1")!!.ledger.isEmpty)
    }

    @Test
    fun fullyCollectedDebtMovesToSettled() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        manage.link("t-in", "p-1", PersonMeaning.COLLECTED)
        manage.link("t-later", "p-1", PersonMeaning.COLLECTED)
        val page = manage.page.load("p-1")!!
        assertTrue(page.ledger.open.isEmpty())
        assertEquals(1, page.ledger.settled.size)
        assertEquals(0L, page.ledger.totals.single().netMinor)
        assertEquals(listOf("t-later", "t-in", "t-out"), page.ledger.entries.map { it.transactionId })
    }
}
