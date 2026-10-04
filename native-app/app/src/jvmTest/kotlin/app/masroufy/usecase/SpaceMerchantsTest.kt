package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizationSource
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Merchant
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.categorize
import app.masroufy.core.merchantIndex
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.port.MerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** التاجر مشترك وتصنيفه لكل بلد (اختيار Claude §64)، وتصنيف السعودية بييجي **اقتراح** في مصر (رد المالك §64-٢) — أسماء مخترعة. */
class SpaceMerchantsTest {
    private val saudiCopy = Merchant("merch-00001", "سوبر ماركت وهمي", "سوبر ماركت وهمي", aliases = listOf("SUPER WAHMI"), verifiedCategoryId = "cat-sa")

    private fun cat(id: String, active: Boolean = true) = Category(id, null, "تصنيف $id", "tag", "#000000", "#ffffff", active, 1)

    private fun purchase(id: String, merchant: String) = Transaction(
        id = id, occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = 12_345, currency = Currency.EGP, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawMerchantName = merchant,
    )

    /** بيعدّ الكتابات على التاجر المشترك. */
    private class Counting(private val inner: MerchantRepository) : MerchantRepository by inner {
        var writes = 0
        override suspend fun saveMany(merchants: List<Merchant>) {
            writes += merchants.size
            inner.saveMany(merchants)
        }
    }

    @Test fun egyptCategoryStaysInEgyptAndTheSharedMerchantKeepsSaudisCategory() = runBlocking<Unit> {
        val shared = Counting(MemoryMerchantRepository(listOf(saudiCopy)))
        val local = MemoryMerchantCategoryRepository()
        // شجرة مصر هنا مفيهاش تصنيف السعودية ⇒ ولا اقتراح كمان
        val egypt = SpaceMerchantRepository(shared, local, MemoryCategoryRepository())

        assertNull(egypt.listAll().single().verifiedCategoryId, "تصنيف السعودية ما بيعدّيش لمصر مؤكد")
        assertNull(egypt.findByNormalizedName("super wahmi")!!.verifiedCategoryId)

        egypt.saveMany(listOf(egypt.listAll().single().copy(verifiedCategoryId = "cat-eg")))
        assertEquals(0, shared.writes, "التصنيف بس اتغير ⇒ ولا كتابة على المستند المشترك")
        assertEquals("cat-sa", shared.listAll().single().verifiedCategoryId, "السعودية زي ما هي")
        assertEquals("cat-eg", egypt.listAll().single().verifiedCategoryId)
        assertEquals("cat-eg", egypt.findByNormalizedName("سوبر ماركت وهمي")!!.verifiedCategoryId)

        // اسم بديل جديد من مصر ⇒ بيروح للتاجر المشترك بتصنيف السعودية زي ما هو
        egypt.saveMany(listOf(egypt.listAll().single().copy(aliases = listOf("SUPER WAHMI", "WAHMI EG"))))
        assertEquals(1, shared.writes)
        assertEquals(listOf("SUPER WAHMI", "WAHMI EG") to "cat-sa", shared.listAll().single().let { it.aliases to it.verifiedCategoryId })

        // تاجر جديد من مصر ⇒ مشترك من غير تصنيف، وتصنيفه في مصر بس
        egypt.saveMany(listOf(Merchant("merch-90000", "كشك وهمي", "كشك وهمي", verifiedCategoryId = "cat-eg-2")))
        assertNull(shared.listAll().single { it.id == "merch-90000" }.verifiedCategoryId)
        assertEquals("cat-eg-2", egypt.listAll().single { it.id == "merch-90000" }.verifiedCategoryId)

        // شيل التصنيف في مصر ⇒ بيتشال من مصر بس
        egypt.saveMany(listOf(egypt.listAll().single { it.id == "merch-00001" }.copy(verifiedCategoryId = null)))
        assertEquals(mapOf("merch-90000" to "cat-eg-2"), local.listAll())
        assertEquals("cat-sa", shared.listAll().single { it.id == "merch-00001" }.verifiedCategoryId)
    }

