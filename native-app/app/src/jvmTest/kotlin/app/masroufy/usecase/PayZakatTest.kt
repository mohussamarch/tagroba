package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.ZakatCategory
import app.masroufy.core.ZakatError
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearLine
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
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.MemoryZakatYearRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * دفع الزكاة وربطه بعمليات الكشف (OVERRIDES §62) — مكتوب بالإيد، الأرقام مخترعة.
 * السنة المتثبّتة: كاش 1,000 · دهب 656.25 · ديون ليك 75 · جمعية 50 ⇒ 1,781.25.
 */
class PayZakatTest {
    private fun txn(id: String, dir: Direction, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = id, occurredAt = "2026-02-20", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val closed = ZakatYear(
        "2026-02-18", "2025-03-01", "2026-02-18", Currency.SAR, "c", "2026-02-18T10:00:00.000Z", 208_250,
        listOf(
            ZakatYearLine(ZakatLineKind.CASH, 4_000_000, 100_000), ZakatYearLine(ZakatLineKind.GOLD, 2_625_000, 65_625),
            ZakatYearLine(ZakatLineKind.RECEIVABLES, 300_000, 7_500), ZakatYearLine(ZakatLineKind.ROSCA, 200_000, 5_000),
        ),
    )
    private val open = ZakatYear("2027-02-08", "2026-02-18", "2027-02-08", Currency.SAR, "c")
    private val nothing = ZakatYear("2025-12-21", "2025-01-01", "2025-12-21", Currency.SAR, "c", "x", 208_250, listOf(ZakatYearLine(ZakatLineKind.CASH, 100_000, 0)))

    private val txns = MemoryTransactionRepository(
        listOf(
            txn("t-1", Direction.OUT, 165_625), txn("t-2", Direction.OUT, 10_000), txn("t-3", Direction.OUT, 5_000), txn("t-4", Direction.OUT, 50_000),
            txn("t-in", Direction.IN, 178_125), txn("t-egp", Direction.OUT, 178_125, Currency.EGP), txn("t-rosca", Direction.OUT, 100_000),
        ),
    )
    private val years = MemoryZakatYearRepository(listOf(closed, open, nothing))
    private val payments = MemoryZakatPaymentRepository()
    private val entries = MemoryRoscaEntryRepository(listOf(RoscaEntry("e-1", "r-1", "t-rosca", RoscaEntryKind.CONTRIBUTION, 100_000)))
    private val installmentPayments = MemoryInstallmentPaymentRepository()
    private val plans = MemoryInstallmentPlanRepository()
    private val categories = MemoryCategoryRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-02-20T09:00:00.000Z")

    private fun pay(tx: TransactionRepository = txns) = PayZakat(
        PayZakatDeps(years, payments, tx, entries, installmentPayments, plans, categories, MemoryUnitOfWork(listOf(payments, txns)), ids, clock),
    )

    @Test
    fun `دفع جزئي على سطرين ثم الباقي على عمليتين بزيادة صدقة`() = runBlocking<Unit> {
        val p = pay()
        val first = p.pay(closed.id, listOf(ZakatLineKind.GOLD, ZakatLineKind.CASH), listOf(ZakatPaymentSource("t-1")))
        assertEquals(listOf(ZakatLineKind.CASH, ZakatLineKind.GOLD), first.single().lines)
        var s = p.status(closed.id)
        assertEquals(Triple(178_125L, 165_625L, 12_500L), Triple(s.dueMinor, s.paidMinor, s.remainingMinor))
        assertEquals(listOf(true, true, false, false), s.lines.map { it.paid })
        // العملية اتصنفت «زكاة» مصروف عادي ومتأكدة، والتصنيف اتعمل
        val t = txns.findByIds(listOf("t-1")).single()
        assertEquals(Triple(EconomicKind.PURCHASE, ZakatCategory.ID, ReviewState.CONFIRMED), Triple(t.economicKind, t.categoryId, t.reviewState))
        assertEquals("زكاة", categories.listAll().single { it.id == ZakatCategory.ID }.name)

        // «دفعت الكل» على عمليتين (دفع على مرات): 100 + 50 على باقي 125 ⇒ صدقة زيادة 25
        val rest = p.pay(closed.id, null, listOf(ZakatPaymentSource("t-2"), ZakatPaymentSource("t-3")))
        assertEquals(2, rest.size)
        s = p.status(closed.id)
        assertEquals(Triple(178_125L, 0L, 2_500L), Triple(s.paidMinor, s.remainingMinor, s.extraCharityMinor))
        assertTrue(s.lines.all { it.paid })
    }

    @Test
    fun `نفس العملية ما تتربطش مرتين ولا بجمعية ولا بقسط`() = runBlocking<Unit> {
        val p = pay()
        p.pay(closed.id, null, listOf(ZakatPaymentSource("t-1", 100_000)))
        assertEquals(uiText(TextKey.DUE_TXN_ALREADY_LINKED), assertFailsWith<DueLinkError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-1"))) }.message)
        assertEquals(uiText(TextKey.ZAKAT_TXN_TWICE), assertFailsWith<ZakatError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-2"), ZakatPaymentSource("t-2"))) }.message)
        // عملية متربطة بجمعية ما تبقاش دفعة زكاة
        assertFailsWith<DueLinkError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-rosca"))) }
        // والعكس: الجمعية والقسط بيرفضوا عملية الزكاة
        val roscas = ManageRoscas(ManageRoscasDeps(MemoryRoscaRepository(), entries, installmentPayments, txns, MemoryUnitOfWork(listOf(entries, txns)), ids, clock, categories, plans, payments))
        val rosca = roscas.save(RoscaInput(name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 100_000, firstDueAt = "2026-01-01", cycleCount = 10, myTurns = listOf(1)))
        assertFailsWith<DueLinkError> { roscas.link(rosca.id, "t-1", RoscaEntryKind.CONTRIBUTION) }
        val installments = ManageInstallments(
            ManageInstallmentsDeps(plans, installmentPayments, entries, MemoryDebtTermsRepository(), MemoryObligationRepository(), txns, MemoryUnitOfWork(listOf(installmentPayments, txns)), ids, clock, categories, payments),
        )
        val plan = installments.save(InstallmentInput(name = "تقسيط وهمي", provider = "محل وهمي", kind = InstallmentKind.PURCHASE_PLAN, currency = Currency.SAR, principalMinor = 300_000, totalMinor = 300_000, installmentMinor = 100_000, firstDueAt = "2026-02-01"))
        assertFailsWith<DueLinkError> { installments.link(plan.id, "t-1") }
        // جزء من عملية مسموح، أكتر منها لأ · وارد · عملة تانية
        assertFailsWith<DueLinkError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-4", 60_000))) }
        assertFailsWith<DueLinkError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-in"))) }
        assertFailsWith<DueLinkError> { p.pay(closed.id, null, listOf(ZakatPaymentSource("t-egp"))) }
        assertEquals(1, payments.all().size)
    }

    @Test
    fun `لو تعديل العملية فشل الدفعة ما بتتسجلش`() = runBlocking<Unit> {
        val failing = object : TransactionRepository by txns {
            override suspend fun update(id: String, patch: TransactionPatch) = throw IllegalStateException("انقطاع وهمي")
        }
        assertFailsWith<IllegalStateException> { pay(failing).pay(closed.id, null, listOf(ZakatPaymentSource("t-2"), ZakatPaymentSource("t-3"))) }
        assertTrue(payments.all().isEmpty())
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf("t-2")).single().economicKind)
        assertEquals(178_125L, pay().status(closed.id).remainingMinor)
    }

    @Test
    fun `دفع كاش من غير عملية وفك الربط`() = runBlocking<Unit> {
        val p = pay()
        val cash = p.payCash(closed.id, listOf(ZakatLineKind.ROSCA), 5_000, "2026-02-19")
        assertNull(cash.transactionId)
        assertTrue(p.status(closed.id).lines.single { it.kind == ZakatLineKind.ROSCA }.paid)
        val linked = p.pay(closed.id, listOf(ZakatLineKind.CASH), listOf(ZakatPaymentSource("t-4"))).single()
        p.unlink(closed.id, linked.id)
        val t = txns.findByIds(listOf("t-4")).single()
        assertEquals(Triple(EconomicKind.UNCLASSIFIED, null, ReviewState.NEEDS_REVIEW), Triple(t.economicKind, t.categoryId, t.reviewState))
        assertEquals(173_125L, p.status(closed.id).remainingMinor)
        p.unlink(closed.id, cash.id)
        assertTrue(payments.all().isEmpty())
        assertFailsWith<ZakatError> { p.unlink(closed.id, cash.id) }
        assertFailsWith<ZakatError> { p.payCash(closed.id, null, 0, "2026-02-19") }
        assertFailsWith<ZakatError> { p.payCash(closed.id, null, 100, "امبارح") }
    }

    @Test
    fun `السنة لازم تكون متثبّتة وعليها مطلوب والسطور منها`() = runBlocking<Unit> {
        val p = pay()
        assertEquals(uiText(TextKey.ZAKAT_YEAR_NOT_CLOSED), assertFailsWith<ZakatError> { p.pay(open.id, null, listOf(ZakatPaymentSource("t-1"))) }.message)
        assertEquals(uiText(TextKey.ZAKAT_NOTHING_DUE), assertFailsWith<ZakatError> { p.pay(nothing.id, null, listOf(ZakatPaymentSource("t-1"))) }.message)
        assertFailsWith<ZakatError> { p.pay(closed.id, listOf(ZakatLineKind.SILVER), listOf(ZakatPaymentSource("t-1"))) }
        assertFailsWith<ZakatError> { p.pay(closed.id, emptyList(), listOf(ZakatPaymentSource("t-1"))) }
        assertFailsWith<ZakatError> { p.pay(closed.id, null, emptyList()) }
        assertFailsWith<ZakatError> { p.status("مش-موجودة") }
        assertTrue(payments.all().isEmpty())
    }
}
