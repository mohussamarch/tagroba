package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.SharedMerchantEntry
import app.masroufy.core.emptyProfile
import app.masroufy.core.normalizeText
import app.masroufy.port.SeedSource
import app.masroufy.port.SeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ملف المستخدم وتجهيز المراجع وقاعدة التجار المشتركة على **قواعد المشروع الحقيقية** (`emulator-auth/`) — حسابات وهمية.
 * قاعدة التجار برا مساحة المستخدم، والقواعد بتفحص كل حقل (منها `updatedAt` = وقت السيرفر) — فالاختبار هنا مش على القواعد المفتوحة.
 */
@RunWith(AndroidJUnit4::class)
class AccountAdaptersTest {
    private suspend fun signedIn(tag: String): FirestoreContainer {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val auth = FirebaseAuthAdapter(AuthEmulator.auth(tag), scope)
        val user = auth.registerWithEmail("$tag-${java.util.UUID.randomUUID().toString().take(8)}@example.com", "secret1")
        scope.cancel()
        return FirestoreContainer(FirestoreSpace.forUser(AuthEmulator.firestore(tag), user.uid))
    }

    @Test fun profileAndReferenceSeed() = runBlocking<Unit> {
        withTimeout(60_000) {
            val c = signedIn("seed")
            assertNull(c.profile.load(), "حساب جديد مالوش ملف")
            val profile = emptyProfile().copy(displayName = "تجربة", salaryMinor = 1_000_000, payday = 27, dependentKinds = listOf("children"), hasCar = true)
            c.profile.save(profile)
            assertEquals(profile, c.profile.load())

            assertEquals(SeedState.PENDING, c.referenceSeed.begin(hasExistingCategories = false))
            val seed = SeedSource(
                listOf(Category("c-1", null, "أكل", "utensils", "#AA3344", "#FF8899", true, 1)),
                listOf(ClassificationRule("r-1", 1, "مطعم", RuleMatchMode.CONTAINS, "c-1", true)),
                listOf(Merchant("m-1", "Test Mart", normalizeText("Test Mart"))),
            )
            c.referenceSeed.insertMissing(seed)
            // المستخدم غيّر التصنيف ⇒ تجهيز تاني ما يكتبش فوقه
            c.categories.save(seed.categories.single().copy(name = "أكل بره"))
            c.referenceSeed.insertMissing(seed)
            assertEquals("أكل بره", c.categories.listAll().single().name)
            assertEquals(listOf("r-1"), c.rules.listAll().map { it.id })
            assertEquals("m-1", c.merchants.findByNormalizedName("test mart")?.id)
            c.referenceSeed.complete()
            assertEquals(SeedState.COMPLETE, c.referenceSeed.begin(hasExistingCategories = false))

            // حساب قديم فيه تصنيفات ⇒ «خلص» على طول
            assertEquals(SeedState.COMPLETE, signedIn("seed-old").referenceSeed.begin(hasExistingCategories = true))
        }
    }

    @Test fun sharedMerchantCatalog() = runBlocking<Unit> {
        withTimeout(60_000) {
            val c = signedIn("shared")
            val name = normalizeText("Kotlin Test ${java.util.UUID.randomUUID().toString().take(6)}")
            val before = c.sharedMerchants.listChangedSince(null).size
            c.sharedMerchants.save(SharedMerchantEntry(name, "Kotlin Test", listOf(normalizeText("KT")), "c-1", confirmed = true))
            val saved = assertNotNull(c.sharedMerchants.get(name))
            assertFalse(saved.confirmed, "التطبيق عمره ما يكتب «مؤكد» — القواعد كانت هترفض")
            val at = assertNotNull(saved.updatedAt, "وقت السيرفر لازم يتقري")
            assertTrue(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""").matches(at), "نفس شكل toISOString: $at")
            assertEquals(before + 1, c.sharedMerchants.listChangedSince(null).size)
            assertTrue(c.sharedMerchants.listChangedSince(at).none { it.normalizedName == name }, "«بعد» الوقت ده بالظبط ما يرجّعهوش")
            assertTrue(c.sharedMerchants.listChangedSince("2000-01-01T00:00:00.000Z").any { it.normalizedName == name })
            assertTrue(c.sharedMerchants.listConfirmed().none { it.normalizedName == name })
        }
    }
}
