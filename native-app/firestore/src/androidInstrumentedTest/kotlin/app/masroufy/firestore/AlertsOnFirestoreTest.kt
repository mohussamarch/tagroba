package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.ACCOUNT_GROUPS
import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
import app.masroufy.data.AlertCodecs
import app.masroufy.data.receiptDocId
import app.masroufy.data.toStore
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.usecase.AlertEngineDeps
import app.masroufy.usecase.RunAlertEngine
import dev.gitlive.firebase.firestore.Source
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
import kotlin.test.assertTrue

/**
 * محرك التنبيهات على جوالين قدام Firestore Emulator (OVERRIDES §61 — رد المالك (١)، جلسة 18): الإعدادات والصفحة والإيصالات
 * **على الحساب وبتتزامن**، والتعلّم على كل جوال لوحده. الجوال «ب» شغال بالمزامنة الحقيقية (`FirestoreSync` على [ACCOUNT_GROUPS]
 * زي `AccountSession`). التشغيل زي `EventRepositoriesTest` (8088 + محاكي أندرويد). أسماء ومبالغ مخترعة.
 */
@RunWith(AndroidJUnit4::class)
class AlertsOnFirestoreTest {
    private suspend fun eventually(what: String, check: suspend () -> Boolean) {
        repeat(150) {
            if (check()) return
            delay(100)
        }
        throw AssertionError("ما حصلش خلال 15 ثانية: $what")
    }

    private fun engine(root: FirestoreSpace, interactions: MemoryAlertInteractions) = RunAlertEngine(
        AlertEngineDeps(FirestoreAlertSettings(root), interactions, MemoryUsualHours(), FirestoreAlertReceipts(root), FirestoreAlertInbox(root), FixedClock("2026-10-10T06:00:00.000Z")),
    )

    @Test fun sentOnOnePhoneIsNotSentOnTheOtherAndMutingSyncsButLearningDoesNot() = runBlocking<Unit> {
        withTimeout(150_000) {
            val uid = "kt-" + java.util.UUID.randomUUID()
            val a = FirestoreSpace.forAccount(Emulator.firestore("alerts-phone-a"), uid)
            val b = FirestoreSpace.forAccount(Emulator.firestore("alerts-phone-b"), uid)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val syncB = FirestoreSync(b, ACCOUNT_GROUPS)
            syncB.start(scope)
            syncB.awaitComplete()
            val learnA = MemoryAlertInteractions()
            val learnB = MemoryAlertInteractions()
            val phoneA = engine(a, learnA)
            val phoneB = engine(b, learnB)

            val thread = "eg:due|transfer|سامي/وهمي|2026-10-10|pay"
            val today = AlertCandidate(AlertKind.DUE_TODAY, thread, "قسط وهمي النهارده", "500.00 ج.م", amountMinor = 50_000, monthScaleMinor = 1_000_000)
            assertEquals(1, phoneA.run(listOf(today), LocalMoment("2026-10-10", 9)).posts.size)

            // على السيرفر: على الحساب، معرّف السطر متشفّر، إيصال واحد — ومفيش ولا مستند للتعلّم
            val inboxDocs = a.collection("alertInbox").get(Source.SERVER).documents
            assertEquals(listOf(receiptDocId(thread)), inboxDocs.map { it.id })
            assertEquals(1, a.collection("alertReceipts").get(Source.SERVER).documents.size)
            assertEquals(0, FirestoreSpace.forSpace(a.db, uid, "eg").collection("alertInbox").get(Source.SERVER).documents.size, "مش جوه البلد")

            eventually("إيصال الأول يوصل التاني") { FirestoreAlertReceipts(b).listAll().any { it.eventKey == today.eventKey } }
            assertTrue(phoneB.run(listOf(today), LocalMoment("2026-10-10", 10)).posts.isEmpty(), "التاني ما بيبعتش تاني")
            assertEquals(1, learnA.load()[AlertKind.DUE_TODAY]?.shown)
            assertEquals(emptyMap(), learnB.load(), "التعلّم ما بيتزامنش")

            // القفل من الأول بيوصل التاني، والسطر بيفضل في الصفحة بس
            phoneA.setGroupEnabled(AlertGroup.DUES, false)
            eventually("القفل يوصل التاني") { AlertGroup.DUES in FirestoreAlertSettings(b).disabledGroups() }
            val overdue = today.copy(kind = AlertKind.DUE_OVERDUE)
            val muted = phoneB.run(listOf(overdue), LocalMoment("2026-10-11", 9))
            assertTrue(muted.posts.isEmpty() && muted.inAppWindows.isEmpty())
            eventually("السطر المقفول يوصل الأول") { phoneA.inbox().singleOrNull()?.let { it.muted && it.entry.kind == AlertKind.DUE_OVERDUE } == true }

            // سطر كتبته نسخة أحدث من التطبيق (نوع ما نعرفوش) ⇒ بيتخطّى، والصفحة ما بتقعش
            a.collection("alertInbox").document("newer").set(AlertCodecs.alertInbox.toStore(overdue.let { c ->
                app.masroufy.port.AlertInboxEntry("newer", "newer|x", c.kind, c.flow, "x", "x", app.masroufy.core.mutedAlertDecision(c.kind), "2026-10-11T00:00:00.000Z")
            }) + ("kind" to "kind_from_newer_app"))
            eventually("السطر الغريب يوصل التاني") { syncB.documentsInMemory > 0 && b.mirror?.docsOf("alertInbox")?.containsKey("newer") == true }
            assertEquals(listOf(thread), FirestoreAlertInbox(b).listAll().map { it.threadKey })

            // اتحل ⇒ بيختفي من الجوالين
            phoneB.run(emptyList(), LocalMoment("2026-10-12", 9))
            eventually("المسح يوصل السيرفر") { a.collection("alertInbox").get(Source.SERVER).documents.map { it.id } == listOf("newer") }
            syncB.stop()
            scope.cancel()
        }
    }
}
