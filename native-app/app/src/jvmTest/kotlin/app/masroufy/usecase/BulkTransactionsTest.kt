package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.MatchingState
import app.masroufy.core.Project
import app.masroufy.core.ProjectLink
import app.masroufy.core.ReviewState
import app.masroufy.core.SelectionTotal
import app.masroufy.core.SourceRecord
import app.masroufy.core.Tag
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.core.projectLinkId
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** تحديد كذا عملية + الأفعال الجماعية والتراجع (قايمة المالك §6.5 بند 15). كل الأسامي والمبالغ مخترعة. */
class BulkTransactionsTest {
    private fun txn(id: String, amount: Long, dir: Direction = Direction.OUT, currency: Currency = Currency.SAR, kind: EconomicKind = EconomicKind.UNCLASSIFIED) =
        Transaction(
            id = id, occurredAt = "2026-10-05", datePrecision = "day", sourceOrder = 1, economicKind = kind,
            economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawMerchantName = "TEST SHOP $id", suggestedKind = EconomicKind.PURCHASE.takeIf { dir == Direction.OUT },
        )

    private val seed = listOf(
        txn("t-1", 12_000), txn("t-2", 3_550), txn("t-in", 6_000, Direction.IN), txn("t-egp", 25_000, currency = Currency.EGP),
        txn("t-imported", 1_000), txn("t-debt", 2_000),
    )
    private val txns = MemoryTransactionRepository(seed)
    private val categories = MemoryCategoryRepository(listOf(Category("cat-food", null, "تصنيف تجربة", "tag", "#000000", "#ffffff", true, 1)))
    private val projects = MemoryProjectRepository(listOf(Project("p-1", "مشروع تجربة", "مشروع تجربة", false, "x")))
    private val links = MemoryProjectLinkRepository(listOf(ProjectLink(projectLinkId("p-1", "t-2"), "p-1", "t-2", "excluded", "old")))
    private val tags = MemoryTagRepository(listOf(Tag(id = "tag-old", normalizedName = "قديم", displayName = "قديم")))
    private val tagLinks = MemoryTransactionTagRepository(listOf(TransactionTag(id = "tt-1", transactionId = "t-1", tagId = "tag-old")))
    private val sources = MemorySourceRecordRepository(
        listOf(SourceRecord("s-1", "b-1", "acc", null, "h", 0, "line", "t-imported", MatchingState.NEW, "r")),
    )
    private val bulk = BulkTransactions(
        BulkTransactionsDeps(
            txns, categories, projects, links, tags, tagLinks, sources, MemoryUnitOfWork(listOf(txns, links, tagLinks, sources)),
            SequentialIdGenerator(), FixedClock("2026-10-15T10:00:00.000Z"),
            linked = { ids -> ids.filter { it == "t-debt" }.toSet() },
        ),
    )
    private val all = seed.map { it.id }

    private suspend fun snapshot() = Triple(txns.findByIds(all).sortedBy { it.id }, links.listAll().sortedBy { it.id }, tagLinks.listByTransactionIds(all).sortedBy { it.id })

    @Test fun summaryCountsAndTotalsPerCurrency() = runBlocking {
        val s = bulk.summary(listOf("t-1", "t-2", "t-in", "t-egp", "t-1"))
        assertEquals(4, s.count)
        assertEquals(
            listOf(SelectionTotal(Currency.SAR, 3, 15_550, 6_000), SelectionTotal(Currency.EGP, 1, 25_000, 0)),
            s.totals,
        )
        assertEquals(4, s.deletable)
        assertEquals(0, bulk.summary(emptyList()).count)
        assertTrue(bulk.summary(emptyList()).totals.isEmpty())
    }

    @Test fun categoryForAllThenUndoRestoresExactly() = runBlocking {
        val before = snapshot()
        val r = bulk.setCategory(listOf("t-1", "t-2", "missing"), "cat-food")
        assertEquals(2, r.changed)
        assertEquals(1, r.skipped)
        txns.findByIds(listOf("t-1", "t-2")).forEach {
            assertEquals("cat-food", it.categoryId)
            assertTrue(it.categoryConfirmed)
            assertEquals(ReviewState.CONFIRMED, it.reviewState)
        }
        bulk.undo(r.undo!!)
        assertEquals(before, snapshot())
        assertFailsWith<IllegalStateException> { bulk.setCategory(listOf("t-1"), "cat-none") }
        Unit
    }

