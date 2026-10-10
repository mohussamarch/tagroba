package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Direction
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Language
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.usecase.RevertDecision
import app.masroufy.usecase.RevertLineOutcome
import app.masroufy.usecase.RevertPlan
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «دفعات الاستيراد» (`ImportBatches`) + «إرجاع الدفعة» (`RevertBatchSheet`): من `RevertImportBatch.history` · `ResumeStagedBatch.findStaged`
 * · `RevertImportBatch.plan` لحالة الشاشة. العدّ عدّ عمليات، مش فلوس.
 */
class ImportBatchesModelTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun stagedBatchesStandAloneAndTheRestIsNewestFirst() {
        val history = listOf(
            Fx.batch("old", "2026-09-10T10:00:00.000Z"),
            Fx.batch("staged", "2026-10-05T10:00:00.000Z", ImportBatchState.STAGED),
            Fx.batch("new", "2026-10-09T09:00:00.000Z", type = ImportSourceType.SMS),
            Fx.batch("rev", "2026-09-20T10:00:00.000Z", ImportBatchState.REVERTED),
        )
        val ui = importBatchesUi(history, staged = listOf(history[1]))
        assertEquals(listOf("new", "rev", "old"), ui.batches.map { it.id })
        assertEquals(listOf("staged"), ui.staged.map { it.id })
        assertFalse(ui.isEmpty)
        assertTrue(importBatchesUi(emptyList(), emptyList()).isEmpty, "لم تستورد شيئًا بعد")
    }

    @Test fun eachBatchShowsItsSourceAndOnlyNonZeroCounts() {
        assertEquals(UiKey.IMPORT_BATCHES_SRC_SMS, batchSourceLabel(ImportSourceType.SMS))
        assertEquals(UiKey.IMPORT_BATCHES_SRC_QNB, batchSourceLabel(ImportSourceType.PDF_QNB))
        assertEquals(UiKey.IMPORT_BATCHES_SRC_CSV, batchSourceLabel(ImportSourceType.CSV_LEGACY))
        val counts = batchCounts(Fx.batch("b", "2026-10-01T00:00:00.000Z", counts = ImportCounts(13, 6, 3, 2, 1, 1)))
        assertEquals(listOf(6, 3, 2, 1, 1), counts.map { it.second })
        assertEquals(UiKey.IMPORT_BATCHES_ADDED, counts.first().first)
        assertEquals(1, batchCounts(Fx.batch("c", "2026-10-01T00:00:00.000Z", counts = ImportCounts(41, 41, 0, 0, 0, 0))).size)
    }

    private val plan = RevertPlan(
        batchId = "b4",
        toDelete = listOf("d1", "d2", "d3", "d4", "d5", "d6"),
        toKeep = listOf(
            RevertLineOutcome("k1", RevertDecision.KEPT_HAS_SETTLEMENT, "سُجّلت على هذه العملية تسوية"),
            RevertLineOutcome("k2", RevertDecision.KEPT_OTHER_SOURCE, "لها مصدر آخر"),
        ),
        outcomes = emptyList(),
        isClean = false,
        expectedCount = 8,
        recordsFound = 8,
        blocked = false,
        unlinkCount = 3,
    )

    @Test fun thePlanShowsWhatIsDeletedAndWhatStaysWithTheLogicsReason() {
        val details = mapOf("k1" to Fx.txn("k1", "عشاء مع خالد", 36000), "d1" to Fx.txn("d1", "سوبرماركت الحي", 27410, direction = Direction.OUT))
        val ui = revertUi(plan, details)
        assertEquals(6, ui.deleteCount)
        assertEquals(4, ui.deleted.size, "أول 4 بس من اللي هيتمسح")
        assertEquals(2, ui.more)
        assertEquals(listOf("عشاء مع خالد", null), ui.kept.map { it.txn.name }, "عملية ما اتقرتش ⇒ «غير متاح» مش اسم مخترع")
        assertNull(ui.kept[1].txn.amountMinor)
        assertEquals("سُجّلت على هذه العملية تسوية", ui.kept[0].reason)
        assertEquals(3, ui.unlink)
        assertFalse(ui.clean)
        assertFalse(ui.blocked)
        assertEquals(listOf("k1", "k2", "d1", "d2", "d3", "d4"), revertDetailIds(plan))
    }

    @Test fun aCleanPlanAndABlockedOne() {
        val clean = revertUi(plan.copy(toKeep = emptyList(), toDelete = listOf("d1")), emptyMap())
        assertTrue(clean.clean)
        assertEquals(0, clean.more)
        assertTrue(revertUi(plan.copy(blocked = true), emptyMap()).blocked, "معرّفات تالفة ⇒ «تعذّر إرجاع هذه الدفعة»")
    }

    @Test fun keptReasonsHaveAWordForTheirChip() {
        assertEquals(UiKey.REVERT_SHEET_KEEP_GIFT, keptLabel(RevertDecision.KEPT_HAS_GIFT))
        assertEquals(UiKey.REVERT_SHEET_KEEP_DUE, keptLabel(RevertDecision.KEPT_HAS_DUE))
        assertEquals(UiKey.REVERT_SHEET_KEEP_INVEST, keptLabel(RevertDecision.KEPT_HAS_INVESTMENT))
        assertEquals(UiKey.REVERT_SHEET_KEEP_ALLOC, keptLabel(RevertDecision.KEPT_HAS_ALLOCATION))
    }

    @Test fun theRevertResultInBothArabicVariantsAndEnglish() {
        assertEquals("حُذفت 4 عمليات، وبقيت عمليتان لارتباطها بغيرها.", revertResult(4, 2))
        assertEquals("حُذفت عملية واحدة.", revertResult(1, 0))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("اتمسحت 4 عمليات، وفضلت عمليتين متربطة بحاجات تانية.", revertResult(4, 2))
        Texts.language = Language.EN
        assertEquals("deleted 12 transactions.", revertResult(12, 0))
    }
}
