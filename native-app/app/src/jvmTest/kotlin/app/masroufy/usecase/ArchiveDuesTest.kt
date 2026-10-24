package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-archive (OVERRIDES §118-ب-1): «أرشفه» للخطة والجمعية ومصدر الدخل — أول اختيار في لوحة المسح · المؤرشف بيختفي من المواعيد
 * (`LoadDues.dueItems` — التقويم والتنبيهات والرئيسية) · المجاميع زي ما هي · «رجّعه» بيرجّعه · «تراجع» بيرجّع المستند بالظبط ·
 * مفيش كتابة لو مفيش تغيير · مصدر الدخل المؤرشف منتهي. كل الأسامي والمبالغ مخترعة.
 */
class ArchiveDuesTest {
    private val plan = InstallmentPlan("pl-1", "جوال تجربة", "متجر تجربة", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 3_000_00, 3_000_00, 1_000_00, 1, "2026-08-01")
    private val rosca = Rosca("r-1", "جمعية تجربة", Currency.SAR, 500_00, 1, "2026-01-01", 10, listOf(3), 5_000_00)
    private val job = IncomeSource("inc-1", "شركة تجربة", "شركة تجربة", IncomeSourceKind.JOB, Currency.SAR, "2026-01-01")

    private suspend fun kit() = RemovalKit().apply {
        plans.save(plan)
        roscas.save(rosca)
        incomeSources.saveMany(listOf(job))
        txns.saveMany(listOf(op("p-1", 1_000_00), op("r-e", 500_00), op("x", 70_00)))
        payments.saveMany(listOf(InstallmentPayment("ip-1", "pl-1", "p-1", 1_000_00)))
        entries.saveMany(listOf(RoscaEntry("e-1", "r-1", "r-e", RoscaEntryKind.CONTRIBUTION, 500_00)))
    }

    private fun RemovalKit.dues() = LoadDues(LoadDuesDeps(roscas, entries, plans, payments, terms, people, obligations, settlements, recurring, txns))

    private suspend fun RemovalKit.dueIds() = dues().dueItems("2026-01-01", "2027-12-31").map { it.sourceId }.toSet()

    @Test
    fun deleteSheet_archiveIsTheFirstChoice() = runBlocking {
        val k = kit()
        assertEquals(RemoveChoice.ARCHIVE, k.removals.plan.ask("pl-1").choices.first())
        assertEquals(RemoveChoice.ARCHIVE, k.removals.rosca.ask("r-1").choices.first())
    }

    @Test
    fun plan_archiveHides_totalsSame_unarchiveRestores() = runBlocking {
        val k = kit()
        val totals = k.totals()
        val ops = k.picture()
        assertTrue("pl-1" in k.dueIds())
        val done = k.removals.plan.remove("pl-1", RemoveChoice.ARCHIVE)
        assertEquals(RemoveChoice.ARCHIVE, done.choice)
        assertEquals("2026-10-24T10:00:00Z", k.plans.listAll().single().archivedAt)
        assertTrue("pl-1" !in k.dueIds())
        assertEquals(totals, k.totals())
        assertEquals(ops, k.picture())
        assertEquals(1, k.payments.listByPlan("pl-1").size) // التاريخ زي ما هو
        val back = k.removals.archives.plan.setArchived("pl-1", false)
        assertEquals(RemoveChoice.UNARCHIVE, back?.choice)
        assertEquals(listOf(plan), k.plans.listAll())
        assertTrue("pl-1" in k.dueIds())
    }

    @Test
    fun rosca_archiveHides_undoExact() = runBlocking {
        val k = kit()
        val totals = k.totals()
        val done = k.removals.rosca.remove("r-1", RemoveChoice.ARCHIVE)
        assertTrue(k.roscas.listAll().single().archived)
        assertTrue("r-1" !in k.dueIds())
        assertEquals(totals, k.totals())
        done.undo()
        assertEquals(listOf(rosca), k.roscas.listAll())
        assertTrue("r-1" in k.dueIds())
    }

    @Test
    fun noChange_writesNothing() = runBlocking {
        val k = kit()
        assertNull(k.removals.archives.plan.setArchived("pl-1", false))
        assertNull(k.removals.archives.rosca.setArchived("r-1", false))
        assertNull(k.removals.archives.incomeSource.setArchived("inc-1", false))
        assertEquals(listOf(plan), k.plans.listAll())
        k.removals.archives.rosca.setArchived("r-1", true)
        val once = k.roscas.listAll()
        assertNull(k.removals.archives.rosca.setArchived("r-1", true))
        assertEquals(once, k.roscas.listAll())
    }

    @Test
    fun income_archivedCountsAsEnded_unarchiveReopens() = runBlocking {
        val k = kit()
        val totals = k.totals()
        k.removals.archives.incomeSource.setArchived("inc-1", true)
        val s = k.incomeSources.listAll().single()
        assertTrue(s.archived)
        assertEquals("2026-10-24", s.endedAt)
        assertEquals(true, s.endedByArchive)
        assertEquals(totals, k.totals())
        k.removals.archives.incomeSource.setArchived("inc-1", false)
        assertEquals(listOf(job), k.incomeSources.listAll())
    }

    @Test
    fun income_endedBefore_keepsItsEndDate() = runBlocking {
        val k = kit()
        val ended = job.copy(endedAt = "2026-05-31")
        k.incomeSources.saveMany(listOf(ended))
        val done = k.removals.incomeSource.remove("inc-1", RemoveChoice.ARCHIVE)
        assertEquals("2026-05-31", k.incomeSources.listAll().single().endedAt)
        assertNull(k.incomeSources.listAll().single().endedByArchive)
        done.undo()
        assertEquals(listOf(ended), k.incomeSources.listAll())
        k.removals.archives.incomeSource.setArchived("inc-1", true)
        k.removals.archives.incomeSource.setArchived("inc-1", false)
        assertEquals(listOf(ended), k.incomeSources.listAll())
    }

    @Test
    fun income_notStartedYet_endsOnItsStart() {
        val future = job.copy(startedAt = "2026-12-01")
        assertEquals("2026-12-01", archivedSource(future, true, "2026-10-24T10:00:00Z").endedAt)
    }
}
