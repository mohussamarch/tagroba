package app.masroufy.usecase

import app.masroufy.core.Merchant
import app.masroufy.memory.MemoryMerchantCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.port.MerchantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** التاجر مشترك وتصنيفه لكل بلد (اختيار Claude §64) — أسماء مخترعة. */
class SpaceMerchantsTest {
    private val saudiCopy = Merchant("merch-00001", "سوبر ماركت وهمي", "سوبر ماركت وهمي", aliases = listOf("SUPER WAHMI"), verifiedCategoryId = "cat-sa")

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
        val egypt = SpaceMerchantRepository(shared, local)

        assertNull(egypt.listAll().single().verifiedCategoryId, "تصنيف السعودية ما بيعدّيش لمصر")
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
}
