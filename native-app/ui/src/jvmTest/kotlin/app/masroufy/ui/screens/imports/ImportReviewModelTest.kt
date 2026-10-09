package app.masroufy.ui.screens.imports

import app.masroufy.core.ArabicVariant
import app.masroufy.core.CategorizationSource
import app.masroufy.core.ImportCounts
import app.masroufy.core.MatchingState
import app.masroufy.core.RowError
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.usecase.ImportCountsPreview
import app.masroufy.usecase.ImportImpact
import app.masroufy.usecase.ImportPreview
import app.masroufy.usecase.ImportPreviewLine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «مراجعة الكشف» (`ImportReview`) + «قارن» (`ImportDuplicateSheet`): من `ImportStatement.preview` لحالة الشاشة. الأثر رقم المعاينة نفسه
 * للاختيار المبدئي بس — اختيار تاني ⇒ «غير متاح» (الشاشة ما بتجمعش فلوس — CLAUDE.md #4 · #10).
 */
class ImportReviewModelTest {
    @AfterTest fun reset() = Fx.resetTexts()

    private fun line(n: Int, state: MatchingState, merchant: String = "متجر $n", source: CategorizationSource? = null, matched: String? = null) =
        ImportPreviewLine(Fx.parsed(n, merchant, 100L * n), state, "سبب $n", matched, categoryId = if (state == MatchingState.NEW) "cat" else "", categoryReason = "",
            categorySource = source, selectedByDefault = state == MatchingState.NEW)

    private val impact = ImportImpact(walletDeltaMinor = -400, expenseMinor = 400, incomeMinor = 0)

    private fun preview(lines: List<ImportPreviewLine>, errors: List<RowError> = emptyList(), previous: app.masroufy.core.ImportBatch? = null) =
        ImportPreview("ك.pdf", "hash", SchemaId.ALRAJHI_PDF, "حساب الراتب", previous, lines, errors, ImportCountsPreview(lines.size + errors.size, 2, 1, 1, 1, errors.size), impact)

    private val lines = listOf(
        line(4, MatchingState.SIMILAR, matched = "t-old"),
        line(1, MatchingState.NEW, " سوبرماركت ", CategorizationSource.RULE),
        line(3, MatchingState.NEW),
        line(2, MatchingState.DUPLICATE, matched = "t-dup"),
        line(5, MatchingState.CONFLICT, matched = "t-c"),
    )

    @Test fun linesAreSortedWithErrorsAsInvalidAndNewSelectedByDefault() {
        val ui = importReviewUi(preview(lines, listOf(RowError(6, "amount", "مدين ودائن معًا", "raw line six"))))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ui.lines.map { it.lineNumber })
        assertEquals("سوبرماركت", ui.lines[0].name)
        assertEquals(setOf(1, 3), ui.defaultSelection, "الجديد مختار، والباقي محتاج قرار")
        val invalid = ui.lines.last()
        assertEquals(MatchingState.INVALID, invalid.state)
        assertNull(invalid.amountMinor, "غير صالح ⇒ «غير متاح» مش صفر")
        assertEquals("مدين ودائن معًا", invalid.reason)
        assertEquals(listOf(true, false, true, true, false, false), ui.lines.map { it.selectable })
        assertEquals(listOf(false, true, false, true, true, false), ui.lines.map { it.canCompare })
        assertNull(ui.lines[1].categoryId, "تصنيف فاضي ⇒ «غير مصنّف»")
    }

    @Test fun theImpactIsThePreviewsOnlyForTheDefaultSelection() {
        val ui = importReviewUi(preview(lines))
        assertEquals(impact, ui.impactFor(setOf(1, 3)))
        assertNull(ui.impactFor(setOf(1)), "اختيار تاني ⇒ «غير متاح» لحد ما تبقى حالة استخدام تحسبه")
        assertNull(ui.impactFor(setOf(1, 3, 4)))
    }

    @Test fun countersComeFromThePreview() {
        val ui = importReviewUi(preview(lines))
        assertEquals(listOf(2, 1, 1, 1, 0), MatchingState.entries.map { ui.count(it) })
    }

    @Test fun theSameFileWithNothingNewIsTheEmptyState() {
        val old = Fx.batch("b1", "2026-10-02T21:40:00.000Z", counts = ImportCounts(13, 11, 2, 0, 0, 0))
        val nothingNew = importReviewUi(preview(listOf(line(2, MatchingState.DUPLICATE)), previous = old))
        assertTrue(nothingNew.alreadyImported)
        assertEquals("في ٢ أكتوبر: سُجّلت ١١ عملية، ولم تُضف عمليتان. كل ما فيه مسجل، فلا جديد.", alreadyImportedBody(old))
        val partial = importReviewUi(preview(lines, previous = old))
        assertFalse(partial.alreadyImported, "الملف اللي اتستورد منه جزء لازم يكمل")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("يوم ٢ أكتوبر: اتسجلت عملية واحدة. كل اللي فيه متسجل، فمفيش حاجة جديدة.", alreadyImportedBody(old.copy(counts = ImportCounts(1, 1, 0, 0, 0, 0))))
    }

    @Test fun theSavedMessageSaysWhatWasNotAdded() {
        assertEquals("سُجّلت ٣ عمليات، ولم تُضف عمليتان", savedToast(3, 5))
        assertEquals("سُجّلت عملية واحدة", savedToast(1, 1))
    }

    @Test fun labelsForStatesAndCategorySources() {
        assertEquals(TextKey.IMPORT_REVIEW_SRC_RULE, sourceLabel(CategorizationSource.RULE))
        assertEquals(TextKey.IMPORT_REVIEW_SRC_NEW, sourceLabel(null))
        assertEquals(TextKey.IMPORT_REVIEW_SRC_OTHER_COUNTRY, sourceLabel(CategorizationSource.OTHER_COUNTRY_MERCHANT))
        assertEquals(TextKey.IMPORT_REVIEW_CONFLICT, stateLabel(MatchingState.CONFLICT))
        assertEquals(TagTone.DANGER, stateTone(MatchingState.CONFLICT))
        assertEquals(TagTone.NEW, stateTone(MatchingState.NEW))
    }

    @Test fun theCompareSheetMarksTheAmountBeforeTheDate() {
        assertEquals(DiffField.AMOUNT, diffOf(15900, "2026-09-19", 14900, "2026-09-18"))
        assertEquals(DiffField.DATE, diffOf(2300, "2026-09-03", 2300, "2026-09-02"))
        assertNull(diffOf(4200, "2026-09-14", 4200, "2026-09-14"))
        assertNull(diffOf(null, null, 4200, "2026-09-14"), "سطر غير صالح ⇒ مفيش مقارنة")
    }
}
