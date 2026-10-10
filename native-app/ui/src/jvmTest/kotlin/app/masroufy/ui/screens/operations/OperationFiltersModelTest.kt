package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Texts
import app.masroufy.core.periodForDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * التصفية (`OperationFilters`): اختيار بس من عمليات الفترات اللي حالة الاستخدام رجّعتها — البحث بالكلام · المبلغ بالظبط أو «قريب منه ±٥٪» ·
 * النوع · التصنيف (و«بلا تصنيف») · المحفظة · المراجعة — والشرايح الشغالة بتتشال واحدة واحدة. **مفيش جمع مبالغ** (مجموع النتايج مالوش حالة استخدام).
 */
class OperationFiltersModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val rif = Fx.tx("rif", amount = 4_200, merchant = "مطعم الريف")
    private val market = Fx.tx("market", date = "2026-10-08", amount = 31_840, category = Fx.shop.id, merchant = "سوبرماركت الحي")
    private val cash = Fx.tx("cash", amount = 6_000, category = null, wallet = Fx.cash.id, merchant = null, review = ReviewState.NEEDS_REVIEW)
    private val salary = Fx.tx("salary", date = "2026-10-01", amount = 1_250_000, direction = Direction.IN, kind = EconomicKind.SALARY, category = null, merchant = null)
    private val move = Fx.tx("move", amount = 100_000, kind = EconomicKind.INTERNAL_TRANSFER, category = null, merchant = null, toWallet = Fx.cash.id)
    private val loan = Fx.tx("loan", amount = 18_500, kind = EconomicKind.LOAN_GRANTED, category = null, merchant = null)
    private val data = listOf(Fx.screen(listOf(rif, market, cash, salary, move, loan), merchants = mapOf("rif" to listOf("مطعم الريف"))))

    private fun ids(f: Filters) = applyFilters(data, f, Currency.SAR).map { it.id }

    @Test fun noFilterKeepsEverythingNewestFirst() {
        assertEquals(listOf("cash", "loan", "move", "rif", "market", "salary").sorted(), ids(Filters()).sorted())
        assertEquals("salary", ids(Filters()).last(), "الأقدم آخر واحد")
    }

    @Test fun searchAmountAndNearAmount() {
        assertEquals(listOf("rif"), ids(Filters(query = "الريف")))
        assertEquals(listOf("rif"), ids(Filters(amountText = "42")), "المبلغ بالظبط")
        assertTrue(ids(Filters(amountText = "40")).isEmpty())
        assertEquals(listOf("rif"), ids(Filters(amountText = "40", near = true)), "٤٢ جوه ±٥٪ من ٤٠")
        assertEquals(listOf("rif"), ids(Filters(amountText = "٤٢")), "الأرقام العربي بتتقرا")
    }

    @Test fun kindCategoryWalletAndReviewGroups() {
        assertEquals(listOf("salary"), ids(Filters(kinds = setOf(KindGroup.IN))))
        assertEquals(listOf("move"), ids(Filters(kinds = setOf(KindGroup.MOVE))))
        assertEquals(listOf("loan"), ids(Filters(kinds = setOf(KindGroup.DEBT))), "السلفة ليها أثر على شخص ⇒ ديون وأقساط")
        assertEquals(setOf("rif", "market", "cash"), ids(Filters(kinds = setOf(KindGroup.OUT))).toSet())
        assertEquals(listOf("market"), ids(Filters(categories = setOf(Fx.shop.id))))
        assertTrue(NO_CATEGORY in filterCategories(data).map { it.first }, "«بلا تصنيف» دايمًا شريحة")
        assertEquals(setOf("cash", "salary", "move", "loan"), ids(Filters(categories = setOf(NO_CATEGORY))).toSet())
        assertEquals(listOf("cash"), ids(Filters(wallets = setOf(Fx.cash.id))))
        assertEquals(listOf("cash"), ids(Filters(review = ReviewFilter.NEEDS)))
        assertEquals(listOf(Fx.food.id, Fx.shop.id, NO_CATEGORY), filterCategories(data).map { it.first }, "المستعملة بس بترتيبها")
    }

    @Test fun activeChipsDropOneByOne() {
        val f = Filters(query = "الريف", amountText = "40", near = true, period = FilterPeriod.LAST, kinds = setOf(KindGroup.OUT), wallets = setOf(Fx.cash.id), review = ReviewFilter.CONFIRMED)
        val chips = activeChips(f, filterCategories(data), listOf(Fx.bank, Fx.cash), Currency.SAR)
        assertEquals(listOf("«الريف»", "≈ 40.00", "الشهر الماضي", "مصروف", "الكاش", "مؤكدة"), chips.map { it.label })
        var left = f
        for (c in chips) left = c.clear(left)
        assertEquals(Filters(), left, "كل شريحة بتشيل فلترها بس")
    }

    @Test fun periodsGoBackByFinancialMonth() {
        val current = periodForDate(Fx.TODAY, 28)
        assertEquals(listOf("2026-09"), filterPeriods(current, FilterPeriod.THIS, 28).map { it.key })
        assertEquals(listOf("2026-08"), filterPeriods(current, FilterPeriod.LAST, 28).map { it.key })
        assertEquals(listOf("2026-09", "2026-08", "2026-07"), filterPeriods(current, FilterPeriod.THREE, 28).map { it.key })
        assertEquals("2026-11", filterPeriods(periodForDate("2027-01-10", 28), FilterPeriod.LAST, 28).single().key, "رجوع السنة")
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("الشهر اللي فات", periodName(FilterPeriod.LAST))
    }
}
