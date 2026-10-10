package app.masroufy.usecase

import app.masroufy.core.LocalMoment
import app.masroufy.core.UserSetting
import app.masroufy.core.Wallet
import app.masroufy.core.Currency
import app.masroufy.core.parseBankSms
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §83 (المالك 2026-10-10): «اقرأ اللي فات؟» أول ربط · «من إمتى» لو فيه رسايل قبل رصيد البداية · وبعدها **بيكمّل لوحده من آخر نقطة** — حتى
 * الفترة اللي القراية كانت واقفة فيها أو البرنامج ما كانش متثبت — من غير ما يسجل حاجة مرتين. كل الرسايل مخترعة.
 */
class SmsHistoryFlowTest {
    private fun purchase(id: String, date: String, minor: Long) = HistoryTxn(id, date, minor, false, "TEST MART", 0).sms

    private suspend fun HistoryWorld.point(): UserSetting.SmsReadPoint? =
        settings.listAll().filterIsInstance<UserSetting.SmsReadPoint>().singleOrNull { it.spaceId == "sa" }

    private val moment = LocalMoment("2026-07-05", 10)

    @Test fun noQuestionWhenThePhoneHasNothingOlderThanTheLink() = runBlocking<Unit> {
        val w = HistoryWorld()
        w.memory.deliver(purchase("new", "2026-07-02", 2_500))
        w.memory.enable(listOf("TESTBANK"))
        assertNull(w.history().question("sa"))
    }

    @Test fun noMeansFromNowOnAndTheQuestionDoesNotComeBack() = runBlocking<Unit> {
        val w = HistoryWorld()
        val old = purchase("old", "2026-05-02", 2_500)
        val fresh = purchase("fresh", "2026-07-02", 4_000)
        w.memory.deliver(old, fresh)
        w.memory.preLearn("sa", ::parseBankSms, old)
        w.memory.enable(listOf("TESTBANK"))
        val history = w.history()
        val q = assertNotNull(history.question("sa"))
        assertFalse(q.askStart, "opening balance 2025-01-01 is older than every message ⇒ no «from when?»")
        history.decline("sa", q.senders)
        assertNull(history.question("sa"))
        assertEquals(listOf("testbank"), w.point()?.asked)
        w.cycle().run(moment)
        assertEquals(listOf(4_000L), w.space.all().map { it.amountMinor }, "only what came after the link")
    }

    @Test fun yesWithoutOlderThanOpeningReadsEverythingFromTheOldestMessage() = runBlocking<Unit> {
        val w = HistoryWorld()
        val messages = listOf(purchase("a", "2025-02-01", 1_100), purchase("b", "2025-08-01", 1_200), purchase("c", "2026-03-01", 1_300))
        w.memory.deliver(*messages.toTypedArray())
        w.memory.enable(listOf("TESTBANK"))
        val history = w.history()
        val q = assertNotNull(history.question("sa"))
        assertEquals("2025-02-01" to false, q.oldestDay to q.askStart)
        val report = history.start("sa", q.senders, null)
        // أول رسالة من الشكل بتستنى، والباقي بنفس الشكل في نفس السطر (+2)
        assertEquals(listOf(3), report.shapes.map { it.count })
        assertEquals("c", report.shapes.single().messageId, "the newest message stands for its shape")
        val done = history.confirmShapes(report.shapeMessageIds)
        assertEquals(3, done.recorded)
        assertEquals(listOf(1_100L, 1_200L, 1_300L), w.space.all().map { it.amountMinor }.sorted())
        assertTrue(done.shapes.isEmpty())
    }

