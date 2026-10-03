package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPrice
import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.Period
import app.masroufy.core.Project
import app.masroufy.core.ProjectKind
import app.masroufy.core.ProjectLink
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.Tag
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.memory.FixedClock
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadDuesDeps
import app.masroufy.usecase.ManageRoscas
import app.masroufy.usecase.ManageRoscasDeps
import app.masroufy.usecase.RoscaInput
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** باقي المستودعات على محاكي أندرويد قدام Firestore Emulator + الجمعية بحالات الاستخدام نفسها على فايربيز. بيانات وهمية. */
@RunWith(AndroidJUnit4::class)
class RemainingRepositoriesTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun budgetRemovesItsLinesFirst() = run {
        val repo = FirestoreBudgetRepository(space())
        repo.save(Budget("2026-09", "2026-09", "2026-08-28", "2026-09-27", 800_000, 80, "x", "x"))
        repo.saveCategoryBudget(CategoryBudget("cb-1", "2026-09", "c-1", 150_000, true, null))
        assertEquals(listOf("cb-1"), repo.listCategoryBudgets("2026-09").map { it.id })
        assertEquals(800_000, repo.findByPeriod("2026-09")?.totalLimitMinor)
        repo.remove("2026-09")
        assertNull(repo.findByPeriod("2026-09"))
        assertEquals(emptyList(), repo.listCategoryBudgets("2026-09"))
    }

    @Test fun tagsReceiptsAssetsAndProjects() = run {
        val s = space()
        FirestoreTagRepository(s).save(Tag("tag-1", "سفر", "سفر"))
        FirestoreTransactionTagRepository(s).saveMany(listOf(TransactionTag("tt-1", "t-1", "tag-1")))
        assertEquals(listOf("tt-1"), FirestoreTransactionTagRepository(s).listByTag("tag-1").map { it.id })
        FirestoreTagRepository(s).remove("tag-1")
        assertEquals(emptyList(), FirestoreTagRepository(s).listAll())

        // المفتاح فيه `|` و`.` — معرّف المستند متشفّر، والمسح بالمفتاح نفسه
        val receipts = FirestoreNotificationReceiptRepository(s)
        receipts.saveMany(listOf(NotificationReceipt("budget|2026.09|total|80", 80, "2026-08-28", "x")))
        assertEquals("budget|2026.09|total|80", receipts.listAll().single().eventKey)
        receipts.deleteMany(listOf("budget|2026.09|total|80"))
        assertEquals(emptyList(), receipts.listAll())

        FirestoreAssetRepository(s).save(Asset("a-1", "ذهب وهمي", "gold", "جرام", Currency.SAR, false))
        FirestoreAssetLotRepository(s).saveMany(listOf(AssetLot("l-1", "a-1", "2026-01-10", 1_250_000_000, 300_000, 1_500)))
        FirestoreAssetPriceRepository(s).save(AssetPrice("a-1", 28_550, "2026-09-29", "feed"))
        assertEquals(1_250_000_000, FirestoreAssetLotRepository(s).listByAsset("a-1").single().quantity)
        assertEquals(28_550, FirestoreAssetPriceRepository(s).listAll().single().pricePerUnitMinor)

        FirestoreProjectRepository(s).save(Project("pr-1", "شغل وهمي", "شغل وهمي", false, "x", ProjectKind.WORK))
        assertEquals(ProjectKind.WORK, FirestoreProjectRepository(s).listAll().single().kind)
        FirestoreProjectLinkRepository(s).saveMany(listOf(ProjectLink("pl-1", "pr-1", "t-1", "manual", "x")))
        assertEquals(listOf("pl-1"), FirestoreProjectLinkRepository(s).listByTransaction("t-1").map { it.id })
    }

    @Test fun roscaFlowRunsOnFirestore() = run {
        val s = space()
        val txns = FirestoreTransactionRepository(s)
        txns.saveMany(listOf(Transaction(
            id = "t-c1", occurredAt = "2026-01-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
            economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = 100_000, currency = Currency.SAR, categoryConfirmed = false,
            excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        )))
        val entries = FirestoreRoscaEntryRepository(s)
        val roscas = ManageRoscas(
            ManageRoscasDeps(FirestoreRoscaRepository(s), entries, FirestoreInstallmentPaymentRepository(s), txns, PassthroughUnitOfWork(), SequentialIdGenerator(), FixedClock("x"), FirestoreCategoryRepository(s), FirestoreInstallmentPlanRepository(s)),
        )
        val rosca = roscas.save(RoscaInput(name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 100_000, firstDueAt = "2026-01-01", cycleCount = 10, myTurns = listOf(4)))
        roscas.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION)
        assertEquals(EconomicKind.ROSCA_CONTRIBUTION, txns.findByIds(listOf("t-c1")).single().economicKind)
        // فرع «المستحقات ← جمعيات» اتعمل على فايربيز واتحط على العملية (§56)
        assertEquals(app.masroufy.core.DuesCategories.ROSCAS, txns.findByIds(listOf("t-c1")).single().categoryId)
        assertEquals(4, FirestoreCategoryRepository(s).listAll().count { it.id.startsWith("cat-dues") })
        assertEquals(100_000, roscas.list("2026-01-15").single().status.positionMinor)

        val dues = LoadDues(
            LoadDuesDeps(
                FirestoreRoscaRepository(s), entries, FirestoreInstallmentPlanRepository(s), FirestoreInstallmentPaymentRepository(s), FirestoreDebtTermsRepository(s),
                FirestorePersonRepository(s), FirestoreObligationRepository(s), FirestoreSettlementRepository(s), FirestoreRecurringRepository(s), txns,
            ),
        ).load("2026-02-15", Period("2026-01", "2026-01-28", "2026-02-27", 31), Currency.SAR, 500_000)
        // قسط فبراير (متأخر) عليك لسه، وقسط يناير اتربط فمش ظاهر
        assertEquals(100_000, dues.month.toPayMinor)
        assertEquals(400_000, dues.month.remainingAfterMinor)
    }
}
