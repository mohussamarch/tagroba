package app.masroufy.data

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.NotificationReceipt
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.port.AlertInboxEntry
import app.masroufy.port.AlertInboxStore
import app.masroufy.port.AlertReceiptStore
import app.masroufy.port.AlertSettingsStore
import app.masroufy.usecase.AlertEngineDeps
import app.masroufy.usecase.RunAlertEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * جوالين على نفس الحساب (OVERRIDES §61 — رد المالك (١)، جلسة 18): **الإعدادات والصفحة والإيصالات بتتزامن، والتعلّم على كل جوال لوحده.**
 * «السحابة» هنا مستندات في الذاكرة **بالمحوّلات نفسها** (كتابة merge + مسح الفاضي + تخطي اللي ما يتقريش) زي `FirestoreAlerts.kt`
 * بالظبط — ونفس السيناريو على فايربيز الحقيقي في `AlertsOnFirestoreTest` (المحاكي). كل الأسماء والمبالغ مخترعة.
 */
class AlertSyncTest {
    /** مستندات مشتركة بين الجوالين — زي مجموعات `users/{uid}` على فايربيز. */
    private class Cloud {
        val docs = HashMap<String, LinkedHashMap<String, Map<String, Any?>>>()

        fun <T> save(codec: DocCodec<T>, value: T) {
            val group = docs.getOrPut(codec.group) { LinkedHashMap() }
            val id = codec.id(value)
            group[id] = (group[id].orEmpty() - codec.omittedFields(value)) + codec.toStore(value)
        }

        fun <T : Any> list(codec: DocCodec<T>): List<T> = docs[codec.group].orEmpty().values.mapNotNull { codec.skippingUnreadable().decode(it) }

        fun remove(group: String, ids: List<String>) {
            docs[group]?.keys?.removeAll(ids.toSet())
        }
    }

    private class CloudSettings(private val cloud: Cloud) : AlertSettingsStore {
        override suspend fun disabledGroups() = cloud.list(AlertCodecs.alertSettings).filter { !it.enabled }.map { it.group }.toSet()

        override suspend fun setGroupEnabled(group: AlertGroup, enabled: Boolean) = cloud.save(AlertCodecs.alertSettings, AlertGroupSetting(group, enabled))
    }

    private class CloudReceipts(private val cloud: Cloud) : AlertReceiptStore {
        override suspend fun listAll() = cloud.list(AlertCodecs.alertReceipts)

        override suspend fun saveMany(receipts: List<NotificationReceipt>) = receipts.forEach { cloud.save(AlertCodecs.alertReceipts, it) }
    }

    private class CloudInbox(private val cloud: Cloud) : AlertInboxStore {
        override suspend fun listAll() = cloud.list(AlertCodecs.alertInbox)

        override suspend fun save(entry: AlertInboxEntry) = cloud.save(AlertCodecs.alertInbox, entry)

        override suspend fun remove(threadKeys: List<String>) = cloud.remove(AlertCodecs.alertInbox.group, threadKeys.map(::receiptDocId))
    }

    /** جوال: المتزامن من السحابة، والتعلّم في ذاكرته هو بس (على الجهاز الحقيقي: `AndroidAlertInteractionStore`/`IosUsualHoursStore`…). */
    private class Phone(cloud: Cloud) {
        val interactions = MemoryAlertInteractions()
        val hours = MemoryUsualHours()
        val engine = RunAlertEngine(
            AlertEngineDeps(CloudSettings(cloud), interactions, hours, CloudReceipts(cloud), CloudInbox(cloud), FixedClock("2026-10-10T06:00:00.000Z")),
        )
    }

    private val thread = "due|installment|ip-1|2026-10-10|pay"
    private val today = AlertCandidate(AlertKind.DUE_TODAY, thread, "قسط وهمي ميعاده النهارده", "500.00 ر.س", amountMinor = 50_000, monthScaleMinor = 1_000_000)
    private val overdue = today.copy(kind = AlertKind.DUE_OVERDUE, title = "قسط وهمي متأخر يوم")