    @Test fun aBankAddedLaterIsAskedAboutItsOwnHistory() = runBlocking<Unit> {
        val w = HistoryWorld(space = SmsSpace(wallets = listOf(CASH, HISTORY_BANK, Wallet("w-other", "بنك تاني", Currency.SAR, "bank", 0, "2026-01-01"))))
        w.memory.deliver(purchase("t1", "2026-06-01", 1_000), purchase("t2", "2026-06-01", 2_000).copy(sender = "OTHERBANK"))
        w.memory.enable(listOf("TESTBANK"))
        val history = w.history()
        history.decline("sa", assertNotNull(history.question("sa")).senders)
        w.memory.enable(listOf("TESTBANK", "OTHERBANK"))
        assertEquals(listOf("OTHERBANK"), assertNotNull(history.question("sa")).senders)
    }

    @Test fun reEnablingAfterAStopReadsTheGap() = runBlocking<Unit> {
        val w = HistoryWorld()
        val before = purchase("m1", "2026-07-02", 1_500)
        w.memory.preLearn("sa", ::parseBankSms, before)
        w.memory.enable(listOf("TESTBANK"))
        w.memory.deliver(before)
        w.cycle().run(moment)
        w.memory.disable()
        // وصلت والقراية واقفة
        w.memory.deliver(purchase("m2", "2026-07-03", 2_500))
        w.cycle().run(moment)
        assertEquals(1, w.space.all().size, "nothing is read while it is off")
        w.memory.enable(listOf("TESTBANK"))
        w.cycle().run(moment)
        assertEquals(listOf(1_500L, 2_500L), w.space.all().map { it.amountMinor }.sorted(), "the stopped period is read after re-linking")
    }

    @Test fun afterAReinstallTheBackgroundCatchesUpFromTheStoredPointWithoutDoubling() = runBlocking<Unit> {
        val w = HistoryWorld()
        val a = purchase("a", "2026-07-02", 1_500)
        w.memory.preLearn("sa", ::parseBankSms, a)
        w.memory.enable(listOf("TESTBANK"))
        w.memory.deliver(a)
        w.cycle().run(moment)
        assertEquals(1, w.space.all().size)
        val stored = assertNotNull(w.point(), "the read point is kept per space in the account").point
        assertEquals("2026-07-02T10:00:00Z", stored)
        // البرنامج اتمسح يوم 3 واتثبت يوم 20 — رسالة يوم 10 وصلت وهو مش متثبت
        val gap = purchase("b", "2026-07-10", 2_500)
        w.memory.deliver(gap)
        val fresh = w.memory.reinstall("2026-07-20T09:00:00Z")
        fresh.enable(listOf("TESTBANK"))
        val result = w.cycle(fresh).run(moment)
        assertEquals(1, w.space.all().size, "a new device has not learned the shape yet ⇒ the gap waits once")
        assertEquals(listOf("b"), fresh.sync().messages.map { it.id }, "the gap is read; «a» was already recorded ⇒ removed as a duplicate")
        assertEquals(1, result.sms?.duplicates)
        assertEquals(listOf("b"), result.sms?.newShape)
        fresh.preLearn("sa", ::parseBankSms, gap)
        w.cycle(fresh).run(moment)
        w.cycle(fresh).run(moment)
        assertEquals(listOf(1_500L, 2_500L), w.space.all().map { it.amountMinor }.sorted(), "recorded once each")
        assertEquals(0, fresh.sync().count)
    }

    @Test fun theReadPointIsTheOldestMessageStillWaiting() = runBlocking<Unit> {
        val w = HistoryWorld()
        w.memory.enable(listOf("TESTBANK"))
        // شكل لسه ما اتعلّمش ⇒ بتستنى في الصندوق ⇒ النقطة ما تعدّيهاش (لو البرنامج اتمسح، الرسالة دي بتتقري تاني)
        w.memory.deliver(purchase("w1", "2026-07-02", 1_500), purchase("w2", "2026-07-03", 1_600))
        w.cycle().run(moment)
        assertEquals("2026-07-02T10:00:00Z", w.point()?.point)
        assertEquals(emptyList(), w.point()?.asked, "the background never marks a bank as asked")
    }
}
