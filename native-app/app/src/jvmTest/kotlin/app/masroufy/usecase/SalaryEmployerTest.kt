package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.salaryRetroPlan
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * جولة r05 — البند 9: راتب من غير حساب مُحوِّل («حوالة واردة راتب») ⇒ الجهة الوحيدة الشغالة يومه لوحدها (و«مش دي» بتراجع) · جهتين ⇒ «راتب من أي
 * جهة؟» · «طبّق على اللي فات» لجهة بياخد رسايل الراتب اللي من غير حساب في فترتها. **كل الأسماء والأرقام مخترعة.**
 */
class SalaryEmployerTest {
    private fun salary(id: String, date: String, kind: EconomicKind = EconomicKind.SALARY, confirmed: Boolean = true) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.IN, amountMinor = 900_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "حوالة واردة راتب\nمبلغ: 9000 SAR\nإلى: 1234\nفي: 26-02-27",
    )

    private fun employer(id: String, name: String, from: String, to: String? = null) =
        IncomeSource(id, name, "", IncomeSourceKind.JOB, Currency.SAR, from, to, createdAt = "x")

    private val txns = MemoryTransactionRepository(listOf(salary("t-feb", "2026-02-27"), salary("t-old", "2025-06-27", EconomicKind.UNCLASSIFIED, false)))

    private fun manage(vararg sources: IncomeSource) =
        ManageEmployers(ManageEmployersDeps(MemoryIncomeSourceRepository(sources.toList()), txns, SequentialIdGenerator(), FixedClock("2026-03-01T10:00:00.000Z"), Currency.SAR))

    @Test
    fun soleActiveEmployerIsAttachedAutomaticallyAndNotThisUndoes() = runBlocking {
        val m = manage(employer("e-a", "شركة أ", "2026-01-01"), employer("e-b", "شركة ب", "2024-01-01", "2025-12-31"))
        val first = m.employerFor("t-feb")!!
        assertEquals("e-a", first.current?.id)
        assertTrue(first.auto)
        val before = m.declineAuto("e-a", "t-feb")
        assertNull(m.employerFor("t-feb")!!.current)
        m.restore(before)
        assertEquals("e-a", m.employerFor("t-feb")!!.current?.id)
    }

    @Test
    fun twoActiveEmployersAskAndThePickIsUndoable() = runBlocking {
        val m = manage(employer("e-a", "شركة أ", "2026-01-01"), employer("e-b", "شركة ب", "2025-01-01"))
        val ask = m.employerFor("t-feb")!!
        assertNull(ask.current)
        assertEquals(setOf("e-a", "e-b"), ask.choices.map { it.id }.toSet())
        val before = m.attachTransaction("e-b", "t-feb")
        val picked = m.employerFor("t-feb")!!
        assertEquals("e-b", picked.current?.id)
        assertFalse(picked.auto)
        m.restore(before)
        assertNull(m.employerFor("t-feb")!!.current)
    }

    @Test
    fun retroForAnEmployerIncludesPartylessSalarySmsInItsPeriod() = runBlocking {
        val a = employer("e-a", "شركة أ", "2025-01-01")
        val b = employer("e-b", "شركة ب", "2025-03-01", "2025-12-31")
        val all = txns.listByDateRange("0000-01-01", "9999-12-31")
        // جهتين شغالين في يونيو 2025 ⇒ «كل الجهات» ما بيخمّنش، بس «طبّق» لجهة بعينها بياخدها
        assertTrue(salaryRetroPlan(all, listOf(a, b), null).toSalary.none { it.id == "t-old" })
        assertEquals(listOf("t-old"), salaryRetroPlan(all, listOf(a, b), "e-b").toSalary.map { it.id })
        assertEquals(listOf("t-old"), salaryRetroPlan(all, listOf(a), null).toSalary.map { it.id })
    }
}
