package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.ProjectLink
import app.masroufy.core.Settlement
import app.masroufy.core.TransactionTag
import app.masroufy.core.Wallet
import app.masroufy.core.computePeriodTotals
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «امسح» لعملية اتسجلت غلط (HANDOVER §6.5 بند 12) — يدوية ومن رسالة البنك. كل الأسامي والمبالغ مخترعة. */
class DeleteTransactionTest {
    private val wallet = Wallet("w-1", "كاش تجربة", Currency.SAR, "cash", 100_000, "2026-01-01")
    private val friend = Person("p-1", "صديق تجربة")

    private class Links {
        val tags = MemoryTransactionTagRepository()
        val projects = MemoryProjectLinkRepository()
        val events = MemoryEventLinkRepository()
        val deps = RevertLinkDeps(
            projects, events, tags, MemoryRoscaEntryRepository(), MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(),
            MemoryZakatPaymentRepository(), MemoryAssetLotRepository(), MemoryAssetSaleRepository(),
        )
    }

    private fun eraser(txns: TransactionRepository, sources: SourceRecordRepository, links: Links, settlements: MemorySettlementRepository = MemorySettlementRepository(), allocations: MemoryAllocationRepository = MemoryAllocationRepository(), obligations: MemoryObligationRepository = MemoryObligationRepository()) =
        DeleteTransaction(DeleteTransactionDeps(txns, sources, settlements, allocations, obligations, links.deps, PassthroughUnitOfWork(), FixedClock("2026-10-15T20:00:00Z")))

