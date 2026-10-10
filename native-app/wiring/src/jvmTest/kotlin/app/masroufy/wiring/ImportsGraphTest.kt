package app.masroufy.wiring

import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.QueuedSms
import app.masroufy.ui.screens.imports.SmsRangeKind
import app.masroufy.ui.screens.imports.bankSmsUi
import app.masroufy.ui.screens.imports.SmsStatus
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * منطقة «الاستيراد» على مستودعات الذاكرة: من غير منافذ الجهاز (الآيفون · الاختبار) ⇒ «غير متاح» واللصق شغال، ومع صندوق رسايل ⇒ التسجيل لوحده
 * بنفس تشغيلة الخلفية · المستني بسببه · «سجّل الكل» · اللي اتسجل النهارده. **الرسايل والأسامي والمبالغ مخترعة** (نفس أشكال `golden/sms.json`).
 */
class ImportsGraphTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01")

    private fun graph(inbox: MemorySmsInbox? = null, payday: Int? = null): Pair<ImportsGraph, SpaceRepositories> {
        val repos = memorySpaceRepositories(listOf(bank, cash), profile = payday?.let { emptyProfile().copy(payday = it) })
        val base = memoryEnv()
        val env = DeviceEnv(
            base.clock, base.ids, base.today, base.hourNow, base.nowMillis, base.interactions, base.usualHours, base.seenAlerts, base.http, base.feedCache,
            smsInbox = inbox,
        )
        val session = object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = listOf(saudi to repos)
            override fun switchSpace(spaceId: String) = false
        }
        val feeds = LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)
        return SpaceGraph(saudi, repos, env, session, feeds).imports as ImportsGraph to repos
    }

    private fun sms(id: String, body: String) = QueuedSms(id, "TESTBANK", "2026-10-09T06:00:00Z", body)

    @Test fun withoutDevicePortsBankMessagesAreUnavailableButPastingWorks() {
        val (g, _) = graph()
        assertNull(g.sms, "مفيش صندوق ⇒ الشاشة بتقول «الآيفون لا يسمح»")
        assertNull(g.pdf, "مفيش قارئ PDF على الجهاز ده")
        val reader = assertNotNull(g.readSms, "السعودية ليها قارئ رسايل")
        assertFalse(reader.available, "قراية فترة محتاجة الجوال")
        // نفس الرسايل المخترعة في `golden/smsFlow.json`: تاريخ كامل ⇒ بتتقري
        val batch = reader.paste("شراء 25 SAR عند محل تجريبي بتاريخ 2026-09-10")
        assertEquals(listOf(2500L), batch.rows.map { it.amountMinor })
        // ⚠️ ناقص في المنطق: الملصوق مالوش «تاريخ وصول» ⇒ شكل البنك بالتاريخ القصير (26/9/18) بيتعدّى بسببه (`ReadBankSms.paste`)
        val short = reader.paste("شراء PoS\nعبر1111;مدى\nبـSR 24\nلـTEST STORE\n26/9/18 09:35")
        assertTrue(short.rows.isEmpty())
        assertEquals(1, short.skipped.size, "بيتعرض في «تجاوزناها» بسببه، مش بيضيع في صمت")
        assertEquals(SmsStatus.UNAVAILABLE, bankSmsUi(null, emptyList(), emptySet()).status)
    }

    @Test fun periodRangesStartAtTheFinancialMonth() = runBlocking<Unit> {
        val ranges = graph(payday = 28).first.smsRanges().associate { it.kind to it.from }
        assertEquals("2026-09-28", ranges[SmsRangeKind.MONTH], "الشهر المالي من يوم ٢٨")
        assertEquals("2026-10-03", ranges[SmsRangeKind.WEEK])
        assertEquals("2026-09-10", ranges[SmsRangeKind.DAYS30])
    }

    @Test fun csvTablesShowTheFirstLinesAndCountAll() {
        val g = graph().first
        val table = assertNotNull(g.csvTable("التاريخ,Description,مدين\n02/09/2026,POS أ,10.00\n03/09/2026,POS ب,5.00\n04/09/2026,ج,1.00", rows = 2))
        assertEquals(listOf("التاريخ", "Description", "مدين"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(3, table.lines)
        assertNull(g.csvTable(""), "ملف فاضي ⇒ مش CSV مقروء")
    }

    @Test fun clearMessagesRecordThemselvesAndTheRestWaitsWithItsReason() = runBlocking<Unit> {
        val inbox = MemorySmsInbox(
            listOf(
                sms("m1", "شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/09"),
                sms("m2", "Purchase\nبـSR 30\nلدى:TEST SHOP\n26/10/09"),
                sms("m3", "رسالة\nبـSR 24\n26/10/09"),
            ),
            available = true,
        )
        inbox.enable(listOf("TESTBANK"))
        val (g, repos) = graph(inbox)
        val sms = assertNotNull(g.sms)
        val overview = sms.overview(record = true)
        assertEquals(1, overview.fresh.size, "الواضحة اتسجلت لوحدها بنفس تشغيلة الخلفية")
        assertEquals(bank.id, overview.senderWallets["TESTBANK"], "حساب بنك واحد في البلد ⇒ رسايله فيه")
        val today = sms.recordedToday()
        val ui = bankSmsUi(overview, today, overview.fresh.toSet())
        assertEquals(SmsStatus.READING, ui.status)
        assertEquals(listOf(2500L), ui.recorded.map { it.amountMinor })
        assertTrue(ui.recorded.single().sheen)
        assertEquals(listOf(3000L), ui.confirm.map { it.amountMinor }, "شكل مش معروف ⇒ مستنية تأكيدك")
        assertNotNull(ui.confirm.single().reason)
        assertEquals(1, ui.failed.size, "من غير تاجر ⇒ «رسالة لم تُفهم» بسببها")

        assertEquals(1, sms.record(emptyMap(), emptySet()), "«سجّل الكل» بيسجّل اللي اتعرض بس")
        assertEquals(listOf(2500L, 3000L), repos.transactions.listByDateRange("2026-10-09", "2026-10-09").map { it.amountMinor }.sorted())
        val after = bankSmsUi(sms.overview(record = false), sms.recordedToday(), emptySet())
        assertTrue(after.confirm.isEmpty())
        sms.dismiss(after.failed.map { it.messageId })
        assertEquals(0, bankSmsUi(sms.overview(record = false), emptyList(), emptySet()).waitingCount)
    }
}
