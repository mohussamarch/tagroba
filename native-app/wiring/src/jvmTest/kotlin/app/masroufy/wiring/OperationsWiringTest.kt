package app.masroufy.wiring

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryHttpText
import app.masroufy.memory.MemorySpaceTransferRepository
import app.masroufy.memory.MemorySpaceTransferWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.usecase.AddKind
import app.masroufy.usecase.AddOperationDraft
import app.masroufy.usecase.AddOperationResult
import app.masroufy.usecase.LoadOnlineFeeds
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.NewSpaceTransfer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «العمليات» متجمّعة على مستودعات الذاكرة: السجل بيشوف اللي اتسجل من «+»، ويوم الراتب والمحافظ والبلاد من التجميع، ورجل «التحويل لنفسك»
 * بتتقري من بلدها. من غير كاتب البلدين التسجيل بيترفض (ولا كتابة)، ومعاه الزوج ورجليه بيتكتبوا مع بعض والفك ما بيمسحش العمليات. بيانات وهمية بس.
 */
class OperationsWiringTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 20_000, "2026-01-01")
    private val egBank = Wallet("w-eg", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2026-02-01")
    private val food = Category("c-food", null, "مطاعم", "utensils", "#A36A21", "#E0B070", active = true, order = 1, groupKey = "food")
    private val saRepos = memorySpaceRepositories(listOf(bank, cash), listOf(food), profile = emptyProfile().copy(payday = 28))
    private val egRepos = memorySpaceRepositories(listOf(egBank))
    private val env = memoryEnv()
    private val feeds = LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)

    private fun graph(withWriter: Boolean): SpaceGraph {
        val session = object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = listOf(saudi to saRepos, egypt to egRepos)
            override fun switchSpace(spaceId: String) = true
            override fun spaceTransferWriter() = if (!withWriter) null else MemorySpaceTransferWriter(
                saRepos.spaceTransfers as MemorySpaceTransferRepository,
                { id -> (if (id == "sa") saRepos else egRepos).transactions as MemoryTransactionRepository },
            )
        }
        return SpaceGraph(saudi, saRepos, env, session, feeds)
    }

    private val move = NewSpaceTransfer("sa", bank.id, 10_000, "eg", egBank.id, 128_200, "2026-10-04", note = "مصروف البيت")

    @Test fun theLogSeesWhatPlusRecordedAndTheBooksComeFromTheWiring() = runBlocking<Unit> {
        val g = graph(withWriter = false)
        val saved = assertIs<AddOperationResult.Saved>(g.shell.addOperation(AddOperationDraft(AddKind.OUT, "42.50", bank.id, categoryId = food.id, name = food.name)))
        val ops = g.operations
        val screen = ops.transactions.load(LoadTransactionsScreenRequest(today = "2026-10-09", payday = ops.payday()))
        assertEquals(listOf(saved.transaction.id), screen.transactions.map { it.id })
        assertEquals(4_250L, screen.expenseMinor, "المجموع من حالة الاستخدام — مش من الشاشة")
        assertEquals(28, ops.payday())
        assertEquals(food.id, ops.edit.load(saved.transaction.id).transaction.categoryId)
        assertEquals(listOf("بنك وهمي", "الكاش"), ops.wallets().map { it.name })
        val books = ops.spaceBooks()
        assertEquals(listOf("sa", "eg"), books.map { it.space.id })
        assertEquals(listOf(true, false), books.map { it.active })
        assertEquals(listOf("بنك مصري وهمي"), books.last().wallets.map { it.name })
    }

    @Test fun withoutTheWriterRecordingIsRefusedAndNothingIsWritten() = runBlocking<Unit> {
        val ops = graph(withWriter = false).operations
        assertFailsWith<SpaceTransferError> { ops.spaceTransfers.recordNew(move) }
        assertTrue(ops.spaceTransfers.list().isEmpty())
        assertTrue(saRepos.transactions.listByDateRange("2026-01-01", "2026-12-31").isEmpty(), "ولا رجل اتكتبت")
        assertTrue(egRepos.transactions.listByDateRange("2026-01-01", "2026-12-31").isEmpty())
    }

    @Test fun withTheWriterThePairAndItsLegsAreWrittenAndReadBack() = runBlocking<Unit> {
        val ops = graph(withWriter = true).operations
        val pair = ops.spaceTransfers.recordNew(move)
        assertEquals(listOf(pair.id), ops.spaceTransfers.list().map { it.id })
        val out = assertNotNull(ops.legOf("sa", pair.fromTransactionId))
        val arrived = assertNotNull(ops.legOf("eg", pair.toTransactionId))
        assertEquals(bank.id to "2026-10-04", out.walletId to out.occurredAt)
        assertEquals(egBank.id to 128_200L, arrived.walletId to arrived.amountMinor)
        assertNull(ops.legOf("xx", pair.fromTransactionId), "بلد مش مفتوحة ⇒ مفيش رجل")

        ops.spaceTransfers.unlink(pair.id)
        assertTrue(ops.spaceTransfers.list().isEmpty())
        assertNotNull(ops.legOf("sa", pair.fromTransactionId), "الفك ما بيمسحش العمليات")
    }
}