    private inner class Manual {
        val txns = MemoryTransactionRepository()
        val wallets = MemoryWalletRepository(listOf(wallet))
        val sources = MemorySourceRecordRepository()
        val allocations = MemoryAllocationRepository()
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val links = Links()
        val add = AddTransaction(AddTransactionDeps(txns, wallets, SequentialIdGenerator(), FixedClock("2026-10-15T09:00:00Z")))
        val erase = eraser(txns, sources, links, settlements, allocations, obligations)
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(listOf(friend)), obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns,
                PassthroughUnitOfWork(), SequentialIdGenerator(), FixedClock("2026-10-15T09:00:00Z"),
            ),
        )

        suspend fun coffee() = add.add(NewTransactionInput(2_500, occurredAt = "2026-10-14", walletId = wallet.id, economicKind = EconomicKind.PURCHASE, merchantName = "مقهى تجربة"))

        /** رصيد المحفظة والمجاميع — اللي لازم يرجع زي ما كان. */
        suspend fun numbers(): List<Any?> {
            val rows = txns.listByDateRange("2026-01-01", "2026-12-31")
            return listOf(LoadWithYouNow(wallets, txns, Currency.SAR).load("2026-10-15").totalMinor, computePeriodTotals(rows, allocations.listByTransactionIds(rows.map { it.id })))
        }
    }

    @Test fun manualOperationIsDeletedAndBalancesAreAsIfItWasNeverRecorded() = runBlocking<Unit> {
        val m = Manual()
        val never = m.numbers()
        val t = m.coffee()
        assertEquals(97_500L, m.numbers()[0])
        val removal = m.erase.delete(t.id)
        assertFalse(removal.imported)
        assertEquals(never, m.numbers())
        assertTrue(m.txns.findByIds(listOf(t.id)).isEmpty())
    }

    @Test fun undoRestoresTheOperationAndItsLinksExactly() = runBlocking<Unit> {
        val m = Manual()
        val t = m.coffee()
        m.links.tags.saveMany(listOf(TransactionTag("tt-1", t.id, "tag-1")))
        m.links.projects.saveMany(listOf(ProjectLink("pl-1", "pr-1", t.id, "manual", "2026-10-15T09:00:00Z")))
        m.links.events.saveMany(listOf(EventLink("el-1", "ev-1", t.id, EventRole.SPEND, createdAt = "2026-10-15T09:00:00Z")))
        m.allocations.saveMany(listOf(PersonAllocation("al-1", t.id, friend.id, AllocationKind.GIFT, 1_000, Currency.SAR)))
        val before = m.numbers()
        val removal = m.erase.delete(t.id)
        assertEquals(4, removal.unlinkCount, "الوسم والمشروع والحدث والدعم اتشالوا معاها")
        assertTrue(m.links.tags.listByTransactionIds(listOf(t.id)).isEmpty())
        assertTrue(m.links.projects.listAll().isEmpty(), "ولا ربط بيشاور على عملية مش موجودة")
        assertTrue(m.allocations.listByTransactionIds(listOf(t.id)).isEmpty())
        m.erase.restore(removal)
        assertEquals(listOf(t), m.txns.findByIds(listOf(t.id)))
        assertEquals(1, m.links.tags.listByTransactionIds(listOf(t.id)).size)
        assertEquals(1, m.links.projects.listAll().size)
        assertEquals(1, m.links.events.listByTransactionIds(listOf(t.id)).size)
        assertEquals(before, m.numbers())
    }

    @Test fun moneyLinkedSomewhereElseBlocksTheDeleteAndSaysWhereToRemoveItFirst() = runBlocking<Unit> {
        val m = Manual()
        val withDebt = m.coffee()
        m.people.linkToPerson(withDebt.id, friend.id, app.masroufy.core.ObligationKind.RECEIVABLE, 1_000)
        assertEquals(listOf(EraseBlock.DEBT_ORIGIN), m.erase.preview(withDebt.id).blocks)
        assertFailsWith<IllegalStateException> { m.erase.delete(withDebt.id) }
        assertEquals(1, m.txns.findByIds(listOf(withDebt.id)).size, "ولا حاجة اتمسحت")

        val repayment = m.coffee()
        m.settlements.saveMany(listOf(Settlement("s-1", repayment.id, "obl-x", 500)))
        assertEquals(listOf(EraseBlock.SETTLEMENT), m.erase.preview(repayment.id).blocks)

        val gift = m.coffee()
        m.links.events.saveMany(listOf(EventLink("el-g", "ev-1", gift.id, EventRole.GIFT_IN, friend.id, "2026-10-15T09:00:00Z")))
        assertEquals(listOf(EraseBlock.EVENT_GIFT), m.erase.preview(gift.id).blocks)

        val paired = m.coffee()
        m.txns.update(paired.id, app.masroufy.port.TransactionPatch(reversedById = "t-back"))
        assertEquals(listOf(EraseBlock.REVERSAL_PAIR), m.erase.preview(paired.id).blocks)
    }

    @Test fun deletedSmsOperationDoesNotComeBackWhenTheSameMessageIsReadAgain() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        val space = world.spaces.single()
        world.receive(sms("m1", CAFE))
        assertEquals(1, world.auto().run().recorded)
        val recorded = space.all().single()
        val erase = eraser(space.txnStore, space.sources, Links())
        val removal = erase.delete(recorded.id)
        assertTrue(removal.imported)
        val mark = space.sources.all().single()
        assertNull(mark.transactionId, "العلامة من غير عملية (النسخة الشاملة بترفض رابط مقطوع)")
        assertEquals("2026-10-15T20:00:00Z", mark.ownerDeletedAt)

        world.receive(sms("m1", CAFE)) // نفس الرسالة رجعت («اقرأ اللي فات؟» أو المزامنة)
        val again = world.auto().run()
        assertEquals(0, again.recorded, "ما رجعتش")
        assertEquals(1, again.duplicates)
        assertTrue(space.all().isEmpty())
        assertTrue(world.queued().isEmpty(), "اتشالت من الصندوق من غير ما تستنى")

        world.receive(sms("m2", CAFE_TWIN)) // رسالة تانية بنفس المبلغ واليوم ⇒ بتتسجل عادي
        assertEquals(1, world.auto().run().recorded)
    }

    @Test fun undoOfAnImportedDeleteBringsTheSourceBackSoReReadingIsStillADuplicate() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        val space = world.spaces.single()
        world.receive(sms("m1", CAFE))
        world.auto().run()
        val recorded = space.all().single()
        val sourcesBefore = space.sources.all()
        val erase = eraser(space.txnStore, space.sources, Links())
        erase.restore(erase.delete(recorded.id))
        assertEquals(listOf(recorded), space.all())
        assertEquals(sourcesBefore, space.sources.all(), "سجل المصدر رجع زي ما كان")
        world.receive(sms("m1", CAFE))
        assertEquals(0, world.auto().run().recorded)
        assertEquals(1, space.all().size)
    }
}
