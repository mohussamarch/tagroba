package app.masroufy.ui.screens.operations

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Merchant
import app.masroufy.core.Texts
import app.masroufy.ui.components.AmountTone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * صفحة التاجر (`MerchantProfile`): من التجار المحفوظين (`ManageRules.listMerchants`) وعمليات الشهر (`LoadTransactionsScreen`) — المطابقة بالاسم
 * المطبّع والأسماء البديلة، التصنيف المؤكد في البلد دي ≠ المقترح (من بلدك التانية أو من قاعدة عامة)، والمحل اللي لسه ما اتحفظش ما بيتعدّلش.
 */
class MerchantProfileModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val saved = Merchant("m1", "مطعم الريف", "مطعم الريف", aliases = listOf("الريف للمأكولات"), verifiedCategoryId = Fx.food.id)
    private val data = Fx.screen(
        listOf(
            Fx.tx("a", merchant = "مطعم الريف", amount = 4_200),
            Fx.tx("b", date = "2026-10-02", merchant = "الريف للمأكولات", amount = 6_850, wallet = Fx.cash.id),
            Fx.tx("c", merchant = "سوبرماركت الحي"),
        ),
    )

    @Test fun savedMerchantShowsItsCategoryAliasesAndThisMonthsOperations() {
        val v = merchantView("الريف للمأكولات", listOf(saved), listOf(Fx.food, Fx.shop), data, listOf(Fx.bank, Fx.cash), Fx.TODAY)
        assertEquals("مطعم الريف", v.name)
        assertEquals("m1", v.merchantId)
        assertEquals(Fx.food, v.category)
        assertTrue(v.confirmed)
        assertEquals(listOf("الريف للمأكولات"), v.aliases)
        assertEquals(listOf("a", "b"), v.txns.map { it.id }, "الاسم والاسم البديل ⇒ نفس التاجر، والأحدث الأول")
        assertEquals(listOf("اليوم", "٢ أكتوبر"), v.txns.map { it.date })
        assertEquals(listOf("حساب الراتب", "الكاش"), v.txns.map { it.wallet })
        assertEquals(listOf(4_200L, 6_850L), v.txns.map { it.amountMinor }, "كل عملية بمبلغها — مفيش مجموع محسوب هنا")
        assertEquals(AmountTone.EXPENSE, v.txns.first().tone)
        assertEquals("أكتوبر", v.periodLabel)
        assertEquals("يُطبَّق على عملياته القادمة قبل أي قاعدة، وفي السعودية فقط.", merchantCategoryHint(v, "السعودية"))
        assertTrue(aliasExists(v, "مطعم الريف"))
        assertTrue(aliasExists(v, " الريف  للمأكولات "))
        assertFalse(aliasExists(v, "الريف فرع العليا"))
    }

    @Test fun suggestionFromTheOtherCountryAndUnsavedShop() {
        val suggested = merchantView("مطعم الريف", listOf(saved.copy(verifiedCategoryId = null, suggestedCategoryId = Fx.food.id)), listOf(Fx.food), data, emptyList(), Fx.TODAY)
        assertFalse(suggested.confirmed)
        assertTrue(suggested.fromOtherCountry)
        assertEquals("التاجر مصنّف هكذا في بلدك الآخر — اقتراح، أكّده إن كان صحيحًا.", merchantCategoryHint(suggested, "مصر"))
        val unsaved = merchantView("سوبرماركت الحي", listOf(saved), listOf(Fx.food), data, emptyList(), Fx.TODAY)
        assertNull(unsaved.merchantId, "مش محفوظ ⇒ التصنيف والاسم ما بيتغيروش من هنا")
        assertEquals("سوبرماركت الحي", unsaved.name)
        assertEquals(listOf("c"), unsaved.txns.map { it.id })
        assertEquals("غير متاح", unsaved.txns.single().wallet, "محفظة مش معروفة ⇒ «غير متاح» مش فاضي")
        assertTrue(merchantView("مطعم الريف", emptyList(), emptyList(), null, emptyList(), Fx.TODAY).txns.isEmpty(), "فشل تحميل الشهر ⇒ القايمة فاضية مش مخترعة")
    }
}
