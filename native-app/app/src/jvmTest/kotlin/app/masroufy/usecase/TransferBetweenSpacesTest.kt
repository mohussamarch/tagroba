package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransferError
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.defaultSpace
import app.masroufy.core.periodForDate
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemorySpaceTransferRepository
import app.masroufy.memory.MemorySpaceTransferWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** التحويل لنفسك بين السعودية ومصر (§64) على مستودعات الذاكرة — مبالغ وأسماء مخترعة. */
class TransferBetweenSpacesTest {
    private class Country(val space: Space, currency: Currency, seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val wallets = MemoryWalletRepository(listOf(Wallet("wallet-bank", "بنك وهمي", currency, "bank", 1_000_000, "2026-01-01"), Wallet("wallet-cash", "كاش", currency, "cash", 0, "2026-01-01")))
        val roscaEntries = MemoryRoscaEntryRepository()
        val eventLinks = MemoryEventLinkRepository()
        val links = TransactionLinkRepos(
            roscaEntries, MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), eventLinks,
            MemoryProjectLinkRepository(), MemoryAllocationRepository(), MemoryObligationRepository(), MemorySettlementRepository(),
        )
        val book = SpaceBook(space, txns, wallets, links)

        /** الرئيسية (مصروف · دخل · مستبعد) + الميزانية (المصروف) + التحليل (توزيع التصنيفات). */
        suspend fun home(): List<Any?> {
            val period = periodForDate("2026-09-10", 28)
            val h = LoadHomeScreen(LoadHomeScreenDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository(), MemoryBudgetRepository()))
                .load(LoadHomeScreenRequest(period, "2026-09-20", 28, includeHistory = false))
            val budget = LoadBudgetScreen(LoadBudgetScreenDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository(), MemoryBudgetRepository()))
                .load(LoadBudgetScreenRequest(period, "2026-09-20", 28))
            val distribution = app.masroufy.core.categoryDistribution(txns.listByDateRange(period.start, period.end))
            return listOf(h.expenseMinor, h.incomeMinor, h.excludedExpenseMinor, budget.spentMinor, distribution.totalMinor)
        }

        suspend fun money() = LoadMoneySummary(LoadMoneySummaryDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository())).load("2026-01-01", "2026-12-31")
            .let { Triple(it.expenseMinor, it.incomeMinor, it.cash) }

        suspend fun balance() = ReconcileBalance(ReconcileDeps(txns, wallets)).run("wallet-bank", "2026-09-30", 28).result
    }

    private fun txn(id: String, dir: Direction, minor: Long, currency: Currency, kind: EconomicKind = EconomicKind.UNCLASSIFIED) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = dir, amountMinor = minor, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = if (kind == EconomicKind.UNCLASSIFIED) ReviewState.NEEDS_REVIEW else ReviewState.CONFIRMED, isCashTagged = false,
        createdAt = "2026-09-10T00:00:00.000Z", updatedAt = "2026-09-10T00:00:00.000Z", walletId = "wallet-bank", rawMerchantName = "متجر وهمي $id",
    )

    private val everyday = listOf(txn("t-salary", Direction.IN, 900_000, Currency.SAR, EconomicKind.SALARY), txn("t-food", Direction.OUT, 30_000, Currency.SAR, EconomicKind.PURCHASE))
    private val sa = Country(defaultSpace(), Currency.SAR, everyday + txn("t-out", Direction.OUT, 200_000, Currency.SAR))
    private val egSpace = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
    // نفس معرّف «t-out» موجود في مصر كمان — كل بلد عدّادها
    private val eg = Country(egSpace, Currency.EGP, listOf(txn("t-rent", Direction.OUT, 500_000, Currency.EGP, EconomicKind.PURCHASE), txn("t-out", Direction.IN, 2_469_120, Currency.EGP)))
    private val pairs = MemorySpaceTransferRepository()
    private val writer = MemorySpaceTransferWriter(pairs, { id -> if (id == DEFAULT_SPACE_ID) sa.txns else eg.txns })
    private val clock = FixedClock("2026-10-04T10:00:00.000Z")
    private val transfers = TransferBetweenSpaces(
        TransferBetweenSpacesDeps(pairs, writer, { id -> mapOf(DEFAULT_SPACE_ID to sa.book, "eg" to eg.book)[id] }, SequentialIdGenerator(), clock),
    )

    @Test fun linkingIsNeitherExpenseNorIncomeAndUnlinkingBringsTheQuestionBack() = runBlocking<Unit> {
        val withoutLegs = Country(defaultSpace(), Currency.SAR, everyday).home()
        val withoutLegsEg = Country(egSpace, Currency.EGP, listOf(txn("t-rent", Direction.OUT, 500_000, Currency.EGP, EconomicKind.PURCHASE))).home()
        val beforeSa = sa.home()
        val beforeEg = eg.home()
        val balanceSa = sa.balance()
        val pair = transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out", note = "  تحويل لأهلي  ")
        assertEquals("stx-default-t-out", pair.id)
        assertEquals(200_000L to Currency.SAR, pair.fromAmountMinor to pair.fromCurrency)
        assertEquals(2_469_120L to Currency.EGP, pair.toAmountMinor to pair.toCurrency, "كل رجل بمبلغها وعملتها زي الكشف")
        assertEquals("تحويل لأهلي", pair.note)
        // لا مصروف ولا دخل في أي بلد — زي ما الرجلين مش موجودين
        assertEquals(withoutLegs, sa.home())
        assertEquals(withoutLegsEg, eg.home())
        assertEquals(balanceSa, sa.balance(), "الرصيد هو هو — الفلوس خرجت فعلًا، الربط بيغيّر النوع بس")
        assertEquals(EconomicKind.INTERNAL_TRANSFER, sa.txns.findByIds(listOf("t-out")).single().economicKind)
        assertEquals(EconomicKind.INTERNAL_TRANSFER, eg.txns.findByIds(listOf("t-out")).single().economicKind)

        transfers.unlink(pair.id)
        assertTrue(pairs.listAll().isEmpty())
        assertEquals(beforeSa, sa.home(), "بعد الفك: زي ما كانت قبل الربط")
        assertEquals(beforeEg, eg.home())
        assertEquals(EconomicKind.UNCLASSIFIED, sa.txns.findByIds(listOf("t-out")).single().economicKind, "الرجل اترجعت تتسأل — ما اتمسحتش")
        assertEquals(EconomicKind.UNCLASSIFIED, eg.txns.findByIds(listOf("t-out")).single().economicKind)
    }

    @Test fun aNewTransferWritesTwoLegsThatAreNeitherExpenseNorIncome() = runBlocking<Unit> {
        val saMoney = sa.money()
        val egMoney = eg.money()
        val pair = transfers.recordNew(NewSpaceTransfer(DEFAULT_SPACE_ID, "wallet-cash", 50_000, "eg", "wallet-cash", 617_280, "2026-09-12"))
        val out = sa.txns.findByIds(listOf(pair.fromTransactionId)).single()
        val inn = eg.txns.findByIds(listOf(pair.toTransactionId)).single()
        assertEquals(Direction.OUT to Currency.SAR, out.observedDirection to out.currency)
        assertEquals(Direction.IN to Currency.EGP, inn.observedDirection to inn.currency)
        assertTrue(out.isCashTagged && inn.isCashTagged, "محفظة كاش ⇒ وسم كاش")
        assertEquals(saMoney, sa.money(), "حركة الفلوس والدخل والمصروف في السعودية هي هي")
        assertEquals(egMoney, eg.money())
        assertFailsWith<SpaceTransferError>("محفظة مش في مصر") { transfers.recordNew(NewSpaceTransfer(DEFAULT_SPACE_ID, "wallet-cash", 1, "eg", "wallet-x", 1, "2026-09-12")) }
        assertFailsWith<SpaceTransferError>("مبلغ صفر") { transfers.recordNew(NewSpaceTransfer(DEFAULT_SPACE_ID, "wallet-cash", 0, "eg", "wallet-cash", 1, "2026-09-12")) }
        assertFailsWith<SpaceTransferError>("بلد مش موجودة") { transfers.recordNew(NewSpaceTransfer(DEFAULT_SPACE_ID, "wallet-cash", 1, "ae", "wallet-cash", 1, "2026-09-12")) }
        assertEquals(1, pairs.listAll().size)
    }

    @Test fun rulesAndAtomicity() = runBlocking<Unit> {
        assertFailsWith<SpaceTransferError>("نفس البلد") { transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", DEFAULT_SPACE_ID, "t-salary") }
        assertFailsWith<SpaceTransferError>("الطالعة لازم صادرة") { transfers.linkExisting(DEFAULT_SPACE_ID, "t-salary", "eg", "t-out") }
        // رجل متربطة بجمعية ⇒ مرفوضة
        eg.roscaEntries.saveMany(listOf(RoscaEntry("e-1", "rc-1", "t-out", RoscaEntryKind.PAYOUT, 1)))
        assertFailsWith<SpaceTransferError> { transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out") }
        eg.roscaEntries.deleteMany(listOf("e-1"))
        // انقطاع بعد الرجل الأولى ⇒ ولا زوج ولا نص رجل
        writer.failAfterFirstLeg = true
        assertFailsWith<IllegalStateException> { transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out") }
        assertTrue(pairs.listAll().isEmpty())
        assertEquals(EconomicKind.UNCLASSIFIED, sa.txns.findByIds(listOf("t-out")).single().economicKind, "الرجل الأولى اترجعت")
        writer.failAfterFirstLeg = false
        val pair = transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out")
        assertFailsWith<SpaceTransferError>("العملية في زوجين") { transfers.recordNew(NewSpaceTransfer(DEFAULT_SPACE_ID, "wallet-bank", 1, "eg", "wallet-bank", 1, "2026-09-12")).also { transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", it.toTransactionId) } }
        assertEquals(pair.id, transfers.legsIn("eg").pairsOf(listOf("t-out")).single().id)
        assertTrue(transfers.legsIn("eg").pairsOf(listOf("t-rent")).isEmpty())
    }

    @Test fun aLegCannotBeLinkedToAnythingElseOrChangeKind() = runBlocking<Unit> {
        transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out")
        val legs = transfers.legsIn("eg")
        val dueLinks = DueLinks(eg.txns, eg.roscaEntries, MemoryInstallmentPaymentRepository(), MemoryInstallmentPlanRepository(), MemoryCategoryRepository(), clock, spaceLegs = legs)
        val due = assertFailsWith<DueLinkError>("قسط أو جمعية أو زكاة") { dueLinks.check("t-out", Direction.IN, Currency.EGP, "جمعية وهمية", null) }
        assertEquals(app.masroufy.core.uiText(app.masroufy.core.TextKey.SPACE_TRANSFER_LEG_LOCKED), due.message)
        val kinds = SetEconomicKind(SetEconomicKindDeps(eg.txns, MemoryCategoryRepository(), PassthroughUnitOfWork(), clock, legs))
        val kind = assertFailsWith<IllegalStateException>("تغيير نوع الرجل ⇒ كانت هتبقى دخل") { kinds.setOne("t-out", EconomicKind.SALARY) }
        assertEquals(app.masroufy.core.uiText(app.masroufy.core.TextKey.SPACE_TRANSFER_LEG_LOCKED), kind.message)
        kinds.setOne("t-rent", EconomicKind.PURCHASE)
        val event = LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2026-09-01", true, null, false, "c")
        val gifts = EventGifts(
            EventGiftsDeps(MemoryLifeEventRepository(listOf(event)), sa.eventLinks, sa.txns, sa.wallets, MemoryPersonRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), clock, MemoryCategoryRepository(), spaceLegs = transfers.legsIn(DEFAULT_SPACE_ID)),
        )
        // الرجل السعودية صادرة ⇒ كانت تنفع «مصروف على الحدث» لولا إنها تحويل لنفسك
        val refused = assertFailsWith<IllegalArgumentException>("مصروف في حدث") { gifts.link("ev-1", "t-out", app.masroufy.core.EventRole.SPEND) }
        assertEquals(app.masroufy.core.uiText(app.masroufy.core.TextKey.SPACE_TRANSFER_LEG_LOCKED), refused.message)
        gifts.link("ev-1", "t-food", app.masroufy.core.EventRole.SPEND)

        // مبلغ الرجل متسجل في الزوج ⇒ ما يتعدلش غير بعد الفك
        val edit = EditTransaction(
            EditTransactionDeps(sa.txns, MemoryCategoryRepository(), app.masroufy.memory.MemoryTagRepository(), MemoryTransactionTagRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), clock, spaceLegs = transfers.legsIn(DEFAULT_SPACE_ID)),
        )
        assertFailsWith<IllegalStateException>("مبلغ الرجل") { edit.setAmount("t-out", 1) }
        edit.setAmount("t-food", 29_000)

        // شخص (تخصيص/دين/تسوية) ومشروع (بإيدك أو بقاعدة)
        val saLegs = transfers.legsIn(DEFAULT_SPACE_ID)
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(listOf(app.masroufy.core.Person("p-1", "شخص وهمي"))), obligations, settlements, app.masroufy.memory.MemorySettlementWriter(obligations, settlements),
                MemoryAllocationRepository(), sa.txns, PassthroughUnitOfWork(), SequentialIdGenerator(), clock, saLegs,
            ),
        )
        assertFailsWith<IllegalStateException>("رجل تتخصص لشخص") { people.linkToPerson("t-out", "p-1", app.masroufy.core.ObligationKind.RECEIVABLE, 1_000) }
        people.linkToPerson("t-food", "p-1", app.masroufy.core.ObligationKind.RECEIVABLE, 1_000)
        assertFailsWith<IllegalStateException>("رجل تبقى عملية تسوية") { people.settle(obligations.listByPerson("p-1").single().id, "p-1", 500, "t-out") }
        val projectLinks = MemoryProjectLinkRepository()
        val rules = app.masroufy.memory.MemoryProjectRuleRepository()
        val projects = ManageProjects(
            ProjectsDeps(
                app.masroufy.memory.MemoryProjectRepository(listOf(app.masroufy.core.Project("pr-1", "مشروع وهمي", "مشروع وهمي", false, "c"))), projectLinks, rules,
                sa.txns, MemoryAllocationRepository(), MemoryCategoryRepository(), SequentialIdGenerator(), clock, app.masroufy.memory.MemorySyncCursor(), saLegs,
            ),
        )
        assertFailsWith<app.masroufy.core.ProjectError>("رجل في مشروع بإيدك") { projects.setMember("t-out", "pr-1", true) }
        projects.setMember("t-food", "pr-1", true)
        // قاعدة بتطابق كل حاجة ⇒ الرجل ما تدخلش
        projects.addRule("pr-1", "متجر وهمي", app.masroufy.core.RuleMatchMode.CONTAINS, "out")
        projects.applyRuleToOld(rules.listAll().single().id)
        assertTrue(projectLinks.listAll().none { it.transactionId == "t-out" }, "القاعدة ما دخّلتش الرجل")
        assertTrue(projectLinks.listAll().any { it.transactionId == "t-food" && it.source == "rule" } || projectLinks.listAll().any { it.transactionId == "t-food" }, "والقاعدة شغالة على الباقي")
    }

    @Test fun revertingAnImportedLegUnlinksThePairAndTheOtherLegAsksAgain() = runBlocking<Unit> {
        val batch = ImportBatch("b-1", ImportSourceType.CSV_PREVIEW, "hash", "كشف وهمي.csv", "2026-09-11T00:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(1, 1, 0, 0, 0, 0))
        val sources = MemorySourceRecordRepository(listOf(SourceRecord("sr-1", "b-1", "acct", null, "h", 1, "سطر وهمي", "t-out", MatchingState.NEW, "جديد")))
        transfers.linkExisting(DEFAULT_SPACE_ID, "t-out", "eg", "t-out")
        val revert = RevertImportBatch(
            RevertDeps(
                sa.txns, sources, MemoryImportBatchRepository(listOf(batch)), MemorySettlementRepository(), MemoryAllocationRepository(), MemoryObligationRepository(),
                MemoryUnitOfWork(listOf(sa.txns, sources)),
                RevertLinkDeps(
                    MemoryProjectLinkRepository(), MemoryEventLinkRepository(), MemoryTransactionTagRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPaymentRepository(),
                    MemoryInstallmentPlanRepository(), MemoryZakatPaymentRepository(), app.masroufy.memory.MemoryAssetLotRepository(), app.masroufy.memory.MemoryAssetSaleRepository(),
                    spaceLegs = transfers.legsIn(DEFAULT_SPACE_ID),
                ),
            ),
        )
        assertEquals(1, revert.plan("b-1").unlinkCount, "الزوج هيتفك مع العملية")
        assertEquals(listOf("t-out"), revert.execute("b-1").toDelete)
        assertTrue(sa.txns.findByIds(listOf("t-out")).isEmpty())
        assertTrue(pairs.listAll().isEmpty(), "ولا زوج بيشاور على عملية اتمسحت")
        assertEquals(EconomicKind.UNCLASSIFIED, eg.txns.findByIds(listOf("t-out")).single().economicKind, "رجل مصر فضلت وبتتسأل تاني")
    }
}
