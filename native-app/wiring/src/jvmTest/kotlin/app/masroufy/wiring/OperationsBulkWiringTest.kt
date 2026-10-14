package app.masroufy.wiring

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.SelectionTotal
import app.masroufy.core.Space
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.MemoryAccount
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * «تحديد» في «العمليات» متجمّع (قايمة المالك §6.5 بند 15): المجموع من حالة الاستخدام، والمسح بيرفض العملية المتربطة بمشروع (فحص الروابط
 * من التجميع)، و«تراجع» واحد بيرجّع الدفعة. بيانات وهمية بس.
 */
class OperationsBulkWiringTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val food = Category("c-food", null, "مطاعم", "utensils", "#A36A21", "#E0B070", active = true, order = 1, groupKey = "food")
    private val repos = memorySpaceRepositories(listOf(bank), listOf(food), profile = emptyProfile().copy(payday = 28))
    private val env = memoryEnv()

    private fun graph(): SpaceGraph {
        val session = object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = listOf(saudi to repos)
            override fun switchSpace(spaceId: String) = true
            override fun spaceTransferWriter() = null
        }
        return SpaceGraph(saudi, repos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))
    }

    @Test fun totalsDeleteGuardAndOneUndo() = runBlocking<Unit> {
        val g = graph()
        fun draft(amount: String) = AddOperationDraft(AddKind.OUT, amount, bank.id, categoryId = food.id, name = food.name)
        val a = assertIs<AddOperationResult.Saved>(g.shell.addOperation(draft("42.50"))).transaction.id
        val b = assertIs<AddOperationResult.Saved>(g.shell.addOperation(draft("10"))).transaction.id
        val ops = g.operations
        val bulk = assertNotNull(ops.bulk, "التجميع لازم يدّي الأفعال الجماعية")

        val summary = bulk.summary(listOf(a, b))
        assertEquals(listOf(SelectionTotal(Currency.SAR, 2, 5_250, 0)), summary.totals)
        assertEquals(2, summary.deletable)

        val project = ops.projects.create("مشروع وهمي")
        assertEquals(1, bulk.addToProject(listOf(a), project.id).changed)
        assertEquals(1, bulk.summary(listOf(a, b)).deletable, "المتربطة بمشروع ما تتمسحش")

        val gone = bulk.delete(listOf(a, b))
        assertEquals(1, gone.changed)
        assertTrue(repos.transactions.findByIds(listOf(b)).isEmpty())
        bulk.undo(assertNotNull(gone.undo))
        assertEquals(listOf(a, b).sorted(), repos.transactions.findByIds(listOf(a, b)).map { it.id }.sorted())
    }
}
