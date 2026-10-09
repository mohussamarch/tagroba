package app.masroufy.ui.screens.home

import app.masroufy.core.Category
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.KindSuggestion
import app.masroufy.core.SuggestionConfidence
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.screens.home.HomeTestData.cash
import app.masroufy.ui.screens.home.HomeTestData.tx
import app.masroufy.usecase.CategorizationChange
import app.masroufy.usecase.CategorizationReport
import app.masroufy.usecase.SuggestionLine
import app.masroufy.usecase.SuggestionSummary
import app.masroufy.core.CashSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * المراجعة ولوحة الكاش: من نتايج حالات الاستخدام للعرض — المجموعات بالنوع المقترح ثم الغامض الوارد والصادر · «أكّد الـN» للقاطع بس ·
 * العنوان والشريط بالعدّ · معاينة «طبّق قواعدك» · صفوف الكاش بلون اتجاهها و«غير متاح» لو مفيش محفظة كاش.
 */
class ReviewQueueModelTest {
    private fun line(id: String, date: String, dir: Direction, kind: EconomicKind?, confidence: SuggestionConfidence, vararg alts: EconomicKind) =
        SuggestionLine(tx(id, date, 1_000, direction = dir, merchant = "تاجر $id"), KindSuggestion(kind, confidence, "سبب $id", alts.toList()))

    private val summary = SuggestionSummary(
        confirmable = listOf(
            line("p1", "2026-10-02", Direction.OUT, EconomicKind.PURCHASE, SuggestionConfidence.HIGH, EconomicKind.SUPPORT_GIFT),
            line("p2", "2026-10-05", Direction.OUT, EconomicKind.PURCHASE, SuggestionConfidence.HIGH),
        ),
        needsLook = listOf(line("d1", "2026-10-03", Direction.OUT, EconomicKind.DEBT_REPAID, SuggestionConfidence.MEDIUM, EconomicKind.PURCHASE)),
        ambiguous = listOf(
            line("i1", "2026-10-04", Direction.IN, null, SuggestionConfidence.AMBIGUOUS, EconomicKind.SALARY, EconomicKind.FREELANCE),
            line("o1", "2026-10-01", Direction.OUT, null, SuggestionConfidence.AMBIGUOUS, EconomicKind.PURCHASE),
        ),
        alreadySet = 7,
    )

    @Test fun groupsBySuggestedKindThenUnclearInAndOut() {
        val groups = reviewGroups(summary)
        assertEquals(listOf("kind-purchase", "kind-debt_repaid", "in", "out"), groups.map { it.key })
        assertEquals("تبدو «شراء / فاتورة»", groups[0].title, "اسم النوع من المنطق (`KIND_*`)")
        assertEquals(listOf("p2", "p1"), groups[0].items.map { it.id }, "الأحدث الأول")
        val p1 = groups[0].items[1]
        assertEquals(EconomicKind.PURCHASE, p1.suggestion)
        assertEquals(listOf(EconomicKind.SUPPORT_GIFT), p1.alternatives)
        assertEquals(AmountTone.EXPENSE, p1.tone)
        assertEquals("تاجر p1", p1.name)
        val i1 = groups[2].items.single()
        assertNull(i1.suggestion, "الغامض من غير اقتراح")
        assertEquals(AmountTone.INCOME, i1.tone)
        assertEquals("مبالغ واردة نوعها غير واضح", groups[2].title)
        assertEquals(5, reviewTotal(summary), "المؤكد قبل كده ما بيتعدّش")
    }

    @Test fun bulkOnlyForMoreThanOneConfirmableStillOpen() {
        val groups = reviewGroups(summary)
        assertEquals(listOf("p2", "p1"), groups[0].bulkIds(emptySet()))
        assertTrue(groups[0].bulkIds(setOf("p1")).isEmpty(), "فاضل واحدة ⇒ من غير «أكّد الكل»")
        assertTrue(groups[1].bulkIds(emptySet()).isEmpty(), "المرجّح محتاج نظرة — مش جماعي")
        assertTrue(groups[2].bulkIds(emptySet()).isEmpty(), "الغامض عمره ما بيتأكد جماعي")
    }

    @Test fun heroTitleAndProgress() {
        assertEquals("٥ عمليات نوعها غير مؤكد", reviewHeroTitle(5))
        assertEquals("عمليتان نوعهما غير مؤكد", reviewHeroTitle(2))
        assertEquals("اكتملت المراجعة", reviewHeroTitle(0))
        assertEquals(40, reviewDonePercent(5, 2))
        assertEquals(100, reviewDonePercent(0, 0))
    }

    @Test fun rulesPreviewCountsByTargetCategory() {
        val food = Category("c-food", null, "مطاعم", "utensils", "#A36A21", "#E0B070", active = true, order = 1)
        val plan = CategorizationReport(
            changed = listOf(
                CategorizationChange("t1", null, "c-food", "قاعدة", "rule"),
                CategorizationChange("t2", null, "c-food", "قاعدة", "rule"),
                CategorizationChange("t3", "c-food", null, "قاعدة", "rule"),
            ),
            skippedConfirmed = listOf("t9"),
            stillNeedsReview = emptyList(),
        )
        assertEquals(listOf("مطاعم" to 2, "بلا تصنيف" to 1), ruleRows(plan, listOf(food)))
    }

    @Test fun cashSheetRowsFollowTheDirectionAndNoWalletIsNotAvailable() {
        assertNull(cashViewOf(null), "مفيش محفظة كاش ⇒ «غير متاح» (مش صفر)")
        val rows = listOf(
            tx("atm", "2026-10-03", 50_000, wallet = "w-bank", to = cash.id, note = "سحب"),
            tx("tea", "2026-10-04", 1_500, wallet = cash.id, merchant = "شاي"),
        ) + (1..6).map { tx("x$it", "2026-10-0$it", 100, wallet = cash.id) }
        val view = cashViewOf(CashSummary(cash, 68_400, 50_000, 1_600, 1_600, rows))!!
        assertEquals(CASH_ROWS, view.rows.size, "اللوحة طولها ثابت")
        assertEquals(AmountTone.INCOME, view.rows[0].tone, "داخل للكاش أخضر")
        assertEquals("سحب", view.rows[0].title)
        assertEquals(AmountTone.EXPENSE, view.rows[1].tone)
        assertEquals("شاي", view.rows[1].title)
        assertEquals(68_400, view.balanceMinor, "الرقم من حالة الاستخدام زي ما هو")
        assertEquals("منذ رصيد البداية في ١ سبتمبر", view.sinceLine)
    }
}
