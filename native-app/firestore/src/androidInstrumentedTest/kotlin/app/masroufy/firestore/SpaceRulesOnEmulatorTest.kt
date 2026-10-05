package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * المسار الجديد للبلاد (`users/{uid}/spaces/{id}/…`) تحت **قواعد المشروع الحقيقية** (`emulator-auth/` — نسخة من `firestore.rules`):
 * صاحب الحساب بيكتب ويقرا · حساب تاني مرفوض. ده الدليل إن البلاد **مش محتاجة نشر قواعد** (OVERRIDES §64). حسابات وهمية بس.
 */
@RunWith(AndroidJUnit4::class)
class SpaceRulesOnEmulatorTest {
    private fun txn(id: String) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.EGP, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private fun email(tag: String) = "$tag-${java.util.UUID.randomUUID().toString().take(8)}@example.com"

    @Test fun ownerReadsAndWritesHisCountriesAndOthersAreDenied() = runBlocking<Unit> {
        withTimeout(90_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val authA = FirebaseAuthAdapter(AuthEmulator.auth("rules-a"), scope)
            val authB = FirebaseAuthAdapter(AuthEmulator.auth("rules-b"), scope)
            val a = authA.registerWithEmail(email("a"), "secret1")
            val b = authB.registerWithEmail(email("b"), "secret1")
            val dbA = AuthEmulator.firestore("rules-a")
            val dbB = AuthEmulator.firestore("rules-b")

            // «أ» في حسابه: سجل البلد + عملية + تصنيف تاجر جوه البلد
            val accountA = FirestoreSpace.forAccount(dbA, a.uid)
            FirestoreSpaceRegistry(accountA).addIfMissing(Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z"))
            val egA = FirestoreSpace.forSpace(dbA, a.uid, "eg")
            FirestoreTransactionRepository(egA).saveMany(listOf(txn("t-1")))
            FirestoreMerchantCategoryRepository(egA).set("merch-1", "cat-1")
            assertEquals(listOf("t-1"), FirestoreTransactionRepository(egA).listByDateRange("2026-09-01", "2026-09-30").map { it.id })
            assertEquals(listOf("eg"), FirestoreSpaceRegistry(accountA).listAll().map { it.id })
            assertEquals(mapOf("merch-1" to "cat-1"), FirestoreMerchantCategoryRepository(egA).listAll())

            // «ب» يحاول يقرا أو يكتب في بلد «أ» ⇒ القواعد بترفض
            val egOfAFromB = FirestoreSpace.forSpace(dbB, a.uid, "eg")
            val read = assertFailsWith<FirebaseFirestoreException> { FirestoreTransactionRepository(egOfAFromB).listByDateRange("2026-09-01", "2026-09-30") }
            assertEquals(FirestoreExceptionCode.PERMISSION_DENIED, read.code)
            val write = assertFailsWith<FirebaseFirestoreException> { FirestoreTransactionRepository(egOfAFromB).saveMany(listOf(txn("t-x"))) }
            assertEquals(FirestoreExceptionCode.PERMISSION_DENIED, write.code)
            val registry = assertFailsWith<FirebaseFirestoreException> { FirestoreSpaceRegistry(FirestoreSpace.forAccount(dbB, a.uid)).listAll() }
            assertEquals(FirestoreExceptionCode.PERMISSION_DENIED, registry.code)
            // و«ب» في حسابه هو مسموح
            FirestoreTransactionRepository(FirestoreSpace.forSpace(dbB, b.uid, "eg")).saveMany(listOf(txn("t-b")))
            authA.signOut(); authB.signOut()
            scope.cancel()
        }
    }
}
