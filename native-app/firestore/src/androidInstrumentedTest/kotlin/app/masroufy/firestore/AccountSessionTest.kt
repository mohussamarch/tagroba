package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.MemoryAuth
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
import kotlin.test.assertTrue

/**
 * منطق الجلسة **لوحده**: دخول في الذاكرة (`MemoryAuth`) + Firestore بقواعد الاختبار المفتوحة.
 * ليه: مع دخول فايربيز الحقيقي، فايربيز نفسه بيفضّي ويوقف المستمعين القدام لما الحساب يتغير (اتشاف 2026-10-01) —
 * فاختبار `AuthOnEmulatorTest` ما يقدرش يعرف لو `AccountSession` نفسها نسيت توقف المزامنة. هنا مفيش حد غيرها يوقفها.
 */
@RunWith(AndroidJUnit4::class)
class AccountSessionTest {
    private fun txn(id: String) = Transaction(
        id = id, occurredAt = "2026-09-10", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 1_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    @Test fun switchingAccountsStopsTheOldSyncAndClearsItsMemory() = runBlocking<Unit> {
        withTimeout(60_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val auth = MemoryAuth(uidPrefix = "kt-session-" + java.util.UUID.randomUUID().toString().take(8) + "-")
            val session = AccountSession(auth, Emulator.firestore("session-memory-auth"), scope)
            session.start()

            val a = auth.registerWithEmail("a@example.com", "secret1")
            val readyA = session.state.first { it is AccountSession.State.Ready } as AccountSession.State.Ready
            readyA.repos.transactions.saveMany(listOf(txn("t-of-a")))
            assertEquals(1, readyA.sync.documentsInMemory)

            // حساب تاني دخل على طول (من غير خروج في النص)
            auth.signOut()
            val b = auth.registerWithEmail("b@example.com", "secret1")
            val readyB = session.state.first { it is AccountSession.State.Ready && it.user.uid == b.uid } as AccountSession.State.Ready
            assertTrue(a.uid != b.uid)
            assertTrue(!readyA.sync.isRunning, "مستمعين «أ» لازم يقفوا")
            assertEquals(0, readyA.sync.documentsInMemory, "مستندات «أ» ما تفضلش في الذاكرة")
            assertEquals(emptyList(), readyB.repos.transactions.listByDateRange("2026-09-01", "2026-09-30"))

            session.stop()
            assertTrue(!readyB.sync.isRunning)
            assertTrue(session.state.value is AccountSession.State.SignedOut)
            scope.cancel()
        }
    }

    /** بعد أول تنزيل كامل الجهاز بيتعلّم (`FirstSyncMarks`) ⇒ الفتح الجاي ما بيستناش السيرفر أكتر من مهلة (§54 — من غير نت من نسخة الجهاز). */
    @Test fun aCompleteFirstSyncIsRememberedOnTheDevice() = runBlocking<Unit> {
        withTimeout(60_000) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val marks = object : FirstSyncMarks {
                val done = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
                override fun completed(uid: String) = uid in done
                override fun markCompleted(uid: String) { done += uid }
            }
            val auth = MemoryAuth(uidPrefix = "kt-first-" + java.util.UUID.randomUUID().toString().take(8) + "-")
            val session = AccountSession(auth, Emulator.firestore("session-memory-auth"), scope, marks)
            session.start()
            val a = auth.registerWithEmail("a@example.com", "secret1")
            session.state.first { it is AccountSession.State.Ready }
            withTimeout(20_000) { while (a.uid !in marks.done) kotlinx.coroutines.delay(50) }
            assertTrue(marks.completed(a.uid))
            session.stop()
            scope.cancel()
        }
    }
}
