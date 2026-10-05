package app.masroufy.data

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.BackupRow
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.MatchingState
import app.masroufy.core.Person
import app.masroufy.core.Project
import app.masroufy.core.ProjectLink
import app.masroufy.core.QUANTITY_SCALE
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Tag
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearLine
import app.masroufy.core.checkFullBackupData
import app.masroufy.core.emptyBackupData
import app.masroufy.core.eventLinkId
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.usecase.RevertDecision
import app.masroufy.usecase.RevertDeps
import app.masroufy.usecase.RevertImportBatch
import app.masroufy.usecase.RevertLinkDeps
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * التراجع عن دفعة استيراد ما بيسيبش ربط بيشاور على عملية اتمسحت (غلطة جلسة 6) — والنسخة الشاملة بتعدّي فحص العلاقات بعده.
 * كل الأسامي والمبالغ مخترعة.
 */
class RevertLinksTest {
    private fun txn(id: String, dir: Direction = Direction.OUT) = Transaction(
        id = id, occurredAt = "2026-05-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "w-1",
    )

    /** عمليات الدفعة: كل واحدة متربطة بحاجة مختلفة، و«t-plain» من غير أي ربط. */
    private val ids = listOf("t-project", "t-spend", "t-tag", "t-plain", "t-zakat", "t-installment", "t-gift", "t-lot", "t-rosca")

