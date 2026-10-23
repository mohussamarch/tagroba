package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-debts — «ضيف دين» (OVERRIDES §112-2): الطرق التلاتة والميعاد والسبب والرجوع لو حاجة فشلت. **كل الأسماء والأرقام مخترعة.** */
class AddDebtTest {
    private val today = "2026-10-20"

    private fun t(id: String, dir: Direction, amount: Long, date: String = "2026-10-10") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x", rawDescription = "تحويل تجريبي",
        sourceOperationType = "تحويل", walletId = "w-bank",
    )

    private val txns = MemoryTransactionRepository(listOf(t("t-out", Direction.OUT, 40_000), t("t-in", Direction.IN, 25_000), t("t-old", Direction.OUT, 9_900, "2026-09-01")))
    private val people = MemoryPersonRepository(listOf(Person("p-1", "زياد التجريبي")))
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val links = MemoryPersonLinkRepository()
    private val terms = MemoryDebtTermsRepository()
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01"), Wallet("w-bank", "البنك", Currency.SAR, "bank", 0, "2026-01-01")))
    private val clock = FixedClock("2026-10-20T10:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val uow = MemoryUnitOfWork(listOf(txns, links))
    private val manage = ManagePeople(ManagePeopleDeps(people, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, ids, clock))
    private val personLinks = ManagePersonLinks(
        PersonLinksDeps(
            links, people, obligations, settlements, allocations, txns, MemoryCategoryRepository(), MemoryTransferPartyRepository(), uow, ids, clock,
            wallets = wallets, add = AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)), terms = terms,
        ),
    )
    private val installments = ManageInstallments(
        ManageInstallmentsDeps(
            MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(), MemoryRoscaEntryRepository(), terms, obligations, txns, uow, ids, clock,
            MemoryCategoryRepository(),
        ),
    )
    private val addDebt = AddDebt(manage, personLinks, installments, obligations)

    private fun input(
        side: DebtSideIn = DebtSideIn.FOR_YOU, who: DebtWho = DebtWho.Existing("p-1"), amount: Long = 40_000, reason: String? = null,
        due: DebtDueChoice = DebtDueChoice.NONE, picked: String? = null, money: DebtMoney = DebtMoney.Old,
    ) = NewDebt(side, who, amount, reason, due, picked, money, today, Currency.SAR)

    @Test fun oldDebtForAnExistingPersonHasNoTransactionAndKeepsTheReason() = runBlocking {
        val added = addDebt.add(input(amount = 15_050, reason = "  سلفة السفر  "))
        val o = obligations.listByPerson("p-1").single()
        assertEquals(added.obligation.id, o.id)
        assertNull(o.originTransactionId)
        assertEquals(ObligationKind.RECEIVABLE, o.kind)
        assertEquals(15_050L, o.originalMinor)
        assertEquals("سلفة السفر", o.note)
        assertTrue(terms.listAll().isEmpty(), "من غير ميعاد ⇒ مفيش شروط")
        assertEquals(3, txns.listByDateRange("0000-01-01", "9999-12-31").size, "دين قديم ⇒ مفيش عملية جديدة")
    }

    @Test fun newPersonIsCreatedAndAWeekDueIsSaved() = runBlocking {
        val added = addDebt.add(input(side = DebtSideIn.ON_YOU, who = DebtWho.New("هالة التجريبية"), amount = 70_000, due = DebtDueChoice.WEEK))
        assertEquals("هالة التجريبية", added.personName)
        assertTrue(people.listAll().any { it.id == added.personId && !it.archived })
        assertEquals(ObligationKind.LOAN_PAYABLE, added.obligation.kind)
        assertEquals("2026-10-27", terms.listAll().single().firstDueAt)
        assertEquals("2026-11-20", addDebt.dueDateOf(DebtDueChoice.MONTH, today, null))
    }

    @Test fun recordedOperationIsLinkedAndLeavesTheCandidateList() = runBlocking {
        assertEquals(listOf("t-out", "t-old"), addDebt.candidates(DebtSideIn.FOR_YOU).map { it.id })
        assertEquals(listOf("t-in"), addDebt.candidates(DebtSideIn.ON_YOU).map { it.id })
        val added = addDebt.add(input(money = DebtMoney.Operation("t-out")))
        assertEquals("t-out", added.obligation.originTransactionId)
        assertEquals(EconomicKind.LOAN_GRANTED, txns.findByIds(listOf("t-out")).single().economicKind)
        assertEquals(listOf("t-old"), addDebt.candidates(DebtSideIn.FOR_YOU).map { it.id })
    }

    @Test fun operationMustMatchTheAmountAndDirection() = runBlocking<Unit> {
        assertFailsWith<AddDebtError> { addDebt.add(input(amount = 39_999, money = DebtMoney.Operation("t-out"))) }
        assertFailsWith<AddDebtError> { addDebt.add(input(side = DebtSideIn.ON_YOU, amount = 40_000, money = DebtMoney.Operation("t-out"))) }
        assertTrue(obligations.listByPerson("p-1").isEmpty())
        assertTrue(links.listByTransactionIds(listOf("t-out")).isEmpty())
    }

    @Test fun cashWritesAnOperationInTheCashWalletWithTheReason() = runBlocking {
        val added = addDebt.add(input(side = DebtSideIn.ON_YOU, amount = 5_000, reason = "غدا", money = DebtMoney.Cash))
        val tx = txns.findByIds(listOf(added.transactionId!!)).single()
        assertEquals("w-cash", tx.walletId)
        assertEquals(Direction.IN, tx.observedDirection)
        assertEquals(EconomicKind.LOAN_RECEIVED, tx.economicKind)
        assertEquals(5_000L, tx.amountMinor)
        assertEquals("غدا", tx.note)
        assertEquals("غدا", obligations.listByPerson("p-1").single().note)
    }

    @Test fun badInputWritesNothing() = runBlocking<Unit> {
        assertFailsWith<AddDebtError> { addDebt.add(input(amount = 0)) }
        assertFailsWith<AddDebtError> { addDebt.add(input(due = DebtDueChoice.PICK, picked = "2026-10-19")) }
        assertFailsWith<AddDebtError> { addDebt.add(input(reason = "س".repeat(DEBT_REASON_MAX + 1))) }
        assertFailsWith<AddDebtError> { addDebt.add(input(who = DebtWho.New("   "))) }
        assertEquals(1, people.listAll().size)
        assertTrue(obligations.listByPerson("p-1").isEmpty())
    }

    @Test fun pickedDateIsSavedAndAFailureArchivesTheNewPerson() = runBlocking<Unit> {
        addDebt.add(input(due = DebtDueChoice.PICK, picked = "2026-12-01"))
        assertEquals("2026-12-01", terms.listAll().single().firstDueAt)
        // العملية اتربطت قبل كده ⇒ الربط بيفشل بعد إنشاء الشخص الجديد ⇒ الشخص بيتأرشف
        addDebt.add(input(money = DebtMoney.Operation("t-out")))
        assertFailsWith<Exception> { addDebt.add(input(who = DebtWho.New("شخص مؤقت"), money = DebtMoney.Operation("t-out"))) }
        assertTrue(people.listAll().single { it.name == "شخص مؤقت" }.archived)
    }
}
