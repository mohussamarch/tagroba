package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizationSource
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Merchant
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.categorize
import app.masroufy.core.normalizeText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySharedMerchantCatalog
import app.masroufy.memory.MemorySyncCursor
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-shared (رد المالك 2026-10-22 · OVERRIDES §25 · §80): قاعدة التجار المشتركة بحسابين على **نفس** الكتالوج في الذاكرة
 * (بيقلّد قواعد الحماية: مش مؤكد بس، والمؤكد ما يتعدلش). كل الأسامي مخترعة.
 */
class SharedMerchantsTwoAccountsTest {
    private val tree = setOf("cat-food", "cat-home")

    private class Account(catalog: MemorySharedMerchantCatalog, tree: Set<String>, people: List<Person> = emptyList(), seed: List<Merchant> = emptyList()) {
        val merchants = MemoryMerchantRepository(seed)
        val lastSync = MemorySyncCursor()
        val shared = SharedMerchants(
            SharedMerchantsDeps(
                catalog = catalog, merchants = merchants, baseline = emptyList(), treeCategoryIds = tree,
                cursor = MemorySyncCursor(), confirmedCursor = MemorySyncCursor(), clock = FixedClock("2026-10-22T10:00:00.000Z"),
                people = MemoryPersonRepository(people), transferParties = MemoryTransferPartyRepository(), lastSync = lastSync,
            ),
        )

        /** الرفع «من غير ما حد يستنى» — هنا بيتشغل على طول عشان الاختبار يشوف النتيجة. */
        val share = ShareMerchantCategory(shared) { block -> runBlocking { block() } }

        suspend fun merchant(name: String) = merchants.listAll().firstOrNull { it.normalizedName == normalizeText(name) }
    }

    private fun purchase(name: String) = MerchantContribution(EconomicKind.PURCHASE, Direction.OUT, name)

    @Test fun aContributesThenBSyncsAndGetsASuggestion() = runBlocking<Unit> {
        val catalog = MemorySharedMerchantCatalog()
        val a = Account(catalog, tree)
        val b = Account(catalog, tree)
        a.share.hook(purchase("Test Bakery One"), "cat-food")
        val entry = assertNotNull(catalog.get("TEST BAKERY ONE"))
        assertEquals(false, entry.confirmed)
        assertEquals("cat-food", entry.categoryId)

        val r = b.shared.sync()
        assertEquals(1, r.suggested)
        val m = assertNotNull(b.merchant("Test Bakery One"))
        assertEquals("cat-food", m.sharedCategoryId)
        assertNull(m.verifiedCategoryId, "اقتراح بس — مش تأكيد")
        assertNotNull(b.lastSync.read(), "آخر مزامنة اتسجلت")

        // التصنيف عند B: مقترح (يتغير)، مش مؤكد
        val res = categorize(
            CategorizationInput(currentConfirmed = false, merchantName = "Test Bakery One"),
            CategorizeDeps(mapOf(m.normalizedName to m), emptyList(), emptyMap()),
        )
        assertEquals(CategorizationSource.SHARED_MERCHANT, res.source)
        assertEquals(ReviewState.SUGGESTED, res.reviewState)
        assertEquals("cat-food", res.categoryId)
    }

    @Test fun bsOwnChoiceIsNeverOverwritten() = runBlocking<Unit> {
        val catalog = MemorySharedMerchantCatalog()
        val a = Account(catalog, tree)
        val b = Account(catalog, tree, seed = listOf(Merchant("m-b", "Test Bakery Two", "TEST BAKERY TWO", verifiedCategoryId = "cat-home")))
        a.share.hook(purchase("Test Bakery Two"), "cat-food")
        b.shared.sync()
        val m = assertNotNull(b.merchant("Test Bakery Two"))
        assertEquals("cat-home", m.verifiedCategoryId)
        assertNull(m.sharedCategoryId)
        assertEquals(1, b.merchants.listAll().size)
    }

    @Test fun aPersonNameIsNeverContributed() = runBlocking<Unit> {
        val catalog = MemorySharedMerchantCatalog()
        val a = Account(catalog, tree, people = listOf(Person("p-1", "Fake Sample Person")))
        a.share.hook(purchase("Fake Sample Person"), "cat-food")
        a.share.hook(purchase("PAY Fake Sample Person"), "cat-food")
        // تحويل أو وارد ما بيترفعش أصلًا
        a.share.hook(MerchantContribution(EconomicKind.INTERNAL_TRANSFER, Direction.OUT, "Test Shop Three"), "cat-food")
        a.share.hook(MerchantContribution(EconomicKind.PURCHASE, Direction.IN, "Test Shop Three"), "cat-food")
        assertTrue(catalog.listChangedSince(null).isEmpty())
    }

    @Test fun aConfirmedEntryChangedByBStaysBOnly() = runBlocking<Unit> {
        val catalog = MemorySharedMerchantCatalog(listOf(SharedMerchantEntry("TEST MART", "Test Mart", emptyList(), "cat-food", confirmed = true, updatedAt = "2026-01-01T00:00:00.000Z")))
        val a = Account(catalog, tree)
        val b = Account(catalog, tree)
        b.shared.sync()
        assertEquals("cat-food", assertNotNull(b.merchant("Test Mart")).verifiedCategoryId)

        // B بيغيّر تصنيف تاجر مؤكد ⇒ عنده هو بس، والقاعدة المشتركة ما بتتلمسش
        val memory = MerchantMemory(b.merchants, SequentialIdGenerator(), b.share.hook)
        memory.remember(MerchantPick("Test Mart", "cat-home", shareable = true))
        assertEquals("cat-home", assertNotNull(b.merchant("Test Mart")).verifiedCategoryId)
        val after = assertNotNull(catalog.get("TEST MART"))
        assertEquals(true, after.confirmed)
        assertEquals("cat-food", after.categoryId)

        // مزامنة تانية لـB ما بترجّعش المؤكد فوق اختياره، وA بياخد المؤكد زي ما هو
        b.shared.sync()
        assertEquals("cat-home", assertNotNull(b.merchant("Test Mart")).verifiedCategoryId)
        a.shared.sync()
        assertEquals("cat-food", assertNotNull(a.merchant("Test Mart")).verifiedCategoryId)
    }
}
