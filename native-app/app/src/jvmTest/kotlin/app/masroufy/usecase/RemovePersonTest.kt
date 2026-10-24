package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.Settlement
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح الشخص: الدين المفتوح بيقفل «سيب العمليات». كل الأسامي والمبالغ مخترعة. */
class RemovePersonTest {
    private val friend = Person("p-1", "صديق تجربة")

    /** سلفة 300 منك + نصيبه 40 في عشا + (لو [settled]) سداد الـ300 كلها. */
    private suspend fun kit(settled: Boolean, ops: Boolean = true) = RemovalKit().apply {
        people.save(friend)
        if (!ops) return@apply
        txns.saveMany(listOf(op("lend", 300_00, EconomicKind.LOAN_GRANTED), op("dinner", 120_00), op("x", 9_00)))
        obligations.saveMany(listOf(Obligation("o-1", "p-1", "lend", ObligationKind.RECEIVABLE, 300_00, Currency.SAR)))
        allocations.saveMany(listOf(PersonAllocation("al-1", "dinner", "p-1", AllocationKind.GIFT, 40_00, Currency.SAR)))
        personLinks.saveMany(listOf(PersonLink("lend", "p-1", PersonMeaning.LENT, 300_00, "2026-10-10T08:00:00Z", obligationId = "o-1")))
        if (settled) {
            txns.saveMany(listOf(op("back", 300_00, EconomicKind.DEBT_COLLECTED, Direction.IN)))
            settlements.saveMany(listOf(Settlement("st-1", "back", "o-1", 300_00)))
        }
    }

    @Test
    fun noOperations_deleteThenUndo() = runBlocking {
        val k = kit(settled = false, ops = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.person.ask("p-1").choices)
        val done = k.removals.person.remove("p-1", RemoveChoice.DELETE)
        assertTrue(k.people.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(friend), k.people.listAll())
    }

    @Test
    fun openDebt_blocksKeep() = runBlocking {
        val k = kit(settled = false)
        val ask = k.removals.person.ask("p-1")
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), ask.choices)
        assertNotNull(ask.keepBlocked)
        assertFailsWith<DuesRemovalRefused> { k.removals.person.remove("p-1", RemoveChoice.KEEP_OPERATIONS) }
        assertEquals(listOf(friend), k.people.listAll())
    }

    @Test
    fun archive_thenUndo() = runBlocking {
        val k = kit(settled = false)
        val done = k.removals.person.remove("p-1", RemoveChoice.ARCHIVE)
        assertEquals(true, k.people.listAll().single().archived)
        done.undo()
        assertEquals(listOf(friend), k.people.listAll())
    }

    @Test
    fun closedDebt_keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit(settled = true)
        assertNull(k.removals.person.ask("p-1").keepBlocked)
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.person.remove("p-1", RemoveChoice.KEEP_OPERATIONS)
        assertEquals(totals, k.totals(), "النصيب والدين المقفول فضلوا ⇒ المجاميع زي ما هي")
        assertEquals(true, k.people.listAll().single().archived, "مستند مؤرشف عشان النصيب والدين المقفول ما يبقوش رابط مقطوع")
        assertTrue(k.personLinks.listByPerson("p-1").isEmpty())
        assertEquals(3, k.all().count { it.sourceRemoved == "person:صديق تجربة" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(friend), k.people.listAll())
        assertEquals(1, k.personLinks.listByPerson("p-1").size)
    }

    @Test
    fun withOperations_evenWithOpenDebt_undoExact() = runBlocking {
        val k = kit(settled = false)
        val before = k.picture()
        val done = k.removals.person.remove("p-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("x"), k.all().map { it.id })
        assertTrue(k.people.listAll().isEmpty() && k.obligations.listByPerson("p-1").isEmpty())
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(friend), k.people.listAll())
        assertEquals("lend", k.obligations.listByPerson("p-1").single().originTransactionId)
        assertEquals(1, k.allocations.listByTransactionIds(listOf("dinner")).size)
    }
}