    /** مراجعة r04: تحويل بين محافظك ونص زوج «رجعت» ما بيتغيّرش نوعهم جماعي — كانوا بيتعدّوا مصروف والفلوس لسه في المحفظة التانية. */
    @Test fun kindSkipsOwnWalletTransfersAndReversalHalves() = runBlocking {
        txns.saveMany(listOf(txn("t-own", 5_000).copy(transferToWalletId = "w-2"), txn("t-rev", 700).copy(reversedById = "t-x")))
        val r = bulk.setKind(listOf("t-own", "t-rev", "t-2"), EconomicKind.PURCHASE)
        assertEquals(1, r.changed)
        assertEquals(2, r.skipped)
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf("t-own")).single().economicKind)
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf("t-rev")).single().economicKind)
    }

    @Test fun kindSkipsWrongDirectionAndUndoes() = runBlocking {
        val before = snapshot()
        val r = bulk.setKind(listOf("t-1", "t-in", "t-egp"), EconomicKind.PURCHASE)
        assertEquals(2, r.changed)
        assertEquals(1, r.skipped)
        val t1 = txns.findByIds(listOf("t-1")).single()
        assertEquals(EconomicKind.PURCHASE, t1.economicKind)
        assertTrue(t1.economicKindConfirmed)
        assertNull(t1.suggestedKind)
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf("t-in")).single().economicKind)
        bulk.undo(r.undo!!)
        assertEquals(before, snapshot())
        assertFailsWith<IllegalArgumentException> { bulk.setKind(listOf("t-1"), EconomicKind.LOAN_GRANTED) }
        Unit
    }

    @Test fun projectAddsManualLinksBringsBackExcludedAndUndoes() = runBlocking {
        val before = snapshot()
        val r = bulk.addToProject(listOf("t-1", "t-2"), "p-1")
        assertEquals(2, r.changed)
        val now = links.listAll().filter { it.projectId == "p-1" }.associateBy { it.transactionId }
        assertEquals("manual", now.getValue("t-1").source)
        assertEquals("manual", now.getValue("t-2").source)
        val again = bulk.addToProject(listOf("t-1", "t-2"), "p-1")
        assertEquals(0, again.changed)
        assertNull(again.undo)
        bulk.undo(r.undo!!)
        assertEquals(before, snapshot())
        assertFailsWith<IllegalArgumentException> { bulk.addToProject(listOf("t-1"), "p-none") }
        Unit
    }

    @Test fun tagCreatesOnceSkipsDuplicatesAndUndoRemovesNewTag() = runBlocking {
        val before = snapshot()
        val r = bulk.addTag(listOf("t-1", "t-2"), "  سفر  ")
        assertEquals(2, r.changed)
        val created = r.undo!!.createdTagId!!
        assertEquals("سفر", tags.listAll().single { it.id == created }.displayName)
        val old = bulk.addTag(listOf("t-1", "t-2"), "قديم")
        assertEquals(1, old.changed)
        assertNull(old.undo!!.createdTagId)
        bulk.undo(old.undo!!)
        bulk.undo(r.undo!!)
        assertEquals(before, snapshot())
        assertTrue(tags.listAll().none { it.id == created })
        assertFailsWith<IllegalStateException> { bulk.addTag(listOf("t-1"), "   ") }
        Unit
    }

    @Test fun deleteOnlyManualUnlinkedAndUndoBringsAllBack() = runBlocking {
        val before = snapshot()
        val r = bulk.delete(listOf("t-1", "t-imported", "t-debt"))
        assertEquals(1, r.changed)
        assertEquals(2, r.skipped)
        assertTrue(txns.findByIds(listOf("t-1")).isEmpty())
        assertTrue(tagLinks.listByTransactionIds(listOf("t-1")).isEmpty())
        assertEquals(2, txns.findByIds(listOf("t-imported", "t-debt")).size)
        bulk.undo(r.undo!!)
        assertEquals(before, snapshot())
    }

    @Test fun deleteRefusesEverythingWhenLinksAreUnknown() = runBlocking {
        val blind = BulkTransactions(
            BulkTransactionsDeps(txns, categories, projects, links, tags, tagLinks, sources, MemoryUnitOfWork(listOf(txns)), SequentialIdGenerator(), FixedClock("x")),
        )
        val r = blind.delete(listOf("t-1", "t-2"))
        assertEquals(0, r.changed)
        assertNull(r.undo)
        assertEquals(0, blind.summary(listOf("t-1")).deletable)
        assertEquals(seed.size, txns.findByIds(all).size)
    }

    /** «امسح» في وضع التحديد بيعدّي من نفس طريق مسح العملية الواحدة (`DeleteTransaction` — §6.5 بند 12) و«تراجع» بـ`restore`. */
    @Test fun deleteGoesThroughTheSingleDeletePathWhenGivenAndUndoRestoresExactly() = runBlocking {
        val uow = MemoryUnitOfWork(listOf(txns, links, tagLinks, sources))
        val erase = DeleteTransaction(
            DeleteTransactionDeps(
                txns, sources, app.masroufy.memory.MemorySettlementRepository(), app.masroufy.memory.MemoryAllocationRepository(),
                app.masroufy.memory.MemoryObligationRepository(),
                RevertLinkDeps(
                    links, app.masroufy.memory.MemoryEventLinkRepository(), tagLinks, app.masroufy.memory.MemoryRoscaEntryRepository(),
                    app.masroufy.memory.MemoryInstallmentPaymentRepository(), app.masroufy.memory.MemoryInstallmentPlanRepository(),
                    app.masroufy.memory.MemoryZakatPaymentRepository(), app.masroufy.memory.MemoryAssetLotRepository(),
                    app.masroufy.memory.MemoryAssetSaleRepository(),
                ),
                uow, FixedClock("2026-10-15T10:00:00.000Z"),
            ),
        )
        val through = BulkTransactions(
            BulkTransactionsDeps(
                txns, categories, projects, links, tags, tagLinks, sources, uow, SequentialIdGenerator(), FixedClock("2026-10-15T10:00:00.000Z"),
                linked = { ids -> ids.filter { it == "t-debt" }.toSet() }, erase = erase,
            ),
        )
        val before = snapshot()
        val r = through.delete(listOf("t-1", "t-2", "t-imported", "t-debt"))
        assertEquals(2, r.changed)
        assertEquals(2, r.skipped)
        assertEquals(2, r.undo!!.removals.size)
        assertTrue(txns.findByIds(listOf("t-1", "t-2")).isEmpty())
        assertTrue(tagLinks.listByTransactionIds(listOf("t-1")).isEmpty())
        assertTrue(links.listByTransaction("t-2").isEmpty())
        assertEquals(2, txns.findByIds(listOf("t-imported", "t-debt")).size)
        through.undo(r.undo!!)
        assertEquals(before, snapshot())
    }
}

