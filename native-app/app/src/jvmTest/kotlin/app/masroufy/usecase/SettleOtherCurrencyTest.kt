package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.FxSettleError
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.SettlementFx
import app.masroufy.core.defaultSpace
import app.masroufy.core.fxDebtMinor
import app.masroufy.core.fxRateLabel
import app.masroufy.core.newSpaceFor
import app.masroufy.core.parseFxRate4
import app.masroufy.core.remainingOfObligation
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** §111-4: دين بالريال اتسدد بالجنيه بسعر يكتبه المالك — الدين بينزل بالهللة بعد التحويل، والسعر متسجل. مبالغ وأسامي مخترعة. */
class SettleOtherCurrencyTest {
    private val people = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
    private val obligations = MemoryObligationRepository(listOf(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 100_000, Currency.SAR)))
    private val settlements = MemorySettlementRepository()
    private val manage = ManagePeople(
        ManagePeopleDeps(
            people, obligations, settlements, MemorySettlementWriter(obligations, settlements), MemoryAllocationRepository(),
            MemoryTransactionRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), FixedClock("2026-10-23T10:00:00.000Z"),
        ),
    )

    @Test fun theRateIsParsedAsAnIntegerAndTheDebtDropsByTheRoundedAmount() {
        assertEquals(132_500L, parseFxRate4("13.25"))
        assertEquals(132_500L, parseFxRate4("13,25"))
        assertNull(parseFxRate4("13.25001"), "أكتر من 4 أرقام بعد العلامة ⇒ مرفوض")
        assertNull(parseFxRate4("0"))
        assertNull(parseFxRate4(""))
        assertEquals("13.25", fxRateLabel(132_500))
        assertEquals(10_000L, fxDebtMinor(132_500, 132_500), "1,325.00 جنيه بسعر 13.25 = 100.00 ريال")
        assertEquals(7_547L, fxDebtMinor(100_000, 132_500), "1,000.00 ÷ 13.25 = 75.4716… ⇒ 75.47")
        assertNull(fxDebtMinor(100_000, null), "من غير سعر ⇒ مش رقم (القاعدة 10)")
    }

    @Test fun settlingInPoundsReducesTheRiyalDebtAndRecordsTheRate() = runBlocking<Unit> {
        assertNull(manage.otherCurrency.prefillRate4(Currency.EGP, Currency.SAR), "مفيش سعر للجنيه في ملف الأسعار ⇒ الخانة فاضية")
        assertFailsWith<FxSettleError> { manage.otherCurrency.settle("o-1", "p-1", 132_500, Currency.EGP, null) }
        assertFailsWith<FxSettleError> { manage.otherCurrency.settle("o-1", "p-1", 10_000, Currency.SAR, 10_000) }
        val s = manage.otherCurrency.settle("o-1", "p-1", 132_500, Currency.EGP, 132_500)
        assertEquals(10_000L, s.amountMinor)
        assertEquals(SettlementFx(132_500, Currency.EGP, 132_500), s.fx)
        val o = obligations.listByPerson("p-1").single()
        assertEquals(90_000L, remainingOfObligation(o, settlements.listByObligations(listOf("o-1"))))
    }

    @Test fun archivingWithAnOpenDebtInAnyCountryAsksFirst() = runBlocking<Unit> {
        val egypt = newSpaceFor("EG", emptyList(), "2026-01-01T00:00:00Z")
        val egDebts = MemoryObligationRepository(listOf(Obligation("o-eg", "p-1", null, ObligationKind.LOAN_PAYABLE, 50_000, Currency.EGP)))
        val across = PersonAcrossSpaces(listOf(PersonSpaceBook(defaultSpace(), MemoryObligationRepository(), settlements), PersonSpaceBook(egypt, egDebts, MemorySettlementRepository())))
        val safe = ArchivePersonSafely(manage, across) { id -> people.listAll().firstOrNull { it.id == id }?.name }
        val asked = assertIs<ArchiveOutcome.OpenDebts>(safe.archive("p-1", true))
        assertEquals(listOf(egypt.id), asked.debts.map { it.spaceId }, "الدين المفتوح في مصر بيتشاف من السعودية")
        assertEquals(false, people.listAll().single().archived, "ما اتأرشفش من غير تأكيد")
        assertIs<ArchiveOutcome.Done>(safe.archive("p-1", true, confirmed = true))
        assertEquals(true, people.listAll().single().archived)
    }
}