    private inner class Account {
        val txns = MemoryTransactionRepository(ids.map { txn(it) })
        val sources = MemorySourceRecordRepository(
            ids.mapIndexed { i, t -> SourceRecord("sr-$i", "b-1", "acct", null, "h-$i", i, "سطر وهمي $i", t, MatchingState.NEW, "جديدة") },
        )
        val batches = MemoryImportBatchRepository(
            listOf(ImportBatch("b-1", ImportSourceType.PDF_ALRAJHI, "hash", "كشف وهمي.pdf", "2026-05-02T00:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(9, 9, 0, 0, 0, 0))),
        )
        val project = Project("pr-1", "مشروع وهمي", "مشروع وهمي", false, "c")
        val projectLinks = MemoryProjectLinkRepository(listOf(ProjectLink("pl-1", "pr-1", "t-project", "manual", "c")))
        val event = LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2026-05-01", mine = false, hostPersonId = "p-1", createdAt = "c")
        val eventLinks = MemoryEventLinkRepository(
            listOf(
                EventLink(eventLinkId("t-spend"), "ev-1", "t-spend", EventRole.SPEND, null, "c", sharePercent = 40),
                EventLink(eventLinkId("t-gift"), "ev-1", "t-gift", EventRole.GIFT_OUT, "p-1", "c"),
            ),
        )
        val tag = Tag("tag-1", "وسم وهمي", "وسم وهمي")
        val tags = MemoryTransactionTagRepository(listOf(TransactionTag("tt-1", "t-tag", "tag-1")))
        val year = ZakatYear("2026-06-16", "2025-06-26", "2026-06-16", Currency.SAR, "c", "c", 208_250, listOf(ZakatYearLine(ZakatLineKind.CASH, 4_000_000, 100_000)))
        val zakatPayments = MemoryZakatPaymentRepository(listOf(ZakatPayment("zp-1", year.id, "t-zakat", 10_000, listOf(ZakatLineKind.CASH), "2026-05-01", "c")))
        val plan = InstallmentPlan("ip-1", "تقسيط وهمي", "جهة وهمية", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 100_000, 100_000, 10_000, 1, "2026-05-01")
        val plans = MemoryInstallmentPlanRepository(listOf(plan))
        val installmentPayments = MemoryInstallmentPaymentRepository(listOf(InstallmentPayment("pay-1", "ip-1", "t-installment", 10_000)))
        val asset = Asset("a-1", "سهم وهمي", "stock", "سهم", Currency.SAR, false)
        val lots = MemoryAssetLotRepository(listOf(AssetLot("l-1", "a-1", "2026-05-01", QUANTITY_SCALE, 10_000, 0, "t-lot")))
        val rosca = Rosca("r-1", "جمعية وهمية", Currency.SAR, 10_000, 1, "2026-05-01", 10, listOf(10), 100_000, createdAt = "c")
        val roscaEntries = MemoryRoscaEntryRepository(listOf(RoscaEntry("e-1", "r-1", "t-rosca", RoscaEntryKind.CONTRIBUTION, 10_000)))

        val revert = RevertImportBatch(
            RevertDeps(
                txns, sources, batches, MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches, projectLinks, eventLinks, tags)),
                RevertLinkDeps(projectLinks, eventLinks, tags, roscaEntries, installmentPayments, plans, zakatPayments, lots, MemoryAssetSaleRepository()),
            ),
        )

        /** النسخة الشاملة من المستودعات نفسها — نفس المحوّلات اللي بتكتب لفايربيز. */
        suspend fun backup(): Map<String, List<BackupRow>> = emptyBackupData().also { d ->
            d.getValue("wallets") += ReferenceCodecs.wallets.toStore(Wallet("w-1", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01"))
            d.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
            d.getValue("transactions") += txns.all().map { LedgerCodecs.transactions.toStore(it) }
            d.getValue("sourceRecords") += sources.all().map { LedgerCodecs.sourceRecords.toStore(it) }
            d.getValue("importBatches") += batches.all().map { LedgerCodecs.importBatches.toStore(it) }
            d.getValue("projects") += AssetProjectCodecs.projects.toStore(project)
            d.getValue("projectLinks") += projectLinks.listAll().map { AssetProjectCodecs.projectLinks.toStore(it) }
            d.getValue("lifeEvents") += EventCodecs.lifeEvents.toStore(event)
            d.getValue("eventLinks") += eventLinks.all().map { EventCodecs.eventLinks.toStore(it) }
            d.getValue("tags") += ReferenceCodecs.tags.toStore(tag)
            d.getValue("transactionTags") += tags.listByTag("tag-1").map { LedgerCodecs.transactionTags.toStore(it) }
            d.getValue("zakatYears") += ZakatCodecs.zakatYears.toStore(year)
            d.getValue("zakatPayments") += zakatPayments.listByYear(year.id).map { ZakatCodecs.zakatPayments.toStore(it) }
            d.getValue("installmentPlans") += DuesCodecs.installmentPlans.toStore(plan)
            d.getValue("installmentPayments") += installmentPayments.listByPlan("ip-1").map { DuesCodecs.installmentPayments.toStore(it) }
            d.getValue("assets") += AssetProjectCodecs.assets.toStore(asset)
            d.getValue("assetLots") += lots.listAll().map { AssetProjectCodecs.assetLots.toStore(it) }
            d.getValue("roscas") += DuesCodecs.roscas.toStore(rosca)
            d.getValue("roscaEntries") += roscaEntries.listByRosca("r-1").map { DuesCodecs.roscaEntries.toStore(it) }
        }
    }

    private suspend fun TransactionRepository.ids() = listByDateRange("2000-01-01", "2100-01-01").map { it.id }.toSet()

    @Test
    fun `التراجع بيشيل روابط المشروع والحدث والوسم مع العملية ويسيب اللي عليها فلوس — والنسخة بتعدّي`() = runBlocking<Unit> {
        val acc = Account()
        checkFullBackupData(acc.backup())
        val plan = acc.revert.plan("b-1")
        assertEquals(listOf("t-project", "t-spend", "t-tag", "t-plain"), plan.toDelete, "الربط اللي هو علامة بس ما بيمنعش المسح")
        assertEquals(3, plan.unlinkCount, "ربط مشروع + مصروف على حدث + وسم")
        assertEquals(
            mapOf(
                "t-zakat" to RevertDecision.KEPT_HAS_DUE, "t-installment" to RevertDecision.KEPT_HAS_DUE,
                "t-gift" to RevertDecision.KEPT_HAS_GIFT, "t-lot" to RevertDecision.KEPT_HAS_INVESTMENT, "t-rosca" to RevertDecision.KEPT_HAS_DUE,
            ),
            plan.toKeep.associate { it.transactionId to it.decision },
        )
        acc.revert.execute("b-1")
        assertEquals(setOf("t-zakat", "t-installment", "t-gift", "t-lot", "t-rosca"), acc.txns.ids())
        assertTrue(acc.projectLinks.listAll().isEmpty())
        assertEquals(listOf("t-gift"), acc.eventLinks.all().map { it.transactionId })
        assertTrue(acc.tags.listByTag("tag-1").isEmpty())
        assertEquals(1, acc.zakatPayments.listByYear(acc.year.id).size, "دفعة الزكاة زي ما هي")
        // 🔒 مفيش ولا علاقة مكسورة — النسخة الشاملة بتعدّي الفحص
        checkFullBackupData(acc.backup())
    }

    @Test
    fun `فشل في النص ⇒ ولا ربط ولا عملية اتشالت`() = runBlocking<Unit> {
        val acc = Account()
        val failing = RevertImportBatch(
            RevertDeps(
                acc.txns, acc.sources, object : app.masroufy.port.ImportBatchRepository by acc.batches {
                    override suspend fun updateState(id: String, state: ImportBatchState) = error("انقطاع وهمي")
                },
                MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(),
                MemoryUnitOfWork(listOf(acc.txns, acc.sources, acc.batches, acc.projectLinks, acc.eventLinks, acc.tags)),
                RevertLinkDeps(acc.projectLinks, acc.eventLinks, acc.tags, acc.roscaEntries, acc.installmentPayments, acc.plans, acc.zakatPayments, acc.lots, MemoryAssetSaleRepository()),
            ),
        )
        assertFailsWith<IllegalStateException> { failing.execute("b-1") }
        assertEquals(ids.toSet(), acc.txns.ids())
        assertEquals(1, acc.projectLinks.listAll().size)
        assertEquals(2, acc.eventLinks.all().size)
        assertEquals(1, acc.tags.listByTag("tag-1").size)
    }
}
