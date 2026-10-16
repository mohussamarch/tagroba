package app.masroufy.wiring

import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.Wallet
import app.masroufy.core.countryPack
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.ui.screens.imports.SmsDeps
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * r041c — رسايل البنك للشاشات (`SmsGraph`) **برّه خيط الواجهة** (`DeviceEnv.work`): «اسحب لتحت» كان بيعلّق البرنامج كله و«إعداد القراءة»
 * بتفضل بيضا على جوال فيه آلاف الرسايل. و`recordNew`/`settings` الخفاف بيدّوا نفس اللي `overview` كان بيدّيه للشاشتين. **كل حاجة مخترعة.**
 */
class SmsGraphWorkTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01")
    private val cafe = QueuedSms("m1", "TESTBANK", "2026-10-09T06:00:00Z", "شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/09")
    private val unclear = QueuedSms("m2", "TESTBANK", "2026-10-09T06:05:00Z", "رسالة\nبـSR 24\n26/10/09")

    /** بيسجّل اسم الخيط اللي الصندوق اتقرا عليه. */
    private class ThreadSpy(private val inner: SmsInboxPort) : SmsInboxPort by inner {
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun sync(): SmsInboxState {
            threads += Thread.currentThread().name
            return inner.sync()
        }
    }

    private suspend fun world(work: CoroutineContext?): Pair<SmsDeps, ThreadSpy> {
        val memory = MemorySmsInbox(listOf(cafe, unclear), available = true)
        memory.enable(listOf("TESTBANK"))
        memory.preLearn(saudi.id, assertNotNull(countryPack("SA").smsReader)::parse, cafe) // المالك أكّد الشكل ده قبل كده
        val spy = ThreadSpy(memory)
        val repos = memorySpaceRepositories(listOf(bank, cash))
        val b = memoryEnv()
        val env = if (work == null) {
            DeviceEnv(b.clock, b.ids, b.today, b.hourNow, b.nowMillis, b.interactions, b.usualHours, b.seenAlerts, b.http, b.feedCache, smsInbox = spy)
        } else {
            DeviceEnv(b.clock, b.ids, b.today, b.hourNow, b.nowMillis, b.interactions, b.usualHours, b.seenAlerts, b.http, b.feedCache, smsInbox = spy, work = work)
        }
        val session = object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = listOf(saudi to repos)
            override fun switchSpace(spaceId: String) = false
        }
        val feeds = LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)
        return assertNotNull(SpaceGraph(saudi, repos, env, session, feeds).imports.sms) to spy
    }

    private suspend fun touchEverything(sms: SmsDeps) {
        sms.recordNew()
        sms.settings()
        sms.overview(record = false)
        sms.readingOn()
        sms.recordedToday()
    }

    @Test fun bankMessagesAreReadOnAWorkerThreadNotTheCaller() = runBlocking<Unit> {
        val (sms, spy) = world(null)
        touchEverything(sms)
        assertTrue(spy.threads.isNotEmpty() && spy.threads.all { it.startsWith("DefaultDispatcher-worker") }, "${spy.threads}")
    }

    @Test fun testsCanInjectTheirOwnContext() = runBlocking<Unit> {
        val (sms, spy) = world(EmptyCoroutineContext)
        val caller = Thread.currentThread().name
        touchEverything(sms)
        assertTrue(spy.threads.isNotEmpty() && spy.threads.all { it == caller }, "${spy.threads} vs $caller")
    }

    @Test fun lightCallsGiveTheScreensWhatOverviewGave() = runBlocking<Unit> {
        val (sms, _) = world(EmptyCoroutineContext)
        assertEquals(null, sms.cachedSettings(), "لسه ما اتحسبتش")
        val fresh = sms.recordNew()
        assertEquals(1, fresh.size, "الشكل المتأكد اتسجل لوحده — ونفس معرّف اللمعة")
        assertTrue(sms.recordNew().isEmpty(), "السحبة التانية: مفيش جديد")
        val full = sms.overview(record = false)
        val light = sms.settings()
        assertEquals(full.inbox.senders, light.inbox.senders)
        assertEquals(full.inbox.enabled to full.inbox.permission, light.inbox.enabled to light.inbox.permission)
        assertEquals(full.senderWallets, light.senderWallets)
        assertEquals(full.unmapped, light.unmapped)
        assertTrue(light.reviews.isEmpty(), "من غير معاينة كل بنك")
        assertEquals(light, sms.cachedSettings(), "الفتحة الجاية بتعرضها على طول")
    }
}
