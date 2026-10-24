package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-fix055b (فحص 0.5.5 ملاحظة 3): مسح تصنيف بـ«احذفه واترك العمليات» كان بيسيب التجار شايلين معرّفه ⇒ النسخة الشاملة بترفض.
 * دلوقتي: التاجر ⇒ «مالوش تصنيف» (أو التصنيف الجديد في «انقل») · القاعدة بتتشال · سقف الميزانية بيتشال · «تراجع» بيرجّع الكل بالظبط.
 * كل الأسامي والمبالغ مخترعة.
 */
class RemoveCategoryLinksTest {
    private fun cat(id: String, name: String) = Category(id, null, name, "tag", "#111111", "#eeeeee", true, 1)
    private val travel = cat("c-travel", "سفر تجربة")
    private val other = cat("c-other", "تاني تجربة")
    private val hotel = Merchant("m-hotel", "فندق تجربة", "فندق تجربة", verifiedCategoryId = "c-travel")
    private val shop = Merchant("m-shop", "محل تجربة", "محل تجربة", verifiedCategoryId = "c-other")
    private val rule = ClassificationRule("r-1", 1, "فندق", RuleMatchMode.CONTAINS, "c-travel", true)
    private val cap = CategoryBudget("cb-1", "2026-10", "c-travel", 500_00, true, 80)

    private suspend fun kit() = RemovalKit().apply {
        categories.save(travel)
        categories.save(other)
        merchants.saveMany(listOf(hotel, shop))
        rules.saveMany(listOf(rule))
        budgets.saveCategoryBudget(cap)
        txns.saveMany(listOf(op("t-1", 120_00, categoryId = "c-travel", merchantId = "m-hotel")))
    }

    @Test
    fun keepOperations_clearsMerchantRuleAndCap_thenUndoRestores() = runBlocking {
        val k = kit()
        val done = k.removals.category.remove("c-travel", RemoveChoice.KEEP_OPERATIONS)
        val merchants = k.merchants.listAll().associateBy { it.id }
        assertNull(merchants.getValue("m-hotel").verifiedCategoryId, "التاجر لسه بيشاور على التصنيف الممسوح")
        assertEquals(shop, merchants.getValue("m-shop"))
        assertTrue(k.rules.listAll().none { it.categoryId == "c-travel" })
        assertTrue(k.budgets.allLines().none { it.categoryId == "c-travel" })
        assertTrue(k.all().none { it.categoryId == "c-travel" })

        done.undo()
        assertEquals(hotel, k.merchants.listAll().single { it.id == "m-hotel" })
        assertEquals(listOf(rule), k.rules.listAll())
        assertEquals(listOf(cap), k.budgets.allLines())
        assertEquals("c-travel", k.all().single().categoryId)
    }

    @Test
    fun moveOperations_remapsMerchantToTarget() = runBlocking {
        val k = kit()
        k.removals.category.remove("c-travel", RemoveChoice.MOVE_OPERATIONS, "c-other")
        assertEquals("c-other", k.merchants.listAll().single { it.id == "m-hotel" }.verifiedCategoryId)
        assertEquals("c-other", k.rules.listAll().single().categoryId)
        assertTrue(k.budgets.allLines().isEmpty())
    }
}