    @Test fun saudiCategoryIsOnlySuggestedInEgyptAndNeverWritten() = runBlocking<Unit> {
        val shared = Counting(MemoryMerchantRepository(listOf(saudiCopy)))
        val local = MemoryMerchantCategoryRepository()
        val tree = MemoryCategoryRepository(listOf(cat("cat-sa")))
        val egypt = SpaceMerchantRepository(shared, local, tree)

        val m = egypt.listAll().single()
        assertEquals(null to "cat-sa", m.verifiedCategoryId to m.suggestedCategoryId, "اقتراح مش تأكيد")
        assertEquals("cat-sa", egypt.findByNormalizedName("super wahmi")!!.suggestedCategoryId)

        // التصنيف الآلي: العملية بتاخد التصنيف **مقترح** والمستخدم يأكد
        val txns = MemoryTransactionRepository(listOf(purchase("t-1", "SUPER WAHMI")))
        val categorize = CategorizeTransactions(
            CategorizeTransactionsDeps(txns, egypt, tree, MemoryRuleRepository(), MemoryUnitOfWork(listOf(txns)), FixedClock("2026-10-05T10:00:00.000Z")),
        )
        val report = categorize.apply(txns.findByIds(listOf("t-1")))
        assertEquals(CategorizationSource.OTHER_COUNTRY_MERCHANT.wire, report.changed.single().source)
        val t = txns.findByIds(listOf("t-1")).single()
        assertEquals("cat-sa" to ReviewState.SUGGESTED, t.categoryId to t.reviewState)
        assertFalse(t.categoryConfirmed, "مش مؤكد لوحده")

        // الاقتراح عمره ما بيتكتب: التاجر اللي اتقرا بيتحفظ زي ما هو ⇒ ولا كتابة على المشترك ولا تصنيف في مصر
        egypt.saveMany(egypt.listAll())
        assertEquals(0, shared.writes)
        assertEquals(emptyMap(), local.listAll())
        // تعديل اسم بديل ⇒ المستند المشترك بيتكتب **من غير** الاقتراح، ومصر لسه من غير تصنيف مؤكد
        egypt.saveMany(listOf(egypt.listAll().single().copy(aliases = listOf("SUPER WAHMI", "WAHMI EG"))))
        assertEquals(1, shared.writes)
        assertEquals(saudiCopy.copy(aliases = listOf("SUPER WAHMI", "WAHMI EG")), shared.listAll().single())
        assertEquals(emptyMap(), local.listAll())

        // المستخدم أكد ⇒ مؤكد في مصر بس، والاقتراح اختفى
        egypt.saveMany(listOf(egypt.listAll().single().copy(verifiedCategoryId = "cat-sa")))
        assertEquals(mapOf("merch-00001" to "cat-sa"), local.listAll())
        assertEquals("cat-sa" to null, egypt.listAll().single().let { it.verifiedCategoryId to it.suggestedCategoryId })
    }

    @Test fun noSuggestionWhenTheSaudiCategoryIsNotInTheEgyptTree() = runBlocking<Unit> {
        for (tree in listOf(emptyList(), listOf(cat("cat-other")), listOf(cat("cat-sa", active = false)))) {
            val egypt = SpaceMerchantRepository(MemoryMerchantRepository(listOf(saudiCopy)), MemoryMerchantCategoryRepository(), MemoryCategoryRepository(tree))
            assertNull(egypt.listAll().single().suggestedCategoryId, tree.toString())
            val r = categorize(CategorizationInput(false, merchantName = "SUPER WAHMI"), CategorizeDeps(merchantIndex(egypt.listAll()), emptyList(), emptyMap()))
            assertEquals(CategorizationSource.NONE to null, r.source to r.categoryId, tree.toString())
        }
    }
}
