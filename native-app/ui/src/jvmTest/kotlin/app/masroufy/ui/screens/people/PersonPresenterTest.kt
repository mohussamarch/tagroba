package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.GiftBadge
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonProfile
import app.masroufy.core.sentenceNumber
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.ui.components.AmountTone
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ملف الشخص: الأرصدة جنب بعض من غير مقاصة · «غير متاح» لو الأرصدة ما اتحمّلتش · السجل من الالتزامات المفتوحة · النقوط بينكما. */
class PersonPresenterTest {
    @BeforeTest fun start() = resetTexts()

    @AfterTest fun end() = resetTexts()

    private val obligations = MemoryObligationRepository(
        listOf(
            Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 60_000, Currency.SAR),
            Obligation("o-2", "p-1", "t-2", ObligationKind.LOAN_PAYABLE, 20_000, Currency.SAR),
        ),
    )
    private val settlements = MemorySettlementRepository()
    private val people = ManagePeople(
        ManagePeopleDeps(
            MemoryPersonRepository(listOf(Person("p-1", "سارة وهمية"))), obligations, settlements, MemorySettlementWriter(obligations, settlements),
            MemoryAllocationRepository(), MemoryTransactionRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), testClock,
        ),
    )

    @Test fun historyAndBalancesComeReadyFromTheUseCases() = runBlocking<Unit> {
        val row = people.listWithBalances().single()
        val dues = listOf(DueItem(DueSource.DEBT, "o-1", "دين", "2026-10-06", 60_000, Currency.SAR, DueFlow.RECEIVE, DueStatus.OVERDUE))
        val badge = GiftBadge("ev-1", "فرح وهمي", LifeEventKind.WEDDING, "2026-05-01", Direction.IN, 50_000, Currency.SAR)
        val ui = personPageUi(row, null, overviewLoaded = true, PersonProfile("p-1", PersonCircle.FAMILY, "أختي", "c"), emptyList(), listOf(badge), dues, TODAY)
        assertEquals("سارة وهمية", ui.name)
        assertEquals("أختي", ui.relLine)
        assertEquals(emptyList(), ui.owed, "اتحمّلت ومالوش سطر ⇒ لا شيء (مش «غير متاح»)")
        assertEquals(listOf("o-1", "o-2"), ui.history.map { it.obligationId })
        assertEquals("سلفة منك", ui.history[0].title)
        assertEquals(AmountTone.EXPENSE, ui.history[0].tone, "سلفة منك = فلوس طلعت منك")
        assertTrue(ui.history[0].sub.startsWith("دين قديم، "), ui.history[0].sub)
        assertEquals("باقٍ 200.00 ر.س من 200.00 ر.س", ui.history[1].sub)
        assertEquals(AmountTone.INCOME, ui.history[1].tone)
        assertEquals("فات موعدها منذ " + sentenceNumber(3) + " أيام", ui.overdue)
        assertEquals(1, ui.badges.size)
    }

    @Test fun balancesNotLoadedMeansUnavailableNotZero() = runBlocking<Unit> {
        val row = people.listWithBalances().single()
        val ui = personPageUi(row, null, overviewLoaded = false, null, emptyList(), emptyList(), emptyList(), TODAY)
        assertNull(ui.owed)
        assertNull(ui.owe)
        assertNull(ui.overdue)
        assertNull(ui.relLine)
    }
}
