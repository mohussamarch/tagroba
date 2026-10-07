package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.data.DocumentCodecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * النسخة المحلية بتاعة فايربيز كبديل لـRoom (قرار المالك §54) — بجهازين على نفس المحاكي (كل واحد نسخته لوحده):
 * التزامن الأول بيكمل · تعديل من جهاز تاني بيوصل النسخة المحلية لوحده · من غير نت الكتابة ما بتعلّقش وبتوصل لما النت يرجع.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreSyncTest {
    private fun txn(id: String, date: String) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    /** بيستنى لحد ما الشرط يتحقق — التزامن مش لحظي، بس لازم يحصل في ثواني. */
    private suspend fun eventually(what: String, check: suspend () -> Boolean) {
        repeat(100) {
            if (check()) return
            delay(100)
        }
        throw AssertionError("ما حصلش خلال 10 ثواني: $what")
    }

    @Test fun localCopyStaysCompleteAndWorksOffline() = runBlocking<Unit> {
        withTimeout(120_000) {
            val uid = "kt-" + java.util.UUID.randomUUID()
            val a = FirestoreSpace.forUser(Emulator.firestore("device-a"), uid)
            val b = FirestoreSpace.forUser(Emulator.firestore("device-b"), uid)
            FirestoreTransactionRepository(b).saveMany(listOf(txn("t-before", "2026-09-01")))

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val sync = FirestoreSync(a, DocumentCodecs.byGroup.keys.toList())
            sync.start(scope)
            sync.awaitComplete()
            assertEquals(DocumentCodecs.byGroup.size, sync.syncedGroups.value.size)
            assertTrue(a.mirror?.isSynced("transactions") == true, "بعد التزامن القراية من الذاكرة")
            val repoA = FirestoreTransactionRepository(a)
            assertEquals(listOf("t-before"), repoA.listByDateRange("2026-09-01", "2026-09-30").map { it.id })
            // مستند مش موجود في الذاكرة = مش موجود (مش خطأ)
            assertNull(FirestoreWalletRepository(a).findById("w-مش-موجودة"))

            // جهاز «ب» بيضيف عملية ⇒ لازم توصل نسخة «أ» المحلية لوحدها
            FirestoreTransactionRepository(b).saveMany(listOf(txn("t-from-b", "2026-09-02")))
            eventually("عملية الجهاز التاني توصل") { repoA.listByDateRange("2026-09-01", "2026-09-30").any { it.id == "t-from-b" } }

            // «أ» من غير نت: الحفظ يرجع على طول، والعملية تبان في نسخته فورًا
            a.db.disableNetwork()
            val started = System.currentTimeMillis()
            repoA.saveMany(listOf(txn("t-offline", "2026-09-03")))
            assertTrue(System.currentTimeMillis() - started < 2_000, "الحفظ من غير نت ما يعلّقش")
            assertTrue(repoA.listByDateRange("2026-09-01", "2026-09-30").any { it.id == "t-offline" })

            // النت رجع ⇒ «ب» يشوفها من السيرفر
            a.db.enableNetwork()
            val repoB = FirestoreTransactionRepository(b)
            eventually("عملية «أ» اللي من غير نت توصل السيرفر") { repoB.findByIds(listOf("t-offline")).isNotEmpty() }
            sync.stop()
            scope.cancel()
        }
    }
}