    @Test
    fun alertSentOnOnePhoneIsNotSentAgainOnTheOther() = runBlocking<Unit> {
        val cloud = Cloud()
        val a = Phone(cloud)
        val b = Phone(cloud)
        val first = a.engine.run(listOf(today), LocalMoment("2026-10-10", 9))
        assertEquals(listOf(listOf(today.eventKey)), first.posts.map { it.eventKeys }, "الجوال الأول بعته")
        val second = b.engine.run(listOf(today), LocalMoment("2026-10-10", 10))
        assertTrue(second.posts.isEmpty(), "الجوال التاني شاف الإيصال ⇒ ما بيبعتش تاني")
        assertEquals(listOf(thread), b.engine.inbox().map { it.entry.threadKey }, "نفس السطر في الصفحة على الجوالين")
        // التعلّم ما اتزامنش: «اتعرض» اتعد على الجوال اللي بعت بس
        assertEquals(1, a.interactions.load()[AlertKind.DUE_TODAY]?.shown)
        assertEquals(emptyMap(), b.interactions.load())

        // الفتح على الأول بيبان على التاني (الصفحة متزامنة)، والعدّ على الأول بس — والتاني ما بيعدّوش تاني
        a.engine.opened(thread)
        assertNotNull(b.engine.inbox().single().entry.openedAt)
        b.engine.opened(thread)
        assertEquals(1, a.interactions.load()[AlertKind.DUE_TODAY]?.opened)
        assertEquals(emptyMap(), b.interactions.load())

        // الدرجة الجاية (متأخر) بتتبعت مرة واحدة بس، من أي جوال لحقها الأول
        assertEquals(1, b.engine.run(listOf(overdue), LocalMoment("2026-10-11", 9)).posts.size)
        assertTrue(a.engine.run(listOf(overdue), LocalMoment("2026-10-11", 10)).posts.isEmpty())
        assertEquals(listOf(AlertKind.DUE_OVERDUE), a.engine.inbox().map { it.entry.kind }, "الدرجة الأعلى حلت محل الأقل على الجوالين")
    }

    @Test
    fun mutedGroupOnOnePhoneIsMutedOnTheOtherAndStaysOnThePageOnly() = runBlocking<Unit> {
        val cloud = Cloud()
        val a = Phone(cloud)
        val b = Phone(cloud)
        a.engine.setGroupEnabled(AlertGroup.DUES, false)
        val run = b.engine.run(listOf(today), LocalMoment("2026-10-10", 9))
        assertTrue(run.posts.isEmpty() && run.inAppWindows.isEmpty(), "اتقفلت على الأول ⇒ مقفولة على التاني: ولا شريط ولا نافذة")
        val line = b.engine.inbox().single()
        assertTrue(line.muted, "في الصفحة بس ومعلّمة مقفولة (رد المالك (٢))")
        assertTrue(a.engine.inbox().single().muted)
        assertTrue(cloud.list(AlertCodecs.alertReceipts).isEmpty(), "المقفول ما بيكتبش إيصال «اتبعت»")
        assertTrue(a.engine.run(listOf(today), LocalMoment("2026-10-10", 10)).posts.isEmpty())
        assertEquals(emptyMap(), a.interactions.load(), "المقفول ما بيتعدّش «اتعرض»")
        assertEquals(emptyMap(), b.interactions.load())

        // اتفتحت تاني من الجوال التاني ⇒ الدرجة اللي ما اتبعتتش بتتبعت مرة واحدة على أي جوال
        b.engine.setGroupEnabled(AlertGroup.DUES, true)
        assertEquals(1, a.engine.run(listOf(today), LocalMoment("2026-10-10", 11)).posts.size)
        assertTrue(b.engine.run(listOf(today), LocalMoment("2026-10-10", 12)).posts.isEmpty())
        assertEquals(setOf("dues"), cloud.docs[AlertCodecs.alertSettings.group]!!.keys, "مستند واحد للمجموعة كتبه الجوالين")
    }

    @Test
    fun usualHoursStayOnEachPhone() = runBlocking<Unit> {
        val cloud = Cloud()
        val a = Phone(cloud)
        val b = Phone(cloud)
        repeat(5) { b.engine.appOpened(LocalMoment("2026-10-10", 21)) }
        assertTrue(b.hours.load().learned)
        assertEquals(0, a.hours.load().opens.sum(), "ساعاتك على جوال ما بتروحش للتاني")
        assertTrue(cloud.docs.keys.all { it.startsWith("alert") } && cloud.docs.keys.none { "hour" in it.lowercase() })
    }
}
