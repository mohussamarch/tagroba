package app.masroufy.usecase

import app.masroufy.core.LocalMoment
import app.masroufy.core.R06Key
import app.masroufy.core.SMS_STATE_TAG_PREFIX
import app.masroufy.core.SmsStateNotice
import app.masroufy.core.isLockSafe
import app.masroufy.core.isoInstantMillis
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryDeviceNotifier
import app.masroufy.memory.MemoryNoticeMemory
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PLAN-0.6 §13 (ج) — إشعارات حالة القراية في الخلفية: التلات حالات بس (شكل جديد مستني · الإذن اتسحب · رسايل فاتته)، كل واحدة مرة لحد ما
 * الحالة تتغير، والعملية اللي اتسجلت لوحدها **من غير إشعار**. كل الرسايل مخترعة.
 */
class SmsStateNoticesTest {
    private val now = LocalMoment("2026-10-07", 12)
    private val notifier = MemoryDeviceNotifier()
    private val memory = MemoryNoticeMemory()
    private var permission = true
    private var lastRead: Long? = null

    private fun notices(world: SmsWorld) = SmsStateNotices(
        SmsStateNoticesDeps({ world.memory.sync().copy(permission = permission) }, { lastRead }, memory, notifier),
    )

    private fun cycle(world: SmsWorld) = RunBackgroundCycle(BackgroundCycleDeps(sms = world.auto(), notifier = notifier, smsNotices = notices(world)))

    private fun tags() = notifier.posted.map { it.tag }

    @Test fun autoRecordedOperationNeverNotifies() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        val result = cycle(world).run(now)
        assertEquals(2, result.sms!!.recorded)
        assertTrue(notifier.posted.isEmpty(), "المالك: العملية اللي اتسجلت لوحدها ما لهاش إشعار")
        assertTrue(result.smsNotices.isEmpty())
    }

    @Test fun newShapeNotifiesOnceUntilAnotherNewShapeArrives() = runBlocking<Unit> {
        val world = SmsWorld(learnOnReceive = false).enable()
        world.receive(sms("m1", CAFE))
        val first = cycle(world).run(now)
        assertEquals(listOf(SmsStateNotice.NEW_SHAPE), first.smsNotices)
        val post = notifier.posted.single()
        assertEquals(SMS_STATE_TAG_PREFIX + "new_shape", post.tag)
        assertEquals(uiText(R06Key.NOTIFY_SMS_NEW_SHAPE), post.notice.body)
        assertTrue(isLockSafe(post.notice.title) && isLockSafe(post.notice.body), "${post.notice}")
        assertFalse(post.notice.body.contains("TEST CAFE") || post.notice.body.contains("25"), "من غير اسم محل ولا مبلغ")

        notifier.posted.clear()
        assertTrue(cycle(world).run(now).smsNotices.isEmpty(), "نفس الحالة ما بتتبعتش تاني")
        assertTrue(notifier.posted.isEmpty())

        world.receive(sms("m2", MART))
        assertEquals(listOf(SmsStateNotice.NEW_SHAPE), cycle(world).run(now).smsNotices, "رسالة شكل جديد تانية = الحالة اتغيرت")
    }

    @Test fun permissionRevokedNotifiesOncePerRevocation() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        permission = false
        assertEquals(listOf(SmsStateNotice.PERMISSION_OFF), cycle(world).run(now).smsNotices)
        assertEquals(uiText(R06Key.NOTIFY_SMS_PERMISSION_OFF), notifier.posted.single().notice.body)
        assertTrue(cycle(world).run(now).smsNotices.isEmpty(), "مرة واحدة لنفس السحب")

        permission = true
        assertTrue(cycle(world).run(now).smsNotices.isEmpty(), "الإذن رجع ⇒ مفيش إشعار")
        permission = false
        assertEquals(listOf(SmsStateNotice.PERMISSION_OFF), cycle(world).run(now).smsNotices, "اتسحب تاني = حالة جديدة")
        assertEquals(1, notifier.posted.size, "نفس المعرّف بيحل محل القديم في الشريط")
    }

    @Test fun missedMessagesUseTheAwayCardLogicAndDoNotRepeat() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        val n = notices(world)
        lastRead = isoInstantMillis("2026-10-07T09:00:00Z")
        world.memory.receive(sms("m1", CAFE, at = "2026-10-07T10:00:00Z"))
        assertEquals(listOf(SmsStateNotice.MISSED), n.check(null, smsFailed = true), "وصلت بعد آخر قراية وما اتقرتش")
        assertEquals(uiText(R06Key.NOTIFY_SMS_MISSED), notifier.posted.single().notice.body)
        assertTrue(n.check(null, smsFailed = true).isEmpty(), "نفس الرسايل ما بتتبعتش تاني")

        world.memory.receive(sms("m2", MART, at = "2026-10-07T11:00:00Z"))
        assertEquals(listOf(SmsStateNotice.MISSED), n.check(null, smsFailed = true), "رسالة أحدث فاتته = الحالة اتغيرت")

        lastRead = isoInstantMillis("2026-10-07T11:30:00Z")
        assertTrue(n.check(null, smsFailed = true).isEmpty(), "كله أقدم من آخر قراية ⇒ مفيش فاتته (زي الكارت)")
    }

    @Test fun nothingIsMissedWhenTheCycleReadTheMessages() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        world.receive(sms("m1", CAFE, at = "2026-10-07T10:00:00Z"))
        lastRead = isoInstantMillis("2026-10-07T09:00:00Z")
        val result = cycle(world).run(now)
        assertFalse(result.smsFailed)
        assertTrue(notifier.posted.none { it.tag == SmsStateNotice.MISSED.tag }, "الدورة قرت ⇒ مش «فاتته»")
    }

    @Test fun blockedNotificationsSendNothingAndAreNotRemembered() = runBlocking<Unit> {
        val world = SmsWorld().enable()
        notifier.allowed = false
        permission = false
        assertTrue(cycle(world).run(now).smsNotices.isEmpty())
        assertTrue(notifier.posted.isEmpty(), "إذن الإشعارات مقفول ⇒ ولا حاجة")
        notifier.allowed = true
        assertEquals(listOf(SmsStateNotice.PERMISSION_OFF), cycle(world).run(now).smsNotices, "أول ما يتسمح بيتبعت")
    }

    @Test fun readingTurnedOffClearsEverything() = runBlocking<Unit> {
        val world = SmsWorld(learnOnReceive = false).enable()
        world.receive(sms("m1", CAFE))
        cycle(world).run(now)
        val off = SmsStateNotices(
            SmsStateNoticesDeps({ SmsInboxState(false, false, false, 0, emptyList(), emptyList()) }, { null }, memory, notifier),
        )
        assertTrue(off.check(null, smsFailed = false).isEmpty(), "القراية مقفولة بإيد المالك ⇒ مفيش إشعار")
        notifier.posted.clear()
        assertEquals(listOf(SmsStateNotice.NEW_SHAPE), cycle(world).run(now).smsNotices, "بعد ما اتقفلت واتفتحت الحالة بتبدأ من الأول")
    }

    @Test fun everyVariantIsLockSafe() {
        for (kind in SmsStateNotice.entries) {
            val notice = app.masroufy.core.smsStateNotice(kind)
            assertTrue(isLockSafe(notice.title) && isLockSafe(notice.body), "$kind")
            assertTrue(kind.tag.startsWith(SMS_STATE_TAG_PREFIX))
        }
    }
}
