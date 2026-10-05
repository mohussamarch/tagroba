package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Language
import app.masroufy.core.ReviewState
import app.masroufy.core.Texts
import app.masroufy.core.Transaction
import app.masroufy.port.AuthError
import app.masroufy.port.AuthField
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * تسجيل الدخول على **Auth Emulator** + Firestore **بقواعد المشروع الحقيقية** (`emulator-auth/`) — حسابات وهمية بس.
 * الأخطاء بنفس رسايل التطبيق الحالي · كل حساب بيشوف بياناته بس · الخروج بيقفل بيانات الحساب.
 */
@RunWith(AndroidJUnit4::class)
class AuthOnEmulatorTest {
    private fun email(tag: String) = "$tag-${java.util.UUID.randomUUID().toString().take(8)}@example.com"

    private fun txn(id: String) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private suspend fun fails(code: String, field: AuthField, block: suspend () -> Unit) {
        val e = assertFailsWith<AuthError> { block() }
        assertEquals(code, e.code, e.message)
        assertEquals(field, e.field, code)
    }

    @Test fun signInErrorsMatchTheCurrentApp() = runBlocking<Unit> {
        withTimeout(60_000) {
            Texts.language = Language.AR
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val auth = FirebaseAuthAdapter(AuthEmulator.auth("errors"), scope)
            val me = email("me")
            val user = auth.registerWithEmail("  $me ", "secret1")
            assertEquals(me, user.email, "الإيميل بيتقص من المسافات")
            assertEquals(user.uid, auth.currentUser()?.uid)
            fails("auth/email-already-in-use", AuthField.EMAIL) { auth.registerWithEmail(me, "secret2") }
            fails("auth/weak-password", AuthField.PASSWORD) { auth.registerWithEmail(email("weak"), "123") }
            fails("auth/invalid-email", AuthField.EMAIL) { auth.registerWithEmail("مش-إيميل", "secret1") }
            fails("auth/missing-password", AuthField.PASSWORD) { auth.signInWithEmail(me, "") }
            auth.signOut()
            assertNull(auth.currentUser())
            val wrong = assertFailsWith<AuthError> { auth.signInWithEmail(me, "wrong-password") }
            assertTrue(wrong.code in setOf("auth/wrong-password", "auth/invalid-credential"), "كلمة سر غلط: ${wrong.code}")
            assertEquals(user.uid, auth.signInWithEmail(me, "secret1").uid)
            auth.sendPasswordReset(me)
            auth.signOut()
            scope.cancel()
        }
    }

    @Test fun eachAccountSeesOnlyItsOwnData() = runBlocking<Unit> {
        withTimeout(90_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val auth = FirebaseAuthAdapter(AuthEmulator.auth("session"), scope)
            val db = AuthEmulator.firestore("session")
            val session = AccountSession(auth, db, scope)
            session.start()
            assertIs<AccountSession.State.SignedOut>(session.state.first())

            // «أ» داخل ⇒ جاهز ⇒ عملية في حسابه
            val a = auth.registerWithEmail(email("a"), "secret1")
            val readyA = session.state.first { it is AccountSession.State.Ready } as AccountSession.State.Ready
            assertEquals(a.uid, readyA.user.uid)
            readyA.repos.transactions.saveMany(listOf(txn("t-of-a")))
            assertTrue(readyA.sync.documentsInMemory >= 1)
            assertEquals(listOf("t-of-a"), readyA.repos.transactions.listByDateRange("2026-09-01", "2026-09-30").map { it.id })

            // خرج ⇒ مفيش بيانات · «ب» دخل على نفس الجهاز ⇒ حسابه فاضي
            auth.signOut()
            session.state.first { it is AccountSession.State.SignedOut }
            val b = auth.registerWithEmail(email("b"), "secret1")
            val readyB = session.state.first { it is AccountSession.State.Ready && it.user.uid == b.uid } as AccountSession.State.Ready
            assertEquals(emptyList(), readyB.repos.transactions.listByDateRange("2026-09-01", "2026-09-30"), "حساب «ب» ما يشوفش عمليات «أ»")

            // «ب» يحاول يقرا مكان «أ» مباشرة ⇒ قواعد المشروع الحقيقية بترفض
            val denied = assertFailsWith<FirebaseFirestoreException> {
                FirestoreTransactionRepository(FirestoreSpace.forUser(db, a.uid)).listByDateRange("2026-09-01", "2026-09-30")
            }
            assertEquals(FirestoreExceptionCode.PERMISSION_DENIED, denied.code)
            // مزامنة «أ» وقفت فعلًا وذاكرته اتمسحت — مش بس إن مستمعيه وقعوا من القواعد بعد الخروج
            assertTrue(!readyA.sync.isRunning, "مستمعين «أ» لازم يقفوا لما يخرج")
            assertTrue(readyA.sync.syncedGroups.value.isEmpty(), "ذاكرة «أ» اتمسحت")
            assertEquals(0, readyA.sync.documentsInMemory, "مستندات «أ» ما تفضلش في الذاكرة بعد ما يخرج")

            session.stop()
            auth.signOut()
            scope.cancel()
        }
    }
}
