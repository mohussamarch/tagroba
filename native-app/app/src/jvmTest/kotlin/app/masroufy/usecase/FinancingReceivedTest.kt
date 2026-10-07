package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentError
import app.masroufy.core.InstallmentKind
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * مبلغ التمويل اللي نزل في الحساب بيتربط بخطته — قرار المالك 2026-10-03 (OVERRIDES §59). مكتوب بالإيد، الأرقام وهمية.
 */
class FinancingReceivedTest {
    private fun txn(id: String, dir: Direction, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = id, occurredAt = "2026-01-05", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false,
        createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "2026-01-01T00:00:00.000Z",
    )

    private val txns = MemoryTransactionRepository(
        listOf(txn("t-got", Direction.IN, 980_000), txn("t-out", Direction.OUT, 100_000), txn("t-egp", Direction.IN, 980_000, Currency.EGP)),
    )
    private val plans = MemoryInstallmentPlanRepository()
    private val payments = MemoryInstallmentPaymentRepository()
    private val entries = MemoryRoscaEntryRepository()
    private val categories = MemoryCategoryRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-01-06T09:00:00.000Z")

    private fun installments(tx: TransactionRepository = txns) = ManageInstallments(
        ManageInstallmentsDeps(
            plans, payments, entries, MemoryDebtTermsRepository(), MemoryObligationRepository(), tx,
            MemoryUnitOfWork(listOf(payments, txns, plans)), ids, clock, categories,
        ),
    )

    private val loan = InstallmentInput(
        name = "تمويل وهمي", provider = "بنك وهمي", kind = InstallmentKind.FINANCING, currency = Currency.SAR,
        principalMinor = 1_000_000, totalMinor = 1_200_000, installmentMinor = 100_000, firstDueAt = "2026-02-10",
    )

    @Test
    fun `المبلغ المستلم بيتربط بخطته مرة واحدة وبيتفك`() = runBlocking<Unit> {
        val manage = installments()
        val plan = manage.save(loan)
        assertNull(manage.list("2026-01-06").single().receivedMinor, "لسه ما اتربطش = مش متاح، مش صفر")

        assertEquals("t-got", manage.linkReceived(plan.id, "t-got").receivedTransactionId)
        val t = txns.findByIds(listOf("t-got")).single()
        assertEquals(EconomicKind.FINANCING_RECEIVED to DuesCategories.FINANCING, t.economicKind to t.categoryId)
        assertEquals(ReviewState.CONFIRMED, t.reviewState)
        // اللوحة بتاخد اللي نزل فعلًا (البنك خصم مصاريف) مش المبلغ المكتوب في الخطة
        assertEquals(980_000L, manage.list("2026-01-06").single().receivedMinor)

        // تعديل الخطة ما بيمسحش الربط، والنوع والعملة بيتقفلوا
        assertEquals("t-got", manage.save(loan.copy(id = plan.id, name = "تمويل وهمي ٢")).receivedTransactionId)
        assertFailsWith<InstallmentError> { manage.save(loan.copy(id = plan.id, kind = InstallmentKind.PURCHASE_PLAN)) }
        assertFailsWith<InstallmentError> { manage.save(loan.copy(id = plan.id, currency = Currency.EGP)) }

        // مرة واحدة لكل خطة · والعملية ما تتربطش بخطة تانية ولا بجمعية
        assertFailsWith<InstallmentError> { manage.linkReceived(plan.id, "t-got") }
        val other = manage.save(loan.copy(name = "تمويل تاني"))
        assertFailsWith<DueLinkError> { manage.linkReceived(other.id, "t-got") }
        val roscas = ManageRoscas(ManageRoscasDeps(MemoryRoscaRepository(), entries, payments, txns, MemoryUnitOfWork(listOf(entries, txns)), ids, clock, categories, plans))
        val rosca = roscas.save(RoscaInput(name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 98_000, firstDueAt = "2026-01-01", cycleCount = 10, myTurns = listOf(1)))
        assertFailsWith<DueLinkError> { roscas.link(rosca.id, "t-got", RoscaEntryKind.PAYOUT) }

        manage.unlink("t-got")
        assertNull(plans.listAll().first { it.id == plan.id }.receivedTransactionId)
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf("t-got")).single().economicKind)
        // بعد الفك يتربط تاني عادي
        assertEquals("t-got", manage.linkReceived(other.id, "t-got").receivedTransactionId)
    }

    @Test
    fun `على خطة تمويل بس وبفلوس داخلة وبنفس العملة`() = runBlocking<Unit> {
        val manage = installments()
        val phone = manage.save(loan.copy(name = "جوال", kind = InstallmentKind.PURCHASE_PLAN))
        assertFailsWith<InstallmentError> { manage.linkReceived(phone.id, "t-got") }
        val plan = manage.save(loan)
        assertEquals(uiText(TextKey.DUE_RECEIVED_NEEDS_IN), assertFailsWith<DueLinkError> { manage.linkReceived(plan.id, "t-out") }.message)
        assertFailsWith<DueLinkError> { manage.linkReceived(plan.id, "t-egp") }
        assertFailsWith<DueLinkError> { manage.linkReceived(plan.id, "t-مش-موجودة") }
        assertNull(plans.listAll().first { it.id == plan.id }.receivedTransactionId)
    }

    @Test
    fun `لو تعديل العملية فشل الخطة ما بتتربطش`() = runBlocking<Unit> {
        val failing = object : TransactionRepository by txns {
            override suspend fun update(id: String, patch: TransactionPatch) = throw IllegalStateException("انقطاع وهمي")
        }
        val manage = installments(failing)
        val plan = manage.save(loan)
        assertFailsWith<IllegalStateException> { manage.linkReceived(plan.id, "t-got") }
        assertNull(plans.listAll().single().receivedTransactionId)
    }
}
